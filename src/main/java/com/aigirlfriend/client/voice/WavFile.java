package com.aigirlfriend.client.voice;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Minimal RIFF/WAVE reader (PCM 8/16/24/32-bit, float, extensible; any channel count, downmixed) and 16-bit writer. */
final class WavFile {
    private WavFile() {}

    static Pcm read(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IOException("not a WAVE file");
        int format = -1, channels = 0, rate = 0, bits = 0;
        int pos = 12;
        while (pos + 8 <= data.length) {
            int id = b.getInt(pos);
            long size = b.getInt(pos + 4) & 0xFFFFFFFFL;
            int body = pos + 8;
            if (id == 0x20746D66) { // "fmt "
                if (size < 16 || body + 16 > data.length) throw new IOException("bad fmt chunk");
                format = b.getShort(body) & 0xFFFF;
                channels = b.getShort(body + 2) & 0xFFFF;
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14) & 0xFFFF;
                if (format == 0xFFFE && size >= 26 && body + 26 <= data.length) format = b.getShort(body + 24) & 0xFFFF;
            } else if (id == 0x61746164) { // "data": streaming writers may leave the size unset
                long avail = data.length - body;
                int len = (int) (size == 0 || size > avail ? avail : size);
                return decode(b, body, len, format, channels, rate, bits);
            }
            pos = (int) Math.min(Integer.MAX_VALUE, body + size + (size & 1));
        }
        throw new IOException("no data chunk");
    }

    private static Pcm decode(ByteBuffer b, int off, int len, int format, int channels, int rate, int bits) throws IOException {
        if (channels < 1 || channels > 8 || rate < 4000 || rate > 192000) throw new IOException("unsupported layout " + channels + "ch " + rate + "Hz");
        boolean isFloat = format == 3;
        if (!(format == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32)) && !(isFloat && (bits == 32 || bits == 64))) {
            throw new IOException("unsupported encoding " + format + "/" + bits);
        }
        int bytes = bits / 8;
        int frames = len / (bytes * channels);
        float[] out = new float[frames];
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                int p = off + (f * channels + c) * bytes;
                double v;
                if (isFloat) {
                    v = bits == 32 ? b.getFloat(p) : b.getDouble(p);
                } else {
                    v = switch (bits) {
                        case 8 -> ((b.get(p) & 0xFF) - 128) / 128.0;
                        case 16 -> b.getShort(p) / 32768.0;
                        case 24 -> (((b.get(p) & 0xFF) | ((b.get(p + 1) & 0xFF) << 8) | (b.get(p + 2) << 16))) / 8388608.0;
                        default -> b.getInt(p) / 2147483648.0;
                    };
                }
                sum += Double.isFinite(v) ? v : 0;
            }
            out[f] = (float) Math.max(-1, Math.min(1, sum / channels));
        }
        return new Pcm(out, rate);
    }

    static byte[] write(short[] pcm, int rate) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46464952).putInt(36 + pcm.length * 2).putInt(0x45564157);
        b.putInt(0x20746D66).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.putInt(0x61746164).putInt(pcm.length * 2);
        for (short s : pcm) b.putShort(s);
        return b.array();
    }
}
