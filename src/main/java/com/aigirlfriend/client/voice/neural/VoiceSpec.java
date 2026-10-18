package com.aigirlfriend.client.voice.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which Kokoro voice she uses: one id ({@code af_heart}) or a blend of several with weights
 * ({@code af_heart:0.7+af_sarah:0.3}). Unknown ids and malformed text fall back to {@link #DEFAULT}.
 */
record VoiceSpec(List<String> ids, List<Float> weights) {
    /** af_heart's young, warm voice with a little of af_sarah's: the best-rated mix in listening tests. */
    static final String DEFAULT_TEXT = "af_heart:0.7+af_sarah:0.3";
    static final VoiceSpec DEFAULT = new VoiceSpec(List.of("af_heart", "af_sarah"), List.of(0.7f, 0.3f));

    static VoiceSpec parse(String text) {
        if (text == null || text.isBlank()) return DEFAULT;
        List<String> ids = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("\\+")) {
            String[] kv = part.trim().split("\\s*[:*]\\s*");
            if (kv.length == 0 || kv.length > 2 || !NeuralFiles.VOICES.containsKey(kv[0].trim())) return DEFAULT;
            float w;
            try {
                w = kv.length == 2 ? Float.parseFloat(kv[1]) : 1f;
            } catch (NumberFormatException e) {
                return DEFAULT;
            }
            if (!(w > 0) || w > 100) return DEFAULT;
            ids.add(kv[0].trim());
            weights.add(w);
        }
        return ids.isEmpty() ? DEFAULT : new VoiceSpec(List.copyOf(ids), List.copyOf(weights));
    }

    /** British voices (bf_*) need the British lexicon; a blend follows its heaviest voice. */
    boolean british() {
        int best = 0;
        for (int i = 1; i < weights.size(); i++) {
            if (weights.get(i) > weights.get(best)) best = i;
        }
        return ids.get(best).startsWith("b");
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) b.append('+');
            b.append(ids.get(i));
            if (ids.size() > 1) b.append(':').append(weights.get(i));
        }
        return b.toString();
    }
}
