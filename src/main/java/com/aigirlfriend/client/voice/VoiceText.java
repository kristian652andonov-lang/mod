package com.aigirlfriend.client.voice;

import java.util.regex.Pattern;

/** Turns a chat line into something worth speaking: no emotes, emoji, emoticons or markup, bounded length. */
final class VoiceText {
    static final int MAX_LENGTH = 220;

    private static final Pattern EMOTE = Pattern.compile("\\*[^*]{0,120}\\*|\\([^()]{0,120}\\)|\\[[^\\[\\]]{0,120}]|<[^<>]{0,60}>");
    private static final Pattern EMOTICON = Pattern.compile("(?<![\\p{L}\\p{N}])(?:[:;=8][-'^]?[)(\\]\\[DPpOo3/\\\\|*]+|<3+|\\^[_.-]?\\^|[xX]D+|[TQ]_[TQ]|[oO]_[oO]|-_-|[uUoO][wW][uUoO])(?![\\p{L}\\p{N}])");
    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern REPEATED_LETTER = Pattern.compile("(\\p{L})\\1{2,}");
    private static final Pattern ELLIPSIS = Pattern.compile("\\.{2,}");
    private static final Pattern REPEATED_MARK = Pattern.compile("([!?,;:])[!?,;:]+");
    private static final Pattern SPACE_BEFORE_MARK = Pattern.compile("\\s+([.,!?;:])");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern LONE_DASH = Pattern.compile("\\s-+(?=\\s|$)");

    private VoiceText() {}

    static String clean(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
                .replace("…", "...").replace('—', ',').replace('–', ',');
        s = URL.matcher(s).replaceAll(" ");
        s = EMOTE.matcher(s).replaceAll(" ");
        s = EMOTICON.matcher(s).replaceAll(" ");
        s = keepSpeakable(s);
        s = LONE_DASH.matcher(s).replaceAll(",");
        s = REPEATED_LETTER.matcher(s).replaceAll("$1$1");
        s = ELLIPSIS.matcher(s).replaceAll("...");
        s = REPEATED_MARK.matcher(s).replaceAll("$1");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        s = SPACE_BEFORE_MARK.matcher(s).replaceAll("$1");
        int start = 0;
        while (start < s.length() && !Character.isLetterOrDigit(s.charAt(start)) && s.charAt(start) != '"') start++;
        s = s.substring(start);
        if (s.length() > MAX_LENGTH) s = truncate(s);
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetterOrDigit(s.charAt(i))) return s;
        }
        return "";
    }

    /** Letters, digits, whitespace and the punctuation that shapes intonation; everything else (emoji, symbols) goes. */
    private static String keepSpeakable(String s) {
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp)) {
                b.appendCodePoint(cp);
            } else if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                b.append(' ');
            } else if (".,!?;:'\"-&%$".indexOf(cp) >= 0) {
                b.appendCodePoint(cp);
            } else if (cp == '/' || cp == '_' || cp == '~' || cp == '|') {
                b.append(' ');
            }
        });
        return b.toString();
    }

    private static String truncate(String s) {
        String head = s.substring(0, MAX_LENGTH);
        int cut = Math.max(head.lastIndexOf(". "), Math.max(head.lastIndexOf("! "), head.lastIndexOf("? ")));
        if (cut >= MAX_LENGTH / 3) return head.substring(0, cut + 1);
        int space = head.lastIndexOf(' ');
        String out = (space > MAX_LENGTH / 2 ? head.substring(0, space) : head).replaceAll("[,;:\\s-]+$", "");
        return out + "...";
    }
}
