package com.aigirlfriend.client.voice;

import java.util.Arrays;

/**
 * A spoken line as mono 16-bit PCM that may still be growing: the renderer appends chunk after chunk while the
 * sound engine already plays the beginning. Once finished it never changes, so finished clips can be cached and
 * played any number of times. Thread-safe.
 */
final class VoiceClip {
    final int sampleRate;
    private short[] data;
    private int length;
    private boolean finished;
    private volatile boolean cancelled;

    /** An empty clip to append to. */
    VoiceClip(int sampleRate) {
        this(sampleRate, new short[sampleRate * 4], 0, false);
    }

    private VoiceClip(int sampleRate, short[] data, int length, boolean finished) {
        this.sampleRate = sampleRate;
        this.data = data;
        this.length = length;
        this.finished = finished;
    }

    /** A complete line. */
    static VoiceClip of(short[] pcm, int sampleRate) {
        return new VoiceClip(sampleRate, pcm, pcm.length, true);
    }

    synchronized void append(short[] pcm) {
        if (finished) throw new IllegalStateException("clip already finished");
        if (length + pcm.length > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, length + pcm.length));
        System.arraycopy(pcm, 0, data, length, pcm.length);
        length += pcm.length;
    }

    synchronized void finish() {
        finished = true;
    }

    synchronized boolean finished() {
        return finished;
    }

    synchronized int length() {
        return length;
    }

    /** Copies up to {@code max} samples from {@code position}: the count, 0 if none are ready yet, -1 at the end. */
    synchronized int read(int position, short[] out, int max) {
        int n = Math.max(0, Math.min(max, length - position));
        if (n == 0) return finished ? -1 : 0;
        System.arraycopy(data, position, out, 0, n);
        return n;
    }

    /** Asks the renderer to stop adding to this line (it was cut off or its speaker left). */
    void cancel() {
        cancelled = true;
    }

    boolean cancelled() {
        return cancelled;
    }

    synchronized short[] samples() {
        return Arrays.copyOf(data, length);
    }

    float seconds() {
        return length() / (float) sampleRate;
    }
}
