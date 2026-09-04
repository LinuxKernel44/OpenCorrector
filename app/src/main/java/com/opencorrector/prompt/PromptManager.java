package com.opencorrector.prompt;

import android.content.Context;

import com.opencorrector.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Loads the bilingual system prompts from res/raw (one file per language x mode, see
 * res/raw/system_prompt_{fr,en}_{correction,formal,concise}.txt) and assembles the final
 * ChatML-formatted prompt sent to the model. Prompts are never hardcoded in Java.
 */
public final class PromptManager {

    /** Marks the end of the assistant turn; also passed to LlamaNative as the stop sequence. */
    public static final String STOP_SEQUENCE = "<|im_end|>";

    private final Context appContext;
    private final Map<Integer, String> cache = new ConcurrentHashMap<>();

    public PromptManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Builds the full prompt (system + user turns) ready to pass to LlamaNative.generate.
     *
     * @param languageCode "fr" or "en" (see LanguageDetector)
     */
    public String buildPrompt(String languageCode, CorrectionMode mode, String userText) {
        String systemPrompt = loadSystemPrompt(languageCode, mode);
        StringBuilder sb = new StringBuilder(systemPrompt.length() + userText.length() + 64);
        sb.append("<|im_start|>system\n").append(systemPrompt).append(STOP_SEQUENCE).append('\n');
        sb.append("<|im_start|>user\n").append(userText).append(STOP_SEQUENCE).append('\n');
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    private String loadSystemPrompt(String languageCode, CorrectionMode mode) {
        int resId = resolveRawResource(languageCode, mode);
        String cached = cache.get(resId);
        if (cached != null) {
            return cached;
        }
        String text = readRawResource(resId);
        cache.put(resId, text);
        return text;
    }

    private int resolveRawResource(String languageCode, CorrectionMode mode) {
        boolean french = "fr".equals(languageCode);
        switch (mode) {
            case FORMAL:
                return french ? R.raw.system_prompt_fr_formal : R.raw.system_prompt_en_formal;
            case CONCISE:
                return french ? R.raw.system_prompt_fr_concise : R.raw.system_prompt_en_concise;
            case CORRECTION:
            default:
                return french ? R.raw.system_prompt_fr_correction : R.raw.system_prompt_en_correction;
        }
    }

    private String readRawResource(int resId) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = appContext.getResources().openRawResource(resId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    sb.append('\n');
                }
                sb.append(line);
                first = false;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read system prompt resource " + resId, e);
        }
        return sb.toString().trim();
    }
}
