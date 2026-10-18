package com.aigirlfriend.client.voice;

import java.util.Arrays;

/**
 * Signal processing that gives her one recognisable voice whatever produced the speech: pitch tracking (YIN),
 * TD-PSOLA pitch/formant/tempo shifting that keeps timing natural, gentle tone shaping and level control.
 */
final class VoiceDsp {
    static final int RATE = 22050;
    /** Her typical speaking pitch in Hz; engine voices are moved towards it. */
    static final float HER_F0 = 218f;
    private static final float BASE_TEMPO = 1.03f;

    private VoiceDsp() {}

    // ------------------------------------------------------------------ her voice

    /** Engine speech to her voice. */
    static float[] herVoice(Pcm in, Prosody prosody, float pitchScale, float rateScale) {
        float[] x = trimSilence(resample(in.samples(), in.sampleRate(), RATE), RATE);
        if (x.length < RATE / 20) return null;
        PitchTrack track = track(x, RATE);
        float measured = track.medianF0();
        // move the engine voice into her register: small nudges for female voices, a real shift for male ones
        float base = measured <= 0 ? 1.06f : measured < 150f ? clamp(HER_F0 / measured, 1.3f, 1.85f) : clamp(HER_F0 / measured, 0.92f, 1.2f);
        float expressive = prosody.pitch() * pitchScale;
        float pitch = clamp(base * expressive, 0.5f, 2.0f);
        float formant = clamp((float) (1.015 * Math.pow(base, 0.3) * Math.pow(expressive, 0.2)), 0.9f, 1.2f);
        float tempo = clamp(BASE_TEMPO * prosody.rate() * rateScale, 0.5f, 2.0f);
        float[] y = psola(x, RATE, track, pitch, formant, tempo);
        tone(y, RATE, prosody.brightness(), true);
        return master(y, RATE, prosody.loudness());
    }

    /** Final polish for her synthesized voice. */
    static float[] finishSynth(float[] x, Prosody prosody) {
        tone(x, RATE, prosody.brightness() * 0.5f, false);
        return master(x, RATE, prosody.loudness());
    }

    private static float[] master(float[] y, int sr, float loudness) {
        normalize(y, sr, 0.1f, 0.95f);
        compress(y, sr, 0.063f, 2.5f);
        normalize(y, sr, 0.14f * loudness, 0.89f);
        fade(y, sr, 0.008f, 0.03f);
        return y;
    }

    static short[] toPcm16(float[] x) {
        short[] out = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            float v = x[i];
            if (!(v == v)) v = 0;
            out[i] = (short) Math.round(clamp(v, -1f, 1f) * 32767f);
        }
        return out;
    }

    // ------------------------------------------------------------------ resampling / trimming

    /** Windowed-sinc resampler (band-limited when downsampling). */
    static float[] resample(float[] x, int from, int to) {
        if (from == to || x.length == 0) return x;
        double ratio = (double) to / from;
        int n = (int) Math.round(x.length * ratio);
        double cutoff = Math.min(1.0, ratio) * 0.97;
        int half = 10;
        double reach = half / cutoff;
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            double c = i / ratio;
            int lo = Math.max(0, (int) Math.ceil(c - reach));
            int hi = Math.min(x.length - 1, (int) Math.floor(c + reach));
            double acc = 0, wsum = 0;
            for (int j = lo; j <= hi; j++) {
                double t = (j - c) * cutoff;
                double w = sinc(t) * (0.42 + 0.5 * Math.cos(Math.PI * t / half) + 0.08 * Math.cos(2 * Math.PI * t / half));
                acc += x[j] * w;
                wsum += w;
            }
            y[i] = wsum > 1e-9 ? (float) (acc / wsum) : 0f;
        }
        return y;
    }

    private static double sinc(double t) {
        if (Math.abs(t) < 1e-9) return 1.0;
        double p = Math.PI * t;
        return Math.sin(p) / p;
    }

    /** Cuts leading/trailing silence, keeping short natural margins. */
    static float[] trimSilence(float[] x, int sr) {
        int block = Math.max(1, sr / 200);
        float peak = 0;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        if (peak < 1e-4f) return x;
        double thr = peak * 0.016;
        int first = -1, last = -1;
        for (int b = 0; b * block < x.length; b++) {
            int s = b * block, e = Math.min(x.length, s + block);
            double sum = 0;
            for (int i = s; i < e; i++) sum += x[i] * x[i];
            if (Math.sqrt(sum / (e - s)) > thr) {
                if (first < 0) first = s;
                last = e;
            }
        }
        if (first < 0) return x;
        int start = Math.max(0, first - sr * 25 / 1000);
        int end = Math.min(x.length, last + sr * 60 / 1000);
        return Arrays.copyOfRange(x, start, end);
    }

    // ------------------------------------------------------------------ pitch tracking

    /** Pitch periods (in samples, 0 = unvoiced) every {@code hop} samples, and the median voiced f0. */
    record PitchTrack(int hop, float[] period, float medianF0) {
        float periodAt(int sample) {
            float fp = sample / (float) hop;
            int i = Math.min(period.length - 1, (int) fp);
            int j = Math.min(period.length - 1, i + 1);
            float a = period[i], b = period[j], f = fp - i;
            if (a > 0 && b > 0) return a + (b - a) * f;
            return f < 0.5f ? a : b;
        }
    }

    static PitchTrack track(float[] x, int sr) {
        int hop = Math.max(1, Math.round(sr * 0.01f));
        int w = Math.round(sr * 0.02f);
        int tauMin = Math.max(2, sr / 700), tauMax = sr / 60;
        int frames = x.length / hop + 1;
        float[] period = new float[frames];
        float[] energy = new float[frames];
        float maxEnergy = 0;
        for (int k = 0; k < frames; k++) {
            int s = Math.max(0, k * hop - w / 2), e = Math.min(x.length, s + w);
            double sum = 0;
            for (int i = s; i < e; i++) sum += x[i] * x[i];
            energy[k] = e > s ? (float) Math.sqrt(sum / (e - s)) : 0;
            maxEnergy = Math.max(maxEnergy, energy[k]);
        }
        float gate = Math.max(1e-4f, maxEnergy * 0.05f);
        double[] d = new double[tauMax + 2];
        int lastStart = x.length - w - tauMax - 2;
        if (lastStart >= 0) {
            for (int k = 0; k < frames; k++) {
                if (energy[k] < gate) continue;
                int s = Math.min(lastStart, Math.max(0, k * hop - w / 2));
                for (int tau = 1; tau <= tauMax + 1; tau++) {
                    double sum = 0;
                    for (int j = 0; j < w; j++) {
                        double diff = x[s + j] - x[s + j + tau];
                        sum += diff * diff;
                    }
                    d[tau] = sum;
                }
                double running = 0;
                d[0] = 1;
                for (int tau = 1; tau <= tauMax + 1; tau++) {
                    running += d[tau];
                    d[tau] = running > 1e-12 ? d[tau] * tau / running : 1;
                }
                int best = -1;
                for (int tau = tauMin; tau <= tauMax; tau++) {
                    if (d[tau] < 0.15) {
                        while (tau + 1 <= tauMax && d[tau + 1] < d[tau]) tau++;
                        best = tau;
                        break;
                    }
                }
                if (best < 0) {
                    int g = tauMin;
                    for (int tau = tauMin; tau <= tauMax; tau++) if (d[tau] < d[g]) g = tau;
                    if (d[g] < 0.3) best = g;
                }
                if (best < 0) continue;
                double p = best;
                if (best > tauMin && best < tauMax) {
                    double a = d[best - 1], b = d[best], c = d[best + 1], den = a - 2 * b + c;
                    if (den > 1e-12) p = best + 0.5 * (a - c) / den;
                }
                period[k] = (float) p;
            }
        }
        cleanTrack(period);
        float[] voiced = new float[frames];
        int n = 0;
        for (float p : period) if (p > 0) voiced[n++] = p;
        float median = 0;
        if (n >= 5) {
            Arrays.sort(voiced, 0, n);
            median = sr / voiced[n / 2];
        }
        return new PitchTrack(hop, period, median);
    }

    /** Drops voiced blips, fixes octave jumps against the local median and bridges tiny gaps. */
    private static void cleanTrack(float[] p) {
        int n = p.length;
        for (int i = 0; i < n; ) {
            if (p[i] <= 0) { i++; continue; }
            int j = i;
            while (j < n && p[j] > 0) j++;
            if (j - i < 3) Arrays.fill(p, i, j, 0f);
            i = j;
        }
        float[] copy = p.clone();
        float[] win = new float[9];
        for (int i = 0; i < n; i++) {
            if (copy[i] <= 0) continue;
            int m = 0;
            for (int k = Math.max(0, i - 4); k <= Math.min(n - 1, i + 4); k++) if (copy[k] > 0) win[m++] = copy[k];
            Arrays.sort(win, 0, m);
            float med = win[m / 2];
            float r = copy[i] / med;
            if (r > 1.6f || r < 0.62f) p[i] = med;
        }
        for (int i = 1; i < n; ) {
            if (p[i] > 0 || p[i - 1] <= 0) { i++; continue; }
            int j = i;
            while (j < n && p[j] <= 0) j++;
            if (j < n && j - i <= 2) {
                for (int k = i; k < j; k++) p[k] = p[i - 1] + (p[j] - p[i - 1]) * (k - i + 1) / (j - i + 1);
            }
            i = j;
        }
    }

    // ------------------------------------------------------------------ TD-PSOLA

    /**
     * Pitch-synchronous overlap-add. Grains are two periods long and centred on pitch marks; spacing them by
     * period/pitch changes the pitch, reading them compressed by {@code formant} moves the formants, and the
     * time map changes the tempo independently.
     */
    static float[] psola(float[] x, int sr, PitchTrack track, float pitch, float formant, float tempo) {
        int n = x.length;
        int unvoiced = Math.max(8, Math.round(sr * 0.005f));
        int cap = n / 4 + 16;
        int[] marks = new int[cap];
        float[] periods = new float[cap];
        boolean[] voicedMark = new boolean[cap];
        int count = 0;
        int t = 0, last = -1;
        boolean lastVoiced = false;
        while (t < n && count < cap) {
            float period = track.periodAt(t);
            if (period >= 8) {
                int ti = Math.round(period);
                int m = lastVoiced ? refineMark(x, last, last + ti, ti) : peak(x, t, Math.min(n, t + ti));
                if (m <= last) m = last + Math.max(1, ti / 2);
                if (m >= n) break;
                marks[count] = m;
                periods[count] = period;
                voicedMark[count++] = true;
                last = m;
                lastVoiced = true;
                t = m + ti;
            } else {
                marks[count] = t;
                periods[count] = unvoiced;
                voicedMark[count++] = false;
                last = t;
                lastVoiced = false;
                t += unvoiced;
            }
        }
        int outLen = Math.max(1, (int) (n / tempo));
        if (count == 0) return new float[outLen];
        // the real spacing between marks sets both grain size and synthesis step, so pitch 1 / tempo 1 is an exact copy
        float[] step = new float[count], reach = new float[count];
        for (int i = 0; i < count; i++) step[i] = i + 1 < count ? Math.max(1, marks[i + 1] - marks[i]) : periods[i];
        for (int i = 0; i < count; i++) reach[i] = Math.max(i > 0 ? step[i - 1] : step[i], step[i]);
        float[] acc = new float[outLen];
        float[] wsum = new float[outLen];
        double tOut = marks[0] / tempo;
        int k = 0;
        while (tOut < outLen) {
            double tIn = tOut * tempo;
            while (k + 1 < count && Math.abs(marks[k + 1] - tIn) <= Math.abs(marks[k] - tIn)) k++;
            double half = Math.max(4, reach[k] / formant);
            int lo = Math.max(0, (int) Math.ceil(tOut - half)), hi = Math.min(outLen - 1, (int) Math.floor(tOut + half));
            for (int o = lo; o <= hi; o++) {
                double j = o - tOut;
                double w = 0.5 + 0.5 * Math.cos(Math.PI * j / half);
                acc[o] += (float) (w * cubic(x, marks[k] + j * formant));
                wsum[o] += (float) w;
            }
            tOut += voicedMark[k] ? step[k] / pitch : step[k];
        }
        for (int i = 0; i < outLen; i++) acc[i] /= Math.max(wsum[i], 0.5f);
        return acc;
    }

    private static int peak(float[] x, int from, int to) {
        int best = from;
        for (int i = from; i < to; i++) if (Math.abs(x[i]) > Math.abs(x[best])) best = i;
        return best;
    }

    /** Places the next mark where the waveform best matches the previous period, keeping marks phase-consistent. */
    private static int refineMark(float[] x, int prev, int predicted, int period) {
        int range = Math.max(1, period / 4), halfLen = Math.max(2, period / 2);
        int best = predicted;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int delta = -range; delta <= range; delta++) {
            int c = predicted + delta;
            if (c - prev < period / 2) continue;
            double dot = 0, energy = 1e-9;
            for (int j = -halfLen; j < halfLen; j++) {
                int a = prev + j, b = c + j;
                if (a < 0 || b < 0 || a >= x.length || b >= x.length) continue;
                dot += x[a] * x[b];
                energy += x[b] * x[b];
            }
            double score = dot / Math.sqrt(energy);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    private static float cubic(float[] x, double pos) {
        int i = (int) Math.floor(pos);
        double f = pos - i;
        double p0 = at(x, i - 1), p1 = at(x, i), p2 = at(x, i + 1), p3 = at(x, i + 2);
        return (float) (p1 + 0.5 * f * (p2 - p0 + f * (2 * p0 - 5 * p1 + 4 * p2 - p3 + f * (3 * (p1 - p2) + p3 - p0))));
    }

    private static float at(float[] x, int i) {
        return i >= 0 && i < x.length ? x[i] : 0f;
    }

    // ------------------------------------------------------------------ tone and dynamics

    /** Her timbre: no rumble, a little body, clear presence, soft air and no harsh top end. */
    static void tone(float[] x, int sr, float brightness, boolean speech) {
        Biquad[] chain = {
                Biquad.highPass(sr, speech ? 95 : 80, 0.707),
                Biquad.peaking(sr, 240, 0.9, 1.5),
                Biquad.peaking(sr, 3000, 0.9, (speech ? 2.0 : 1.0) + brightness),
                Biquad.highShelf(sr, 6500, speech ? 1.5 : 0.5),
                Biquad.lowPass(sr, 9600, 0.707)
        };
        for (int i = 0; i < x.length; i++) {
            float v = x[i];
            for (Biquad b : chain) v = b.process(v);
            x[i] = v;
        }
    }

    /** Smooth peak compressor (threshold as linear amplitude). */
    static void compress(float[] x, int sr, float threshold, float ratio) {
        double att = Math.exp(-1.0 / (0.004 * sr)), rel = Math.exp(-1.0 / (0.08 * sr));
        double env = 0;
        for (int i = 0; i < x.length; i++) {
            double a = Math.abs(x[i]);
            env = a > env ? att * env + (1 - att) * a : rel * env + (1 - rel) * a;
            if (env > threshold) x[i] *= (float) Math.pow(env / threshold, 1.0 / ratio - 1.0);
        }
    }

    /** Scales so the active (non-silent) parts reach {@code targetRms}, never letting peaks exceed {@code ceiling}. */
    static void normalize(float[] x, int sr, float targetRms, float ceiling) {
        float gain = gainFor(x, sr, targetRms, ceiling);
        for (int i = 0; i < x.length; i++) x[i] *= gain;
    }

    /** The gain {@link #normalize} would apply (1 for silence). */
    static float gainFor(float[] x, int sr, float targetRms, float ceiling) {
        int block = Math.max(1, sr / 50);
        int blocks = (x.length + block - 1) / block;
        double[] rms = new double[blocks];
        double loudest = 0, peak = 0;
        for (int b = 0; b < blocks; b++) {
            int s = b * block, e = Math.min(x.length, s + block);
            double sum = 0;
            for (int i = s; i < e; i++) {
                sum += x[i] * x[i];
                peak = Math.max(peak, Math.abs(x[i]));
            }
            rms[b] = Math.sqrt(sum / Math.max(1, e - s));
            loudest = Math.max(loudest, rms[b]);
        }
        if (loudest < 1e-6 || peak < 1e-6) return 1f;
        double sum = 0;
        int used = 0;
        for (double r : rms) {
            if (r > loudest * 0.03) {
                sum += r * r;
                used++;
            }
        }
        double active = Math.sqrt(sum / Math.max(1, used));
        return (float) Math.min(targetRms / active, ceiling / peak);
    }

    static void fade(float[] x, int sr, float inSeconds, float outSeconds) {
        int in = Math.min(x.length / 2, (int) (sr * inSeconds)), out = Math.min(x.length / 2, (int) (sr * outSeconds));
        for (int i = 0; i < in; i++) x[i] *= (float) (0.5 - 0.5 * Math.cos(Math.PI * i / in));
        for (int i = 0; i < out; i++) x[x.length - 1 - i] *= (float) (0.5 - 0.5 * Math.cos(Math.PI * i / out));
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    /** RBJ biquad (transposed direct form II). */
    static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double z1, z2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        float process(float in) {
            double out = b0 * in + z1;
            z1 = b1 * in - a1 * out + z2;
            z2 = b2 * in - a2 * out;
            return (float) out;
        }

        static Biquad lowPass(int sr, double f, double q) {
            double w = 2 * Math.PI * Math.min(f, sr * 0.45) / sr, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
        }

        static Biquad highPass(int sr, double f, double q) {
            double w = 2 * Math.PI * f / sr, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
        }

        static Biquad peaking(int sr, double f, double q, double gainDb) {
            double a = Math.pow(10, gainDb / 40), w = 2 * Math.PI * f / sr, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a);
        }

        static Biquad highShelf(int sr, double f, double gainDb) {
            double a = Math.pow(10, gainDb / 40), w = 2 * Math.PI * Math.min(f, sr * 0.45) / sr, c = Math.cos(w);
            double al = Math.sin(w) / 2 * Math.sqrt(2), sa = 2 * Math.sqrt(a) * al;
            return new Biquad(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
                    (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa);
        }
    }
}
