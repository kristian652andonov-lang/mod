package com.aigirlfriend.client.voice.g2p;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A port of spaCy 3.8's rule-based English tokenizer (MIT, explosion/spaCy: tokenizer.pyx, lang/punctuation.py,
 * lang/char_classes.py), the tokenizer misaki ran before looking words up. It splits on spaces, peels prefixes
 * and suffixes off each chunk, splits infixes and applies the exception table. spaCy's long per-script letter
 * lists are replaced by Unicode letter categories, which agree on everything English chat text contains.
 */
final class SpacyTokenizer {
    private static final String PUNCT = "… …… , : ; ! ? ¿ ؟ ¡ ( ) [ ] { } < > _ # * & 。 ？ ！ ， 、 ； ： ～ · । ، ۔ ؛ ٪";
    private static final String QUOTES = "' \" ” “ ` ‘ ´ ’ ‚ , „ » « 「 」 『 』 （ ） 〔 〕 【 】 《 》 〈 〉 〈 〉 ⟦ ⟧";
    private static final String CURRENCY = "$ £ € ¥ ฿ US$ C$ A$ ₽ ﷼ ₴ ₠ ₡ ₢ ₣ ₤ ₥ ₦ ₧ ₨ ₩ ₪ ₫ € ₭ ₮ ₯ ₰ ₱ ₲ ₳ ₴ ₵ ₶ ₷ ₸ ₹ ₺ ₻ ₼ ₽ ₾ ₿";
    private static final String UNITS = "km km² km³ m m² m³ dm dm² dm³ cm cm² cm³ mm mm² mm³ ha µm nm yd in ft kg g mg µg t "
            + "lb oz m/s km/h kmh mph hPa Pa mbar mb MB kb KB gb GB tb TB T G M K %";
    private static final String HYPHENS = "- – — -- --- —— ~";
    private static final String ELLIPSES = "\\.\\.+|…";
    private static final String ICONS = "[" +
            "\\u00A6\\u00A9\\u00AE\\u00B0\\u0482\\u058D\\u058E\\u060E\\u060F\\u06DE\\u06E9\\u06FD\\u06FE\\u07F6" +
            "\\u09FA\\u0B70\\u0BF3-\\u0BF8\\u0BFA\\u0C7F\\u0D4F\\u0D79\\u0F01-\\u0F03\\u0F13\\u0F15-\\u0F17" +
            "\\u0F1A-\\u0F1F\\u0F34\\u0F36\\u0F38\\u0FBE-\\u0FC5\\u0FC7-\\u0FCC\\u0FCE\\u0FCF\\u0FD5-\\u0FD8" +
            "\\u109E\\u109F\\u1390-\\u1399\\u1940\\u19DE-\\u19FF\\u1B61-\\u1B6A\\u1B74-\\u1B7C\\u2100\\u2101" +
            "\\u2103-\\u2106\\u2108\\u2109\\u2114\\u2116\\u2117\\u211E-\\u2123\\u2125\\u2127\\u2129\\u212E\\u213A" +
            "\\u213B\\u214A\\u214C\\u214D\\u214F\\u218A\\u218B\\u2195-\\u2199\\u219C-\\u219F\\u21A1\\u21A2\\u21A4" +
            "\\u21A5\\u21A7-\\u21AD\\u21AF-\\u21CD\\u21D0\\u21D1\\u21D3\\u21D5-\\u21F3\\u2300-\\u2307\\u230C-" +
            "\\u231F\\u2322-\\u2328\\u232B-\\u237B\\u237D-\\u239A\\u23B4-\\u23DB\\u23E2-\\u2426\\u2440-\\u244A" +
            "\\u249C-\\u24E9\\u2500-\\u25B6\\u25B8-\\u25C0\\u25C2-\\u25F7\\u2600-\\u266E\\u2670-\\u2767\\u2794-" +
            "\\u27BF\\u2800-\\u28FF\\u2B00-\\u2B2F\\u2B45\\u2B46\\u2B4D-\\u2B73\\u2B76-\\u2B95\\u2B98-\\u2BC8" +
            "\\u2BCA-\\u2BFE\\u2CE5-\\u2CEA\\u2E80-\\u2E99\\u2E9B-\\u2EF3\\u2F00-\\u2FD5\\u2FF0-\\u2FFB\\u3004" +
            "\\u3012\\u3013\\u3020\\u3036\\u3037\\u303E\\u303F\\u3190\\u3191\\u3196-\\u319F\\u31C0-\\u31E3\\u3200-" +
            "\\u321E\\u322A-\\u3247\\u3250\\u3260-\\u327F\\u328A-\\u32B0\\u32C0-\\u32FE\\u3300-\\u33FF\\u4DC0-" +
            "\\u4DFF\\uA490-\\uA4C6\\uA828-\\uA82B\\uA836\\uA837\\uA839\\uAA77-\\uAA79\\uFDFD\\uFFE4\\uFFE8" +
            "\\uFFED\\uFFEE\\uFFFC\\uFFFD\\x{10137}-\\x{1013F}\\x{10179}-\\x{10189}\\x{1018C}-\\x{1018E}" +
            "\\x{10190}-\\x{1019B}\\x{101A0}\\x{101D0}-\\x{101FC}\\x{10877}\\x{10878}\\x{10AC8}\\x{1173F}" +
            "\\x{16B3C}-\\x{16B3F}\\x{16B45}\\x{1BC9C}\\x{1D000}-\\x{1D0F5}\\x{1D100}-\\x{1D126}\\x{1D129}-" +
            "\\x{1D164}\\x{1D16A}-\\x{1D16C}\\x{1D183}\\x{1D184}\\x{1D18C}-\\x{1D1A9}\\x{1D1AE}-\\x{1D1E8}" +
            "\\x{1D200}-\\x{1D241}\\x{1D245}\\x{1D300}-\\x{1D356}\\x{1D800}-\\x{1D9FF}\\x{1DA37}-\\x{1DA3A}" +
            "\\x{1DA6D}-\\x{1DA74}\\x{1DA76}-\\x{1DA83}\\x{1DA85}\\x{1DA86}\\x{1ECAC}\\x{1F000}-\\x{1F02B}" +
            "\\x{1F030}-\\x{1F093}\\x{1F0A0}-\\x{1F0AE}\\x{1F0B1}-\\x{1F0BF}\\x{1F0C1}-\\x{1F0CF}\\x{1F0D1}-" +
            "\\x{1F0F5}\\x{1F110}-\\x{1F16B}\\x{1F170}-\\x{1F1AC}\\x{1F1E6}-\\x{1F202}\\x{1F210}-\\x{1F23B}" +
            "\\x{1F240}-\\x{1F248}\\x{1F250}\\x{1F251}\\x{1F260}-\\x{1F265}\\x{1F300}-\\x{1F3FA}\\x{1F400}-" +
            "\\x{1F6D4}\\x{1F6E0}-\\x{1F6EC}\\x{1F6F0}-\\x{1F6F9}\\x{1F700}-\\x{1F773}\\x{1F780}-\\x{1F7D8}" +
            "\\x{1F800}-\\x{1F80B}\\x{1F810}-\\x{1F847}\\x{1F850}-\\x{1F859}\\x{1F860}-\\x{1F887}\\x{1F890}-" +
            "\\x{1F8AD}\\x{1F900}-\\x{1F90B}\\x{1F910}-\\x{1F93E}\\x{1F940}-\\x{1F970}\\x{1F973}-\\x{1F976}" +
            "\\x{1F97A}\\x{1F97C}-\\x{1F9A2}\\x{1F9B0}-\\x{1F9B9}\\x{1F9C0}-\\x{1F9C2}\\x{1F9D0}-\\x{1F9FF}" +
            "\\x{1FA60}-\\x{1FA6D}" + "]";
    private static final String ALPHA = "\\p{L}";
    private static final String ALPHA_LOWER = "\\p{Ll}\\p{Lo}";
    private static final String ALPHA_UPPER = "\\p{Lu}\\p{Lo}";

    private static final Pattern PREFIX = Pattern.compile("^(?:§|%|=|—|–|\\+(?![0-9])|" + alternatives(PUNCT) + "|"
            + ELLIPSES + "|" + alternatives(QUOTES) + "|" + alternatives(CURRENCY) + "|" + ICONS + ")");
    private static final Pattern SUFFIX = Pattern.compile("(?:" + alternatives(PUNCT) + "|" + ELLIPSES + "|"
            + alternatives(QUOTES) + "|" + ICONS + "|'s|'S|’s|’S|—|–"
            + "|(?<=[0-9])\\+"
            + "|(?<=°[FfCcKk])\\."
            + "|(?<=[0-9])(?:" + alternatives(CURRENCY) + ")"
            + "|(?<=[0-9])(?:" + alternatives(UNITS) + ")"
            + "|(?<=[0-9" + ALPHA_LOWER + "%²\\-+" + classChars(PUNCT + " | " + QUOTES) + "])\\."
            + "|(?<=[" + ALPHA_UPPER + "][" + ALPHA_UPPER + "])\\.)$");
    private static final Pattern INFIX = Pattern.compile(ELLIPSES + "|" + ICONS
            + "|(?<=[0-9])[+\\-*^](?=[0-9-])"
            + "|(?<=[" + ALPHA_LOWER + classChars(QUOTES) + "])\\.(?=[" + ALPHA_UPPER + classChars(QUOTES) + "])"
            + "|(?<=[" + ALPHA + "]),(?=[" + ALPHA + "])"
            + "|(?<=[" + ALPHA + "0-9])(?:" + alternatives(HYPHENS) + ")(?=[" + ALPHA + "])"
            + "|(?<=[" + ALPHA + "0-9])[:<>=/](?=[" + ALPHA + "])");
    /** spaCy's URL_MATCH (after Diego Perini's MIT-licensed URL regex). */
    private static final Pattern URL = Pattern.compile("^(?:(?:[\\w+\\-.]{2,})://)?(?:\\S+(?::\\S*)?@)?(?:"
            + "(?!(?:10|127)(?:\\.\\d{1,3}){3})(?!(?:169\\.254|192\\.168)(?:\\.\\d{1,3}){2})"
            + "(?!172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})"
            + "(?:[1-9]\\d?|1\\d\\d|2[01]\\d|22[0-3])(?:\\.(?:1?\\d{1,2}|2[0-4]\\d|25[0-5])){2}"
            + "(?:\\.(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))"
            + "|(?:(?:[A-Za-z0-9\\u00a1-\\uffff][A-Za-z0-9\\u00a1-\\uffff_-]{0,62})?[A-Za-z0-9\\u00a1-\\uffff]\\.)+"
            + "(?:[" + ALPHA_LOWER + "]{2,63}))(?::\\d{2,5})?(?:[/?#]\\S*)?$", Pattern.UNICODE_CHARACTER_CLASS);

    private final Map<String, String[]> specials = TokenizerExceptions.build();
    /** Exceptions that affix splitting would break apart, keyed by their first piece (spaCy's special matcher). */
    private final Map<String, List<String[]>> rejoin = new HashMap<>();

    SpacyTokenizer() {
        for (String key : specials.keySet()) {
            if (findPrefix(key) == 0 && !INFIX.matcher(key).find() && findSuffix(key) == 0 && key.indexOf(' ') < 0) continue;
            List<MToken> pieces = tokenize(key, false);
            String[] texts = new String[pieces.size()];
            for (int i = 0; i < texts.length; i++) texts[i] = pieces.get(i).text;
            if (texts.length > 0) rejoin.computeIfAbsent(texts[0], k -> new ArrayList<>()).add(texts);
        }
    }

    /** Tokens with their trailing whitespace ("" or " "), the way spaCy's Doc sees the text. */
    List<MToken> tokenize(String text) {
        return applySpecialMatches(tokenize(text, true));
    }

    private List<MToken> tokenize(String text, boolean withSpecials) {
        List<MToken> doc = new ArrayList<>();
        if (text.isEmpty()) return doc;
        boolean inSpace = isSpace(text.charAt(0));
        int start = 0, i = 0;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isSpace(c) == inSpace) continue;
            if (start < i) chunk(doc, text.substring(start, i), withSpecials);
            if (c == ' ') {
                if (!doc.isEmpty()) doc.get(doc.size() - 1).whitespace = " ";
                start = i + 1;
            } else {
                start = i;
            }
            inSpace = !inSpace;
        }
        if (start < i) {
            chunk(doc, text.substring(start), withSpecials);
            doc.get(doc.size() - 1).whitespace = text.charAt(text.length() - 1) == ' ' && !inSpace ? " " : "";
        }
        return doc;
    }

    private void chunk(List<MToken> doc, String span, boolean withSpecials) {
        if (withSpecials && pushSpecial(doc, span)) return;
        List<String> prefixes = new ArrayList<>(), suffixes = new ArrayList<>();
        String rest = splitAffixes(span, prefixes, suffixes, withSpecials);
        for (String p : prefixes) doc.add(token(p));
        if (!rest.isEmpty()) {
            if (withSpecials && pushSpecial(doc, rest)) {
                // the exception table had it
            } else if (rest.indexOf('.') >= 0 && URL.matcher(rest).matches()) {
                doc.add(token(rest));
            } else {
                splitInfixes(doc, rest);
            }
        }
        for (int i = suffixes.size() - 1; i >= 0; i--) doc.add(token(suffixes.get(i)));
    }

    private String splitAffixes(String s, List<String> prefixes, List<String> suffixes, boolean withSpecials) {
        int lastSize = -1;
        while (!s.isEmpty() && s.length() != lastSize) {
            if (withSpecials && specials.containsKey(s)) break;
            lastSize = s.length();
            int pre = findPrefix(s);
            String prefix = null, minusPre = null;
            if (pre != 0) {
                prefix = s.substring(0, pre);
                minusPre = s.substring(pre);
                if (!minusPre.isEmpty() && withSpecials && specials.containsKey(minusPre)) {
                    prefixes.add(prefix);
                    return minusPre;
                }
            }
            int suf = findSuffix(s.substring(pre));
            String suffix = null, minusSuf = null;
            if (suf != 0) {
                suffix = s.substring(s.length() - suf);
                minusSuf = s.substring(0, s.length() - suf);
                if (!minusSuf.isEmpty() && withSpecials && specials.containsKey(minusSuf)) {
                    suffixes.add(suffix);
                    return minusSuf;
                }
            }
            if (pre != 0 && suf != 0 && pre + suf <= s.length()) {
                s = s.substring(pre, s.length() - suf);
                prefixes.add(prefix);
                suffixes.add(suffix);
            } else if (pre != 0) {
                s = minusPre;
                prefixes.add(prefix);
            } else if (suf != 0) {
                s = minusSuf;
                suffixes.add(suffix);
            }
        }
        return s;
    }

    private void splitInfixes(List<MToken> doc, String s) {
        if (plainWord(s)) {
            doc.add(token(s));
            return;
        }
        Matcher m = INFIX.matcher(s);
        int start = 0;
        while (m.find()) {
            if (m.start() == 0) continue;
            if (m.start() != start) doc.add(token(s.substring(start, m.start())));
            if (m.start() != m.end()) doc.add(token(s.substring(m.start(), m.end())));
            start = m.end();
        }
        if (start < s.length()) doc.add(token(s.substring(start)));
    }

    private boolean pushSpecial(List<MToken> doc, String span) {
        String[] parts = specials.get(span);
        if (parts == null) return false;
        for (String p : parts) doc.add(token(p));
        return true;
    }

    /** spaCy's special-case matcher: exceptions broken up by affix rules ("Hi:)" gave ":" ")") are rejoined. */
    private List<MToken> applySpecialMatches(List<MToken> doc) {
        List<int[]> matches = new ArrayList<>();
        for (int i = 0; i < doc.size(); i++) {
            List<String[]> candidates = rejoin.get(doc.get(i).text);
            if (candidates == null) continue;
            for (String[] pattern : candidates) {
                if (i + pattern.length > doc.size()) continue;
                boolean ok = true;
                for (int k = 1; k < pattern.length && ok; k++) ok = doc.get(i + k).text.equals(pattern[k]);
                if (ok) matches.add(new int[] {i, i + pattern.length});
            }
        }
        if (matches.isEmpty()) return doc;
        // Longest first, then leftmost; a match may not start or end inside an earlier one.
        matches.sort(Comparator.<int[]>comparingInt(m -> m[1] - m[0]).reversed().thenComparingInt(m -> m[0]));
        Set<Integer> seen = new HashSet<>();
        List<int[]> kept = new ArrayList<>();
        for (int[] m : matches) {
            if (!seen.contains(m[0]) && !seen.contains(m[1] - 1)) kept.add(m);
            for (int k = m[0]; k < m[1]; k++) seen.add(k);
        }
        kept.sort(Comparator.comparingInt(m -> m[0]));
        List<MToken> out = new ArrayList<>(doc.size());
        int next = 0;
        for (int[] m : kept) {
            while (next < m[0]) out.add(doc.get(next++));
            StringBuilder text = new StringBuilder();
            for (int k = m[0]; k < m[1]; k++) text.append(doc.get(k).text).append(k + 1 < m[1] ? doc.get(k).whitespace : "");
            String[] parts = specials.get(text.toString());
            if (parts == null) {
                while (next < m[1]) out.add(doc.get(next++));
                continue;
            }
            for (String p : parts) out.add(token(p));
            out.get(out.size() - 1).whitespace = doc.get(m[1] - 1).whitespace;
            next = m[1];
        }
        while (next < doc.size()) out.add(doc.get(next++));
        return out;
    }

    private static int findPrefix(String s) {
        char first = s.charAt(0);
        if (Character.isLetterOrDigit(first) && !s.startsWith("US$") && !s.startsWith("C$") && !s.startsWith("A$")) return 0;
        Matcher m = PREFIX.matcher(s);
        return m.lookingAt() ? m.end() : 0;
    }

    /**
     * Length of the leftmost suffix match, as Python's search() finds it. Only a run of dots can be longer than a
     * few characters, so just the last six start positions are tried (lookbehinds still see the whole string).
     */
    private static int findSuffix(String s) {
        if (s.isEmpty()) return 0;
        int dots = 0;
        while (dots < s.length() && s.charAt(s.length() - 1 - dots) == '.') dots++;
        if (dots >= 2) return dots;
        // a word ending in a letter can only lose "'s" or a unit after a number ("5km")
        int n = s.length();
        if (Character.isLetter(s.charAt(n - 1)) && !hasDigit(s)) {
            boolean possessive = n >= 2 && (s.charAt(n - 1) == 's' || s.charAt(n - 1) == 'S') && "'’".indexOf(s.charAt(n - 2)) >= 0;
            return possessive ? 2 : 0;
        }
        Matcher m = SUFFIX.matcher(s).useTransparentBounds(true);
        for (int start = Math.max(0, s.length() - 6); start < s.length(); start++) {
            if (m.region(start, s.length()).lookingAt()) return s.length() - start;
        }
        return 0;
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
        return false;
    }

    /** Letters, digits and apostrophes only: no infix rule can apply. */
    private static boolean plainWord(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '\'' && c != '’') return false;
        }
        return true;
    }

    private static MToken token(String text) {
        return new MToken(text, "", "");
    }

    /** Python's str.isspace(), close enough: Unicode whitespace and space separators. */
    static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085';
    }

    private static String alternatives(String list) {
        StringBuilder b = new StringBuilder();
        for (String item : list.split(" ")) {
            if (item.isEmpty()) continue;
            if (b.length() > 0) b.append('|');
            b.append(Pattern.quote(item));
        }
        return b.toString();
    }

    /** The characters of a space-separated list, escaped for use inside a [...] class. */
    private static String classChars(String list) {
        StringBuilder b = new StringBuilder();
        list.replace(" ", "").chars().distinct().forEach(c -> {
            if ("[]\\^-&".indexOf(c) >= 0) b.append('\\');
            b.append((char) c);
        });
        return b.toString();
    }
}
