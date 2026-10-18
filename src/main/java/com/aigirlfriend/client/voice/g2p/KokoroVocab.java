package com.aigirlfriend.client.voice.g2p;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/**
 * Kokoro-82M's phoneme vocabulary: one input id per phoneme character, id 0 ("$") as padding around each
 * chunk. Reads the ONNX repo's tokenizer.json ({@code model.vocab}) or hexgrad's config.json ({@code vocab}).
 */
public final class KokoroVocab {
    /** Rows in a voice style table (voices/*.bin, float32 [510][256]). */
    public static final int STYLE_ROWS = 510;

    private final int[] ids = new int[0x10000];

    private KokoroVocab(Map<String, Object> vocab) {
        Arrays.fill(ids, -1);
        vocab.forEach((symbol, id) -> {
            if (symbol.length() == 1 && !symbol.equals("$")) ids[symbol.charAt(0)] = ((Number) id).intValue();
        });
    }

    @SuppressWarnings("unchecked")
    public static KokoroVocab load(Path json) throws IOException {
        Map<String, Object> root = (Map<String, Object>) Json.parse(Files.readString(json, StandardCharsets.UTF_8));
        Object model = root.get("model");
        Object vocab = model instanceof Map<?, ?> m ? m.get("vocab") : root.get("vocab");
        if (!(vocab instanceof Map<?, ?>)) throw new IOException("no phoneme vocab in " + json);
        return new KokoroVocab((Map<String, Object>) vocab);
    }

    /** Ids of the phoneme characters; characters outside the vocab are dropped, as kokoro does. */
    public int[] ids(String phonemes) {
        int[] out = new int[phonemes.length()];
        int n = 0;
        for (int i = 0; i < phonemes.length(); i++) {
            int id = ids[phonemes.charAt(i)];
            if (id >= 0) out[n++] = id;
        }
        return Arrays.copyOf(out, n);
    }

    /** The model's input_ids: {@code [0, ids..., 0]}. */
    public long[] inputIds(String phonemes) {
        int[] core = ids(phonemes);
        long[] out = new long[core.length + 2];
        for (int i = 0; i < core.length; i++) out[i + 1] = core[i];
        return out;
    }

    /** The voice table row to use as style for a chunk: its length minus one, as kokoro's KPipeline picks it. */
    public static int styleRow(String phonemes) {
        return Math.max(0, Math.min(phonemes.length() - 1, STYLE_ROWS - 1));
    }
}
