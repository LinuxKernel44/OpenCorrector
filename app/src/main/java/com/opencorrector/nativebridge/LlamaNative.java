package com.opencorrector.nativebridge;

/**
 * Thin JNI wrapper around the llama.cpp based native inference engine.
 * All methods are blocking and must be called from a background thread.
 * The native side never logs prompt or generated text content, only sizes/timings.
 */
public final class LlamaNative {

    static {
        System.loadLibrary("opencorrector");
    }

    private LlamaNative() {
    }

    /**
     * Loads a GGUF model from disk and creates an inference context.
     *
     * @param modelPath absolute path to the .gguf file
     * @param nThreads  number of CPU threads to use for inference
     * @param nCtx      context window size in tokens
     * @param useGpu    whether to attempt Vulkan GPU offload (falls back to CPU if unavailable)
     * @return an opaque non-zero native handle, or 0 on failure
     */
    public static native long loadModel(String modelPath, int nThreads, int nCtx, boolean useGpu);

    /**
     * Tokenizes text using the model's own tokenizer and returns the token count.
     * Used to decide whether a text needs chunking before generation.
     */
    public static native int tokenCount(long handle, String text);

    /**
     * Runs generation for a fully formatted prompt (chat template already applied by
     * PromptManager) until stopSequence is produced or maxTokens is reached.
     *
     * @param handle       native handle from {@link #loadModel}
     * @param prompt       fully formatted prompt, including chat template markers
     * @param stopSequence sequence that marks end of the assistant turn (e.g. "<|im_end|>")
     * @param maxTokens    maximum number of tokens to generate
     * @param callback     invoked on the calling thread after every generated token piece;
     *                     may be null
     * @return the generated text, with the stop sequence stripped
     */
    public static native String generate(
            long handle,
            String prompt,
            String stopSequence,
            int maxTokens,
            GenerationCallback callback);

    /**
     * Requests cancellation of any generation currently running on this handle.
     * Safe to call from any thread; takes effect at the next token boundary.
     */
    public static native void cancel(long handle);

    /**
     * Frees the native context and the underlying model. The handle is invalid afterwards.
     */
    public static native void unload(long handle);

    /**
     * Callback invoked from native code during generation.
     */
    public interface GenerationCallback {
        void onToken(String piece);
    }
}
