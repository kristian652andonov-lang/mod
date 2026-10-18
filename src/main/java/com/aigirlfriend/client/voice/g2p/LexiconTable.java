package com.aigirlfriend.client.voice.g2p;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A read-only String to String map packed into one byte pool. A lexicon holds about 90k entries per file;
 * as HashMap nodes and Strings that costs tens of MB, packed it is a few MB. Lexicon keys are ASCII and
 * phonemes use about 50 symbols, so every character fits one byte through a shared code book.
 */
final class LexiconTable {
    private final char[] alphabet;
    private final byte[] pool;
    /** Entry i: key in pool[keyStart[i], valueStart[i]), value in pool[valueStart[i], keyStart[i + 1]). */
    private final int[] keyStart;
    private final int[] valueStart;
    /** Open addressing table of entry index + 1; 0 marks an empty slot. */
    private final int[] slots;

    private LexiconTable(char[] alphabet, byte[] pool, int[] keyStart, int[] valueStart, int[] slots) {
        this.alphabet = alphabet;
        this.pool = pool;
        this.keyStart = keyStart;
        this.valueStart = valueStart;
        this.slots = slots;
    }

    long bytesUsed() {
        return pool.length + 4L * (keyStart.length + valueStart.length + slots.length) + 2L * alphabet.length;
    }

    String get(String key) {
        int i = find(key);
        if (i < 0) return null;
        int start = valueStart[i], end = keyStart[i + 1];
        char[] out = new char[end - start];
        for (int k = start; k < end; k++) out[k - start] = alphabet[pool[k] & 0xff];
        return new String(out);
    }

    boolean containsKey(String key) {
        return find(key) >= 0;
    }

    private int find(String key) {
        int mask = slots.length - 1;
        for (int slot = mix(key.hashCode()) & mask; ; slot = (slot + 1) & mask) {
            int entry = slots[slot] - 1;
            if (entry < 0) return -1;
            if (keyEquals(entry, key)) return entry;
        }
    }

    private boolean keyEquals(int entry, String key) {
        int start = keyStart[entry], end = valueStart[entry];
        if (end - start != key.length()) return false;
        for (int k = start; k < end; k++) {
            if (alphabet[pool[k] & 0xff] != key.charAt(k - start)) return false;
        }
        return true;
    }

    private static int mix(int h) {
        h *= 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    /** Collects entries; the code book grows as new characters appear (ASCII maps to itself). */
    static final class Builder {
        private final char[] alphabet = new char[256];
        private final Map<Character, Integer> codes = new HashMap<>();
        private byte[] pool = new byte[1 << 20];
        private int poolSize;
        private int[] keyStart = new int[1 << 14];
        private int[] valueStart = new int[1 << 14];
        private int count;

        Builder() {
            for (char c = 0; c < 128; c++) alphabet[c] = c;
        }

        void put(String key, String value) {
            if (count + 1 >= keyStart.length) {
                keyStart = Arrays.copyOf(keyStart, keyStart.length * 2);
                valueStart = Arrays.copyOf(valueStart, valueStart.length * 2);
            }
            keyStart[count] = poolSize;
            append(key);
            valueStart[count] = poolSize;
            append(value);
            count++;
        }

        private void append(String s) {
            if (poolSize + s.length() > pool.length) pool = Arrays.copyOf(pool, Math.max(pool.length * 2, poolSize + s.length()));
            for (int i = 0; i < s.length(); i++) pool[poolSize++] = (byte) code(s.charAt(i));
        }

        private int code(char c) {
            if (c < 128) return c;
            Integer code = codes.get(c);
            if (code == null) {
                code = 128 + codes.size();
                if (code > 255) throw new IllegalArgumentException("lexicon uses more than 128 non-ASCII symbols");
                codes.put(c, code);
                alphabet[code] = c;
            }
            return code;
        }

        LexiconTable build() {
            int[] keys = Arrays.copyOf(keyStart, count + 1);
            keys[count] = poolSize;
            int[] values = Arrays.copyOf(valueStart, count);
            int size = Integer.highestOneBit(Math.max(4, count * 2 - 1)) << 1;
            int[] slots = new int[size];
            LexiconTable table = new LexiconTable(alphabet.clone(), Arrays.copyOf(pool, poolSize), keys, values, slots);
            for (int i = 0; i < count; i++) {
                int slot = mix(table.keyAt(i).hashCode()) & (size - 1);
                while (slots[slot] != 0) slot = (slot + 1) & (size - 1);
                slots[slot] = i + 1;
            }
            return table;
        }
    }

    private String keyAt(int entry) {
        int start = keyStart[entry], end = valueStart[entry];
        char[] out = new char[end - start];
        for (int k = start; k < end; k++) out[k - start] = alphabet[pool[k] & 0xff];
        return new String(out);
    }
}
