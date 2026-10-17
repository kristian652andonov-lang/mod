package com.aigirlfriend.ai.local;

import java.util.Arrays;
import java.util.Random;

/** Temperature / top-k / top-p sampling with a repetition penalty over recent tokens. */
final class Sampler {
    final float temperature;
    final int topK;
    final float topP;
    final float repetitionPenalty;

    Sampler(float temperature, int topK, float topP, float repetitionPenalty) {
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
        this.repetitionPenalty = repetitionPenalty;
    }

    /** Picks the next token; {@code banned} tokens are never chosen, {@code recent} ones are penalized. */
    int sample(float[] logits, int[] recent, int recentCount, boolean[] banned, Random random) {
        float[] l = logits.clone();
        if (banned != null) {
            for (int i = 0; i < l.length; i++) if (banned[i]) l[i] = Float.NEGATIVE_INFINITY;
        }
        if (repetitionPenalty != 1f) {
            for (int i = 0; i < recentCount; i++) {
                int t = recent[i];
                if (t >= 0 && t < l.length && l[t] != Float.NEGATIVE_INFINITY) l[t] = l[t] > 0 ? l[t] / repetitionPenalty : l[t] * repetitionPenalty;
            }
        }
        if (temperature <= 0f) return argmax(l);
        int k = Math.min(topK > 0 ? topK : l.length, l.length);
        int[] idx = topIndices(l, k);
        double max = l[idx[0]];
        double[] p = new double[k];
        double sum = 0;
        for (int i = 0; i < k; i++) {
            p[i] = Math.exp((l[idx[i]] - max) / temperature);
            sum += p[i];
        }
        double cum = 0;
        int cut = k;
        for (int i = 0; i < k; i++) {
            cum += p[i] / sum;
            if (cum >= topP) {
                cut = i + 1;
                break;
            }
        }
        double total = 0;
        for (int i = 0; i < cut; i++) total += p[i];
        double r = random.nextDouble() * total;
        for (int i = 0; i < cut; i++) {
            r -= p[i];
            if (r <= 0) return idx[i];
        }
        return idx[cut - 1];
    }

    static int argmax(float[] l) {
        int best = 0;
        for (int i = 1; i < l.length; i++) if (l[i] > l[best]) best = i;
        return best;
    }

    /** Indices of the k largest values, largest first. */
    private static int[] topIndices(float[] l, int k) {
        int[] heap = new int[k];
        int size = 0;
        for (int i = 0; i < l.length; i++) {
            float v = l[i];
            if (v == Float.NEGATIVE_INFINITY) continue;
            if (size < k) {
                heap[size++] = i;
                if (size == k) heapify(heap, l, k);
            } else if (v > l[heap[0]]) {
                heap[0] = i;
                siftDown(heap, l, 0, k);
            }
        }
        if (size < k) {
            int[] h = Arrays.copyOf(heap, size);
            heap = h;
            k = size;
        }
        Integer[] boxed = new Integer[k];
        for (int i = 0; i < k; i++) boxed[i] = heap[i];
        Arrays.sort(boxed, (a, b) -> Float.compare(l[b], l[a]));
        int[] out = new int[k];
        for (int i = 0; i < k; i++) out[i] = boxed[i];
        return out;
    }

    private static void heapify(int[] h, float[] l, int n) {
        for (int i = n / 2 - 1; i >= 0; i--) siftDown(h, l, i, n);
    }

    private static void siftDown(int[] h, float[] l, int i, int n) {
        while (true) {
            int a = 2 * i + 1, b = a + 1, m = i;
            if (a < n && l[h[a]] < l[h[m]]) m = a;
            if (b < n && l[h[b]] < l[h[m]]) m = b;
            if (m == i) return;
            int t = h[i];
            h[i] = h[m];
            h[m] = t;
            i = m;
        }
    }
}
