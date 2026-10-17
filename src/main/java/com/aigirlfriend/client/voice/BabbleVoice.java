package com.aigirlfriend.client.voice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Her built-in voice for machines without text-to-speech: a soft formant-synthesised babble that follows the
 * syllables, rhythm and melody of the line (rising on questions, pausing at punctuation). Deterministic per line.
 */
final class BabbleVoice {
    private static final int SR = VoiceDsp.RATE;
    private static final int BLOCK = 32;
    private static final float BASE_F0 = 232f;
    private static final float FORMANT_SCALE = 1.06f;
    private static final int MAX_HARMONICS = 48;
    private static final double[] TILT = new double[MAX_HARMONICS + 1];

    private static final float[] A = {850, 1450, 2800}, E = {580, 2150, 2950}, I = {380, 2650, 3250}, IH = {470, 2250, 3000},
            O = {560, 1000, 2750}, U = {420, 1100, 2650}, UH = {680, 1450, 2850};
    private static final float[] NASAL_M = {260, 1100, 2500}, NASAL_N = {260, 1650, 2650}, LIQUID_L = {380, 1250, 2800},
            LIQUID_R = {440, 1250, 1750}, GLIDE_W = {330, 800, 2400}, GLIDE_Y = {300, 2500, 3150};
    private static final String[] DIGITS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    static {
        for (int k = 1; k <= MAX_HARMONICS; k++) TILT[k] = Math.pow(k, -0.72);
    }

    /** A stretch of sound: voicing/frication/breath levels, a formant glide and a pitch glide relative to her base f0. */
    private record Seg(int len, float voice, float noise, float noiseHz, float asp, float[] from, float[] to, float pitchFrom, float pitchTo) {}

    private static final class Syl {
        String onset = "";
        float[] from, to;
        char coda;
        boolean stressed, wordStart;
    }

    private BabbleVoice() {}

    static float[] render(String text, Prosody prosody, float pitchScale, float rateScale) {
        SplittableRandom rnd = new SplittableRandom(text.hashCode() * 0x9E3779B97F4A7C15L ^ Float.floatToIntBits(prosody.pitch()));
        float tempo = VoiceDsp.clamp(prosody.rate() * rateScale, 0.5f, 2f);
        List<Seg> segs = plan(text, prosody, tempo, rnd);
        return synthesize(segs, BASE_F0 * prosody.pitch() * pitchScale, rnd);
    }

    // ------------------------------------------------------------------ text -> segments

    private static List<Seg> plan(String text, Prosody prosody, float tempo, SplittableRandom rnd) {
        List<Seg> segs = new ArrayList<>();
        segs.add(silence(0.03f, 1f));
        List<Syl> phrase = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '\'') {
                word.append(c);
                continue;
            }
            addWord(word, phrase);
            char end = 0;
            if (c == '.') {
                end = text.startsWith("...", i) ? '~' : '.';
                if (end == '~') i += 2;
            } else if (c == '!' || c == '?') {
                end = c;
            } else if (c == ',' || c == ';' || c == ':') {
                end = ',';
            }
            if (end != 0) {
                emitPhrase(phrase, end, prosody, tempo, rnd, segs);
                phrase.clear();
            }
        }
        addWord(word, phrase);
        emitPhrase(phrase, '.', prosody, tempo, rnd, segs);
        segs.add(silence(0.06f, 1f));
        return segs;
    }

    private static void addWord(StringBuilder word, List<Syl> phrase) {
        if (word.isEmpty()) return;
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= '0' && c <= '9') {
                syllabify(letters.toString(), phrase);
                letters.setLength(0);
                syllabify(DIGITS[c - '0'], phrase);
            } else {
                letters.append(c);
            }
        }
        syllabify(letters.toString(), phrase);
        word.setLength(0);
    }

    private static void syllabify(String raw, List<Syl> out) {
        if (raw.isEmpty()) return;
        String w = phones(raw);
        List<Syl> res = new ArrayList<>();
        if (w.isEmpty()) {
            // letters outside a-z (other scripts): pseudo-syllables with a stable sound per character
            float[][] vowels = {A, E, I, O, U};
            String onsets = "mnlstkdh";
            for (int i = 0; i < Math.min(4, raw.length() / 2 + 1); i++) {
                int cp = raw.charAt(Math.min(raw.length() - 1, i * 2));
                res.add(syl(String.valueOf(onsets.charAt(cp % onsets.length())), vowels[(cp / 7) % vowels.length], null));
            }
        } else {
            int i = 0, n = w.length();
            while (i < n) {
                int cs = i;
                while (i < n && !isVowel(w.charAt(i))) i++;
                String cons = w.substring(cs, i);
                if (i >= n) {
                    if (res.isEmpty()) {
                        Syl s = syl(cons.length() > 1 ? cons.substring(0, cons.length() - 1) : cons, UH, null);
                        if (cons.length() > 1) s.coda = cons.charAt(cons.length() - 1);
                        res.add(s);
                    } else if (!cons.isEmpty()) {
                        res.get(res.size() - 1).coda = cons.charAt(0);
                    }
                    break;
                }
                if (!res.isEmpty() && cons.length() >= 2) {
                    res.get(res.size() - 1).coda = cons.charAt(0);
                    cons = cons.substring(1);
                }
                int vs = i;
                while (i < n && isVowel(w.charAt(i)) && i - vs < 2) i++;
                String nucleus = w.substring(vs, i);
                if (nucleus.equals("y") && i >= n && res.isEmpty()) res.add(syl(cons, A, I));
                else res.add(nucleus(syl(cons, null, null), nucleus));
            }
        }
        if (res.isEmpty()) return;
        res.get(0).wordStart = true;
        res.get(0).stressed = res.size() >= 2 || raw.length() >= 4;
        out.addAll(res);
    }

    /** Spelling to rough phones: digraphs become single symbols (S=sh, C=ch, T=th, N=ng, Y=consonant y). */
    private static String phones(String raw) {
        String s = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z]", "");
        if (s.isEmpty()) return s;
        s = s.replace("tch", "C").replace("sch", "sk").replace("sh", "S").replace("ch", "C").replace("th", "T").replace("ph", "f")
                .replace("wh", "w").replace("ck", "k").replace("qu", "kw").replace("gh", "").replace("ng", "N").replace("x", "ks");
        s = s.replaceAll("c(?=[eiy])", "s").replace('c', 'k').replace('q', 'k');
        s = s.replaceAll("([b-df-hj-np-tv-zCSTN])\\1+", "$1");
        if (s.length() > 1 && s.charAt(0) == 'y' && isVowel(s.charAt(1))) s = "Y" + s.substring(1);
        s = s.replaceAll("(?<=[aeiou])y(?=[aeiou])", "Y");
        if (s.length() > 3 && s.endsWith("e") && !isVowel(s.charAt(s.length() - 2)) && !s.endsWith("le")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
    }

    private static Syl syl(String cons, float[] from, float[] to) {
        Syl s = new Syl();
        if (cons.length() <= 1) {
            s.onset = cons;
        } else {
            char lastC = cons.charAt(cons.length() - 1);
            s.onset = "" + cons.charAt(0) + ("lrwY".indexOf(lastC) >= 0 ? String.valueOf(lastC) : "");
        }
        s.from = from;
        s.to = to != null ? to : from;
        return s;
    }

    private static Syl nucleus(Syl s, String v) {
        float[][] p = switch (v) {
            case "ai", "ay", "ey" -> new float[][]{E, I};
            case "au", "ao", "oa", "oe" -> new float[][]{O, O};
            case "ee", "ea", "ie", "ei" -> new float[][]{I, I};
            case "eu" -> new float[][]{I, U};
            case "oo", "ue", "ui" -> new float[][]{U, U};
            case "ou" -> new float[][]{A, U};
            case "oi", "oy" -> new float[][]{O, I};
            default -> v.length() == 2 ? new float[][]{single(v.charAt(0)), single(v.charAt(1))} : new float[][]{single(v.charAt(0)), single(v.charAt(0))};
        };
        s.from = p[0];
        s.to = p[1];
        return s;
    }

    private static float[] single(char c) {
        return switch (c) {
            case 'a' -> A;
            case 'e' -> E;
            case 'i' -> IH;
            case 'o' -> O;
            case 'u' -> UH;
            default -> I;
        };
    }

    private static void emitPhrase(List<Syl> phrase, char end, Prosody prosody, float tempo, SplittableRandom rnd, List<Seg> segs) {
        int count = phrase.size();
        if (count == 0) {
            if (end != ',') segs.add(silence(0.2f, tempo));
            return;
        }
        float range = prosody.range();
        float lift = end == '!' ? 0.05f : 0f;
        for (int i = 0; i < count; i++) {
            Syl s = phrase.get(i);
            float u = count > 1 ? i / (float) (count - 1) : 1f;
            float base = 1.05f - 0.10f * u + lift;
            float accent = s.stressed ? 0.05f + (float) rnd.nextDouble() * 0.05f : (float) rnd.nextDouble() * 0.03f - 0.015f;
            float pFrom = base + accent, pTo = base + accent * 0.4f - 0.01f;
            float len = 0.095f * (s.stressed ? 1.2f : 1f) * (0.9f + 0.2f * (float) rnd.nextDouble());
            if (s.from != s.to) len *= 1.2f;
            if (i == count - 1) {
                len *= end == '~' ? 1.7f : 1.45f;
                switch (end) {
                    case '?' -> { pFrom = base + 0.02f; pTo = 1.32f; }
                    case '!' -> { pFrom = 1.16f; pTo = 0.92f; }
                    case ',' -> pTo = 1.06f;
                    case '~' -> pTo = 0.88f;
                    default -> { pFrom = base + accent * 0.5f; pTo = 0.86f; }
                }
            } else if (i == count - 2 && end == '?') {
                pTo = Math.min(pTo, 0.98f);
            }
            pFrom = 1 + (pFrom - 1) * range;
            pTo = 1 + (pTo - 1) * range;
            float[] from = scale(s.from), to = scale(s.to);
            if (s.wordStart && i > 0) segs.add(new Seg(samples(0.016f, tempo), 0.25f, 0, 0, 0, from, from, pFrom, pFrom));
            for (int k = 0; k < s.onset.length(); k++) consonant(s.onset.charAt(k), from, pFrom, tempo, segs);
            segs.add(new Seg(samples(len, tempo), 1f, 0, 0, 0, from, to, pFrom, pTo));
            if (s.coda != 0) consonant(s.coda, to, pTo, tempo, segs);
        }
        segs.add(silence(switch (end) {
            case ',' -> 0.16f;
            case '~' -> 0.42f;
            default -> 0.32f;
        }, tempo));
    }

    private static void consonant(char c, float[] vowel, float pitch, float tempo, List<Seg> segs) {
        switch (c) {
            case 's' -> segs.add(seg(0.065f, 0, 0.28f, 6200, 0, vowel, pitch, tempo));
            case 'z' -> segs.add(seg(0.055f, 0.3f, 0.25f, 6000, 0, vowel, pitch, tempo));
            case 'S' -> segs.add(seg(0.065f, 0, 0.26f, 3000, 0, vowel, pitch, tempo));
            case 'f', 'T' -> segs.add(seg(0.05f, 0, 0.15f, 4500, 0, vowel, pitch, tempo));
            case 'v' -> segs.add(seg(0.045f, 0.3f, 0.12f, 4000, 0, vowel, pitch, tempo));
            case 'h' -> segs.add(seg(0.045f, 0, 0, 0, 0.22f, vowel, pitch, tempo));
            case 'p', 't', 'k' -> {
                segs.add(seg(0.028f, 0, 0, 0, 0, vowel, pitch, tempo));
                segs.add(seg(0.012f, 0, 0.22f, c == 'p' ? 900 : c == 't' ? 4200 : 2000, 0, vowel, pitch, tempo));
                segs.add(seg(0.018f, 0, 0, 0, 0.15f, vowel, pitch, tempo));
            }
            case 'b', 'd', 'g' -> {
                segs.add(seg(0.022f, 0.12f, 0, 0, 0, vowel, pitch, tempo));
                segs.add(seg(0.01f, 0.2f, 0.15f, c == 'b' ? 900 : c == 'd' ? 4000 : 2000, 0, vowel, pitch, tempo));
            }
            case 'C', 'j' -> {
                segs.add(seg(0.022f, c == 'j' ? 0.12f : 0, 0, 0, 0, vowel, pitch, tempo));
                segs.add(seg(0.05f, c == 'j' ? 0.3f : 0, c == 'j' ? 0.22f : 0.3f, 3000, 0, vowel, pitch, tempo));
            }
            case 'm' -> segs.add(seg(0.05f, 0.45f, 0, 0, 0, scale(NASAL_M), pitch, tempo));
            case 'n', 'N' -> segs.add(seg(0.05f, 0.45f, 0, 0, 0, scale(NASAL_N), pitch, tempo));
            case 'l' -> segs.add(seg(0.04f, 0.65f, 0, 0, 0, scale(LIQUID_L), pitch, tempo));
            case 'r' -> segs.add(seg(0.045f, 0.6f, 0, 0, 0, scale(LIQUID_R), pitch, tempo));
            case 'w' -> segs.add(seg(0.04f, 0.55f, 0, 0, 0, scale(GLIDE_W), pitch, tempo));
            case 'Y' -> segs.add(seg(0.035f, 0.6f, 0, 0, 0, scale(GLIDE_Y), pitch, tempo));
            default -> { }
        }
    }

    private static Seg seg(float seconds, float voice, float noise, float noiseHz, float asp, float[] formants, float pitch, float tempo) {
        return new Seg(samples(seconds, tempo), voice, noise, noiseHz, asp, formants, formants, pitch, pitch);
    }

    private static Seg silence(float seconds, float tempo) {
        float[] f = scale(UH);
        return new Seg(samples(seconds, tempo), 0, 0, 0, 0, f, f, 1f, 1f);
    }

    private static int samples(float seconds, float tempo) {
        return Math.max(1, Math.round(seconds / tempo * SR));
    }

    private static float[] scale(float[] f) {
        return new float[]{f[0] * FORMANT_SCALE, f[1] * FORMANT_SCALE, f[2] * FORMANT_SCALE};
    }

    // ------------------------------------------------------------------ segments -> audio

    private static float[] synthesize(List<Seg> segs, float f0Base, SplittableRandom rnd) {
        int total = 0;
        for (Seg s : segs) total += s.len();
        float[] out = new float[Math.max(total, SR / 10)];
        float[] f = segs.get(0).from().clone();
        float voice = 0, noise = 0, asp = 0, noiseHz = 3000, pitch = segs.get(0).pitchFrom();
        float prevVoice = 0, prevNoise = 0, prevAsp = 0, jitter = 0;
        double[] amps = new double[MAX_HARMONICS + 2], prevAmps = new double[MAX_HARMONICS + 2];
        double phase = 0, prevInc = 2 * Math.PI * f0Base * pitch / SR;
        long noiseState = rnd.nextLong() | 1L;
        Resonator breathFilter = new Resonator(2400, 0.9), fricFilter = new Resonator(noiseHz, 1.4);
        double aF = 1 - Math.exp(-BLOCK / (0.018 * SR)), aA = 1 - Math.exp(-BLOCK / (0.006 * SR)), aP = 1 - Math.exp(-BLOCK / (0.03 * SR));
        int segIndex = 0, segStart = 0;
        for (int b0 = 0; b0 < total; b0 += BLOCK) {
            while (segIndex < segs.size() - 1 && b0 >= segStart + segs.get(segIndex).len()) segStart += segs.get(segIndex++).len();
            Seg s = segs.get(segIndex);
            float u = Math.min(1f, (b0 - segStart) / (float) s.len());
            for (int k = 0; k < 3; k++) f[k] += (float) ((s.from()[k] + (s.to()[k] - s.from()[k]) * u - f[k]) * aF);
            voice += (float) ((s.voice() - voice) * aA);
            noise += (float) ((s.noise() - noise) * aA);
            asp += (float) ((s.asp() - asp) * aA);
            if (s.noise() > 0 && Math.abs(s.noiseHz() - noiseHz) > 1) {
                noiseHz += (s.noiseHz() - noiseHz) * 0.5f;
                fricFilter.tune(noiseHz, 1.4);
            }
            pitch += (float) ((s.pitchFrom() + (s.pitchTo() - s.pitchFrom()) * u - pitch) * aP);
            jitter = jitter * 0.9f + (float) (rnd.nextDouble() - 0.5) * 0.004f;
            double f0 = f0Base * pitch * (1 + 0.005 * Math.sin(2 * Math.PI * 5.2 * b0 / SR) + jitter);
            double inc = 2 * Math.PI * f0 / SR;
            harmonics(f0, f, amps);
            int end = Math.min(total, b0 + BLOCK);
            for (int i = b0; i < end; i++) {
                float fr = (i - b0) / (float) BLOCK;
                phase += prevInc + (inc - prevInc) * fr;
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                double sk = Math.sin(phase), skm1 = 0, c2 = 2 * Math.cos(phase), sumPrev = 0, sumNew = 0;
                for (int k = 1; k <= MAX_HARMONICS && (amps[k] != 0 || prevAmps[k] != 0); k++) {
                    sumPrev += prevAmps[k] * sk;
                    sumNew += amps[k] * sk;
                    double next = c2 * sk - skm1;
                    skm1 = sk;
                    sk = next;
                }
                float v = (float) (sumPrev + (sumNew - sumPrev) * fr);
                float vAmp = prevVoice + (voice - prevVoice) * fr;
                float nAmp = prevNoise + (noise - prevNoise) * fr;
                float aAmp = prevAsp + (asp - prevAsp) * fr;
                noiseState ^= noiseState << 13;
                noiseState ^= noiseState >>> 7;
                noiseState ^= noiseState << 17;
                float white = (noiseState >>> 40) / (float) (1L << 24) * 2f - 1f;
                float breath = breathFilter.process(white) * (aAmp + 0.04f * vAmp) * (float) (0.65 + 0.35 * Math.cos(phase));
                out[i] = 0.5f * v * vAmp + breath + fricFilter.process(white) * nAmp;
            }
            System.arraycopy(amps, 0, prevAmps, 0, amps.length);
            prevVoice = voice;
            prevNoise = noise;
            prevAsp = asp;
            prevInc = inc;
        }
        return out;
    }

    /** Harmonic amplitudes of a gently tilted glottal source through a cascade of formant resonances, at unit RMS. */
    private static void harmonics(double f0, float[] formants, double[] amps) {
        int count = (int) Math.min(MAX_HARMONICS, Math.floor(SR * 0.45 / f0));
        double power = 0;
        for (int k = 1; k <= MAX_HARMONICS; k++) {
            if (k > count) {
                amps[k] = 0;
                continue;
            }
            double hz = k * f0;
            double a = TILT[k] * resonance(hz, formants[0], 90) * resonance(hz, formants[1], 110)
                    * resonance(hz, formants[2], 150) * resonance(hz, 4100 * FORMANT_SCALE, 260) * resonance(hz, 5000 * FORMANT_SCALE, 320);
            amps[k] = a;
            power += a * a / 2;
        }
        double norm = power > 0 ? 1 / Math.sqrt(power) : 0;
        for (int k = 1; k <= count; k++) amps[k] *= norm;
    }

    private static double resonance(double hz, double centre, double bandwidth) {
        double sigma = Math.PI * bandwidth, w = 2 * Math.PI * hz, wc = 2 * Math.PI * centre;
        double num = sigma * sigma + wc * wc;
        double a = num - w * w, b = 2 * sigma * w;
        return num / Math.sqrt(a * a + b * b);
    }

    /** Retunable band-pass (constant peak gain) for noise sources. */
    private static final class Resonator {
        private double b0, a1, a2, z1, z2;

        Resonator(double hz, double q) {
            tune(hz, q);
        }

        void tune(double hz, double q) {
            double w = 2 * Math.PI * Math.min(hz, SR * 0.45) / SR, al = Math.sin(w) / (2 * q), a0 = 1 + al;
            b0 = al / a0;
            a1 = -2 * Math.cos(w) / a0;
            a2 = (1 - al) / a0;
        }

        float process(float in) {
            double out = b0 * in + z1;
            z1 = -a1 * out + z2;
            z2 = -b0 * in - a2 * out;
            return (float) out;
        }
    }
}
