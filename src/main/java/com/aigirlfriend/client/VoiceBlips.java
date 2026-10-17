package com.aigirlfriend.client;

import com.aigirlfriend.config.GFClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Optional "voice": soft, pitched blips while she speaks (one per word, like many games do).
 * Disabled by default and fully independent of text dialogue.
 */
public final class VoiceBlips {
    private static final class Pending {
        final int entityId;
        int remaining;
        int cooldown;

        Pending(int entityId, int words) {
            this.entityId = entityId;
            this.remaining = words;
        }
    }

    private static final List<Pending> QUEUE = new ArrayList<>();

    private VoiceBlips() {}

    public static void speak(int entityId, String text) {
        if (!GFClientConfig.VOICE_ENABLED.get()) return;
        int words = Math.min(14, Math.max(1, text.split("\\s+").length));
        QUEUE.removeIf(p -> p.entityId == entityId);
        QUEUE.add(new Pending(entityId, words));
    }

    public static void tick() {
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
            float base = GFClientConfig.VOICE_PITCH.get().floatValue();
            float pitch = base + (mc.level.random.nextFloat() - 0.5f) * 0.25f;
            mc.level.playLocalSound(e.getX(), e.getEyeY(), e.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL,
                    GFClientConfig.VOICE_VOLUME.get().floatValue() * 0.35f, pitch, false);
            p.cooldown = 3 + mc.level.random.nextInt(2);
        }
    }
}
