package com.aigirlfriend.ai.local;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A decoder-only transformer loaded from GGUF (Qwen2 family; Llama-style files work too): RMSNorm, rotary
 * position embedding, grouped-query attention with optional q/k/v biases, SwiGLU feed-forward, tied or separate
 * output head. Weights stay memory-mapped; activations are float32; matrix products use int8 activations.
 */
public final class Model implements AutoCloseable {
    public final String architecture;
    public final int dim, layers, heads, kvHeads, headDim, kvDim, ffn, vocab, trainContext;
    final float eps, ropeTheta;
    final boolean neoxRope;
    public final Tokenizer tokenizer;
    private final GGUF gguf;
    final WorkerPool pool;

    private final QMatrix embedding, output;
    private final float[] outputNorm;
    private final Layer[] blocks;

    private static final class Layer {
        float[] attnNorm, ffnNorm, bq, bk, bv;
        QMatrix wq, wk, wv, wo, gate, up, down;
    }

    /** Per-conversation state: the tokens already processed and their keys/values (grown as the chat gets longer). */
    public static final class Session {
        final float[][] keys, values;
        final int capacity;
        final int[] tokens;
        private final int kvDim;
        private int allocated;
        int length;

        Session(Model m, int capacity) {
            this.capacity = capacity;
            this.kvDim = m.kvDim;
            this.keys = new float[m.layers][];
            this.values = new float[m.layers][];
            this.tokens = new int[capacity];
            grow(Math.min(capacity, 256));
        }

        void ensure(int positions) {
            if (positions > allocated) grow(Math.min(capacity, Math.max(positions, allocated * 2)));
        }

        private void grow(int positions) {
            for (int l = 0; l < keys.length; l++) {
                keys[l] = keys[l] == null ? new float[positions * kvDim] : java.util.Arrays.copyOf(keys[l], positions * kvDim);
                values[l] = values[l] == null ? new float[positions * kvDim] : java.util.Arrays.copyOf(values[l], positions * kvDim);
            }
            allocated = positions;
        }

        public int length() {
            return length;
        }

        public int capacity() {
            return capacity;
        }
    }

    public Model(Path file, int threads) throws IOException {
        this.gguf = GGUF.open(file);
        try {
            this.architecture = gguf.getString("general.architecture", "qwen2");
            if (!architecture.equals("qwen2") && !architecture.equals("llama")) {
                throw new IOException("Unsupported model architecture '" + architecture + "' (expected qwen2)");
            }
            String a = architecture + ".";
            this.dim = gguf.getInt(a + "embedding_length", 0);
            this.layers = gguf.getInt(a + "block_count", 0);
            this.heads = gguf.getInt(a + "attention.head_count", 0);
            this.kvHeads = gguf.getInt(a + "attention.head_count_kv", heads);
            this.ffn = gguf.getInt(a + "feed_forward_length", 0);
            this.trainContext = gguf.getInt(a + "context_length", 4096);
            this.eps = gguf.getFloat(a + "attention.layer_norm_rms_epsilon", 1e-6f);
            this.ropeTheta = gguf.getFloat(a + "rope.freq_base", architecture.equals("qwen2") ? 1_000_000f : 10_000f);
            this.headDim = dim / heads;
            this.kvDim = headDim * kvHeads;
            this.neoxRope = architecture.equals("qwen2");
            this.tokenizer = new Tokenizer(gguf);
            this.vocab = tokenizer.vocabSize();
            this.embedding = new QMatrix(gguf, "token_embd.weight");
            this.output = gguf.tensors.containsKey("output.weight") ? new QMatrix(gguf, "output.weight") : embedding;
            this.outputNorm = vector("output_norm.weight");
            this.blocks = new Layer[layers];
            for (int l = 0; l < layers; l++) {
                String p = "blk." + l + ".";
                Layer b = new Layer();
                b.attnNorm = vector(p + "attn_norm.weight");
                b.ffnNorm = vector(p + "ffn_norm.weight");
                b.wq = new QMatrix(gguf, p + "attn_q.weight");
                b.wk = new QMatrix(gguf, p + "attn_k.weight");
                b.wv = new QMatrix(gguf, p + "attn_v.weight");
                b.wo = new QMatrix(gguf, p + "attn_output.weight");
                b.bq = optionalVector(p + "attn_q.bias");
                b.bk = optionalVector(p + "attn_k.bias");
                b.bv = optionalVector(p + "attn_v.bias");
                b.gate = new QMatrix(gguf, p + "ffn_gate.weight");
                b.up = new QMatrix(gguf, p + "ffn_up.weight");
                b.down = new QMatrix(gguf, p + "ffn_down.weight");
                blocks[l] = b;
            }
            if (dim == 0 || layers == 0 || heads == 0 || ffn == 0 || dim % 32 != 0 || ffn % 32 != 0 || headDim % 2 != 0) {
                throw new IOException("Unexpected model dimensions");
            }
        } catch (IOException | RuntimeException e) {
            gguf.close();
            throw e;
        }
        this.pool = new WorkerPool(threads);
    }

    private float[] vector(String name) throws IOException {
        QMatrix m = new QMatrix(gguf, name);
        float[] v = new float[m.cols * m.rows];
        QMatrix.Scratch s = new QMatrix.Scratch();
        for (int r = 0; r < m.rows; r++) m.dequantizeRow(r, v, r * m.cols, s);
        return v;
    }

    private float[] optionalVector(String name) throws IOException {
        return gguf.tensors.containsKey(name) ? vector(name) : null;
    }

    public Session newSession(int capacity) {
        return new Session(this, Math.min(capacity, trainContext));
    }

    /**
     * Runs tokens through the model at positions session.length..; appends them to the session and returns the
     * logits for the last one. Long inputs are processed in batches (prefill), which is much faster per token.
     */
    public float[] forward(Session s, int[] tokens, int from, int to) {
        float[] logits = null;
        int pos = from;
        while (pos < to) {
            int n = Math.min(to - pos, 16);
            logits = forwardBatch(s, tokens, pos, n);
            pos += n;
        }
        return logits;
    }

    private float[] forwardBatch(Session s, int[] tokens, int start, int n) {
        if (s.length + n > s.capacity) throw new IllegalStateException("context full");
        int base = s.length;
        s.ensure(base + n);
        float[] x = new float[n * dim];
        QMatrix.Scratch sc = pool.localScratch();
        for (int t = 0; t < n; t++) embedding.dequantizeRow(tokens[start + t], x, t * dim, sc);

        float[] xb = new float[n * dim];
        float[] q = new float[n * dim];
        float[] k = new float[n * kvDim];
        float[] v = new float[n * kvDim];
        float[] att = new float[n * dim];
        float[] tmp = new float[n * dim];
        float[] g = new float[n * ffn];
        float[] u = new float[n * ffn];
        QMatrix.Activations actDim = new QMatrix.Activations(dim);
        QMatrix.Activations actFfn = new QMatrix.Activations(ffn);

        for (int l = 0; l < layers; l++) {
            Layer b = blocks[l];
            rmsnorm(xb, x, b.attnNorm, n, dim);
            actDim.set(xb, n);
            matmul(b.wq, actDim, q, dim);
            matmul(b.wk, actDim, k, kvDim);
            matmul(b.wv, actDim, v, kvDim);
            for (int t = 0; t < n; t++) {
                addBias(q, t * dim, b.bq);
                addBias(k, t * kvDim, b.bk);
                addBias(v, t * kvDim, b.bv);
                int p = base + t;
                rope(q, t * dim, heads, p);
                rope(k, t * kvDim, kvHeads, p);
                System.arraycopy(k, t * kvDim, s.keys[l], p * kvDim, kvDim);
                System.arraycopy(v, t * kvDim, s.values[l], p * kvDim, kvDim);
            }
            attention(s, l, q, att, n, base);
            actDim.set(att, n);
            matmul(b.wo, actDim, tmp, dim);
            for (int i = 0; i < n * dim; i++) x[i] += tmp[i];

            rmsnorm(xb, x, b.ffnNorm, n, dim);
            actDim.set(xb, n);
            matmul(b.gate, actDim, g, ffn);
            matmul(b.up, actDim, u, ffn);
            for (int i = 0; i < n * ffn; i++) {
                float a = g[i];
                g[i] = a / (1f + (float) Math.exp(-a)) * u[i];
            }
            actFfn.set(g, n);
            matmul(b.down, actFfn, tmp, dim);
            for (int i = 0; i < n * dim; i++) x[i] += tmp[i];
        }
        for (int t = 0; t < n; t++) s.tokens[base + t] = tokens[start + t];
        s.length = base + n;

        // logits only for the last position
        float[] last = new float[dim];
        rmsnorm(last, 0, x, (n - 1) * dim, outputNorm, dim);
        QMatrix.Activations a = new QMatrix.Activations(dim);
        a.set(last, 1);
        float[] logits = new float[vocab];
        int rows = Math.min(vocab, output.rows);
        pool.parallelFor(rows, 256, (r0, r1, scr) -> output.mulRows(a, logits, rows, r0, r1, scr));
        return logits;
    }

    private void matmul(QMatrix w, QMatrix.Activations x, float[] out, int outDim) {
        pool.parallelFor(w.rows, 32, (r0, r1, scr) -> w.mulRows(x, out, outDim, r0, r1, scr));
    }

    private static void addBias(float[] x, int off, float[] bias) {
        if (bias == null) return;
        for (int i = 0; i < bias.length; i++) x[off + i] += bias[i];
    }

    private void rmsnorm(float[] out, float[] x, float[] w, int n, int size) {
        for (int t = 0; t < n; t++) rmsnorm(out, t * size, x, t * size, w, size);
    }

    private void rmsnorm(float[] out, int oo, float[] x, int xo, float[] w, int size) {
        double ss = 0;
        for (int i = 0; i < size; i++) ss += (double) x[xo + i] * x[xo + i];
        float inv = (float) (1.0 / Math.sqrt(ss / size + eps));
        for (int i = 0; i < size; i++) out[oo + i] = x[xo + i] * inv * w[i];
    }

    private void rope(float[] x, int off, int nHeads, int pos) {
        int half = headDim / 2;
        for (int i = 0; i < half; i++) {
            double freq = Math.pow(ropeTheta, -2.0 * i / headDim);
            double angle = pos * freq;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            for (int h = 0; h < nHeads; h++) {
                int hb = off + h * headDim;
                int i0, i1;
                if (neoxRope) {
                    i0 = hb + i;
                    i1 = hb + i + half;
                } else {
                    i0 = hb + 2 * i;
                    i1 = hb + 2 * i + 1;
                }
                float a = x[i0], b = x[i1];
                x[i0] = a * cos - b * sin;
                x[i1] = a * sin + b * cos;
            }
        }
    }

    private void attention(Session s, int l, float[] q, float[] out, int n, int base) {
        float[] keys = s.keys[l], values = s.values[l];
        float scale = (float) (1.0 / Math.sqrt(headDim));
        int group = heads / kvHeads;
        pool.parallelFor(n * heads, 1, (j0, j1, scr) -> {
            float[] scores = new float[base + n];
            for (int j = j0; j < j1; j++) {
                int t = j / heads, h = j % heads;
                int pos = base + t;
                int qo = t * dim + h * headDim;
                int kvo = (h / group) * headDim;
                float max = Float.NEGATIVE_INFINITY;
                for (int p = 0; p <= pos; p++) {
                    int ko = p * kvDim + kvo;
                    float dot = 0;
                    for (int i = 0; i < headDim; i++) dot += q[qo + i] * keys[ko + i];
                    dot *= scale;
                    scores[p] = dot;
                    if (dot > max) max = dot;
                }
                float sum = 0;
                for (int p = 0; p <= pos; p++) {
                    float e = (float) Math.exp(scores[p] - max);
                    scores[p] = e;
                    sum += e;
                }
                int oo = t * dim + h * headDim;
                for (int i = 0; i < headDim; i++) out[oo + i] = 0;
                for (int p = 0; p <= pos; p++) {
                    float w = scores[p] / sum;
                    int vo = p * kvDim + kvo;
                    for (int i = 0; i < headDim; i++) out[oo + i] += w * values[vo + i];
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        pool.close();
        gguf.close();
    }
}
