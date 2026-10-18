package com.aigirlfriend.client.voice;

import com.aigirlfriend.client.voice.neural.NeuralVoice;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Renders a line to a {@link VoiceClip}: her natural (neural) voice when it is ready and allowed, otherwise system
 * speech shaped into her voice, or her synthesized voice. Neural lines are handed over as soon as enough of them
 * is rendered that the rest keeps up, and keep growing while they play. Called only from the voice worker; recent lines are cached so
 * repeats are instant.
 */
final class VoiceRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_LINES = 24;
    /** Debug: {@code -Daigirlfriend.voice.dump=<dir>} saves every rendered line as a WAV with a .txt beside it. */
    @Nullable
    private static final Path DUMP = dumpDir();
    private static final DateTimeFormatter DUMP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

    /** What to say and how; built on the game thread from the config so the worker never touches it. */
    record Request(String text, String neuralText, int emotion, boolean allowNeural, boolean allowSpeech, boolean allowSynth,
                   float pitch, float rate, String systemVoice, String neuralVoice) {}

    private static final Map<String, VoiceClip> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, VoiceClip> eldest) {
            return size() > CACHE_LINES;
        }
    };
    private static boolean loggedNeuralFailure;

    private VoiceRenderer() {}

    /**
     * Renders the line, completing {@code out} as soon as audio can start (with null if nothing could be said).
     * Stops early once {@code cancelled} says the line is no longer wanted.
     */
    static void render(Request r, CompletableFuture<VoiceClip> out, BooleanSupplier cancelled) {
        long start = System.nanoTime();
        long[] firstAudio = {0};
        out.thenRun(() -> firstAudio[0] = System.nanoTime()); // runs on this thread, the moment audio can start
        String engine = null;
        VoiceClip clip = null;
        if (r.allowNeural() && NeuralVoice.ready() && !r.neuralText().isEmpty()) {
            engine = "neural " + r.neuralVoice();
            clip = renderNeural(r, out, cancelled);
        }
        // a line cancelled mid-way (a newer one replaced it) must not be rendered again with the other voice
        if (clip == null && !cancelled.getAsBoolean() && (r.allowSpeech() || r.allowSynth()) && !r.text().isEmpty()) {
            String speech = r.allowSpeech() ? SystemTts.engineId(r.systemVoice()) : null;
            engine = speech == null ? "synth" : "speech " + speech;
            clip = renderClassic(r, speech);
        }
        out.complete(clip);
        if (DUMP != null && clip != null && !clip.cancelled() && !cancelled.getAsBoolean()) dump(r, engine, clip, start, firstAudio[0]);
    }

    @Nullable
    private static VoiceClip renderNeural(Request r, CompletableFuture<VoiceClip> out, BooleanSupplier cancelled) {
        String key = "neural|" + r.neuralVoice() + '|' + r.emotion() + '|' + r.pitch() + '|' + r.rate() + '|' + r.neuralText();
        VoiceClip hit = cached(key);
        if (hit != null) return hit;
        float pitch = VoiceDsp.clamp(r.pitch(), 0.7f, 1.4f);
        VoiceClip clip = new VoiceClip(NeuralVoice.SAMPLE_RATE);
        NeuralFinish finish = new NeuralFinish(clip.sampleRate, pitch, Prosody.of(r.emotion()).loudness());
        boolean complete = false;
        try {
            // a pitch change is a resample after the fact, so speak correspondingly slower to keep her pace
            NeuralVoice.speak(r.neuralText(), r.emotion(), r.rate() / pitch, 1f / pitch, r.neuralVoice(), (samples, boundary, last, play) -> {
                if (clip.cancelled() || cancelled.getAsBoolean()) return false;
                clip.append(finish.process(samples, boundary, last));
                if (play) out.complete(clip); // her first words play while the rest is rendered
                return !clip.cancelled();
            });
            complete = true;
        } catch (Exception e) {
            if (!loggedNeuralFailure) {
                loggedNeuralFailure = true;
                LOGGER.warn("Companion voice: her natural voice failed on a line{}", clip.length() == 0 ? "; using her other voice for it" : "", e);
            }
        } finally {
            clip.finish();
        }
        if (clip.length() == 0) return null;
        // a line cut short (cancelled, or a chunk failed) plays what it has but is never replayed from the cache
        if (complete && !clip.cancelled() && !cancelled.getAsBoolean()) cache(key, clip);
        return clip;
    }

    @Nullable
    private static VoiceClip renderClassic(Request r, @Nullable String engine) {
        Prosody prosody = Prosody.of(r.emotion());
        String key = (engine == null ? "synth" : engine) + '|' + r.emotion() + '|' + r.pitch() + '|' + r.rate() + '|' + r.text();
        VoiceClip hit = cached(key);
        if (hit != null) return hit;
        float[] out = null;
        if (engine != null) {
            Pcm speech = SystemTts.speak(r.text(), r.systemVoice());
            if (speech != null) out = VoiceDsp.herVoice(speech, prosody, r.pitch(), r.rate());
        }
        boolean fellBack = engine != null && out == null;
        if (out == null && r.allowSynth()) out = VoiceDsp.finishSynth(BabbleVoice.render(r.text(), prosody, r.pitch(), r.rate()), prosody);
        if (out == null) return null;
        VoiceClip clip = VoiceClip.of(VoiceDsp.toPcm16(out), VoiceDsp.RATE);
        if (!fellBack) cache(key, clip);
        return clip;
    }

    @Nullable
    private static VoiceClip cached(String key) {
        synchronized (CACHE) {
            return CACHE.get(key);
        }
    }

    private static void cache(String key, VoiceClip clip) {
        synchronized (CACHE) {
            CACHE.put(key, clip);
        }
    }

    /** Warms up detection (and the engine probe) so her first line is not delayed by it. */
    static void prepare(boolean allowSpeech, String systemVoice) {
        if (allowSpeech) SystemTts.engineId(systemVoice);
    }

    /**
     * Light finishing for neural chunks, which already sound right: trims Kokoro's padding silence, joins chunks
     * with a pause that suits the punctuation, keeps one gain for the whole line (no compression) and fades the
     * edges. Pitch is only touched when the player asked for it.
     */
    static final class NeuralFinish {
        private static final float TARGET_RMS = 0.13f, CEILING = 0.89f;
        private final int rate;
        private final float pitch, loudness;
        private float gain = -1;

        NeuralFinish(int rate, float pitch, float loudness) {
            this.rate = rate;
            this.pitch = pitch;
            this.loudness = loudness;
        }

        short[] process(float[] samples, char boundary, boolean last) {
            float[] x = VoiceDsp.trimSilence(samples, rate);
            if (Math.abs(pitch - 1f) > 0.005f) x = VoiceDsp.resample(x, Math.round(rate * pitch), rate);
            if (gain < 0) gain = VoiceDsp.gainFor(x, rate, TARGET_RMS * loudness, CEILING);
            for (int i = 0; i < x.length; i++) x[i] = softLimit(x[i] * gain);
            VoiceDsp.fade(x, rate, 0.004f, 0.012f);
            int pause = last ? 0 : rate * pauseMillis(boundary) / 1000;
            short[] pcm = VoiceDsp.toPcm16(x);
            return pause == 0 ? pcm : Arrays.copyOf(pcm, pcm.length + pause);
        }

        /** Later chunks share the first chunk's gain; the rare louder peak is rounded off instead of clipping. */
        private static float softLimit(float v) {
            float a = Math.abs(v);
            if (a <= CEILING) return v;
            float over = (a - CEILING) / (1f - CEILING);
            return Math.copySign(CEILING + (1f - CEILING) * (float) Math.tanh(over), v);
        }

        private static int pauseMillis(char boundary) {
            return switch (boundary) {
                case '.', '!' -> 300;
                case '?' -> 320;
                case '…' -> 420;
                case ',', ';', ':', '—' -> 160;
                default -> 120;
            };
        }
    }

    /** Whether lines are being saved ({@code -Daigirlfriend.voice.dump}). */
    static boolean dumping() {
        return DUMP != null;
    }

    @Nullable
    private static Path dumpDir() {
        String dir = System.getProperty("aigirlfriend.voice.dump");
        return dir == null || dir.isBlank() ? null : Path.of(dir);
    }

    private static void dump(Request r, String engine, VoiceClip clip, long start, long firstAudio) {
        try {
            Files.createDirectories(DUMP);
            String text = engine.startsWith("neural") ? r.neuralText() : r.text();
            String slug = text.replaceAll("[^A-Za-z0-9]+", "_");
            String name = LocalDateTime.now().format(DUMP_TIME) + "_" + engine.split(" ")[0] + "_" + slug.substring(0, Math.min(48, slug.length()));
            short[] pcm = clip.samples();
            Files.write(DUMP.resolve(name + ".wav"), WavFile.write(pcm, clip.sampleRate));
            double seconds = pcm.length / (double) clip.sampleRate, renderSeconds = (System.nanoTime() - start) / 1e9;
            double firstSeconds = firstAudio == 0 ? renderSeconds : (firstAudio - start) / 1e9;
            String info = String.format(Locale.ROOT, "text=%s%nengine=%s%nemotion=%d%nsampleRate=%d%naudioSeconds=%.3f%nrenderSeconds=%.3f%nfirstAudioSeconds=%.3f%nrtf=%.3f%n",
                    text, engine, r.emotion(), clip.sampleRate, seconds, renderSeconds, firstSeconds, seconds > 0 ? renderSeconds / seconds : 0);
            Files.writeString(DUMP.resolve(name + ".txt"), info, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Companion voice: could not dump a line", e);
        }
    }
}
