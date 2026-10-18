package com.aigirlfriend.client.voice.g2p;

/** Facts about misaki's phoneme alphabet and its stress arithmetic (misaki/en.py, apply_stress and friends). */
final class Phonemes {
    static final char PRIMARY = 'ˈ';
    static final char SECONDARY = 'ˌ';
    static final String VOWELS = "AIOQWYaiuæɑɒɔəɛɜɪʊʌᵻ";
    static final String CONSONANTS = "bdfhjklmnpstvwzðŋɡɹɾʃʒʤʧθ";
    static final String US_TAUS = "AIOWYiuæɑəɛɪɹʊʌ";
    static final String PUNCTS = ";:,.!?—…\"“”";
    static final String NON_QUOTE_PUNCTS = ";:,.!?—…";
    private static final String DIPHTHONGS = "AIOQWYʤʧ";

    private Phonemes() {}

    static boolean isVowel(char c) {
        return VOWELS.indexOf(c) >= 0;
    }

    static boolean hasVowel(String ps) {
        for (int i = 0; i < ps.length(); i++) if (isVowel(ps.charAt(i))) return true;
        return false;
    }

    static int stressWeight(String ps) {
        if (ps == null) return 0;
        int w = 0;
        for (int i = 0; i < ps.length(); i++) w += DIPHTHONGS.indexOf(ps.charAt(i)) >= 0 ? 2 : 1;
        return w;
    }

    /** misaki's apply_stress: null keeps the word's own stress, negative values weaken it, positive ones add it. */
    static String applyStress(String ps, Double stress) {
        if (stress == null || ps == null) return ps;
        double s = stress;
        boolean primary = ps.indexOf(PRIMARY) >= 0, secondary = ps.indexOf(SECONDARY) >= 0;
        if (s < -1) {
            return ps.replace(String.valueOf(PRIMARY), "").replace(String.valueOf(SECONDARY), "");
        } else if (s == -1 || ((s == 0 || s == -0.5) && primary)) {
            return ps.replace(String.valueOf(SECONDARY), "").replace(PRIMARY, SECONDARY);
        } else if ((s == 0 || s == 0.5 || s == 1) && !primary && !secondary) {
            return hasVowel(ps) ? restress(SECONDARY, ps) : ps;
        } else if (s >= 1 && !primary && secondary) {
            return ps.replace(SECONDARY, PRIMARY);
        } else if (s > 1 && !primary && !secondary) {
            return hasVowel(ps) ? restress(PRIMARY, ps) : ps;
        }
        return ps;
    }

    /** A stress mark placed in front of the word belongs just before its first vowel. */
    private static String restress(char mark, String ps) {
        int vowel = 0;
        while (!isVowel(ps.charAt(vowel))) vowel++;
        return ps.substring(0, vowel) + mark + ps.substring(vowel);
    }
}
