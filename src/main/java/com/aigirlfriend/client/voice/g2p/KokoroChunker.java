package com.aigirlfriend.client.voice.g2p;

import java.util.ArrayList;
import java.util.List;

/** kokoro 0.9.4's KPipeline.en_tokenize: cuts a line's tokens into phoneme chunks Kokoro can take in one call. */
final class KokoroChunker {
    private static final String[] WATERFALL = {"!.?…", ":;", ",—"};

    private KokoroChunker() {}

    static List<String> chunk(List<MToken> tokens) {
        List<String> out = new ArrayList<>();
        List<MToken> tks = new ArrayList<>();
        int count = 0;
        for (MToken t : tokens) {
            if (t.phonemes == null) t.phonemes = "";
            String next = t.phonemes + (t.whitespace.isEmpty() ? "" : " ");
            int nextCount = count + next.stripTrailing().length();
            if (nextCount > EnglishG2P.MAX_PHONEMES) {
                int z = cutPoint(tks, nextCount);
                out.add(join(tks.subList(0, z)));
                tks = new ArrayList<>(tks.subList(z, tks.size()));
                count = join(tks).length();
                if (tks.isEmpty()) next = next.stripLeading();
            }
            tks.add(t);
            count += next.length();
        }
        if (!tks.isEmpty()) out.add(join(tks));
        return out;
    }

    /** After the last sentence end if the rest fits, else the last clause break, else the last comma. */
    private static int cutPoint(List<MToken> tks, int nextCount) {
        for (String breaks : WATERFALL) {
            int z = -1;
            for (int i = tks.size() - 1; i >= 0; i--) {
                String ps = tks.get(i).phonemes;
                if (ps.length() == 1 && breaks.indexOf(ps.charAt(0)) >= 0) {
                    z = i;
                    break;
                }
            }
            if (z < 0) continue;
            z++;
            if (z < tks.size() && (tks.get(z).phonemes.equals(")") || tks.get(z).phonemes.equals("”"))) z++;
            if (nextCount - join(tks.subList(0, z)).length() <= EnglishG2P.MAX_PHONEMES) return z;
        }
        return tks.size();
    }

    private static String join(List<MToken> tks) {
        StringBuilder b = new StringBuilder();
        for (MToken t : tks) b.append(t.phonemes).append(t.whitespace.isEmpty() ? "" : " ");
        return b.toString().strip();
    }
}
