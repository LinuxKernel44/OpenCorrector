package com.opencorrector.text;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight French/English detector based on stopword frequency and accented characters.
 * Deliberately avoids pulling in a heavy ML language-id library: the app only ever needs to
 * distinguish fr vs en, and a stopword heuristic is both accurate enough for that and free
 * to run on every keystroke-sized input on a low-power CPU.
 */
public final class LanguageDetector {

    public static final String FRENCH = "fr";
    public static final String ENGLISH = "en";

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}']+");

    private static final Set<String> FRENCH_STOPWORDS = new HashSet<>();
    private static final Set<String> ENGLISH_STOPWORDS = new HashSet<>();

    static {
        FRENCH_STOPWORDS.addAll(java.util.Arrays.asList(
                "le", "la", "les", "un", "une", "des", "de", "du", "et", "est", "sont",
                "dans", "pour", "avec", "que", "qui", "pas", "ne", "se", "sur", "au", "aux",
                "ce", "cette", "ces", "il", "elle", "ils", "elles", "nous", "vous", "je", "tu",
                "mais", "ou", "donc", "car", "en", "son", "sa", "ses", "leur", "leurs", "être",
                "avoir", "fait", "plus", "très", "bien", "aussi", "comme", "tout", "tous",
                "été", "avez", "avait", "était", "sont", "peut", "faire", "cet"
        ));
        ENGLISH_STOPWORDS.addAll(java.util.Arrays.asList(
                "the", "and", "is", "are", "was", "were", "in", "on", "at", "to", "of", "for",
                "with", "that", "this", "these", "those", "it", "he", "she", "they", "we", "you",
                "i", "but", "or", "so", "because", "a", "an", "his", "her", "their", "be", "been",
                "have", "has", "had", "do", "does", "did", "not", "very", "also", "as", "all",
                "can", "will", "would", "should", "could", "from", "by", "about"
        ));
    }

    private LanguageDetector() {
    }

    /**
     * @return {@link #FRENCH} or {@link #ENGLISH}, defaulting to the device locale's language
     *         (or French) when the text is too short or ambiguous to score reliably.
     */
    public static String detect(String text) {
        if (text == null || text.trim().isEmpty()) {
            return defaultLanguage();
        }

        int frenchScore = 0;
        int englishScore = 0;

        String lower = text.toLowerCase(Locale.ROOT);
        for (char c : lower.toCharArray()) {
            if ("éèêëàâäùûüçîï".indexOf(c) >= 0) {
                frenchScore += 2;
            }
        }

        Matcher matcher = WORD_PATTERN.matcher(lower);
        int totalWords = 0;
        while (matcher.find()) {
            String word = matcher.group();
            totalWords++;
            if (FRENCH_STOPWORDS.contains(word)) {
                frenchScore++;
            }
            if (ENGLISH_STOPWORDS.contains(word)) {
                englishScore++;
            }
        }

        if (totalWords == 0) {
            return defaultLanguage();
        }
        if (frenchScore == englishScore) {
            return defaultLanguage();
        }
        return frenchScore > englishScore ? FRENCH : ENGLISH;
    }

    private static String defaultLanguage() {
        String deviceLang = Locale.getDefault().getLanguage();
        return FRENCH.equals(deviceLang) ? FRENCH : (ENGLISH.equals(deviceLang) ? ENGLISH : FRENCH);
    }
}
