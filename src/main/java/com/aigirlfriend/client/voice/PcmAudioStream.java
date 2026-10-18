package com.aigirlfriend.client.voice;

import net.minecraft.client.sounds.AudioStream;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * Streams a mono 16-bit line to OpenAL (read on the sound engine thread). The engine keeps four buffers queued and
 * reads a new one each time one has played, so whatever is handed out plays in order. While the rest of a line is
 * still being rendered, speech goes out in short pieces (the engine then reads only about a second ahead of the
 * renderer, and a chunk that lands while the previous one plays follows it seamlessly), and when there is nothing
 * new yet the stream hands out silence so the source keeps playing instead of running dry and ending. That silence
 * only tops the queue up to about half a second: enough to ride out a hitch, short enough not to delay the next chunk.
 */
final class PcmAudioStream implements AudioStream {
    /** Longest piece of speech handed out at once while the line is still growing. */
    private static final int PIECE_MILLIS = 250;
    /** While waiting for the renderer, silence keeps about this much audio queued ahead of what is playing. */
    private static final int KEEP_AHEAD_MILLIS = 500;
    private static final int MIN_SILENCE_MILLIS = 30, MAX_SILENCE_MILLIS = 250;
    /** A renderer that has not delivered for this long is given up on. */
    private static final int GIVE_UP_MILLIS = 20_000;

    private final VoiceClip clip;
    private final AudioFormat format;
    private short[] scratch = new short[0];
    private int position;
    /** Samples handed out so far (speech and silence) and when the first was: together they place the playhead. */
    private long handedOut, firstRead;
    private int waitedMillis;
    private boolean closed;

    PcmAudioStream(VoiceClip clip) {
        this.clip = clip;
        this.format = new AudioFormat(clip.sampleRate, 16, 1, true, false);
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    /** Direct, native-order buffers like the vanilla Ogg stream; null once everything has been queued. */
    @Nullable
    @Override
    public ByteBuffer read(int size) {
        if (closed) return null;
        long now = System.nanoTime();
        if (firstRead == 0) firstRead = now;
        int max = Math.max(1, size / 2);
        if (!clip.finished()) max = Math.min(max, clip.sampleRate * PIECE_MILLIS / 1000);
        if (scratch.length < max) scratch = new short[max];
        int count = clip.read(position, scratch, max);
        if (count < 0) return null;
        if (count == 0) return silence(now, Math.max(1, size / 2));
        waitedMillis = 0;
        position += count;
        handedOut += count;
        ByteBuffer buffer = BufferUtils.createByteBuffer(count * 2);
        buffer.asShortBuffer().put(scratch, 0, count);
        return buffer;
    }

    /** Silence while the renderer catches up: little while speech is still queued ahead, more as the queue runs low. */
    @Nullable
    private ByteBuffer silence(long now, int max) {
        if (clip.cancelled() || waitedMillis >= GIVE_UP_MILLIS) {
            clip.cancel(); // nobody will hear the rest, so stop rendering it
            return null;
        }
        // wall time since the first read is never less than what has really played, so this errs towards more silence
        long played = (now - firstRead) * clip.sampleRate / 1_000_000_000L;
        long aheadMillis = Math.max(0, handedOut - played) * 1000 / clip.sampleRate;
        int millis = (int) Math.max(MIN_SILENCE_MILLIS, Math.min(MAX_SILENCE_MILLIS, KEEP_AHEAD_MILLIS - aheadMillis));
        int count = Math.min(max, clip.sampleRate * millis / 1000);
        waitedMillis += millis;
        handedOut += count;
        return BufferUtils.createByteBuffer(count * 2); // direct buffers start zeroed
    }

    @Override
    public void close() {
        closed = true;
    }
}
