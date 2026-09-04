package com.opencorrector.inference;

import android.content.Context;
import android.util.Log;

import com.opencorrector.nativebridge.LlamaNative;
import com.opencorrector.prompt.CorrectionMode;
import com.opencorrector.prompt.PromptManager;
import com.opencorrector.text.TextChunker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the single native llama.cpp context for the process and serializes every load/generate/
 * unload call through one background thread, since a llama.cpp context is not safe to use
 * concurrently. This is the only class that touches {@link LlamaNative} directly.
 *
 * Never logs prompt or generated text content — only sizes, token counts and error codes.
 */
public final class LlamaEngine {

    private static final String TAG = "LlamaEngine";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PromptManager promptManager;
    private final AtomicLong lastUsedAtMillis = new AtomicLong(0);
    private final AtomicBoolean generating = new AtomicBoolean(false);

    private volatile long nativeHandle = 0L;
    private volatile LlamaModel loadedModel = null;
    private volatile int loadedContextSize = 0;

    public LlamaEngine(Context context) {
        this.promptManager = new PromptManager(context);
    }

    public boolean isLoaded() {
        return nativeHandle != 0L;
    }

    public LlamaModel getLoadedModel() {
        return loadedModel;
    }

    public long getLastUsedAtMillis() {
        return lastUsedAtMillis.get();
    }

    /** Runs on the engine's background thread; blocks the caller until the model is ready. */
    public void loadBlocking(LlamaModel model, String modelPath, InferenceConfig config) throws EngineException {
        runBlocking(() -> {
            if (nativeHandle != 0L && loadedModel == model && loadedContextSize == InferenceConfig.CONTEXT_SIZE) {
                return null;
            }
            if (nativeHandle != 0L) {
                LlamaNative.unload(nativeHandle);
                nativeHandle = 0L;
                loadedModel = null;
            }
            long handle;
            try {
                handle = LlamaNative.loadModel(modelPath, config.threads, InferenceConfig.CONTEXT_SIZE, config.useGpu);
            } catch (OutOfMemoryError oom) {
                throw new EngineException(EngineException.Code.OUT_OF_MEMORY, "OOM while loading model", oom);
            }
            if (handle == 0L) {
                throw new EngineException(EngineException.Code.NATIVE_ENGINE_ERROR, "loadModel returned null handle");
            }
            nativeHandle = handle;
            loadedModel = model;
            loadedContextSize = InferenceConfig.CONTEXT_SIZE;
            Log.i(TAG, "Model loaded: " + model.id + " threads=" + config.threads + " gpu=" + config.useGpu);
            return null;
        });
    }

    /**
     * Corrects/reformulates the given text, chunking it if needed, and reports progress through
     * callback. Runs on the engine's background thread; the caller (LlamaService) is expected
     * to invoke this from its own background thread and marshal callback calls to the UI thread.
     */
    public void process(String languageCode, CorrectionMode mode, String text, InferenceCallback callback) {
        executor.execute(() -> {
            if (text == null || text.trim().isEmpty()) {
                callback.onError(EngineException.Code.EMPTY_TEXT);
                return;
            }
            if (nativeHandle == 0L) {
                callback.onError(EngineException.Code.MODEL_MISSING);
                return;
            }

            generating.set(true);
            lastUsedAtMillis.set(System.currentTimeMillis());
            try {
                List<TextChunker.Chunk> chunks = TextChunker.split(
                        text,
                        InferenceConfig.MAX_INPUT_TOKENS,
                        chunkText -> LlamaNative.tokenCount(nativeHandle, chunkText));

                if (chunks.isEmpty()) {
                    callback.onError(EngineException.Code.EMPTY_TEXT);
                    return;
                }
                if (chunks.size() > 1) {
                    callback.onChunkingStarted(chunks.size());
                }

                List<String> processedChunks = new ArrayList<>(chunks.size());
                for (int i = 0; i < chunks.size(); i++) {
                    if (!generating.get()) {
                        callback.onCancelled();
                        return;
                    }
                    String chunkText = chunks.get(i).text;
                    String prompt = promptManager.buildPrompt(languageCode, mode, chunkText);
                    final int chunkIndex = i;
                    final int totalChunks = chunks.size();
                    final StringBuilder chunkAccumulator = new StringBuilder();

                    String result = LlamaNative.generate(
                            nativeHandle,
                            prompt,
                            PromptManager.STOP_SEQUENCE,
                            InferenceConfig.MAX_OUTPUT_TOKENS,
                            piece -> {
                                chunkAccumulator.append(piece);
                                callback.onPartial(chunkAccumulator.toString(), chunkIndex, totalChunks);
                            });

                    if (result == null) {
                        callback.onError(EngineException.Code.NATIVE_ENGINE_ERROR);
                        return;
                    }
                    if (!generating.get()) {
                        // cancel() was called while this chunk was generating: the native side
                        // returned early with a partial chunk that must not be treated as done.
                        callback.onCancelled();
                        return;
                    }
                    processedChunks.add(result);
                }

                String finalText = TextChunker.join(processedChunks, chunks);
                callback.onComplete(finalText);
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "Out of memory during generation", oom);
                callback.onError(EngineException.Code.OUT_OF_MEMORY);
            } catch (RuntimeException e) {
                Log.e(TAG, "Native engine error during generation: " + e.getClass().getSimpleName());
                callback.onError(EngineException.Code.NATIVE_ENGINE_ERROR);
            } finally {
                generating.set(false);
                lastUsedAtMillis.set(System.currentTimeMillis());
            }
        });
    }

    /** Requests cancellation of the in-flight generation, if any. Safe to call from any thread. */
    public void cancel() {
        generating.set(false);
        long handle = nativeHandle;
        if (handle != 0L) {
            LlamaNative.cancel(handle);
        }
    }

    public boolean isGenerating() {
        return generating.get();
    }

    /** Unloads the native model, freeing its memory. Safe to call even if not loaded. */
    public void unloadBlocking() {
        try {
            runBlocking(() -> {
                if (nativeHandle != 0L) {
                    LlamaNative.unload(nativeHandle);
                    Log.i(TAG, "Model unloaded");
                }
                nativeHandle = 0L;
                loadedModel = null;
                loadedContextSize = 0;
                return null;
            });
        } catch (EngineException e) {
            Log.e(TAG, "Error while unloading (ignored): " + e.code);
        }
    }

    public void shutdown() {
        unloadBlocking();
        executor.shutdown();
    }

    private interface ThrowingCallable {
        Void call() throws EngineException;
    }

    private void runBlocking(ThrowingCallable task) throws EngineException {
        java.util.concurrent.atomic.AtomicReference<EngineException> error = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        executor.execute(() -> {
            try {
                task.call();
            } catch (EngineException e) {
                error.set(e);
            } catch (RuntimeException e) {
                error.set(new EngineException(EngineException.Code.NATIVE_ENGINE_ERROR, e.getMessage(), e));
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException(EngineException.Code.CANCELLED, "Interrupted while waiting for engine task");
        }
        if (error.get() != null) {
            throw error.get();
        }
    }
}
