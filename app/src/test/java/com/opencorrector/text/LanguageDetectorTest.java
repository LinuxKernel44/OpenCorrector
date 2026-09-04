package com.opencorrector.text;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LanguageDetectorTest {

    @Test
    public void detectsFrenchWithAccentsAndStopwords() {
        assertEquals(LanguageDetector.FRENCH,
                LanguageDetector.detect("Bonjour, je voudrais savoir si vous êtes disponible demain pour une réunion."));
    }

    @Test
    public void detectsFrenchWithoutAccents() {
        assertEquals(LanguageDetector.FRENCH,
                LanguageDetector.detect("Le chat est sur la table et il regarde par la fenetre tous les jours."));
    }

    @Test
    public void detectsEnglish() {
        assertEquals(LanguageDetector.ENGLISH,
                LanguageDetector.detect("Hello, I would like to know if you are available tomorrow for a meeting."));
    }

    @Test
    public void detectsEnglishShortSentence() {
        assertEquals(LanguageDetector.ENGLISH,
                LanguageDetector.detect("This is a short test sentence with the and is."));
    }

    @Test
    public void emptyTextFallsBackWithoutCrashing() {
        // Should not throw; falls back to a default rather than leaving the mode undetermined.
        String result = LanguageDetector.detect("");
        assertEquals(true, result.equals(LanguageDetector.FRENCH) || result.equals(LanguageDetector.ENGLISH));
    }

    @Test
    public void detectsFrenchWithNumbersAndProperNouns() {
        assertEquals(LanguageDetector.FRENCH,
                LanguageDetector.detect("Jean-Pierre a acheté 3 billets pour Paris le 12 mars 2026."));
    }

    @Test
    public void detectsEnglishWithNumbersAndProperNouns() {
        assertEquals(LanguageDetector.ENGLISH,
                LanguageDetector.detect("John bought 3 tickets to New York on March 12, 2026."));
    }
}
