package com.aigirlfriend.client.voice;

/** Mono audio as floats in [-1, 1]. */
record Pcm(float[] samples, int sampleRate) {
    float seconds() {
        return samples.length / (float) sampleRate;
    }
}
