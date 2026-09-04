package com.opencorrector.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits long input text into model-sized chunks that can be corrected independently and
 * reassembled without altering meaning. Splits only at sentence boundaries (or, as a last
 * resort for one very long sentence, at commas/spaces) so a chunk boundary never lands mid
 * word.
 *
 * Each chunk records the whitespace that originally followed it ({@link Chunk#trailingSeparator})
 * so {@link #join} can re-insert it explicitly between chunk OUTPUTS. This matters because the
 * model is not a literal passthrough: it regenerates each chunk's text and does not reliably
 * reproduce trailing whitespace, so joining raw model outputs back-to-back can glue the last
 * word of one chunk directly onto the first word of the next with no space at all.
 */
public final class TextChunker {

    /** Splits after ., !, ? or newlines, keeping the separator attached to the preceding piece. */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+|\\n+");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("\\s+$");

    private TextChunker() {
    }

    public interface TokenCounter {
        int count(String text);
    }

    public static final class Chunk {
        public final String text;
        public final String trailingSeparator;

        public Chunk(String text, String trailingSeparator) {
            this.text = text;
            this.trailingSeparator = trailingSeparator;
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
                        result.add(toChunk(piece));
                    }
                    current.setLength(0);
                }
            } else {
                result.add(toChunk(current.toString()));
                current.setLength(0);
                current.append(sentence);
            }
        }
        if (current.length() > 0) {
            result.add(toChunk(current.toString()));
        }
        return result;
    }

    /**
     * Reassembles processed chunk outputs back into one text, re-inserting each chunk's
     * recorded {@link Chunk#trailingSeparator} between them rather than trusting the model to
     * have preserved it. Each output is trimmed first since the model's own leading/trailing
     * whitespace is not meaningful (only the recorded separator is).
     */
    public static String join(List<String> processedChunks, List<Chunk> originalChunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < processedChunks.size(); i++) {
            sb.append(processedChunks.get(i).trim());
            if (i < originalChunks.size()) {
                sb.append(originalChunks.get(i).trailingSeparator);
            }
        }
        return sb.toString();
    }

    private static Chunk toChunk(String rawText) {
        Matcher trailing = TRAILING_WHITESPACE.matcher(rawText);
        if (trailing.find()) {
            return new Chunk(rawText.substring(0, trailing.start()), rawText.substring(trailing.start()));
        }
        return new Chunk(rawText, "");
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
