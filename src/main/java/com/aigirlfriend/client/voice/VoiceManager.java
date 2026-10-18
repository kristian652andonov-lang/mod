package com.aigirlfriend.client.voice;

import com.aigirlfriend.client.voice.neural.NeuralVoice;
import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.config.GFClientConfig.VoiceMode;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Her spoken voice on the client. Lines are rendered on a background worker (her natural neural voice once it is
 * downloaded and loaded; system speech shaped into her voice, or her synthesized voice, until then) and played from
 * her position through Minecraft's sound system. One line plays at a time per companion; a newer line replaces one
 * that is still waiting, and the one playing ends with what is already rendered. Game-thread only, apart from the
 * worker itself.
 */
public final class VoiceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How long a ready line politely waits for the previous one to finish before cutting in. */
    private static final int MAX_WAIT_TICKS = 50;
    /** Lines that take longer than this to start are dropped (her bubble is long gone by then). */
    private static final int MAX_RENDER_TICKS = 300;
    /**
     * Neural lines start once enough is rendered for the rest to keep up, which takes at most about 5 s on the
     * computer at hand; the extra margin covers a line queued behind another companion's or behind the model loading.
     */
    private static final int MAX_NEURAL_RENDER_TICKS = 400;
    /** Her natural voice's memory is released after this long without a voice mode that uses it. */
    private static final int NEURAL_UNLOAD_TICKS = 1200;

    private static final class Line {
        final int entityId;
        final long created;
        final int maxRenderTicks;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final CompletableFuture<VoiceClip> audio = new CompletableFuture<>();
        long readySince = -1;

        Line(int entityId, long created, int maxRenderTicks) {
            this.entityId = entityId;
            this.created = created;
            this.maxRenderTicks = maxRenderTicks;
        }

        void cancel() {
            cancelled.set(true);
            VoiceClip clip = audio.getNow(null);
            if (clip != null) clip.cancel();
        }
    }

    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "AIGirlfriend Voice");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final Map<Integer, Line> PENDING = new HashMap<>();
    private static final Map<Integer, VoiceSoundInstance> PLAYING = new HashMap<>();
    private static boolean warmedUp, shutDown;
    private static volatile boolean loggedFailure;
    private static long ticks;
    private static int neuralUnusedTicks;
    private static Path neuralDir;

    static {
        WORKER.allowCoreThreadTimeOut(true);
    }

    private VoiceManager() {}

    /** A finished (non-typing) line she says. */
    public static void speak(int entityId, String text, int emotion) {
        VoiceMode mode = mode();
        if (shutDown || mode == VoiceMode.OFF || text == null) return;
        float pitch = GFClientConfig.VOICE_PITCH.get().floatValue();
        if (mode == VoiceMode.BLIPS) {
            VoiceBlips.speak(entityId, text, pitch, GFClientConfig.VOICE_VOLUME.get().floatValue());
            return;
        }
        boolean neural = wantsNeural(mode);
        boolean speech = mode == VoiceMode.AUTO || mode == VoiceMode.SPEECH, synth = mode == VoiceMode.AUTO || mode == VoiceMode.SYNTH;
        String clean = speech || synth ? VoiceText.clean(text) : "";
        String neuralText = neural ? VoiceText.cleanForNeural(text) : "";
        if (clean.isEmpty() && neuralText.isEmpty()) return;
        VoiceRenderer.Request request = new VoiceRenderer.Request(clean, neuralText, emotion, neural, speech, synth, pitch,
                GFClientConfig.VOICE_RATE.get().floatValue(), GFClientConfig.VOICE_NAME.get(), GFClientConfig.NEURAL_VOICE.get());
        Line line = new Line(entityId, ticks, neural && NeuralVoice.ready() ? MAX_NEURAL_RENDER_TICKS : MAX_RENDER_TICKS);
        try {
            WORKER.execute(() -> render(request, line));
        } catch (RejectedExecutionException e) {
            return;
        }
        Line old = PENDING.put(entityId, line);
        if (old != null) old.cancel();
        // the line she is saying ends with what is already rendered, so the worker moves on to this one at once
        // instead of rendering a rest that this line would cut off anyway
        VoiceSoundInstance playing = PLAYING.get(entityId);
        if (playing != null) playing.finishEarly();
    }

    public static void tick() {
        if (shutDown) return; // the client keeps ticking while the integrated server stops: must not load the model again
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        VoiceMode mode = mode();
        tickNeural(mode);
        showNotices(mc);
        if (mc.isPaused()) return;
        ticks++;
        VoiceBlips.tick();
        if (mode == VoiceMode.OFF || mode == VoiceMode.BLIPS) {
            stopVoices();
            if (mode == VoiceMode.OFF) VoiceBlips.clear();
            return;
        }
        if (!warmedUp && (mode == VoiceMode.AUTO || mode == VoiceMode.SPEECH)) {
            warmedUp = true;
            String voice = GFClientConfig.VOICE_NAME.get();
            WORKER.execute(() -> {
                try {
                    VoiceRenderer.prepare(true, voice);
                } catch (Throwable t) {
                    logFailure(t);
                }
            });
        }
        SoundManager sounds = mc.getSoundManager();
        PLAYING.values().removeIf(s -> {
            if (sounds.isActive(s)) return false;
            // ended or was stopped by the sound engine: stop rendering whatever is left of it, unless lines are being
            // saved for debugging (then one nobody can hear, as without an audio device, still renders in full)
            if (!VoiceRenderer.dumping()) s.halt();
            return true;
        });
        Iterator<Line> it = PENDING.values().iterator();
        while (it.hasNext()) {
            Line line = it.next();
            if (!line.audio.isDone()) {
                if (ticks - line.created > line.maxRenderTicks) {
                    line.cancel();
                    it.remove();
                }
                continue;
            }
            VoiceClip clip = line.audio.getNow(null);
            Entity speaker = mc.level.getEntity(line.entityId);
            if (clip == null || clip.length() == 0 || speaker == null || speaker.isRemoved()
                    || speaker.distanceToSqr(mc.player) > VoiceSoundInstance.MAX_DISTANCE * VoiceSoundInstance.MAX_DISTANCE) {
                line.cancel();
                it.remove();
                continue;
            }
            VoiceSoundInstance current = PLAYING.get(line.entityId);
            if (current != null) {
                if (line.readySince < 0) line.readySince = ticks;
                if (ticks - line.readySince < MAX_WAIT_TICKS) continue;
                current.halt();
                sounds.stop(current);
            }
            VoiceSoundInstance sound = new VoiceSoundInstance(speaker, clip);
            sounds.play(sound);
            PLAYING.put(line.entityId, sound);
            it.remove();
        }
    }

    /** Downloads/loads her natural voice while a voice mode wants it, and frees it a while after none does. */
    private static void tickNeural(VoiceMode mode) {
        if (!wantsNeural(mode)) {
            // every tick once due (a cheap check), so a voice still downloading or loading is let go when it finishes
            if (neuralUnusedTicks < NEURAL_UNLOAD_TICKS) neuralUnusedTicks++;
            else NeuralVoice.unload(WORKER);
            return;
        }
        neuralUnusedTicks = 0;
        if (neuralDir == null) neuralDir = neuralDir();
        NeuralVoice.Settings settings = new NeuralVoice.Settings(neuralDir, GFClientConfig.NEURAL_VOICE.get(), GFClientConfig.NEURAL_THREADS.get());
        boolean explicit = mode == VoiceMode.NEURAL;
        NeuralVoice.ensureStarted(settings, explicit || GFClientConfig.NEURAL_AUTO_DOWNLOAD.get(), explicit, WORKER);
    }

    private static void showNotices(Minecraft mc) {
        for (NeuralVoice.Notice n; (n = NeuralVoice.takeNotice()) != null; ) {
            mc.gui.getChat().addMessage(Component.translatable(n.key(), n.arg()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static boolean wantsNeural(VoiceMode mode) {
        return mode == VoiceMode.AUTO || mode == VoiceMode.NEURAL;
    }

    /** {@code <game dir>/aigirlfriend/voice}, or {@code -Daigirlfriend.voice.dir} (development and tests). */
    private static Path neuralDir() {
        String override = System.getProperty("aigirlfriend.voice.dir");
        if (override != null && !override.isBlank()) return Path.of(override);
        return FMLPaths.GAMEDIR.get().resolve("aigirlfriend").resolve("voice");
    }

    /** Stops everything (logout, world change). */
    public static void clear() {
        VoiceBlips.clear();
        stopVoices();
    }

    /**
     * Releases her natural voice before the game exits: stops her lines so the worker is free, then gives it a moment
     * to close the model: ONNX Runtime still working while the process exits can crash it on the way out. A load or
     * a run in progress cannot be interrupted, so this waits for it, but at most five seconds. She stays silent from
     * then on.
     */
    public static void shutdown() {
        shutDown = true;
        PENDING.values().forEach(Line::cancel);
        PENDING.clear();
        PLAYING.values().forEach(VoiceSoundInstance::halt);
        PLAYING.clear();
        NeuralVoice.unload(WORKER);
        try {
            WORKER.submit(() -> {}).get(5, TimeUnit.SECONDS); // the worker runs jobs in order: the close went first
        } catch (Exception e) {
            LOGGER.debug("Companion voice: her natural voice was still busy at exit", e);
        }
    }

    private static void stopVoices() {
        if (PENDING.isEmpty() && PLAYING.isEmpty()) return;
        PENDING.values().forEach(Line::cancel);
        PENDING.clear();
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        for (VoiceSoundInstance s : PLAYING.values()) {
            s.halt();
            if (sounds != null) sounds.stop(s);
        }
        PLAYING.clear();
    }

    private static VoiceMode mode() {
        return GFClientConfig.VOICE_ENABLED.get() ? GFClientConfig.VOICE_MODE.get() : VoiceMode.OFF;
    }

    private static void render(VoiceRenderer.Request request, Line line) {
        try {
            if (line.cancelled.get()) line.audio.complete(null);
            else VoiceRenderer.render(request, line.audio, line.cancelled::get);
        } catch (Throwable t) {
            logFailure(t);
        } finally {
            line.audio.complete(null); // no-op when audio already started
        }
    }

    private static void logFailure(Throwable t) {
        if (loggedFailure) return;
        loggedFailure = true;
        LOGGER.warn("Companion voice: rendering failed, she will stay quiet for that line", t);
    }
}
