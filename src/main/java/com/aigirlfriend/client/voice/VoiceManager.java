package com.aigirlfriend.client.voice;

import com.aigirlfriend.config.GFClientConfig;
import com.aigirlfriend.config.GFClientConfig.VoiceMode;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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
 * Her spoken voice on the client. Lines are rendered on a background worker (system speech shaped into her voice, or
 * her synthesized voice) and played from her position through Minecraft's sound system. One line plays at a time per
 * companion; a newer line replaces one that is still waiting. Game-thread only, apart from the worker itself.
 */
public final class VoiceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How long a ready line politely waits for the previous one to finish before cutting in. */
    private static final int MAX_WAIT_TICKS = 50;
    /** Lines that take longer than this to render are dropped (her bubble is long gone by then). */
    private static final int MAX_RENDER_TICKS = 300;

    private static final class Line {
        final int entityId;
        final long created;
        final AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<short[]> audio;
        long readySince = -1;

        Line(int entityId, long created) {
            this.entityId = entityId;
            this.created = created;
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
    private static boolean warmedUp;
    private static volatile boolean loggedFailure;
    private static long ticks;

    static {
        WORKER.allowCoreThreadTimeOut(true);
    }

    private VoiceManager() {}

    /** A finished (non-typing) line she says. */
    public static void speak(int entityId, String text, int emotion) {
        VoiceMode mode = mode();
        if (mode == VoiceMode.OFF || text == null) return;
        float pitch = GFClientConfig.VOICE_PITCH.get().floatValue();
        if (mode == VoiceMode.BLIPS) {
            VoiceBlips.speak(entityId, text, pitch, GFClientConfig.VOICE_VOLUME.get().floatValue());
            return;
        }
        String clean = VoiceText.clean(text);
        if (clean.isEmpty()) return;
        VoiceRenderer.Request request = new VoiceRenderer.Request(clean, emotion, mode != VoiceMode.SYNTH, mode != VoiceMode.SPEECH,
                pitch, GFClientConfig.VOICE_RATE.get().floatValue(), GFClientConfig.VOICE_NAME.get());
        Line line = new Line(entityId, ticks);
        try {
            line.audio = CompletableFuture.supplyAsync(() -> line.cancelled.get() ? null : render(request), WORKER);
        } catch (RejectedExecutionException e) {
            return;
        }
        Line old = PENDING.put(entityId, line);
        if (old != null) old.cancelled.set(true);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        VoiceBlips.tick();
        VoiceMode mode = mode();
        if (mode == VoiceMode.OFF || mode == VoiceMode.BLIPS) {
            stopVoices();
            if (mode == VoiceMode.OFF) VoiceBlips.clear();
            return;
        }
        if (!warmedUp && mode != VoiceMode.SYNTH) {
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
        PLAYING.values().removeIf(s -> !sounds.isActive(s));
        Iterator<Line> it = PENDING.values().iterator();
        while (it.hasNext()) {
            Line line = it.next();
            if (!line.audio.isDone()) {
                if (ticks - line.created > MAX_RENDER_TICKS) {
                    line.cancelled.set(true);
                    it.remove();
                }
                continue;
            }
            short[] pcm = line.audio.isCompletedExceptionally() ? null : line.audio.join();
            Entity speaker = mc.level.getEntity(line.entityId);
            if (pcm == null || pcm.length == 0 || speaker == null || speaker.isRemoved()
                    || speaker.distanceToSqr(mc.player) > VoiceSoundInstance.MAX_DISTANCE * VoiceSoundInstance.MAX_DISTANCE) {
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
            VoiceSoundInstance sound = new VoiceSoundInstance(speaker, pcm, VoiceDsp.RATE);
            sounds.play(sound);
            PLAYING.put(line.entityId, sound);
            it.remove();
        }
    }

    /** Stops everything (logout, world change). */
    public static void clear() {
        VoiceBlips.clear();
        stopVoices();
    }

    private static void stopVoices() {
        if (PENDING.isEmpty() && PLAYING.isEmpty()) return;
        PENDING.values().forEach(l -> l.cancelled.set(true));
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

    @Nullable
    private static short[] render(VoiceRenderer.Request request) {
        try {
            return VoiceRenderer.render(request);
        } catch (Throwable t) {
            logFailure(t);
            return null;
        }
    }

    private static void logFailure(Throwable t) {
        if (loggedFailure) return;
        loggedFailure = true;
        LOGGER.warn("Companion voice: rendering failed, she will stay quiet for that line", t);
    }
}
