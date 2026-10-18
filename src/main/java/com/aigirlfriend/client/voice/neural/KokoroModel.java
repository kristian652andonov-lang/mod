package com.aigirlfriend.client.voice.neural;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Kokoro-82M v1.0 (Apache-2.0, hexgrad; ONNX export by onnx-community) on ONNX Runtime: phoneme ids, a 256-value
 * style vector and a speed in, mono float audio at {@value #SAMPLE_RATE} Hz out. The graph has no random ops, so the
 * same input always gives the same audio.
 */
final class KokoroModel implements AutoCloseable {
    static final int SAMPLE_RATE = 24000;
    static final int STYLE_DIM = 256;

    private final OrtRuntime.Session session;

    private KokoroModel(OrtRuntime.Session session) {
        this.session = session;
    }

    static KokoroModel open(OrtRuntime runtime, Path model, int threads) throws Exception {
        OrtRuntime.Session session = runtime.open(model, threads);
        try {
            Set<String> in = session.inputNames(), out = session.outputNames();
            if (!in.containsAll(Set.of("input_ids", "style", "speed")) || !out.contains("waveform")) {
                throw new IllegalStateException("unexpected Kokoro model interface: " + in + " -> " + out);
            }
            return new KokoroModel(session);
        } catch (Exception e) {
            session.close();
            throw e;
        }
    }

    /**
     * Speaks one chunk. {@code inputIds} is {@code [0, ids..., 0]} with at most 510 ids; values above 1 for
     * {@code speed} talk faster. Non-finite samples (never seen with this model, but cheap to rule out) become silence.
     */
    float[] synthesize(long[] inputIds, float[] style, float speed) throws Exception {
        float[] wav = session.runFloat(
                Map.of("input_ids", inputIds, "style", style, "speed", new float[]{speed}),
                Map.of("input_ids", new long[]{1, inputIds.length}, "style", new long[]{1, STYLE_DIM}, "speed", new long[]{1}),
                "waveform");
        for (int i = 0; i < wav.length; i++) {
            if (!Float.isFinite(wav[i])) wav[i] = 0f;
        }
        return wav;
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
