package com.aigirlfriend.client.voice;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** The "blips" voice mode: soft, pitched blips while she speaks (one per word, like many games do). */
final class VoiceBlips {
    private static final float BASE_PITCH = 1.35f;

    private static final class Pending {
        final int entityId;
        final float pitch, volume;
        int remaining;
        int cooldown;

        Pending(int entityId, int words, float pitch, float volume) {
            this.entityId = entityId;
            this.remaining = words;
            this.pitch = pitch;
            this.volume = volume;
        }
    }

    private static final List<Pending> QUEUE = new ArrayList<>();

    private VoiceBlips() {}

    static void speak(int entityId, String text, float pitchScale, float volume) {
        int words = Math.min(14, Math.max(1, text.split("\\s+").length));
        QUEUE.removeIf(p -> p.entityId == entityId);
        QUEUE.add(new Pending(entityId, words, BASE_PITCH * pitchScale, volume));
    }

    static void clear() {
        QUEUE.clear();
    }

    static void tick() {
        if (QUEUE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            QUEUE.clear();
            return;
        }
        Iterator<Pending> it = QUEUE.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (--p.cooldown > 0) continue;
            Entity e = mc.level.getEntity(p.entityId);
            if (e == null || p.remaining-- <= 0) {
                it.remove();
                continue;
            }
            float pitch = p.pitch + (mc.level.random.nextFloat() - 0.5f) * 0.25f;
            mc.level.playLocalSound(e.getX(), e.getEyeY(), e.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL,
                    p.volume * 0.35f, pitch, false);
            p.cooldown = 3 + mc.level.random.nextInt(2);
        }
    }
}
