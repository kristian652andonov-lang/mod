package com.aigirlfriend.client.voice;

import com.aigirlfriend.AIGirlfriend;
import com.aigirlfriend.config.GFClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.CompletableFuture;

/**
 * One spoken line, played through Minecraft's sound system (Voice/Speech volume, pause, distance attenuation) from
 * her head, following her as she moves. The "aigirlfriend:voice" sound event is a streamed placeholder whose
 * audio is replaced by the rendered line.
 */
final class VoiceSoundInstance extends AbstractTickableSoundInstance {
    static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(AIGirlfriend.MOD_ID, "voice");
    private static final SoundEvent EVENT = SoundEvent.createVariableRangeEvent(LOCATION);
    static final double MAX_DISTANCE = 48.0;

    private final Entity speaker;
    private final VoiceClip clip;

    VoiceSoundInstance(Entity speaker, VoiceClip clip) {
        super(EVENT, SoundSource.VOICE, SoundInstance.createUnseededRandom());
        this.speaker = speaker;
        this.clip = clip;
        this.volume = GFClientConfig.VOICE_VOLUME.get().floatValue();
        this.pitch = 1.0f;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        follow();
    }

    int speakerId() {
        return speaker.getId();
    }

    /** Stops playing, and stops rendering the rest of the line if that is still going on. */
    void halt() {
        clip.cancel();
        stop();
    }

    /** Lets the line end with what is already rendered (a newer line is on its way). */
    void finishEarly() {
        clip.cancel();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (speaker.isRemoved() || mc.player == null || speaker.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) {
            halt();
            return;
        }
        volume = GFClientConfig.VOICE_VOLUME.get().floatValue();
        follow();
    }

    private void follow() {
        x = speaker.getX();
        y = speaker.getEyeY();
        z = speaker.getZ();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(new PcmAudioStream(clip));
    }
}
