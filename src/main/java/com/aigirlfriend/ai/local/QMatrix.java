package com.aigirlfriend.ai.local;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A (possibly quantized) weight tensor read straight from a memory-mapped GGUF file. Rows are decoded on the fly
 * into int8 values plus one scale and one offset per 16 elements, so every supported quantization shares one fast
 * integer dot-product path: {@code w[i] = scale[i / 16] * q[i] - offset[i / 16]}.
 */
final class QMatrix {
    final String name;
    final GGMLType type;
    final int rows;
    final int cols;
    final int rowBytes;
    private final ByteBuffer data;

    QMatrix(GGUF gguf, String tensorName) throws IOException {
        GGUF.TensorInfo t = gguf.tensor(tensorName);
        if (!t.type().supported) throw new IOException("Tensor " + tensorName + " uses unsupported type " + t.type());
        this.name = tensorName;
        this.type = t.type();
        this.cols = (int) t.ne()[0];
        this.rows = t.ne().length > 1 ? (int) (t.elements() / t.ne()[0]) : 1;
        if (cols % type.blockSize != 0 || (type.blockSize == 1 && cols % 32 != 0 && rows > 1)) {
            throw new IOException("Tensor " + tensorName + " has an unsupported row size " + cols);
        }
        this.rowBytes = (int) type.byteSize(cols);
        this.data = gguf.map(t);
    }

    boolean isFloat() {
        return type == GGMLType.F32 || type == GGMLType.F16 || type == GGMLType.BF16;
    }

    /** Per-thread scratch buffers for decoding rows. */
    static final class Scratch {
        byte[] raw = new byte[0];
        byte[] q = new byte[0];
        float[] scale = new float[0];
        float[] offset = new float[0];
        float[] f = new float[0];

        void ensure(int cols, int rowBytes) {
            if (raw.length < rowBytes) raw = new byte[rowBytes];
            if (q.length < cols) {
                q = new byte[cols];
                scale = new float[cols / 16 + 1];
                offset = new float[cols / 16 + 1];
                f = new float[cols];
            }
        }
    }

    private void readRow(int r, byte[] dst) {
        data.get(r * rowBytes, dst, 0, rowBytes); // a single mapping is < 2 GB, so the offset fits in an int
    }

    /** Exact dequantization of one row (used for embeddings, float tensors and verification). */
    void dequantizeRow(int r, float[] out, int off, Scratch s) {
        s.ensure(cols, rowBytes);
        if (isFloat()) {
            int base = r * rowBytes;
            switch (type) {
                case F32 -> {
                    for (int i = 0; i < cols; i++) out[off + i] = data.getFloat(base + i * 4);
                }
                case F16 -> {
                    for (int i = 0; i < cols; i++) out[off + i] = Float.float16ToFloat(data.getShort(base + i * 2));
                }
                default -> {
                    for (int i = 0; i < cols; i++) out[off + i] = Float.intBitsToFloat((data.getShort(base + i * 2) & 0xFFFF) << 16);
                }
            }
            return;
        }
        decode(r, s);
        byte[] q = s.q;
        for (int c = 0, n = cols / 16; c < n; c++) {
            float sc = s.scale[c], of = s.offset[c];
            int b = c * 16;
            for (int i = 0; i < 16; i++) out[off + b + i] = sc * q[b + i] - of;
        }
    }

    /** Whether decoded rows carry a non-zero offset term (formats with a block minimum). */
    boolean hasOffset() {
        return switch (type) {
            case Q4_1, Q5_1, Q4_K, Q5_K -> true;
            default -> false;
        };
    }

    /** Decodes row r into s.q (int8 values) and per-16 s.scale / s.offset. */
    void decode(int r, Scratch s) {
        s.ensure(cols, rowBytes);
        byte[] raw = s.raw;
        readRow(r, raw);
        byte[] q = s.q;
        float[] sc = s.scale;
        float[] of = s.offset;
        int blocks = cols / type.blockSize;
        switch (type) {
            case Q8_0 -> {
                for (int b = 0; b < blocks; b++) {
                    int p = b * 34;
                    float d = half(raw, p);
                    System.arraycopy(raw, p + 2, q, b * 32, 32);
                    sc[2 * b] = d;
                    sc[2 * b + 1] = d;
                    of[2 * b] = 0;
                    of[2 * b + 1] = 0;
                }
            }
            case Q4_0 -> {
                for (int b = 0; b < blocks; b++) {
                    int p = b * 18;
                    float d = half(raw, p);
                    int o = b * 32;
                    for (int j = 0; j < 16; j++) {
                        int v = raw[p + 2 + j] & 0xFF;
                        q[o + j] = (byte) ((v & 0x0F) - 8);
                        q[o + j + 16] = (byte) ((v >> 4) - 8);
                    }
                    sc[2 * b] = d;
                    sc[2 * b + 1] = d;
                    of[2 * b] = 0;
                    of[2 * b + 1] = 0;
                }
            }
            case Q4_1 -> {
                for (int b = 0; b < blocks; b++) {
                    int p = b * 20;
                    float d = half(raw, p), m = half(raw, p + 2);
                    int o = b * 32;
                    for (int j = 0; j < 16; j++) {
                        int v = raw[p + 4 + j] & 0xFF;
                        q[o + j] = (byte) (v & 0x0F);
                        q[o + j + 16] = (byte) (v >> 4);
                    }
                    sc[2 * b] = d;
                    sc[2 * b + 1] = d;
                    of[2 * b] = -m;
                    of[2 * b + 1] = -m;
                }
            }
            case Q5_0, Q5_1 -> {
                boolean min = type == GGMLType.Q5_1;
                int size = min ? 24 : 22;
                for (int b = 0; b < blocks; b++) {
                    int p = b * size;
                    float d = half(raw, p);
                    float m = min ? half(raw, p + 2) : 0;
                    int hp = p + (min ? 4 : 2);
                    int qh = (raw[hp] & 0xFF) | (raw[hp + 1] & 0xFF) << 8 | (raw[hp + 2] & 0xFF) << 16 | (raw[hp + 3] & 0xFF) << 24;
                    int qp = hp + 4;
                    int o = b * 32;
                    int bias = min ? 0 : 16;
                    for (int j = 0; j < 16; j++) {
                        int v = raw[qp + j] & 0xFF;
                        int h0 = ((qh >>> j) << 4) & 0x10;
                        int h1 = (qh >>> (j + 12)) & 0x10;
                        q[o + j] = (byte) (((v & 0x0F) | h0) - bias);
                        q[o + j + 16] = (byte) (((v >> 4) | h1) - bias);
                    }
                    sc[2 * b] = d;
                    sc[2 * b + 1] = d;
                    of[2 * b] = -m;
                    of[2 * b + 1] = -m;
                }
            }
            case Q4_K, Q5_K -> {
                boolean five = type == GGMLType.Q5_K;
                int size = five ? 176 : 144;
                for (int b = 0; b < blocks; b++) {
                    int p = b * size;
                    float d = half(raw, p), dmin = half(raw, p + 2);
                    int scales = p + 4;
                    int qhp = p + 16;
                    int qsp = five ? p + 48 : p + 16;
                    int o = b * 256;
                    int is = 0;
                    int u1 = 1, u2 = 2;
                    for (int j = 0; j < 256; j += 64) {
                        int sc1 = scale6(raw, scales, is), m1 = min6(raw, scales, is);
                        int sc2 = scale6(raw, scales, is + 1), m2 = min6(raw, scales, is + 1);
                        for (int l = 0; l < 32; l++) {
                            int v = raw[qsp + l] & 0xFF;
                            int lo = v & 0x0F, hi = v >> 4;
                            if (five) {
                                int h = raw[qhp + l] & 0xFF;
                                if ((h & u1) != 0) lo += 16;
                                if ((h & u2) != 0) hi += 16;
                            }
                            q[o + j + l] = (byte) lo;
                            q[o + j + 32 + l] = (byte) hi;
                        }
                        int c = (o + j) / 16;
                        float s1 = d * sc1, o1 = dmin * m1, s2 = d * sc2, o2 = dmin * m2;
                        sc[c] = s1;
                        sc[c + 1] = s1;
                        of[c] = o1;
                        of[c + 1] = o1;
                        sc[c + 2] = s2;
                        sc[c + 3] = s2;
                        of[c + 2] = o2;
                        of[c + 3] = o2;
                        qsp += 32;
                        is += 2;
                        u1 <<= 2;
                        u2 <<= 2;
                    }
                }
            }
            case Q6_K -> {
                for (int b = 0; b < blocks; b++) {
                    int p = b * 210;
                    int ql = p, qh = p + 128, scales = p + 192;
                    float d = half(raw, p + 208);
                    int o = b * 256;
                    for (int n = 0; n < 256; n += 128) {
                        for (int l = 0; l < 32; l++) {
                            int lq0 = raw[ql + l] & 0xFF, lq1 = raw[ql + l + 32] & 0xFF, h = raw[qh + l] & 0xFF;
                            q[o + n + l] = (byte) (((lq0 & 0x0F) | ((h & 3) << 4)) - 32);
                            q[o + n + l + 32] = (byte) (((lq1 & 0x0F) | (((h >> 2) & 3) << 4)) - 32);
                            q[o + n + l + 64] = (byte) (((lq0 >> 4) | (((h >> 4) & 3) << 4)) - 32);
                            q[o + n + l + 96] = (byte) (((lq1 >> 4) | (((h >> 6) & 3) << 4)) - 32);
                        }
                        int c = (o + n) / 16;
                        for (int k = 0; k < 8; k++) {
                            sc[c + k] = d * raw[scales + k];
                            of[c + k] = 0;
                        }
                        ql += 64;
                        qh += 32;
                        scales += 8;
                    }
                }
            }
            default -> throw new IllegalStateException("decode " + type);
        }
    }

    private static int scale6(byte[] raw, int base, int j) {
        if (j < 4) return raw[base + j] & 63;
        return (raw[base + j + 4] & 0x0F) | ((raw[base + j - 4] & 0xFF) >> 6) << 4;
    }

    private static int min6(byte[] raw, int base, int j) {
        if (j < 4) return raw[base + j + 4] & 63;
        return ((raw[base + j + 4] & 0xFF) >> 4) | ((raw[base + j] & 0xFF) >> 6) << 4;
    }

    private static float half(byte[] b, int p) {
        return Float.float16ToFloat((short) ((b[p] & 0xFF) | (b[p + 1] & 0xFF) << 8));
    }

    /**
     * out[t * outStride + r] = row r . x[t] for rows [r0, r1) and every token t of the batch.
     * Quantized weights use the int8 path; float weights a plain float dot product.
     */
    void mulRows(Activations x, float[] out, int outStride, int r0, int r1, Scratch s) {
        s.ensure(cols, rowBytes);
        int batch = x.batch;
        if (isFloat()) {
            float[] w = s.f;
            for (int r = r0; r < r1; r++) {
                dequantizeRow(r, w, 0, s);
                for (int t = 0; t < batch; t++) {
                    float[] xf = x.f;
                    int xo = t * cols;
                    float acc = 0;
                    for (int i = 0; i < cols; i++) acc += w[i] * xf[xo + i];
                    out[t * outStride + r] = acc;
                }
            }
            return;
        }
        if (type == GGMLType.Q8_0) {
            mulRowsQ8(x, out, outStride, r0, r1, s);
            return;
        }
        if (type == GGMLType.Q4_K && FUSED_MAX_BATCH > 0) { // faster than decode-then-dot even for a prefill batch
            mulRowsQ4K(x, out, outStride, r0, r1, s);
            return;
        }
        if (batch <= FUSED_MAX_BATCH && type == GGMLType.Q6_K) {
            mulRowsQ6K(x, out, outStride, r0, r1, s);
            return;
        }
        boolean offset = hasOffset();
        int chunks = cols / 16;
        byte[] q = s.q;
        float[] sc = s.scale, of = s.offset;
        byte[] xq = x.q;
        float[] xd = x.d;
        int[] xs = x.sum16;
        for (int r = r0; r < r1; r++) {
            decode(r, s);
            for (int t = 0; t < batch; t++) {
                int xo = t * cols;
                int dBase = t * (cols / 32);
                int sBase = t * chunks;
                float acc = 0;
                for (int c = 0; c < chunks; c += 2) {
                    int b = c * 16;
                    int s0 = 0, s1 = 0;
                    for (int i = 0; i < 16; i++) {
                        s0 += q[b + i] * xq[xo + b + i];
                        s1 += q[b + 16 + i] * xq[xo + b + 16 + i];
                    }
                    float part = sc[c] * s0 + sc[c + 1] * s1;
                    if (offset) part -= of[c] * xs[sBase + c] + of[c + 1] * xs[sBase + c + 1];
                    acc += xd[dBase + (c >> 1)] * part;
                }
                out[t * outStride + r] = acc;
            }
        }
    }

    /**
     * Q6_K rows are dotted straight from the packed bits up to this batch size; larger (prefill) batches decode the
     * row once and reuse it, which is faster there. Q4_K always uses its fused kernel (0 disables both, for tests).
     */
    static int FUSED_MAX_BATCH = 4;

    /** Q4_K: 256 values per block in 8 groups of 32, each with a 6-bit scale and min; activations use the same groups. */
    private void mulRowsQ4K(Activations x, float[] out, int outStride, int r0, int r1, Scratch s) {
        byte[] raw = s.raw;
        float[] sc = s.scale, mn = s.offset;
        int blocks = cols / 256;
        byte[] xq = x.q;
        float[] xd = x.d;
        int[] xs = x.sum16;
        for (int r = r0; r < r1; r++) {
            readRow(r, raw);
            for (int b = 0; b < blocks; b++) {
                int p = b * 144;
                float d = half(raw, p), dmin = half(raw, p + 2);
                for (int j = 0; j < 8; j++) {
                    sc[b * 8 + j] = d * scale6(raw, p + 4, j);
                    mn[b * 8 + j] = dmin * min6(raw, p + 4, j);
                }
            }
            for (int t = 0; t < x.batch; t++) {
                int xo = t * cols;
                int g0 = t * (cols / 32);
                float acc = 0;
                for (int b = 0; b < blocks; b++) {
                    int qs = b * 144 + 16;
                    int a = xo + b * 256;
                    int g = b * 8;
                    for (int j = 0; j < 4; j++) {
                        int lo = 0, hi = 0;
                        for (int l = 0; l < 32; l++) {
                            int v = raw[qs + l];
                            lo += (v & 0x0F) * xq[a + l];
                            hi += ((v >> 4) & 0x0F) * xq[a + 32 + l];
                        }
                        int k = g + 2 * j;
                        int ka = g0 + k;
                        acc += xd[ka] * (sc[k] * lo - mn[k] * (xs[2 * ka] + xs[2 * ka + 1]))
                                + xd[ka + 1] * (sc[k + 1] * hi - mn[k + 1] * (xs[2 * ka + 2] + xs[2 * ka + 3]));
                        qs += 32;
                        a += 64;
                    }
                }
                out[t * outStride + r] = acc;
            }
        }
    }

    /** Q6_K: 256 values per block, 4 low + 2 high bits each, an int8 scale per 16 values; stored values are offset by 32. */
    private void mulRowsQ6K(Activations x, float[] out, int outStride, int r0, int r1, Scratch s) {
        byte[] raw = s.raw;
        int blocks = cols / 256;
        byte[] xq = x.q;
        float[] xd = x.d;
        int[] xs = x.sum16;
        for (int r = r0; r < r1; r++) {
            readRow(r, raw);
            for (int t = 0; t < x.batch; t++) {
                int xo = t * cols;
                int g0 = t * (cols / 32);
                float acc = 0;
                for (int b = 0; b < blocks; b++) {
                    int p = b * 210;
                    float d = half(raw, p + 208);
                    int ql = p, qh = p + 128, scales = p + 192;
                    float blockAcc = 0;
                    for (int n = 0; n < 256; n += 128) {
                        int a = xo + b * 256 + n;
                        int s0 = 0, s1 = 0, s2 = 0, s3 = 0, s4 = 0, s5 = 0, s6 = 0, s7 = 0;
                        for (int l = 0; l < 16; l++) {
                            int lq0 = raw[ql + l], lq1 = raw[ql + l + 32], h = raw[qh + l];
                            s0 += ((lq0 & 0x0F) | (h & 3) << 4) * xq[a + l];
                            s2 += ((lq1 & 0x0F) | (h & 0x0C) << 2) * xq[a + 32 + l];
                            s4 += ((lq0 >> 4 & 0x0F) | (h & 0x30)) * xq[a + 64 + l];
                            s6 += ((lq1 >> 4 & 0x0F) | (h >> 2 & 0x30)) * xq[a + 96 + l];
                        }
                        for (int l = 16; l < 32; l++) {
                            int lq0 = raw[ql + l], lq1 = raw[ql + l + 32], h = raw[qh + l];
                            s1 += ((lq0 & 0x0F) | (h & 3) << 4) * xq[a + l];
                            s3 += ((lq1 & 0x0F) | (h & 0x0C) << 2) * xq[a + 32 + l];
                            s5 += ((lq0 >> 4 & 0x0F) | (h & 0x30)) * xq[a + 64 + l];
                            s7 += ((lq1 >> 4 & 0x0F) | (h >> 2 & 0x30)) * xq[a + 96 + l];
                        }
                        // the stored values are q + 32: subtract 32 * (sum of the activations) per 16
                        int c = (g0 + (b * 256 + n) / 32) * 2;
                        s0 -= 32 * xs[c];
                        s1 -= 32 * xs[c + 1];
                        s2 -= 32 * xs[c + 2];
                        s3 -= 32 * xs[c + 3];
                        s4 -= 32 * xs[c + 4];
                        s5 -= 32 * xs[c + 5];
                        s6 -= 32 * xs[c + 6];
                        s7 -= 32 * xs[c + 7];
                        int ka = c / 2;
                        blockAcc += xd[ka] * (raw[scales] * s0 + raw[scales + 1] * s1)
                                + xd[ka + 1] * (raw[scales + 2] * s2 + raw[scales + 3] * s3)
                                + xd[ka + 2] * (raw[scales + 4] * s4 + raw[scales + 5] * s5)
                                + xd[ka + 3] * (raw[scales + 6] * s6 + raw[scales + 7] * s7);
                        ql += 64;
                        qh += 32;
                        scales += 8;
                    }
                    acc += d * blockAcc;
                }
                out[t * outStride + r] = acc;
            }
        }
    }

    /** Q8_0 rows are dotted straight from the raw bytes: no per-block copies (they cost as much as the math). */
    private void mulRowsQ8(Activations x, float[] out, int outStride, int r0, int r1, Scratch s) {
        byte[] raw = s.raw;
        float[] wd = s.scale;
        int blocks = cols / 32;
        byte[] xq = x.q;
        float[] xd = x.d;
        for (int r = r0; r < r1; r++) {
            readRow(r, raw);
            for (int b = 0; b < blocks; b++) wd[b] = half(raw, b * 34);
            for (int t = 0; t < x.batch; t++) {
                int xo = t * cols;
                int dBase = t * blocks;
                float acc = 0;
                for (int b = 0; b < blocks; b++) {
                    int p = b * 34 + 2;
                    int xb = xo + b * 32;
                    int s0 = 0, s1 = 0;
                    for (int i = 0; i < 16; i++) {
                        s0 += raw[p + i] * xq[xb + i];
                        s1 += raw[p + 16 + i] * xq[xb + 16 + i];
                    }
                    acc += wd[b] * xd[dBase + b] * (s0 + s1);
                }
                out[t * outStride + r] = acc;
            }
        }
    }

    /** A batch of input vectors, kept both as floats and quantized to int8 in blocks of 32. */
    static final class Activations {
        final int cols;
        int batch;
        float[] f = new float[0];
        byte[] q = new byte[0];
        float[] d = new float[0];
        int[] sum16 = new int[0];

        Activations(int cols) {
            this.cols = cols;
        }

        void set(float[] src, int batch) {
            this.batch = batch;
            int n = batch * cols;
            if (f.length < n) {
                f = new float[n];
                q = new byte[n];
                d = new float[n / 32];
                sum16 = new int[n / 16];
            }
            System.arraycopy(src, 0, f, 0, n);
            for (int b = 0; b < n / 32; b++) {
                int o = b * 32;
                float amax = 0;
                for (int i = 0; i < 32; i++) amax = Math.max(amax, Math.abs(src[o + i]));
                float scale = amax / 127f;
                float inv = scale > 0 ? 1f / scale : 0f;
                d[b] = scale;
                int s0 = 0, s1 = 0;
                for (int i = 0; i < 32; i++) {
                    int v = Math.round(src[o + i] * inv);
                    q[o + i] = (byte) v;
                    if (i < 16) s0 += v;
                    else s1 += v;
                }
                sum16[2 * b] = s0;
                sum16[2 * b + 1] = s1;
            }
        }
    }
}
