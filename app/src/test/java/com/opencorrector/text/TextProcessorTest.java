package com.opencorrector.text;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextProcessorTest {

    @Test
    public void shortSentenceIsNotLikelyLong() {
        assertFalse(TextProcessor.isLikelyLong("Ceci est une phrase courte."));
    }

    @Test
    public void veryLongParagraphIsLikelyLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("mot ");
        }
        assertTrue(TextProcessor.isLikelyLong(sb.toString()));
    }

    @Test
    public void chunkCountIsAtLeastOne() {
        assertTrue(TextProcessor.estimateChunkCount("court") >= 1);
    }
}
