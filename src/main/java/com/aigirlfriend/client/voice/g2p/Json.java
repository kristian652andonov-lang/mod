package com.aigirlfriend.client.voice.g2p;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Just enough JSON for the lexicon and vocab files: objects become {@link LinkedHashMap}, arrays
 * {@link ArrayList}, numbers {@link Double}. Kept local so the G2P code depends on nothing but java.*.
 */
final class Json {
    private final String s;
    private int pos;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        Object value = p.value();
        p.skipSpace();
        if (p.pos != p.s.length()) throw p.error("trailing characters");
        return value;
    }

    /** Streams the members of a top-level object, so a 3 MB lexicon never exists as one big map. */
    static void forEachMember(String text, BiConsumer<String, Object> member) {
        Json p = new Json(text);
        p.skipSpace();
        p.expect('{');
        p.skipSpace();
        if (p.peek() == '}') return;
        while (true) {
            p.skipSpace();
            String key = p.string();
            p.skipSpace();
            p.expect(':');
            member.accept(key, p.value());
            p.skipSpace();
            if (p.peek() == ',') {
                p.pos++;
            } else {
                p.expect('}');
                return;
            }
        }
    }

    private Object value() {
        skipSpace();
        char c = peek();
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipSpace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
            if (peek() == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipSpace();
            if (peek() == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = null;
        int start = pos;
        while (true) {
            if (pos >= s.length()) throw error("unterminated string");
            char c = s.charAt(pos);
            if (c == '"') {
                String out = b == null ? s.substring(start, pos) : b.append(s, start, pos).toString();
                pos++;
                return out;
            }
            if (c != '\\') {
                pos++;
                continue;
            }
            if (b == null) b = new StringBuilder();
            b.append(s, start, pos);
            char e = s.charAt(pos + 1);
            pos += 2;
            switch (e) {
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    b.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> b.append(e);
            }
            start = pos;
        }
    }

    private Double number() {
        int start = pos;
        while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("unexpected character");
        return Double.valueOf(s.substring(start, pos));
    }

    private void literal(String word) {
        if (!s.startsWith(word, pos)) throw error("expected " + word);
        pos += word.length();
    }

    private void skipSpace() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= s.length()) throw error("unexpected end");
        return s.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) throw error("expected '" + c + "'");
        pos++;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("JSON: " + what + " at offset " + pos);
    }
}
