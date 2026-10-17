package com.aigirlfriend.ai.local;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal reader for GGUF (v2/v3) model files: metadata key/values and tensor descriptors. Tensor data is
 * memory-mapped read-only, so weights live in the OS page cache instead of the Java heap.
 */
public final class GGUF implements AutoCloseable {
    private static final int MAGIC = 0x46554747; // "GGUF" little-endian

    /** ne[0] is the fastest-varying dimension (for a weight matrix used as y = W x: ne = [in, out]). */
    public record TensorInfo(String name, long[] ne, GGMLType type, long offset) {
        public long elements() {
            long n = 1;
            for (long d : ne) n *= d;
            return n;
        }

        public long byteSize() {
            return type.byteSize(elements());
        }
    }

    public final Map<String, Object> metadata = new LinkedHashMap<>();
    public final Map<String, TensorInfo> tensors = new LinkedHashMap<>();
    private final FileChannel channel;
    private final long dataStart;

    private GGUF(Path path) throws IOException {
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        long headerEnd;
        try (InputStream raw = Files.newInputStream(path)) {
            LittleEndianReader in = new LittleEndianReader(new BufferedInputStream(raw, 1 << 16));
            if (in.int32() != MAGIC) throw new IOException("Not a GGUF file: " + path);
            int version = in.int32();
            if (version < 2 || version > 3) throw new IOException("Unsupported GGUF version " + version);
            long tensorCount = in.int64();
            long kvCount = in.int64();
            for (long i = 0; i < kvCount; i++) {
                String key = in.string();
                metadata.put(key, in.value(in.int32()));
            }
            for (long i = 0; i < tensorCount; i++) {
                String name = in.string();
                int dims = in.int32();
                long[] ne = new long[dims];
                for (int d = 0; d < dims; d++) ne[d] = in.int64();
                GGMLType type = GGMLType.byId(in.int32());
                long offset = in.int64();
                tensors.put(name, new TensorInfo(name, ne, type, offset));
            }
            headerEnd = in.position;
        }
        long alignment = metadata.get("general.alignment") instanceof Number n ? n.longValue() : 32;
        this.dataStart = (headerEnd + alignment - 1) / alignment * alignment;
    }

    public static GGUF open(Path path) throws IOException {
        return new GGUF(path);
    }

    /** Maps a tensor's raw bytes (read-only, little-endian). */
    public ByteBuffer map(TensorInfo t) throws IOException {
        return channel.map(FileChannel.MapMode.READ_ONLY, dataStart + t.offset(), t.byteSize()).order(ByteOrder.LITTLE_ENDIAN);
    }

    public TensorInfo tensor(String name) {
        TensorInfo t = tensors.get(name);
        if (t == null) throw new IllegalArgumentException("Missing tensor " + name);
        return t;
    }

    public int getInt(String key, int fallback) {
        return metadata.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    public float getFloat(String key, float fallback) {
        return metadata.get(key) instanceof Number n ? n.floatValue() : fallback;
    }

    public String getString(String key, String fallback) {
        return metadata.get(key) instanceof String s ? s : fallback;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static final class LittleEndianReader {
        private final DataInputStream in;
        private final byte[] buf = new byte[8];
        long position;

        LittleEndianReader(InputStream in) {
            this.in = new DataInputStream(in);
        }

        private void fill(int n) throws IOException {
            in.readFully(buf, 0, n);
            position += n;
        }

        int int8() throws IOException {
            fill(1);
            return buf[0];
        }

        int int16() throws IOException {
            fill(2);
            return (short) ((buf[0] & 0xFF) | (buf[1] & 0xFF) << 8);
        }

        int int32() throws IOException {
            fill(4);
            return (buf[0] & 0xFF) | (buf[1] & 0xFF) << 8 | (buf[2] & 0xFF) << 16 | (buf[3] & 0xFF) << 24;
        }

        long int64() throws IOException {
            fill(8);
            long v = 0;
            for (int i = 7; i >= 0; i--) v = v << 8 | (buf[i] & 0xFF);
            return v;
        }

        String string() throws IOException {
            long len = int64();
            if (len < 0 || len > Integer.MAX_VALUE) throw new IOException("Bad string length " + len);
            byte[] b = new byte[(int) len];
            try {
                in.readFully(b);
            } catch (EOFException e) {
                throw new IOException("Truncated GGUF header", e);
            }
            position += len;
            return new String(b, StandardCharsets.UTF_8);
        }

        Object value(int type) throws IOException {
            return switch (type) {
                case 0 -> int8() & 0xFF;
                case 1 -> int8();
                case 2 -> int16() & 0xFFFF;
                case 3 -> int16();
                case 4 -> int32() & 0xFFFFFFFFL;
                case 5 -> int32();
                case 6 -> Float.intBitsToFloat(int32());
                case 7 -> int8() != 0;
                case 8 -> string();
                case 9 -> array();
                case 10, 11 -> int64();
                case 12 -> Double.longBitsToDouble(int64());
                default -> throw new IOException("Unknown GGUF value type " + type);
            };
        }

        Object array() throws IOException {
            int type = int32();
            long n = int64();
            if (n < 0 || n > Integer.MAX_VALUE) throw new IOException("Bad array length " + n);
            int len = (int) n;
            switch (type) {
                case 8 -> {
                    String[] a = new String[len];
                    for (int i = 0; i < len; i++) a[i] = string();
                    return a;
                }
                case 5 -> {
                    int[] a = new int[len];
                    for (int i = 0; i < len; i++) a[i] = int32();
                    return a;
                }
                case 6 -> {
                    float[] a = new float[len];
                    for (int i = 0; i < len; i++) a[i] = Float.intBitsToFloat(int32());
                    return a;
                }
                default -> {
                    Object[] a = new Object[len];
                    for (int i = 0; i < len; i++) a[i] = value(type);
                    return a;
                }
            }
        }
    }
}
