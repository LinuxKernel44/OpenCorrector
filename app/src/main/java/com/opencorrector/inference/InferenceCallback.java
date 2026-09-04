package com.opencorrector.inference;

/**
 * Delivered on the caller's own thread (LlamaService posts these back to the main thread
 * via a Handler before invoking the Activity's listener) while a correction runs.
 */
public interface InferenceCallback {

    /** Called once before the first chunk if the input had to be split. */
    void onChunkingStarted(int totalChunks);

    /** Streamed as tokens are generated for the chunk currently being processed. */
    void onPartial(String textSoFar, int chunkIndex, int totalChunks);

    /** Called once with the final, fully reassembled text. */
    void onComplete(String finalText);

    void onError(EngineException.Code code);

    void onCancelled();
}
