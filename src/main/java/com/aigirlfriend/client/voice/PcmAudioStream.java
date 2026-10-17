package com.aigirlfriend.client.voice;

import net.minecraft.client.sounds.AudioStream;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/** Streams an in-memory mono 16-bit line to OpenAL (read on the sound engine thread). */
final class PcmAudioStream implements AudioStream {
    private final short[] pcm;
    private final AudioFormat format;
    private int position;

    PcmAudioStream(short[] pcm, int sampleRate) {
        this.pcm = pcm;
        this.format = new AudioFormat(sampleRate, 16, 1, true, false);
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    /** Direct, native-order buffers like the vanilla Ogg stream; null once everything has been queued. */
    @Nullable
    @Override
    public ByteBuffer read(int size) {
        int count = Math.min(Math.max(1, size / 2), pcm.length - position);
        if (count <= 0) return null;
        ByteBuffer buffer = BufferUtils.createByteBuffer(count * 2);
        buffer.asShortBuffer().put(pcm, position, count);
        position += count;
        return buffer;
    }

    @Override
    public void close() {
        position = pcm.length;
    }
}
