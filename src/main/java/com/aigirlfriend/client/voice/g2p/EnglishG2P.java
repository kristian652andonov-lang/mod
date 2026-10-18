package com.aigirlfriend.client.voice.g2p;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * English text to Kokoro phonemes, a Java port of misaki 0.9.4's {@code en.G2P} (Apache-2.0, hexgrad/misaki), the
 * G2P Kokoro-82M was trained with. spaCy's English tokenizer (MIT) is ported rule for rule; its statistical tagger
 * is replaced by {@link Tagger}'s rules, so the handful of words whose sound depends on the part of speech
 * ("that", "read", "live", ...) can differ from misaki. Words missing from the lexicon go to {@link LetterToSound}
 * instead of being dropped. Uses only java.*; thread-safe once loaded.
 *
 * <pre>{@code
 * EnglishG2P g2p = EnglishG2P.load(usGold, usSilver, false);
 * for (String chunk : g2p.kokoroChunks("Hi! I'm Iris.")) {
 *     long[] inputIds = vocab.inputIds(chunk);   // KokoroVocab
 *     float[] style = voice[KokoroVocab.styleRow(chunk)];
 * }
 * }</pre>
 */
public final class EnglishG2P {
    /** Kokoro's context limit in phoneme characters per chunk. */
    public static final int MAX_PHONEMES = 510;

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]*)\\)");
    private static final Pattern NEWLINES = Pattern.compile("\\n+");
    private static final String SUBTOKEN_JUNKS = "',-._‘’/";
    private static final int MAX_SPAN = 16;
    private static final String PUNCT_TAGS = " . , -LRB- -RRB- `` \"\" '' : $ # NFP ";
    private static final Pattern SUBTOKEN = Pattern.compile("^['‘’]+|\\p{Lu}(?=\\p{Lu}\\p{Ll})|(?:^-)?(?:\\d?[,.]?\\d)+|[-_]+"
            + "|['‘’]{2,}|\\p{L}*?(?:['‘’]\\p{L})*?\\p{Ll}(?=\\p{Lu})|\\p{L}+(?:['‘’]\\p{L})*|[^-_\\p{L}'‘’\\d]|['‘’]+$",
            Pattern.UNICODE_CHARACTER_CLASS);

    private final Lexicon lexicon;
    private final SpacyTokenizer tokenizer = new SpacyTokenizer();
    private final Tagger tagger;
    private final LetterToSound fallback;

    private EnglishG2P(Lexicon lexicon) {
        this.lexicon = lexicon;
        this.tagger = new Tagger(w -> lexicon.inGolds(w) || lexicon.silvers(w) != null);
        this.fallback = new LetterToSound(lexicon);
    }

    /**
     * Loads misaki's gold and silver lexicons: us_gold.json/us_silver.json for American voices (af_*),
     * gb_gold.json/gb_silver.json with {@code british} for British ones (bf_*). Takes about a second.
     */
    public static EnglishG2P load(Path goldJson, Path silverJson, boolean british) throws IOException {
        return new EnglishG2P(Lexicon.load(goldJson, silverJson, british));
    }

    public boolean british() {
        return lexicon.british;
    }

    /** Approximate heap held by the loaded lexicons. */
    public long lexiconBytes() {
        return lexicon.bytesUsed();
    }

    /** Phonemes for a line of text; words nobody can pronounce (emoji, stray symbols) are left out. */
    public String phonemize(String text) {
        return phonemize(text, "", true, tagger);
    }

    /**
     * The phoneme strings Kokoro should speak for {@code text}, one per model call, as kokoro 0.9.4's KPipeline
     * cuts them: lines are split at newlines, long lines at the last sentence, clause or comma break that keeps a
     * chunk within {@value #MAX_PHONEMES} characters. Empty chunks are dropped.
     */
    public List<String> kokoroChunks(String text) {
        return kokoroChunks(text, true, tagger);
    }

    Tagger tagger() {
        return tagger;
    }

    List<String> kokoroChunks(String text, boolean useFallback, Consumer<List<MToken>> tagger) {
        List<String> out = new ArrayList<>();
        for (String line : NEWLINES.split(text.strip(), -1)) {
            if (line.isBlank()) continue;
            for (String ps : KokoroChunker.chunk(process(line, "", useFallback, tagger))) {
                if (!ps.isEmpty()) out.add(ps.length() > MAX_PHONEMES ? ps.substring(0, MAX_PHONEMES) : ps);
            }
        }
        return out;
    }

    String phonemize(String text, String unk, boolean useFallback, Consumer<List<MToken>> tagger) {
        StringBuilder b = new StringBuilder();
        for (MToken t : process(text, unk, useFallback, tagger)) {
            b.append(t.phonemes == null ? unk : t.phonemes).append(t.whitespace);
        }
        return b.toString();
    }

    /** misaki's G2P.__call__; the returned tokens carry final phonemes (null = unknown) and whitespace. */
    List<MToken> process(String text, String unk, boolean useFallback, Consumer<List<MToken>> tagger) {
        List<Feature> features = new ArrayList<>();
        String clean = preprocess(text, features);
        List<MToken> tokens = tokenizer.tokenize(clean);
        tagger.accept(tokens);
        applyFeatures(tokens, features);
        tokens = foldLeft(tokens, unk);
        List<Object> words = retokenize(tokens);
        Lexicon.Context ctx = Lexicon.Context.START;
        for (int i = words.size() - 1; i >= 0; i--) {
            Object w = words.get(i);
            if (w instanceof MToken tk) {
                if (tk.phonemes == null) tk.phonemes = resolveSingle(tk, ctx, useFallback);
                ctx = nextContext(ctx, tk.phonemes, tk);
            } else {
                @SuppressWarnings("unchecked")
                List<MToken> group = (List<MToken>) w;
                ctx = resolveGroup(group, ctx, useFallback);
            }
        }
        List<MToken> out = new ArrayList<>(words.size());
        for (Object w : words) {
            @SuppressWarnings("unchecked")
            MToken tk = w instanceof MToken t ? t : MToken.merge((List<MToken>) w, unk);
            if (tk.phonemes != null) tk.phonemes = tk.phonemes.replace('ɾ', 'T').replace('ʔ', 't');
            out.add(tk);
        }
        return out;
    }

    private String resolveSingle(MToken tk, Lexicon.Context ctx, boolean useFallback) {
        // chat shorthand ("b4") and ALL-CAPS chat words misaki would spell ("HEHE") read better from the chat tables
        if (useFallback && fallback.overrides(tk.text)) return fallback.phonemize(tk, ctx);
        String ps = lexicon.apply(tk, ctx);
        if (ps == null && useFallback && LetterToSound.wants(tk.text)) ps = fallback.phonemize(tk, ctx);
        return ps;
    }

    /**
     * Longest known span first, right to left, as misaki resolves words that spaCy split ("do"+"n't"). Parts
     * still unknown afterwards go to {@link LetterToSound}: the whole written word if it is a plain word
     * ("Steve'll"), otherwise just the unknown parts ("Shap" in "Shap1n", "Sparklez" in "CaptainSparklez").
     */
    private Lexicon.Context resolveGroup(List<MToken> w, Lexicon.Context ctx, boolean useFallback) {
        if (useFallback && fallback.overrides(MToken.merge(w, null).text)) return speakWhole(w, ctx);
        int left = 0, right = w.size();
        List<MToken> unknown = new ArrayList<>();
        while (left < right) {
            // no lexicon entry spans more sub-tokens than this; skipping longer spans keeps symbol soup linear
            if (right - left > MAX_SPAN) left = right - MAX_SPAN;
            boolean preset = false;
            for (int k = left; k < right; k++) preset |= w.get(k).alias != null || w.get(k).phonemes != null;
            MToken tk = preset ? null : MToken.merge(w.subList(left, right), null);
            String ps = tk == null ? null : lexicon.apply(tk, ctx);
            if (ps != null) {
                w.get(left).phonemes = ps;
                for (int k = left + 1; k < right; k++) w.get(k).phonemes = "";
                ctx = nextContext(ctx, ps, tk);
                right = left;
                left = 0;
            } else if (left + 1 < right) {
                left++;
            } else {
                right--;
                MToken last = w.get(right);
                if (last.phonemes == null) {
                    if (allIn(last.text, SUBTOKEN_JUNKS)) {
                        last.phonemes = "";
                    } else if (useFallback && LetterToSound.wants(last.text)) {
                        unknown.add(last);
                    }
                }
                left = 0;
            }
        }
        if (unknown.isEmpty()) {
            resolveTokens(w);
            return ctx;
        }
        if (LetterToSound.plainWord(MToken.merge(w, null).text)) return speakWhole(w, ctx);
        for (MToken part : unknown) part.phonemes = fallback.phonemize(part, ctx);
        resolveTokens(w);
        return nextContext(ctx, w.get(0).phonemes, w.get(0));
    }

    /** The fallback's reading of a whole group, carried by its first token. */
    private Lexicon.Context speakWhole(List<MToken> w, Lexicon.Context ctx) {
        MToken whole = MToken.merge(w, null);
        w.get(0).phonemes = fallback.phonemize(whole, ctx);
        for (int k = 1; k < w.size(); k++) w.get(k).phonemes = "";
        return nextContext(ctx, w.get(0).phonemes, whole);
    }

    /** misaki's resolve_tokens: spacing and stress balance between the parts of one written word. */
    private static void resolveTokens(List<MToken> tokens) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            text.append(tokens.get(i).text);
            if (i + 1 < tokens.size()) text.append(tokens.get(i).whitespace);
        }
        String t = text.toString();
        boolean prespace = t.indexOf(' ') >= 0 || t.indexOf('/') >= 0 || charClasses(t) > 1;
        for (int i = 0; i < tokens.size(); i++) {
            MToken tk = tokens.get(i);
            if (tk.phonemes == null) {
                if (i == tokens.size() - 1 && tk.text.length() == 1 && Phonemes.NON_QUOTE_PUNCTS.indexOf(tk.text.charAt(0)) >= 0) {
                    tk.phonemes = tk.text;
                } else if (allIn(tk.text, SUBTOKEN_JUNKS)) {
                    tk.phonemes = "";
                }
            } else if (i > 0) {
                tk.prespace = prespace;
            }
        }
        if (prespace) return;
        List<int[]> indices = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String ps = tokens.get(i).phonemes;
            if (ps != null && !ps.isEmpty()) {
                indices.add(new int[] {ps.indexOf(Phonemes.PRIMARY) >= 0 ? 1 : 0, Phonemes.stressWeight(ps), i});
            }
        }
        if (indices.size() == 2 && tokens.get(indices.get(0)[2]).text.length() == 1) {
            MToken second = tokens.get(indices.get(1)[2]);
            second.phonemes = Phonemes.applyStress(second.phonemes, -0.5);
            return;
        }
        int primaries = 0;
        for (int[] x : indices) primaries += x[0];
        if (indices.size() < 2 || primaries <= (indices.size() + 1) / 2) return;
        indices.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] != b[1] ? a[1] - b[1] : a[2] - b[2]);
        for (int[] x : indices.subList(0, indices.size() / 2)) {
            MToken tk = tokens.get(x[2]);
            tk.phonemes = Phonemes.applyStress(tk.phonemes, -0.5);
        }
    }

    /** Letters, digits and other characters count as three classes; a word mixing them is spoken in parts. */
    private static int charClasses(String text) {
        int mask = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SUBTOKEN_JUNKS.indexOf(c) >= 0) continue;
            mask |= Character.isLetter(c) ? 1 : Lexicon.isAsciiDigit(c) ? 2 : 4;
        }
        return Integer.bitCount(mask);
    }

    static Lexicon.Context nextContext(Lexicon.Context ctx, String ps, MToken token) {
        Boolean vowel = ctx.futureVowel();
        if (ps != null) {
            for (int i = 0; i < ps.length(); i++) {
                char c = ps.charAt(i);
                boolean punct = Phonemes.NON_QUOTE_PUNCTS.indexOf(c) >= 0;
                if (punct || Phonemes.isVowel(c) || Phonemes.CONSONANTS.indexOf(c) >= 0) {
                    vowel = punct ? null : Phonemes.isVowel(c);
                    break;
                }
            }
        }
        String t = token.text;
        boolean to = t.equals("to") || t.equals("To") || (t.equals("TO") && (token.tag.equals("TO") || token.tag.equals("IN")));
        return new Lexicon.Context(vowel, to);
    }

    // ---- misaki preprocessing: [text](/phonemes/), [text](stress) and [text](#flags#) markup ----

    private record Feature(int start, int end, Object value) {}

    private static String preprocess(String text, List<Feature> features) {
        text = lstrip(text);
        StringBuilder result = new StringBuilder();
        Matcher m = LINK.matcher(text);
        int lastEnd = 0;
        while (m.find()) {
            result.append(text, lastEnd, m.start());
            Object f = feature(m.group(2));
            int start = result.length();
            result.append(m.group(1));
            if (f != null) features.add(new Feature(start, result.length(), f));
            lastEnd = m.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
    }

    private static Object feature(String f) {
        String digits = f.startsWith("-") || f.startsWith("+") ? f.substring(1) : f;
        if (Lexicon.isDigits(digits)) {
            try {
                return (double) Integer.parseInt(f.startsWith("+") ? digits : f);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (f.equals("0.5") || f.equals("+0.5")) return 0.5;
        if (f.equals("-0.5")) return -0.5;
        if (f.length() > 1 && f.charAt(0) == '/' && f.charAt(f.length() - 1) == '/') return "/" + rstrip(f.substring(1), '/');
        if (f.length() > 1 && f.charAt(0) == '#' && f.charAt(f.length() - 1) == '#') return "#" + rstrip(f.substring(1), '#');
        return null;
    }

    private static void applyFeatures(List<MToken> tokens, List<Feature> features) {
        if (features.isEmpty()) return;
        int[] starts = new int[tokens.size()];
        int offset = 0;
        for (int j = 0; j < tokens.size(); j++) {
            starts[j] = offset;
            offset += tokens.get(j).text.length() + tokens.get(j).whitespace.length();
        }
        for (Feature f : features) {
            int i = 0;
            for (int j = 0; j < tokens.size(); j++) {
                MToken tk = tokens.get(j);
                if (starts[j] < f.start() || starts[j] + tk.text.length() > f.end() || tk.text.isBlank()) continue;
                if (f.value() instanceof Double stress) {
                    tk.stress = stress;
                } else if (((String) f.value()).startsWith("/")) {
                    tk.isHead = i == 0;
                    tk.phonemes = i == 0 ? ((String) f.value()).substring(1) : "";
                } else {
                    tk.numFlags = ((String) f.value()).substring(1);
                }
                i++;
            }
        }
    }

    private static List<MToken> foldLeft(List<MToken> tokens, String unk) {
        List<MToken> result = new ArrayList<>(tokens.size());
        for (MToken tk : tokens) {
            if (!result.isEmpty() && !tk.isHead) tk = MToken.merge(List.of(result.remove(result.size() - 1), tk), unk);
            result.add(tk);
        }
        return result;
    }

    /** misaki's retokenize: sub-token split, punctuation phonemes, currency, and grouping of space-less runs. */
    private static List<Object> retokenize(List<MToken> tokens) {
        List<Object> words = new ArrayList<>();
        String currency = null;
        for (int i = 0; i < tokens.size(); i++) {
            MToken token = tokens.get(i);
            List<MToken> tks = new ArrayList<>();
            if (token.alias == null && token.phonemes == null) {
                Matcher m = SUBTOKEN.matcher(token.text);
                while (m.find()) tks.add(token.copyAs(m.group()));
            } else {
                tks.add(token);
            }
            if (tks.isEmpty()) continue;
            tks.get(tks.size() - 1).whitespace = token.whitespace;
            for (int j = 0; j < tks.size(); j++) {
                MToken tk = tks.get(j);
                if (tk.alias != null || tk.phonemes != null) {
                    // already decided
                } else if (tk.tag.equals("$") && isCurrencySymbol(tk.text)) {
                    currency = tk.text;
                    tk.phonemes = "";
                } else if (tk.tag.equals(":") && (tk.text.equals("-") || tk.text.equals("–"))) {
                    tk.phonemes = "—";
                } else if (PUNCT_TAGS.contains(" " + tk.tag + " ") && !allAsciiLetters(tk.text)) {
                    tk.phonemes = punctPhonemes(tk);
                } else if (currency != null) {
                    if (!tk.tag.equals("CD")) {
                        currency = null;
                    } else if (j + 1 == tks.size() && (i + 1 == tokens.size() || !tokens.get(i + 1).tag.equals("CD"))) {
                        tk.currency = currency;
                    }
                } else if (j > 0 && j < tks.size() - 1 && tk.text.equals("2")) {
                    String prev = tks.get(j - 1).text, next = tks.get(j + 1).text;
                    if (Lexicon.isAlpha(prev.charAt(prev.length() - 1) + next.substring(0, 1))) tk.alias = "to";
                }
                if (tk.alias != null || tk.phonemes != null) {
                    words.add(tk);
                } else if (!words.isEmpty() && words.get(words.size() - 1) instanceof List<?> group
                        && ((MToken) group.get(group.size() - 1)).whitespace.isEmpty()) {
                    tk.isHead = false;
                    @SuppressWarnings("unchecked")
                    List<MToken> g = (List<MToken>) group;
                    g.add(tk);
                } else if (tk.whitespace.isEmpty()) {
                    List<MToken> g = new ArrayList<>();
                    g.add(tk);
                    words.add(g);
                } else {
                    words.add(tk);
                }
            }
        }
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i) instanceof List<?> g && g.size() == 1) words.set(i, g.get(0));
        }
        return words;
    }

    private static String punctPhonemes(MToken tk) {
        switch (tk.tag) {
            case "-LRB-": return "(";
            case "-RRB-": return ")";
            case "``": return "“";
            case "\"\"", "''": return "”";
            default:
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < tk.text.length(); i++) {
                    char c = tk.text.charAt(i);
                    if (Phonemes.PUNCTS.indexOf(c) >= 0) b.append(c);
                }
                return b.toString();
        }
    }

    private static boolean isCurrencySymbol(String s) {
        return s.equals("$") || s.equals("£") || s.equals("€");
    }

    private static boolean allAsciiLetters(String s) {
        for (int i = 0; i < s.length(); i++) {
            int c = Character.toLowerCase(s.charAt(i));
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }

    private static boolean allIn(String s, String chars) {
        for (int i = 0; i < s.length(); i++) if (chars.indexOf(s.charAt(i)) < 0) return false;
        return true;
    }

    private static String lstrip(String s) {
        int i = 0;
        while (i < s.length() && SpacyTokenizer.isSpace(s.charAt(i))) i++;
        return s.substring(i);
    }

    private static String rstrip(String s, char c) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == c) end--;
        return s.substring(0, end);
    }
}
