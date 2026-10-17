package com.aigirlfriend.client.voice;

import com.aigirlfriend.companion.Emotion;

/**
 * How an emotion colours her delivery: pitch and tempo multipliers, brightness (dB of presence lift),
 * melodic range of the intonation and loudness. Deliberately subtle so she always sounds like herself.
 */
record Prosody(float pitch, float rate, float brightness, float range, float loudness) {
    static final Prosody NEUTRAL = new Prosody(1.0f, 1.0f, 0.0f, 1.0f, 1.0f);

    static Prosody of(int emotionId) {
        return switch (Emotion.byId(emotionId)) {
            case JOY -> new Prosody(1.05f, 1.05f, 1.0f, 1.25f, 1.0f);
            case LOVE -> new Prosody(1.03f, 0.95f, 0.5f, 1.1f, 0.92f);
            case PRIDE -> new Prosody(1.03f, 1.02f, 0.8f, 1.15f, 1.0f);
            case RELIEF -> new Prosody(1.0f, 0.95f, 0.3f, 1.0f, 0.95f);
            case SURPRISE -> new Prosody(1.09f, 1.08f, 1.2f, 1.4f, 1.0f);
            case FEAR -> new Prosody(1.08f, 1.12f, 0.6f, 1.2f, 0.95f);
            case WORRY -> new Prosody(1.04f, 1.06f, 0.2f, 1.05f, 0.92f);
            case EMBARRASSMENT -> new Prosody(1.05f, 1.04f, 0.3f, 1.0f, 0.85f);
            case ANNOYANCE -> new Prosody(0.98f, 1.04f, 0.4f, 0.9f, 1.0f);
            case SADNESS -> new Prosody(0.94f, 0.88f, -1.0f, 0.7f, 0.88f);
            case DISCOMFORT -> new Prosody(0.96f, 0.94f, -0.5f, 0.85f, 0.9f);
            case TIREDNESS -> new Prosody(0.93f, 0.85f, -1.2f, 0.65f, 0.85f);
            case NONE -> NEUTRAL;
        };
    }
}
