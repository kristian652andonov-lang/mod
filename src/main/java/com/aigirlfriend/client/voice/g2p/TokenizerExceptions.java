package com.aigirlfriend.client.voice.g2p;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * spaCy 3.8's English tokenizer exceptions (MIT, explosion/spaCy, spacy/lang/en/tokenizer_exceptions.py and
 * spacy/lang/tokenizer_exceptions.py): strings that are split in a fixed way ("don't" = "do" + "n't") or never
 * split ("Mr.", ":)"). misaki ran on spaCy's tokens, so reproducing these keeps words, punctuation and
 * abbreviations where misaki had them.
 */
final class TokenizerExceptions {
    /** Exceptions that stay one token: emoticons, abbreviations, odd contractions, whitespace. */
    private static final String[] WHOLE = {
            "\t", "\n", " ", "'", "''", "'Cause", "'Cos", "'Coz", "'Cuz", "'S", "'bout", "'cause", "'cos",
            "'coz", "'cuz", "'d", "'em", "'ll", "'nuff", "'re", "'s", "(*_*)", "(-8", "(-:", "(-;", "(-_-)", "(._.)",
            "(:", "(;", "(=", "(>_<)", "(^_^)", "(o:", "(\u00ac_\u00ac)", "(\u0ca0_\u0ca0)",
            "(\u256f\u00b0\u25a1\u00b0\uff09\u256f\ufe35\u253b\u2501\u253b", ")-:", "):", "-_-", "-__-", "._.",
            "0.0", "0.o", "0_0", "0_o", "8)", "8-)", "8-D", "8D", ":'(", ":')", ":'-(", ":'-)", ":(", ":((", ":(((",
            ":()", ":)", ":))", ":)))", ":*", ":-(", ":-((", ":-(((", ":-)", ":-))", ":-)))", ":-*", ":-/", ":-0",
            ":-3", ":->", ":-D", ":-O", ":-P", ":-X", ":-]", ":-o", ":-p", ":-x", ":-|", ":-}", ":/", ":0", ":1",
            ":3", ":>", ":D", ":O", ":P", ":X", ":]", ":o", ":o)", ":p", ":x", ":|", ":}", ":\u2019(", ":\u2019)",
            ":\u2019-(", ":\u2019-)", ";)", ";-)", ";-D", ";D", ";_;", "<.<", "</3", "<3", "<33", "<333", "<space>",
            "=(", "=)", "=/", "=3", "=D", "=[", "=]", "=|", ">.<", ">.>", ">:(", ">:o", "><(((*>", "@_@", "Adm.",
            "Ak.", "Ala.", "Apr.", "Ariz.", "Ark.", "Aug.", "Bros.", "C++", "Calif.", "Co.", "Colo.", "Conn.",
            "Corp.", "D.C.", "Dec.", "Del.", "Dr.", "E.G.", "E.g.", "Feb.", "Fla.", "Ga.", "Gen.", "Gov.", "Havin",
            "Havin'", "Havin\u2019", "I.E.", "I.e.", "Ia.", "Id.", "Ill.", "Inc.", "Ind.", "Jan.", "Jr.", "Jul.",
            "Jun.", "Kan.", "Kans.", "Ky.", "La.", "Lovin", "Lovin'", "Lovin\u2019", "Ltd.", "Ma'am", "Mar.",
            "Mass.", "Ma\u2019am", "Md.", "Messrs.", "Mich.", "Minn.", "Miss.", "Mo.", "Mont.", "Mr.", "Mrs.", "Ms.",
            "Mt.", "N.C.", "N.D.", "N.H.", "N.J.", "N.M.", "N.Y.", "Neb.", "Nebr.", "Nev.", "Nov.", "O'clock", "O.O",
            "O.o", "O_O", "O_o", "Oct.", "Okla.", "Ore.", "O\u2019clock", "Pa.", "Ph.D.", "Prof.", "Rep.", "Rev.",
            "S.C.", "Sen.", "Sep.", "Sept.", "St.", "Tenn.", "V.V", "V_V", "Va.", "Wash.", "Wis.", "XD", "XDD",
            "[-:", "[:", "[=", "\\\")", "\\n", "\\t", "]=", "^_^", "^__^", "^___^", "a.m.", "and/or", "co.", "e.g.",
            "em", "havin", "havin'", "havin\u2019", "i.e.", "ll", "lovin", "lovin'", "lovin\u2019", "ma'am",
            "ma\u2019am", "nuff", "o'clock", "o.0", "o.O", "o.o", "o_0", "o_O", "o_o", "o\u2019clock", "p.m.",
            "v.s.", "v.v", "v_v", "vs.", "w/o", "xD", "xDD", "\u00a0", "\u00af\\(\u30c4)/\u00af", "\u0ca0_\u0ca0",
            "\u0ca0\ufe35\u0ca0", "\u2014", "\u2018S", "\u2018s", "\u2019", "\u2019Cause", "\u2019Cos", "\u2019Coz",
            "\u2019Cuz", "\u2019S", "\u2019bout", "\u2019cause", "\u2019cos", "\u2019coz", "\u2019cuz", "\u2019d",
            "\u2019em", "\u2019ll", "\u2019nuff", "\u2019re", "\u2019s", "\u2019\u2019"
    };
    private static final List<String> EXCLUDED = List.of("Ill", "ill", "Its", "its", "Hell", "hell", "Shell", "shell",
            "Shed", "shed", "were", "Were", "Well", "well", "Whore", "whore");

    private TokenizerExceptions() {}

    /** Every exception string mapped to its token texts, which concatenate back to the string. */
    static Map<String, String[]> build() {
        Map<String, String[]> exc = new HashMap<>();
        for (String orth : cases("i")) {
            split(exc, orth, "'m");
            split(exc, orth, "m");
            split(exc, orth, "'m", "a");
            split(exc, orth, "m", "a");
        }
        for (String pron : List.of("i", "you", "he", "she", "it", "we", "they")) {
            for (String orth : cases(pron)) {
                willAndWould(exc, orth);
            }
        }
        for (String pron : List.of("i", "you", "we", "they")) {
            for (String orth : cases(pron)) have(exc, orth);
        }
        for (String pron : List.of("you", "we", "they")) {
            for (String orth : cases(pron)) are(exc, orth);
        }
        for (String pron : List.of("he", "she", "it")) {
            for (String orth : cases(pron)) is(exc, orth);
        }
        for (String word : List.of("who", "what", "when", "where", "why", "how", "there", "that", "this", "these", "those")) {
            boolean plural = word.equals("these") || word.equals("those");
            boolean singular = word.equals("that") || word.equals("this");
            for (String orth : cases(word)) {
                if (!plural) is(exc, orth);
                split(exc, orth, "'ll");
                split(exc, orth, "ll");
                split(exc, orth, "'ll", "'ve");
                split(exc, orth, "ll", "ve");
                if (!singular) {
                    are(exc, orth);
                    have(exc, orth);
                }
                split(exc, orth, "'d");
                split(exc, orth, "d");
                split(exc, orth, "'d", "'ve");
                split(exc, orth, "d", "ve");
            }
        }
        for (String verb : List.of("ca", "could", "do", "does", "did", "had", "may", "might", "must", "need", "ought",
                "sha", "should", "wo", "would")) {
            for (String orth : cases(verb)) {
                split(exc, orth, "n't");
                split(exc, orth, "nt");
                split(exc, orth, "n't", "'ve");
                split(exc, orth, "nt", "ve");
            }
        }
        for (String verb : List.of("could", "might", "must", "should", "would")) {
            for (String orth : cases(verb)) have(exc, orth);
        }
        for (String verb : List.of("ai", "are", "is", "was", "were", "have", "has", "dare")) {
            for (String orth : cases(verb)) {
                split(exc, orth, "n't");
                split(exc, orth, "nt");
            }
        }
        for (String word : List.of("doin", "goin", "nothin", "nuthin", "ol", "somethin")) {
            for (String orth : cases(word)) {
                whole(exc, orth);
                whole(exc, orth + "'");
            }
        }
        for (int h = 1; h <= 12; h++) {
            for (String period : List.of("a.m.", "am", "p.m.", "pm")) split(exc, String.valueOf(h), period);
        }
        split(exc, "y'", "all");
        split(exc, "y", "all");
        for (String orth : cases("how")) split(exc, orth, "'d", "'y");
        for (String orth : cases("not")) have(exc, orth);
        for (String orth : cases("can")) split(exc, orth, "not");
        for (String orth : cases("gon")) split(exc, orth, "na");
        for (String orth : cases("got")) split(exc, orth, "ta");
        for (String orth : cases("let")) split(exc, orth, "'s");
        for (String orth : cases("c'm")) split(exc, orth, "on");
        for (String unit : "CFKcfk".split("")) split(exc, "\u00b0", unit, ".");
        for (String orth : "abcdefghijklmnopqrstuvwxyz\u00e4\u00f6\u00fc".split("")) whole(exc, orth + ".");
        for (String s : WHOLE) whole(exc, s);
        EXCLUDED.forEach(exc::remove);
        Map<String, String[]> curly = new HashMap<>();
        exc.forEach((key, parts) -> {
            if (key.indexOf('\'') < 0) return;
            String[] fixed = new String[parts.length];
            for (int i = 0; i < parts.length; i++) fixed[i] = parts[i].replace('\'', '\u2019');
            curly.put(key.replace('\'', '\u2019'), fixed);
        });
        exc.putAll(curly);
        return exc;
    }

    private static List<String> cases(String word) {
        String title = Character.toUpperCase(word.charAt(0)) + word.substring(1);
        return title.equals(word) ? List.of(word) : List.of(word, title);
    }

    private static void willAndWould(Map<String, String[]> exc, String orth) {
        split(exc, orth, "'ll");
        split(exc, orth, "ll");
        split(exc, orth, "'ll", "'ve");
        split(exc, orth, "ll", "ve");
        split(exc, orth, "'d");
        split(exc, orth, "d");
        split(exc, orth, "'d", "'ve");
        split(exc, orth, "d", "ve");
    }

    private static void have(Map<String, String[]> exc, String orth) {
        split(exc, orth, "'ve");
        split(exc, orth, "ve");
    }

    private static void are(Map<String, String[]> exc, String orth) {
        split(exc, orth, "'re");
        split(exc, orth, "re");
    }

    private static void is(Map<String, String[]> exc, String orth) {
        split(exc, orth, "'s");
        split(exc, orth, "s");
    }

    private static void split(Map<String, String[]> exc, String first, String... rest) {
        String[] parts = new String[rest.length + 1];
        parts[0] = first;
        System.arraycopy(rest, 0, parts, 1, rest.length);
        exc.put(String.join("", parts), parts);
    }

    private static void whole(Map<String, String[]> exc, String s) {
        exc.put(s, new String[] {s});
    }
}
