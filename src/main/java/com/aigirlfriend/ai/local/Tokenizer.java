package com.aigirlfriend.ai.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Byte-level BPE tokenizer (GPT-2 style, as used by Qwen2) built from the vocabulary and merges stored in the
 * GGUF file: NFC normalization, the Qwen2 pre-tokenizer split, bytes mapped to printable characters, then merges
 * by rank. Special tokens are only recognised when explicitly allowed, so player text can never inject them.
 */
public final class Tokenizer {
    private static final Pattern QWEN2_SPLIT = Pattern.compile(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final int TYPE_CONTROL = 3;
    private static final int TYPE_USER_DEFINED = 4;

    private static final char[] BYTE_TO_CHAR = new char[256];
    private static final Map<Character, Integer> CHAR_TO_BYTE = new HashMap<>();

    static {
        List<Integer> bs = new ArrayList<>();
        for (int b = '!'; b <= '~'; b++) bs.add(b);
        for (int b = 0xA1; b <= 0xAC; b++) bs.add(b);
        for (int b = 0xAE; b <= 0xFF; b++) bs.add(b);
        int n = 0;
        for (int b = 0; b < 256; b++) {
            char c = bs.contains(b) ? (char) b : (char) (256 + n++);
            BYTE_TO_CHAR[b] = c;
            CHAR_TO_BYTE.put(c, b);
        }
    }

    private final String[] tokens;
    private final int[] types;
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<String, Integer> mergeRanks = new HashMap<>();
    private final List<String> specials = new ArrayList<>();
    private final Map<String, int[]> cache = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
            return size() > 20000;
        }
    };
    public final int eosId;

    public Tokenizer(GGUF gguf) {
        String model = gguf.getString("tokenizer.ggml.model", "gpt2");
        if (!model.equals("gpt2")) throw new IllegalArgumentException("Unsupported tokenizer model " + model);
        this.tokens = (String[]) gguf.metadata.get("tokenizer.ggml.tokens");
        Object t = gguf.metadata.get("tokenizer.ggml.token_type");
        this.types = t instanceof int[] a ? a : new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            ids.put(tokens[i], i);
            if (types[i] == TYPE_CONTROL || types[i] == TYPE_USER_DEFINED) specials.add(tokens[i]);
        }
        specials.sort((a, b) -> b.length() - a.length());
        String[] merges = (String[]) gguf.metadata.get("tokenizer.ggml.merges");
        for (int i = 0; i < merges.length; i++) mergeRanks.put(merges[i], i);
        this.eosId = gguf.getInt("tokenizer.ggml.eos_token_id", id("<|im_end|>"));
    }

    public int vocabSize() {
        return tokens.length;
    }

    /** Id of an exact token string, or -1. */
    public int id(String token) {
        Integer i = ids.get(token);
        return i == null ? -1 : i;
    }

    public boolean isControl(int id) {
        return id >= 0 && id < types.length && types[id] == TYPE_CONTROL;
    }

    /** Encodes text; special token strings are only matched when {@code allowSpecial} is set. */
    public List<Integer> encode(String text, boolean allowSpecial) {
        List<Integer> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        if (!allowSpecial) {
            encodeOrdinary(text, out);
            return out;
        }
        int pos = 0;
        while (pos < text.length()) {
            int best = -1;
            String bestTok = null;
            for (String s : specials) {
                int i = text.indexOf(s, pos);
                if (i >= 0 && (best < 0 || i < best)) {
                    best = i;
                    bestTok = s;
                }
            }
            if (best < 0) {
                encodeOrdinary(text.substring(pos), out);
                break;
            }
            if (best > pos) encodeOrdinary(text.substring(pos, best), out);
            out.add(ids.get(bestTok));
            pos = best + bestTok.length();
        }
        return out;
    }

    private void encodeOrdinary(String text, List<Integer> out) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        Matcher m = QWEN2_SPLIT.matcher(normalized);
        while (m.find()) {
            String piece = m.group();
            if (piece.isEmpty()) continue;
            int[] ids;
            synchronized (cache) {
                ids = cache.get(piece);
            }
            if (ids == null) {
                ids = bpe(piece);
                synchronized (cache) {
                    cache.put(piece, ids);
                }
            }
            for (int id : ids) out.add(id);
        }
    }

    private int[] bpe(String piece) {
        byte[] bytes = piece.getBytes(StandardCharsets.UTF_8);
        List<String> parts = new ArrayList<>(bytes.length);
        for (byte b : bytes) parts.add(String.valueOf(BYTE_TO_CHAR[b & 0xFF]));
        while (parts.size() > 1) {
            int bestRank = Integer.MAX_VALUE, bestIdx = -1;
            for (int i = 0; i < parts.size() - 1; i++) {
                Integer r = mergeRanks.get(parts.get(i) + " " + parts.get(i + 1));
                if (r != null && r < bestRank) {
                    bestRank = r;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) break;
            String a = parts.get(bestIdx), b = parts.get(bestIdx + 1);
            List<String> merged = new ArrayList<>(parts.size());
            for (int i = 0; i < parts.size(); i++) {
                if (i < parts.size() - 1 && parts.get(i).equals(a) && parts.get(i + 1).equals(b)) {
                    merged.add(a + b);
                    i++;
                } else {
                    merged.add(parts.get(i));
                }
            }
            parts = merged;
        }
        int[] result = new int[parts.size()];
        for (int i = 0; i < result.length; i++) {
            Integer id = ids.get(parts.get(i));
            if (id == null) throw new IllegalStateException("No token for BPE piece '" + parts.get(i) + "'");
            result[i] = id;
        }
        return result;
    }

    /** Raw bytes of a token (control tokens decode to nothing). */
    public byte[] tokenBytes(int id) {
        if (id < 0 || id >= tokens.length || isControl(id)) return new byte[0];
        String s = tokens[id];
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            Integer b = CHAR_TO_BYTE.get(s.charAt(i));
            if (b != null) out.write(b);
            else out.writeBytes(String.valueOf(s.charAt(i)).getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    public String decode(List<Integer> ids) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int id : ids) out.writeBytes(tokenBytes(id));
        return out.toString(StandardCharsets.UTF_8);
    }
}
