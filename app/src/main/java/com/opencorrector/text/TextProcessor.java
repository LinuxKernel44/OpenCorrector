package com.opencorrector.text;

import com.opencorrector.inference.InferenceConfig;

/**
 * Cheap, tokenizer-free heuristics used before the model is loaded (e.g. to show the "this
 * text is long" warning instantly). Once the model is loaded, LlamaEngine re-checks with the
 * real tokenizer via LlamaNative.tokenCount before deciding actual chunk boundaries.
 */
public final class TextProcessor {

    /** Rough average for French/English latin text; only used for the pre-load estimate. */
    private static final double CHARS_PER_TOKEN_ESTIMATE = 4.0;

    private TextProcessor() {
    }

    public static int estimateTokenCount(String text) {
        if (text == null) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / CHARS_PER_TOKEN_ESTIMATE));
    }

    public static boolean isLikelyLong(String text) {
        return estimateTokenCount(text) > InferenceConfig.MAX_INPUT_TOKENS;
    }

    public static int estimateChunkCount(String text) {
        int tokens = estimateTokenCount(text);
        return Math.max(1, (int) Math.ceil((double) tokens / InferenceConfig.MAX_INPUT_TOKENS));
    }
}
