package com.opencorrector.inference;

/**
 * Immutable snapshot of the inference settings for one generation session.
 * Built from {@link com.opencorrector.settings.AppPreferences} at call time so a change
 * in Settings takes effect on the next inference without restarting the service.
 */
public final class InferenceConfig {

    /** Context window given to llama.cpp. Kept below the model's max (8192) to save RAM/CPU. */
    public static final int CONTEXT_SIZE = 4096;

    /** Input tokens beyond this trigger chunking (see TextChunker). */
    public static final int MAX_INPUT_TOKENS = 900;

    /** Hard ceiling on generated tokens per chunk, to bound worst-case latency. */
    public static final int MAX_OUTPUT_TOKENS = 1024;

    public final int threads;
    public final boolean useGpu;
    public final long unloadDelayMillis;

    public InferenceConfig(int threads, boolean useGpu, long unloadDelayMillis) {
        this.threads = threads;
        this.useGpu = useGpu;
        this.unloadDelayMillis = unloadDelayMillis;
    }
}
