package com.aigirlfriend.client.voice.g2p;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * misaki's English Lexicon (misaki 0.9.4, en.py): gold then silver dictionary lookup with capitalisation
 * twins, POS-keyed entries, stress rules, -s/-ed/-ing stemming, special words and numbers.
 */
final class Lexicon {
    /** Where the next word starts (a vowel or not, or punctuation when null) and whether it is "to". */
    record Context(Boolean futureVowel, boolean futureTo) {
        static final Context START = new Context(null, false);
    }

    private static final Map<String, String[]> CURRENCIES = Map.of(
            "$", new String[] {"dollar", "cent"},
            "£", new String[] {"pound", "pence"},
            "€", new String[] {"euro", "cent"});
    private static final Map<String, String> ADD_SYMBOLS = Map.of(".", "dot", "/", "slash");
    static final Map<String, String> SYMBOLS = Map.of("%", "percent", "&", "and", "+", "plus", "@", "at");
    private static final String[] NUMBER_SUFFIXES = {"ing", "'d", "ed", "'s", "st", "nd", "rd", "th", "s"};
    private static final Pattern DOUBLED_ING = Pattern.compile("([bcdgklmnprstvxz])\\1ing$|cking$");
    private static final Pattern NUMBER_SUFFIX = Pattern.compile("[a-z']+$");
    private static final Pattern VERSUS = Pattern.compile("(?i)vs\\.?");
    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]+");
    private static final Double MINUS_HALF = -0.5, HALF = 0.5, TWO = 2.0, MINUS_TWO = -2.0;

    final boolean british;
    private final LexiconTable gold;
    private final LexiconTable silver;
    /** Gold entries whose pronunciation depends on the part of speech; a value may be null ("spell it out"). */
    private final Map<String, Map<String, String>> goldByTag;

    private Lexicon(boolean british, LexiconTable gold, LexiconTable silver, Map<String, Map<String, String>> goldByTag) {
        this.british = british;
        this.gold = gold;
        this.silver = silver;
        this.goldByTag = goldByTag;
    }

    static Lexicon load(Path goldJson, Path silverJson, boolean british) throws IOException {
        Map<String, Map<String, String>> byTag = new HashMap<>();
        LexiconTable gold = readTable(goldJson, byTag);
        LexiconTable silver = readTable(silverJson, byTag);
        return new Lexicon(british, gold, silver, byTag);
    }

    @SuppressWarnings("unchecked")
    private static LexiconTable readTable(Path json, Map<String, Map<String, String>> byTag) throws IOException {
        LexiconTable.Builder table = new LexiconTable.Builder();
        Json.forEachMember(Files.readString(json, StandardCharsets.UTF_8), (key, value) -> {
            if (value instanceof String s) {
                table.put(key, s);
            } else if (value instanceof Map<?, ?> m) {
                Map<String, String> tags = new LinkedHashMap<>();
                ((Map<String, Object>) m).forEach((tag, ps) -> tags.put(tag, (String) ps));
                byTag.put(key, tags);
            }
        });
        return table.build();
    }

    long bytesUsed() {
        return gold.bytesUsed() + silver.bytesUsed() + 200L * goldByTag.size();
    }

    // ---- dictionary access with misaki's grow_dictionary twins (Capitalised <-> lower-case), computed lazily ----

    private Object goldRaw(String w) {
        Map<String, String> tags = goldByTag.get(w);
        return tags != null ? tags : gold.get(w);
    }

    private Object grown(String w, boolean fromGold) {
        Object v = fromGold ? goldRaw(w) : silver.get(w);
        if (v != null || w.length() < 2) return v;
        String lower = lower(w);
        String twin;
        if (!w.equals(lower)) {
            if (!w.equals(capitalize(lower))) return null;
            twin = lower;
        } else {
            twin = capitalize(w);
            if (twin.equals(w)) return null;
        }
        return fromGold ? goldRaw(twin) : silver.get(twin);
    }

    Object golds(String w) {
        return grown(w, true);
    }

    String silvers(String w) {
        return (String) grown(w, false);
    }

    boolean inGolds(String w) {
        return golds(w) != null;
    }

    private boolean inSilvers(String w) {
        return silvers(w) != null;
    }

    private String goldString(String w) {
        Object v = golds(w);
        return v instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    private String goldTagged(String w, String tag) {
        return ((Map<String, String>) golds(w)).get(tag);
    }

    // ---- misaki Lexicon methods ----

    String getNNP(String word) {
        StringBuilder ps = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!Character.isLetter(c)) continue;
            String letter = goldString(upper(String.valueOf(c)));
            if (letter == null) return null;
            ps.append(letter);
        }
        String s = Phonemes.applyStress(ps.toString(), 0.0);
        int last = s.lastIndexOf(Phonemes.SECONDARY);
        return last < 0 ? s : s.substring(0, last) + Phonemes.PRIMARY + s.substring(last + 1);
    }

    private String specialCase(String word, String tag, Double stress, Context ctx) {
        if ("ADD".equals(tag) && ADD_SYMBOLS.containsKey(word)) {
            return lookup(ADD_SYMBOLS.get(word), null, MINUS_HALF, ctx);
        } else if (SYMBOLS.containsKey(word)) {
            return lookup(SYMBOLS.get(word), null, null, ctx);
        } else if (strip(word, '.').indexOf('.') >= 0 && isAlpha(word.replace(".", "")) && longestDotPart(word) < 3) {
            return getNNP(word);
        } else if (word.equals("a") || word.equals("A")) {
            return "DT".equals(tag) ? "ɐ" : "ˈA";
        } else if (word.equals("am") || word.equals("Am") || word.equals("AM")) {
            if (tag.startsWith("NN")) return getNNP(word);
            if (ctx.futureVowel() == null || !word.equals("am") || (stress != null && stress > 0)) return goldString("am");
            return "ɐm";
        } else if (word.equals("an") || word.equals("An") || word.equals("AN")) {
            if (word.equals("AN") && tag.startsWith("NN")) return getNNP(word);
            return "ɐn";
        } else if (word.equals("I") && "PRP".equals(tag)) {
            return Phonemes.SECONDARY + "I";
        } else if ((word.equals("by") || word.equals("By") || word.equals("BY")) && "ADV".equals(parentTag(tag))) {
            return "bˈI";
        } else if (word.equals("to") || word.equals("To") || (word.equals("TO") && (tag.equals("TO") || tag.equals("IN")))) {
            Boolean vowel = ctx.futureVowel();
            return vowel == null ? goldString("to") : vowel ? "tʊ" : "tə";
        } else if (word.equals("in") || word.equals("In") || (word.equals("IN") && !tag.equals("NNP"))) {
            return (ctx.futureVowel() == null || !tag.equals("IN") ? "ˈ" : "") + "ɪn";
        } else if (word.equals("the") || word.equals("The") || (word.equals("THE") && tag.equals("DT"))) {
            return Boolean.TRUE.equals(ctx.futureVowel()) ? "ði" : "ðə";
        } else if (tag.equals("IN") && VERSUS.matcher(word).matches()) {
            return lookup("versus", null, null, ctx);
        } else if (word.equals("used") || word.equals("Used") || word.equals("USED")) {
            boolean usedTo = (tag.equals("VBD") || tag.equals("JJ")) && ctx.futureTo();
            return goldTagged("used", usedTo ? "VBD" : "DEFAULT");
        }
        return null;
    }

    static String parentTag(String tag) {
        if (tag == null) return null;
        if (tag.startsWith("VB")) return "VERB";
        if (tag.startsWith("NN")) return "NOUN";
        if (tag.startsWith("ADV") || tag.startsWith("RB")) return "ADV";
        if (tag.startsWith("ADJ") || tag.startsWith("JJ")) return "ADJ";
        return tag;
    }

    boolean isKnown(String word) {
        if (inGolds(word) || SYMBOLS.containsKey(word) || inSilvers(word)) return true;
        if (!isAlpha(word) || !allLexiconChars(word)) return false;
        if (word.length() == 1) return true;
        if (word.equals(upper(word)) && inGolds(lower(word))) return true;
        String tail = word.substring(1);
        return tail.equals(upper(tail));
    }

    @SuppressWarnings("unchecked")
    String lookup(String word, String tag, Double stress, Context ctx) {
        boolean isNNP = false;
        if (word.equals(upper(word)) && !inGolds(word)) {
            word = lower(word);
            isNNP = "NNP".equals(tag);
        }
        Object ps = golds(word);
        if (ps == null && !isNNP) ps = silvers(word);
        if (ps instanceof Map<?, ?> m) {
            Map<String, String> tags = (Map<String, String>) m;
            String t = tag;
            if (ctx != null && ctx.futureVowel() == null && tags.containsKey("None")) {
                t = "None";
            } else if (!tags.containsKey(t)) {
                t = parentTag(t);
            }
            ps = tags.containsKey(t) ? tags.get(t) : tags.get("DEFAULT");
        }
        String s = (String) ps;
        if (s == null || (isNNP && s.indexOf(Phonemes.PRIMARY) < 0)) {
            String spelled = getNNP(word);
            if (spelled != null) return spelled;
        }
        return Phonemes.applyStress(s, stress);
    }

    private String plural(String stem) {
        if (stem == null || stem.isEmpty()) return null;
        char last = stem.charAt(stem.length() - 1);
        if ("ptkfθ".indexOf(last) >= 0) return stem + "s";
        if ("szʃʒʧʤ".indexOf(last) >= 0) return stem + (british ? "ɪ" : "ᵻ") + "z";
        return stem + "z";
    }

    String stemS(String word, String tag, Double stress, Context ctx) {
        if (word.length() < 3 || !word.endsWith("s")) return null;
        String stem;
        if (!word.endsWith("ss") && isKnown(cut(word, 1))) {
            stem = cut(word, 1);
        } else if ((word.endsWith("'s") || (word.length() > 4 && word.endsWith("es") && !word.endsWith("ies")))
                && isKnown(cut(word, 2))) {
            stem = cut(word, 2);
        } else if (word.length() > 4 && word.endsWith("ies") && isKnown(cut(word, 3) + "y")) {
            stem = cut(word, 3) + "y";
        } else {
            return null;
        }
        return plural(lookup(stem, tag, stress, ctx));
    }

    private String past(String stem) {
        if (stem == null || stem.isEmpty()) return null;
        char last = stem.charAt(stem.length() - 1);
        if ("pkfθʃsʧ".indexOf(last) >= 0) return stem + "t";
        if (last == 'd') return stem + (british ? "ɪ" : "ᵻ") + "d";
        if (last != 't') return stem + "d";
        if (british || stem.length() < 2) return stem + "ɪd";
        if (Phonemes.US_TAUS.indexOf(stem.charAt(stem.length() - 2)) >= 0) return cut(stem, 1) + "ɾᵻd";
        return stem + "ᵻd";
    }

    String stemEd(String word, String tag, Double stress, Context ctx) {
        if (word.length() < 4 || !word.endsWith("d")) return null;
        String stem;
        if (!word.endsWith("dd") && isKnown(cut(word, 1))) {
            stem = cut(word, 1);
        } else if (word.length() > 4 && word.endsWith("ed") && !word.endsWith("eed") && isKnown(cut(word, 2))) {
            stem = cut(word, 2);
        } else {
            return null;
        }
        return past(lookup(stem, tag, stress, ctx));
    }

    private String progressive(String stem) {
        if (stem == null || stem.isEmpty()) return null;
        char last = stem.charAt(stem.length() - 1);
        if (british) {
            if (last == 'ə' || last == 'ː') return null;
        } else if (stem.length() > 1 && last == 't' && Phonemes.US_TAUS.indexOf(stem.charAt(stem.length() - 2)) >= 0) {
            return cut(stem, 1) + "ɾɪŋ";
        }
        return stem + "ɪŋ";
    }

    String stemIng(String word, String tag, Double stress, Context ctx) {
        if (word.length() < 5 || !word.endsWith("ing")) return null;
        String stem;
        if (word.length() > 5 && isKnown(cut(word, 3))) {
            stem = cut(word, 3);
        } else if (isKnown(cut(word, 3) + "e")) {
            stem = cut(word, 3) + "e";
        } else if (word.length() > 5 && DOUBLED_ING.matcher(word).find() && isKnown(cut(word, 4))) {
            stem = cut(word, 4);
        } else {
            return null;
        }
        return progressive(lookup(stem, tag, stress, ctx));
    }

    String getWord(String word, String tag, Double stress, Context ctx) {
        String ps = specialCase(word, tag, stress, ctx);
        if (ps != null) return ps;
        String wl = lower(word);
        if (word.length() > 1 && isAlpha(word.replace("'", "")) && !word.equals(wl)
                && (!"NNP".equals(tag) || word.length() > 7)
                && !inGolds(word) && !inSilvers(word)
                && (word.equals(upper(word)) || word.substring(1).equals(lower(word.substring(1))))
                && (inGolds(wl) || inSilvers(wl) || nonEmpty(stemS(wl, tag, stress, ctx))
                    || nonEmpty(stemEd(wl, tag, stress, ctx)) || nonEmpty(stemIng(wl, tag, stress, ctx)))) {
            word = wl;
        }
        if (isKnown(word)) return lookup(word, tag, stress, ctx);
        if (word.endsWith("s'") && isKnown(cut(word, 2) + "'s")) return lookup(cut(word, 2) + "'s", tag, stress, ctx);
        if (word.endsWith("'") && isKnown(cut(word, 1))) return lookup(cut(word, 1), tag, stress, ctx);
        String s = stemS(word, tag, stress, ctx);
        if (s != null) return s;
        String ed = stemEd(word, tag, stress, ctx);
        if (ed != null) return ed;
        return stemIng(word, tag, stress == null ? HALF : stress, ctx);
    }

    // ---- numbers ----

    private static boolean knownCurrency(String currency) {
        return currency != null && CURRENCIES.containsKey(currency);
    }

    private static boolean isCurrency(String word) {
        int dot = word.indexOf('.');
        if (dot < 0) return true;
        if (word.indexOf('.', dot + 1) >= 0) return false;
        return word.length() - dot - 1 < 3;
    }

    static boolean isNumber(String word, boolean isHead) {
        boolean digit = false;
        for (int i = 0; i < word.length(); i++) digit |= isAsciiDigit(word.charAt(i));
        if (!digit) return false;
        for (String s : NUMBER_SUFFIXES) {
            if (word.endsWith(s)) {
                word = cut(word, s.length());
                break;
            }
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!(isAsciiDigit(c) || c == ',' || c == '.' || (isHead && i == 0 && c == '-'))) return false;
        }
        return true;
    }

    private String getNumber(String word, String currency, boolean isHead, String numFlags) {
        Matcher m = NUMBER_SUFFIX.matcher(word);
        String suffix = m.find() ? m.group() : null;
        if (suffix != null) word = cut(word, suffix.length());
        boolean ordinal = suffix != null && (suffix.equals("st") || suffix.equals("nd") || suffix.equals("rd") || suffix.equals("th"));
        List<String> result = new ArrayList<>();
        if (word.startsWith("-")) {
            result.add(lookup("minus", null, null, null));
            word = word.substring(1);
        }
        if (isDigits(word) && ordinal) {
            extendNum(result, NumberWords.ordinal(parse(word)), true, numFlags);
        } else if (result.isEmpty() && word.length() == 4 && !knownCurrency(currency) && isDigits(word)) {
            extendNum(result, NumberWords.year(parse(word)), true, numFlags);
        } else if (!isHead && word.indexOf('.') < 0) {
            String num = word.replace(",", "");
            if (num.isEmpty()) return null;
            if (num.charAt(0) == '0' || num.length() > 3) {
                digitByDigit(result, num, numFlags);
            } else if (num.length() == 3 && !num.endsWith("00")) {
                extendNum(result, cardinal(num.substring(0, 1)), true, numFlags);
                if (num.charAt(1) == '0') {
                    result.add(lookup("O", null, MINUS_TWO, null));
                    extendNum(result, cardinal(num.substring(2)), false, numFlags);
                } else {
                    extendNum(result, cardinal(num.substring(1)), false, numFlags);
                }
            } else {
                extendNum(result, cardinal(num), true, numFlags);
            }
        } else if (count(word, '.') > 1 || !isHead) {
            boolean first = true;
            for (String num : word.replace(",", "").split("\\.", -1)) {
                if (num.isEmpty()) {
                    // nothing to say for an empty part
                } else if (num.charAt(0) == '0' || (num.length() != 2 && hasNonZero(num.substring(1)))) {
                    digitByDigit(result, num, numFlags);
                } else {
                    extendNum(result, cardinal(num), first, numFlags);
                }
                first = false;
            }
        } else if (knownCurrency(currency) && isCurrency(word)) {
            String[] parts = word.replace(",", "").split("\\.", -1);
            String[] units = CURRENCIES.get(currency);
            List<long[]> pairs = new ArrayList<>();
            for (int i = 0; i < Math.min(parts.length, units.length); i++) {
                pairs.add(new long[] {parts[i].isEmpty() ? 0 : parse(parts[i]), i});
            }
            if (pairs.size() > 1) {
                if (pairs.get(1)[0] == 0) pairs = pairs.subList(0, 1);
                else if (pairs.get(0)[0] == 0) pairs = pairs.subList(1, pairs.size());
            }
            for (int i = 0; i < pairs.size(); i++) {
                long num = pairs.get(i)[0];
                String unit = units[(int) pairs.get(i)[1]];
                if (i > 0) result.add(lookup("and", null, null, null));
                extendNum(result, NumberWords.cardinal(num), i == 0, numFlags);
                result.add(Math.abs(num) != 1 && !unit.equals("pence") ? stemS(unit + "s", null, null, null)
                        : lookup(unit, null, null, null));
            }
        } else {
            String words;
            if (isDigits(word)) {
                words = cardinal(word);
            } else if (word.indexOf('.') < 0) {
                long n = parse(word.replace(",", ""));
                words = ordinal ? NumberWords.ordinal(n) : NumberWords.cardinal(n);
            } else {
                word = word.replace(",", "");
                if (word.charAt(0) == '.') {
                    StringBuilder b = new StringBuilder("point");
                    for (int i = 1; i < word.length(); i++) b.append(' ').append(cardinal(word.substring(i, i + 1)));
                    words = b.toString();
                } else {
                    words = decimal(word);
                }
            }
            extendNum(result, words, true, numFlags);
        }
        if (result.isEmpty() || result.contains(null)) return null;
        String ps = String.join(" ", result);
        if (suffix == null) return ps;
        return switch (suffix) {
            case "s", "'s" -> plural(ps);
            case "ed", "'d" -> past(ps);
            case "ing" -> progressive(ps);
            default -> ps;
        };
    }

    /** misaki's extend_num: looks up each word of a number's spelling; "and" only on request (#&# flag). */
    private void extendNum(List<String> result, String words, boolean first, String numFlags) {
        String[] splits = NON_LETTERS.split(words, -1);
        for (int i = 0; i < splits.length; i++) {
            String w = splits[i];
            if (!w.equals("and") || numFlags.indexOf('&') >= 0) {
                if (first && i == 0 && splits.length > 1 && w.equals("one") && numFlags.indexOf('a') >= 0) {
                    result.add("ə");
                } else {
                    result.add(lookup(w, null, w.equals("point") ? MINUS_TWO : null, null));
                }
            } else if (numFlags.indexOf('n') >= 0 && !result.isEmpty()) {
                result.set(result.size() - 1, result.get(result.size() - 1) + "ən");
            }
        }
    }

    private void digitByDigit(List<String> result, String num, String numFlags) {
        for (int i = 0; i < num.length(); i++) extendNum(result, cardinal(num.substring(i, i + 1)), false, numFlags);
    }

    private static String cardinal(String digits) {
        return NumberWords.cardinal(parse(digits));
    }

    /** num2words(float(s)): whole values read as integers, otherwise "point" and one word per decimal digit. */
    private static String decimal(String s) {
        int dot = s.indexOf('.');
        String whole = s.substring(0, dot), fraction = s.substring(dot + 1);
        int end = fraction.length();
        while (end > 0 && fraction.charAt(end - 1) == '0') end--;
        fraction = fraction.substring(0, end);
        long pre = whole.isEmpty() ? 0 : parse(whole);
        if (fraction.isEmpty()) return NumberWords.cardinal(pre);
        StringBuilder b = new StringBuilder(NumberWords.cardinal(pre)).append(" point");
        for (int i = 0; i < fraction.length(); i++) b.append(' ').append(NumberWords.cardinal(fraction.charAt(i) - '0'));
        return b.toString();
    }

    private String appendCurrency(String ps, String currency) {
        if (currency == null || currency.isEmpty()) return ps;
        String[] units = CURRENCIES.get(currency);
        String unit = units == null ? null : stemS(units[0] + "s", null, null, null);
        return unit == null ? ps : ps + " " + unit;
    }

    /** misaki's Lexicon.__call__: the phonemes of one (possibly merged) token, or null if it is unknown. */
    String apply(MToken tk, Context ctx) {
        String word = (tk.alias == null ? tk.text : tk.alias).replace('‘', '\'').replace('’', '\'');
        word = Normalizer.normalize(word, Normalizer.Form.NFKC);
        word = foldDigits(word);
        Double stress = word.equals(lower(word)) ? null : word.equals(upper(word)) ? TWO : HALF;
        String ps = getWord(word, tk.tag, stress, ctx);
        if (ps != null) return Phonemes.applyStress(appendCurrency(ps, tk.currency), tk.stress);
        if (isNumber(word, tk.isHead)) return Phonemes.applyStress(getNumber(word, tk.currency, tk.isHead, tk.numFlags), tk.stress);
        return null;
    }

    // ---- small string helpers with Python semantics ----

    private static String foldDigits(String word) {
        StringBuilder b = null;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= 128 && Character.isDigit(c)) {
                if (b == null) b = new StringBuilder(word.substring(0, i));
                b.append(Character.getNumericValue(c));
            } else if (b != null) {
                b.append(c);
            }
        }
        return b == null ? word : b.toString();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }

    static String capitalize(String s) {
        if (s.isEmpty()) return s;
        int first = s.codePointAt(0), n = Character.charCount(first);
        return new StringBuilder().appendCodePoint(Character.toTitleCase(first)).append(lower(s.substring(n))).toString();
    }

    static boolean isAlpha(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (!Character.isLetter(cp)) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    static boolean allLexiconChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == '\'' || c == '-' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        }
        return true;
    }

    static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!isAsciiDigit(s.charAt(i))) return false;
        return true;
    }

    private static long parse(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE / 10;
        }
    }

    private static boolean nonEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static boolean hasNonZero(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) != '0') return true;
        return false;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    static String cut(String s, int n) {
        return s.substring(0, s.length() - n);
    }

    static String strip(String s, char c) {
        int start = 0, end = s.length();
        while (start < end && s.charAt(start) == c) start++;
        while (end > start && s.charAt(end - 1) == c) end--;
        return s.substring(start, end);
    }

    private static int longestDotPart(String word) {
        int best = 0;
        for (String part : word.split("\\.", -1)) best = Math.max(best, part.length());
        return best;
    }
}
