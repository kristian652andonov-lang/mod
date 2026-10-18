package com.aigirlfriend.client.voice.neural;

import com.aigirlfriend.companion.Emotion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How an emotion colours her natural voice. Kokoro already hears the feeling in the words, so this only adds what
 * listening tests found free of cost: a slightly faster or slower pace, half of another voice's delivery (pitch and
 * timing, not timbre) for bright or soft moods, and at most one thoughtful pause. Never pitch shifting, never added
 * interjections (both made her sound less natural).
 */
record Delivery(float speed, Tone tone, boolean pause, boolean firmPause, boolean exclaim) {
    /** Whose delivery is mixed in: none, af_bella's (brighter, livelier) or af_nicole's (lower, slower). */
    enum Tone { PLAIN, BRIGHT, SOFT }

    static final Delivery NEUTRAL = new Delivery(1.0f, Tone.PLAIN, false, false, false);
    private static final Pattern FIRST_COMMA = Pattern.compile(", (?=\\p{L})");
    /** A sentence end inside the line: lower-case word of 3+ letters, a full stop, then a capital (so not "Mr. Smith"). */
    private static final Pattern INNER_STOP = Pattern.compile("(?<=\\p{Ll}{3})\\. (?=\\p{Lu})");

    static Delivery of(int emotionId) {
        return switch (Emotion.byId(emotionId)) {
            case JOY -> new Delivery(1.05f, Tone.BRIGHT, false, false, true);
            case PRIDE, RELIEF -> new Delivery(1.05f, Tone.BRIGHT, false, false, false);
            case SURPRISE -> new Delivery(1.08f, Tone.BRIGHT, false, false, false);
            case LOVE -> new Delivery(0.92f, Tone.PLAIN, true, false, false);
            case EMBARRASSMENT -> new Delivery(0.95f, Tone.PLAIN, false, false, false);
            case SADNESS, DISCOMFORT -> new Delivery(0.90f, Tone.SOFT, true, true, false);
            case TIREDNESS -> new Delivery(0.85f, Tone.SOFT, true, true, false);
            case FEAR -> new Delivery(1.10f, Tone.PLAIN, false, false, true);
            case WORRY, ANNOYANCE -> new Delivery(1.05f, Tone.PLAIN, false, false, false);
            case NONE -> NEUTRAL;
        };
    }

    /** The line with this mood's punctuation: one "..." pause (first comma, or a sentence break) and a final "!". */
    String shape(String text) {
        String s = text;
        if (pause && !s.contains("...") && !s.contains("…")) {
            Matcher comma = FIRST_COMMA.matcher(s);
            Matcher stop = INNER_STOP.matcher(s);
            if (comma.find()) s = s.substring(0, comma.start()) + "... " + s.substring(comma.end());
            else if (firmPause && stop.find()) s = s.substring(0, stop.start()) + "... " + s.substring(stop.end());
        }
        if (exclaim && s.endsWith(".") && !s.endsWith("..")) s = s.substring(0, s.length() - 1) + "!";
        return s;
    }
}
