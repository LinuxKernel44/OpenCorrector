package com.opencorrector.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits long input text into model-sized chunks that can be corrected independently and
 * reassembled without altering meaning. Splits only at sentence boundaries (or, as a last
 * resort for one very long sentence, at commas/spaces) so a chunk boundary never lands mid
 * word. The original whitespace between sentences is preserved verbatim so {@link #join}
 * reproduces the exact spacing/newlines of the input.
 */
public final class TextChunker {

    /** Splits after ., !, ? or newlines, keeping the separator attached to the following piece. */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+|\\n+");

    private TextChunker() {
    }

    public interface TokenCounter {
        int count(String text);
    }

    public static final class Chunk {
        public final String text;

        public Chunk(String text) {
            this.text = text;
        }
    }

    /**
     * @param maxTokens maximum tokens allowed per chunk (see InferenceConfig.MAX_INPUT_TOKENS)
     */
    public static List<Chunk> split(String text, int maxTokens, TokenCounter counter) {
        List<Chunk> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }

        List<String> sentences = splitIntoSentencePieces(text);

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String candidate = current.length() == 0 ? sentence : current + sentence;
            if (counter.count(candidate) <= maxTokens || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
                if (counter.count(current.toString()) > maxTokens) {
                    // Single sentence already exceeds the limit: flush what we can and
                    // hard-split the oversized sentence itself instead of losing text.
                    for (String piece : hardSplit(current.toString(), maxTokens, counter)) {
                        result.add(new Chunk(piece));
                    }
                    current.setLength(0);
                }
            } else {
                result.add(new Chunk(current.toString()));
                current.setLength(0);
                current.append(sentence);
            }
        }
        if (current.length() > 0) {
            result.add(new Chunk(current.toString()));
        }
        return result;
    }

    /** Reassembles processed chunks back into one text. Chunks already carry their own spacing. */
    public static String join(List<String> processedChunks) {
        StringBuilder sb = new StringBuilder();
        for (String chunk : processedChunks) {
            sb.append(chunk);
        }
        return sb.toString();
    }

    private static List<String> splitIntoSentencePieces(String text) {
        List<String> pieces = new ArrayList<>();
        Matcher matcher = SENTENCE_BOUNDARY.matcher(text);
        int last = 0;
        while (matcher.find()) {
            pieces.add(text.substring(last, matcher.end()));
            last = matcher.end();
        }
        if (last < text.length()) {
            pieces.add(text.substring(last));
        }
        return pieces;
    }

    /** Fallback for a single sentence longer than maxTokens: split on spaces, never mid-word. */
    private static List<String> hardSplit(String text, int maxTokens, TokenCounter counter) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("(?<=\\s)");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current + word;
            if (current.length() > 0 && counter.count(candidate) > maxTokens) {
                result.add(current.toString());
                current.setLength(0);
            }
            current.append(word);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}
