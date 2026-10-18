package com.aigirlfriend.client.voice.g2p;

import java.util.List;
import java.util.TreeSet;

/** One token on its way through the G2P pipeline; mirrors misaki's MToken and its "_" extension fields. */
final class MToken {
    String text;
    String tag;
    String whitespace;
    /** Phonemes, or null while unknown. */
    String phonemes;
    boolean isHead = true;
    String alias;
    /** misaki's stress override: null, an integer, or +-0.5. */
    Double stress;
    String currency;
    String numFlags = "";
    boolean prespace;

    MToken(String text, String tag, String whitespace) {
        this.text = text;
        this.tag = tag;
        this.whitespace = whitespace;
    }

    MToken copyAs(String newText) {
        MToken t = new MToken(newText, tag, "");
        t.phonemes = phonemes;
        t.numFlags = numFlags;
        t.stress = stress;
        return t;
    }

    /**
     * misaki's merge_tokens: one token spanning a group of sub-tokens. With {@code unk == null} the merged
     * token has no phonemes (it is about to be looked up); otherwise unknown parts become {@code unk}.
     */
    static MToken merge(List<MToken> tokens, String unk) {
        MToken first = tokens.get(0), last = tokens.get(tokens.size() - 1);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < tokens.size() - 1; i++) text.append(tokens.get(i).text).append(tokens.get(i).whitespace);
        text.append(last.text);
        MToken best = first;
        int bestWeight = -1;
        Double stress = null;
        boolean stressConflict = false;
        String currency = null;
        TreeSet<Character> flags = new TreeSet<>();
        for (MToken t : tokens) {
            int weight = caseWeight(t.text);
            if (weight > bestWeight) {
                best = t;
                bestWeight = weight;
            }
            if (t.stress != null) {
                if (stress != null && !stress.equals(t.stress)) stressConflict = true;
                stress = t.stress;
            }
            if (t.currency != null && (currency == null || t.currency.compareTo(currency) > 0)) currency = t.currency;
            for (int i = 0; i < t.numFlags.length(); i++) flags.add(t.numFlags.charAt(i));
        }
        MToken m = new MToken(text.toString(), best.tag, last.whitespace);
        if (unk != null) {
            StringBuilder ps = new StringBuilder();
            for (MToken t : tokens) {
                if (t.prespace && ps.length() > 0 && !Character.isWhitespace(ps.charAt(ps.length() - 1))
                        && t.phonemes != null && !t.phonemes.isEmpty()) {
                    ps.append(' ');
                }
                ps.append(t.phonemes == null ? unk : t.phonemes);
            }
            m.phonemes = ps.toString();
        }
        m.isHead = first.isHead;
        m.stress = stressConflict ? null : stress;
        m.currency = currency;
        StringBuilder f = new StringBuilder();
        for (char c : flags) f.append(c);
        m.numFlags = f.toString();
        m.prespace = first.prespace;
        return m;
    }

    /** Lower-case characters count 1 and others 2, so the most "shouty" sub-token lends its tag to a merge. */
    private static int caseWeight(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += c == Character.toLowerCase(c) ? 1 : 2;
        }
        return w;
    }
}
