package com.aigirlfriend.client.voice;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders a cleaned line to mono 16-bit PCM at {@link VoiceDsp#RATE}: system speech shaped into her voice, or her
 * synthesized voice. Pure Java and called only from the voice worker; recent lines are cached so repeats are instant.
 */
final class VoiceRenderer {
    private static final int CACHE_LINES = 24;

    /** What to say and how; built on the game thread from the config so the worker never touches it. */
    record Request(String text, int emotion, boolean allowSpeech, boolean allowSynth, float pitch, float rate, String systemVoice) {}

    private static final Map<String, short[]> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, short[]> eldest) {
            return size() > CACHE_LINES;
        }
    };

    private VoiceRenderer() {}

    @Nullable
    static short[] render(Request r) {
        Prosody prosody = Prosody.of(r.emotion());
        String engine = r.allowSpeech() ? SystemTts.engineId(r.systemVoice()) : null;
        String key = (engine == null ? "synth" : engine) + '|' + r.emotion() + '|' + r.pitch() + '|' + r.rate() + '|' + r.text();
        synchronized (CACHE) {
            short[] hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        float[] out = null;
        if (engine != null) {
            Pcm speech = SystemTts.speak(r.text(), r.systemVoice());
            if (speech != null) out = VoiceDsp.herVoice(speech, prosody, r.pitch(), r.rate());
        }
        boolean fellBack = engine != null && out == null;
        if (out == null && r.allowSynth()) out = VoiceDsp.finishSynth(BabbleVoice.render(r.text(), prosody, r.pitch(), r.rate()), prosody);
        if (out == null) return null;
        short[] pcm = VoiceDsp.toPcm16(out);
        if (!fellBack) {
            synchronized (CACHE) {
                CACHE.put(key, pcm);
            }
        }
        return pcm;
    }

    /** Warms up detection (and the engine probe) so her first line is not delayed by it. */
    static void prepare(boolean allowSpeech, String systemVoice) {
        if (allowSpeech) SystemTts.engineId(systemVoice);
    }
}
