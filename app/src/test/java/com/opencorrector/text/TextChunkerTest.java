package com.opencorrector.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TextChunkerTest {

    /** Deterministic stand-in for the real llama.cpp tokenizer: 1 "token" per word. */
    private static final TextChunker.TokenCounter WORD_COUNTER = text -> text.trim().isEmpty()
            ? 0
            : text.trim().split("\\s+").length;

    @Test
    public void shortTextIsNotSplit() {
        String text = "Ceci est une phrase courte.";
        List<TextChunker.Chunk> chunks = TextChunker.split(text, 100, WORD_COUNTER);
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).text);
    }

    @Test
    public void reassemblyIsByteIdenticalToOriginal() {
        String text = "Premiere phrase. Deuxieme phrase! Troisieme phrase? Derniere phrase sans ponctuation finale";
        List<TextChunker.Chunk> chunks = TextChunker.split(text, 3, WORD_COUNTER);
        assertTrue("expected multiple chunks for a small maxTokens", chunks.size() > 1);

        List<String> asIs = new ArrayList<>();
        for (TextChunker.Chunk c : chunks) {
            asIs.add(c.text);
        }
        assertEquals(text, TextChunker.join(asIs, chunks));
    }

    @Test
    public void joinReinsertsSeparatorEvenWhenModelOutputDropsTrailingWhitespace() {
        // Simulates the real llama.cpp bug this test guards against: the model regenerates each
        // chunk's text and does not reliably reproduce trailing whitespace, so a naive
        // concatenation of "processed" outputs can glue two sentences together with no space.
        String text = "Premiere phrase. Deuxieme phrase.";
        List<TextChunker.Chunk> chunks = TextChunker.split(text, 2, WORD_COUNTER);
        assertTrue("expected at least 2 chunks", chunks.size() >= 2);

        List<String> modelOutputsWithNoTrailingSpace = new ArrayList<>();
        for (TextChunker.Chunk c : chunks) {
            modelOutputsWithNoTrailingSpace.add(c.text.trim());
        }

        String joined = TextChunker.join(modelOutputsWithNoTrailingSpace, chunks);
        assertTrue("chunks must not be glued together without a separator: '" + joined + "'",
                joined.contains("phrase. Deuxieme"));
    }

    @Test
    public void neverProducesAChunkOverTheLimitExceptForcedSingleWordOverflow() {
        String text = "one two three four five six seven eight nine ten.";
        int maxTokens = 4;
        List<TextChunker.Chunk> chunks = TextChunker.split(text, maxTokens, WORD_COUNTER);
        for (TextChunker.Chunk c : chunks) {
            assertTrue("chunk exceeded limit: '" + c.text + "'", WORD_COUNTER.count(c.text) <= maxTokens);
        }
    }

    @Test
    public void emptyTextProducesNoChunks() {
        assertEquals(0, TextChunker.split("", 100, WORD_COUNTER).size());
    }

    @Test
    public void joinOfSingleChunkEqualsOriginal() {
        String text = "Une seule phrase qui tient dans un seul morceau.";
        List<TextChunker.Chunk> chunks = TextChunker.split(text, 100, WORD_COUNTER);
        List<String> texts = new ArrayList<>();
        for (TextChunker.Chunk c : chunks) {
            texts.add(c.text);
        }
        assertEquals(text, TextChunker.join(texts, chunks));
    }
}
