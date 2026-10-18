package com.aigirlfriend.client.voice.neural;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A Kokoro voice: a style table of 510 rows of 256 floats (voices/*.bin, little-endian), one row per chunk length.
 * The first 128 values of a row shape the timbre (decoder), the last 128 the delivery (duration and pitch
 * predictor), so a voice can borrow another's delivery while keeping her own sound.
 */
final class VoiceTable {
    static final int ROWS = 510;
    static final int DIM = KokoroModel.STYLE_DIM;
    private static final int TIMBRE = DIM / 2;

    private final float[] data;

    private VoiceTable(float[] data) {
        this.data = data;
    }

    static VoiceTable read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length != ROWS * DIM * 4) throw new IOException("unexpected voice table size " + bytes.length + ": " + file);
        float[] data = new float[ROWS * DIM];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(data);
        return new VoiceTable(data);
    }

    /** Weighted sum of voices (weights are normalised). */
    static VoiceTable blend(List<VoiceTable> voices, List<Float> weights) {
        float total = 0;
        for (float w : weights) total += w;
        float[] out = new float[ROWS * DIM];
        for (int v = 0; v < voices.size(); v++) {
            float w = weights.get(v) / total;
            float[] d = voices.get(v).data;
            for (int i = 0; i < out.length; i++) out[i] += w * d[i];
        }
        return new VoiceTable(out);
    }

    /** This voice's timbre with its delivery moved {@code amount} of the way towards {@code other}'s. */
    VoiceTable withDeliveryOf(VoiceTable other, float amount) {
        float[] out = data.clone();
        for (int r = 0; r < ROWS; r++) {
            for (int c = TIMBRE; c < DIM; c++) {
                int i = r * DIM + c;
                out[i] = (1 - amount) * data[i] + amount * other.data[i];
            }
        }
        return new VoiceTable(out);
    }

    /** One style row (see {@link com.aigirlfriend.client.voice.g2p.KokoroVocab#styleRow}). */
    float[] row(int row) {
        float[] out = new float[DIM];
        System.arraycopy(data, row * DIM, out, 0, DIM);
        return out;
    }
}
