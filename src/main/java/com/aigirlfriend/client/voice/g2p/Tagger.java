package com.aigirlfriend.client.voice.g2p;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Rule-based stand-in for spaCy's statistical tagger (Penn Treebank tags). misaki reads tags in only a few places:
 * punctuation and currency tokens, the words "a", "I", "am", "in", "by", "used" and "that", ALL-CAPS proper
 * names, and the few hundred lexicon words whose sound depends on noun, verb or adjective use ("use", "read",
 * "live", "close"). Those decisions get real rules. An open-class word without clear evidence gets "XX", which
 * picks the lexicon's default pronunciation, as it does for most words anyway.
 */
final class Tagger implements Consumer<List<MToken>> {
    private static final Map<String, String> CLOSED = new HashMap<>();
    /** Short words that stay words when shouted; other ALL-CAPS words of up to three letters are spelled ("ID", "TV"). */
    private static final Set<String> COMMON_SHORT = Set.of("get", "got", "had", "has", "did", "own", "way", "yes", "ok", "bad",
            "big", "boy", "day", "eat", "end", "far", "few", "fun", "god", "hot", "joy", "lot", "man", "new", "old", "one",
            "run", "sad", "say", "see", "set", "sit", "six", "sky", "son", "sun", "ten", "top", "try", "two", "use", "war",
            "win", "yet", "aww", "wow", "omg", "lol", "oh", "ah", "hi", "hey", "huh", "go", "do", "no", "so", "up", "hug",
            "kiss", "cat", "dog", "pig", "cow", "bed", "car", "box", "air", "fly", "cry", "die", "sea", "ice", "eye");
    private static final Set<String> BE = Set.of("be", "am", "is", "are", "was", "were", "been", "being", "'m", "'re", "'s");
    private static final Set<String> VERB_TRIGGERS = Set.of("to", "can", "could", "will", "would", "shall", "should", "may",
            "might", "must", "'ll", "ll", "ca", "wo", "sha", "do", "does", "did", "n't", "nt", "let", "please", "let's");
    private static final Set<String> DETERMINERS = Set.of("a", "an", "the", "this", "that", "these", "those", "my", "your",
            "his", "her", "its", "our", "their", "some", "any", "no", "every", "each", "another", "whose", "'s");
    private static final Set<String> DEGREE = Set.of("so", "very", "too", "pretty", "quite", "more", "most", "less", "as",
            "really", "super", "extra");
    /** Verbs that link to an adjective ("stay close", "it's perfect"). */
    private static final Set<String> LINKING = Set.of("be", "am", "is", "are", "was", "were", "been", "being", "'m", "'re",
            "'s", "stay", "stays", "stayed", "staying", "feel", "feels", "felt", "look", "looks", "looked", "seem", "seems",
            "seemed", "become", "becomes", "became", "get", "gets", "got", "getting", "grow", "grows", "grew", "growing",
            "keep", "keeps", "kept", "come", "came", "sounds", "sound");
    private static final Set<String> SKIPPABLE = Set.of("really", "just", "also", "always", "never", "still", "even",
            "actually", "totally", "kinda", "sorta", "only", "already", "probably", "definitely", "maybe", "ever",
            "often", "sometimes", "usually", "finally", "not", "all", "both", "truly", "seriously", "honestly");
    private static final Set<String> OBJECT_STARTERS = Set.of("me", "us", "it", "him", "her", "them", "the", "a", "an",
            "my", "your", "his", "our", "their", "this", "that", "these", "those", "up", "down", "out", "off", "some", "yourself");
    /** Words after which "that" introduces a clause ("I think that ...", "so glad that ..."). */
    private static final Set<String> CLAUSE_TAKERS = Set.of("think", "thought", "know", "knew", "known", "say", "said",
            "says", "hope", "hoped", "wish", "believe", "feel", "felt", "realize", "realized", "realise", "realised",
            "mean", "meant", "guess", "sure", "glad", "happy", "true", "see", "saw", "seen", "hear", "heard", "notice",
            "noticed", "promise", "promised", "suppose", "tell", "told", "show", "showed", "remember", "forget", "forgot",
            "understand", "imagine", "bet", "swear", "admit", "agree", "afraid", "proud", "sorry", "weird", "funny",
            "strange", "possible", "so", "such", "now", "given", "except", "lucky", "amazing", "crazy", "obvious", "fact",
            "news", "idea", "means", "thankful", "grateful", "relieved", "surprised", "shocked", "worried", "scared");
    /** Words that typically take a relative clause ("the ones that glow"). */
    private static final Set<String> ANTECEDENTS = Set.of("one", "ones", "thing", "things", "something", "anything",
            "everything", "nothing", "someone", "anyone", "everyone", "somebody", "anybody", "everybody", "place", "way",
            "kind", "sort", "person", "people", "stuff");
    private static final Set<String> QUANTITY = Set.of("many", "much", "far", "long", "often", "badly", "bad");
    private static final Set<String> ADVERB_AFTER_THAT = Set.of("ever", "really", "just", "always", "never", "also",
            "actually", "still", "only", "once", "even", "can", "could", "will", "would", "should", "might", "must", "'ll",
            "'s", "'d", "is", "was", "are", "were", "has", "had", "have", "makes", "made", "keeps", "kept", "got", "gets");
    private static final Set<String> LETTER_NAMERS = Set.of("plan", "option", "grade", "type", "vitamin", "point", "class",
            "part", "section", "team", "group", "level", "size", "row", "letter", "capital", "big", "an", "the", "from");
    private static final Pattern EMOTICON = Pattern.compile("[:;=8]['’-]?[)(\\]\\[DPpOo03/\\\\|*}{><]+|<3+|</3|"
            + "\\^[_.-]*\\^|[xXoO0TQ>-]_+[xXoO0TQ<-]|[xX]D+|[(\\[][:;=]|[)\\]][:;=]|[oO0]\\.[oO0]|[vV][._][vV]|>[.:]<");

    static {
        tags("DT", "a an the this that these those each every some any no another all both either neither");
        tags("PRP", "i you he she it we they me him us them myself yourself himself herself itself ourselves "
                + "yourselves themselves y'all");
        tags("PRP$", "my your his its our their");
        tags("IN", "in on at by for with from of about into onto over under through after before around near like "
                + "since until till than because if while as upon within without across along against among behind "
                + "below beneath beside besides beyond during except inside outside past per toward towards via "
                + "whether though although unless vs vs. versus");
        tags("TO", "to");
        tags("MD", "can could will would shall should may might must 'll 'd ca wo sha");
        tags("CC", "and or but nor plus");
        tags("WDT", "which whatever whichever");
        tags("WP", "who whom what whoever");
        tags("WP$", "whose");
        tags("WRB", "when where why how wherever whenever");
        tags("RB", "not n't nt very really so too just also never always here now then again already still even only "
                + "quite maybe perhaps actually almost soon later once ever often sometimes rather pretty totally "
                + "definitely probably usually finally yet anyway instead together away back forward home tonight "
                + "today tomorrow yesterday please else");
        tags("UH", "oh hi hello hey yes yeah yep nope wow um uh hmm hm aww aw ooh oops ah ahh huh whoa yay okay ok "
                + "hehe haha thanks bye mhm eh welp ugh");
        tags("VB", "be");
        tags("VBP", "am are 're 'm 've");
        tags("VBZ", "is 's has does");
        tags("VBD", "was were did had");
        tags("VBN", "been");
        tags("VBG", "being");
        tags("EX", "there");
    }

    /** Whether a lower-case word is in the lexicon; ALL-CAPS words that are not are treated as acronyms (NNP). */
    private final Predicate<String> isWord;

    Tagger(Predicate<String> isWord) {
        this.isWord = isWord;
    }

    private static void tags(String tag, String words) {
        for (String w : words.split(" ")) CLOSED.put(w, tag);
    }

    @Override
    public void accept(List<MToken> tokens) {
        for (int i = 0; i < tokens.size(); i++) tokens.get(i).tag = initialTag(tokens, i);
        for (int i = 0; i < tokens.size(); i++) {
            MToken t = tokens.get(i);
            if (t.tag.equals("XX")) t.tag = openClassTag(tokens, i, Lexicon.lower(t.text));
        }
        for (int i = 0; i < tokens.size(); i++) {
            MToken t = tokens.get(i);
            switch (Lexicon.lower(t.text)) {
                case "that" -> t.tag = thatTag(tokens, i);
                case "used" -> t.tag = usedTag(tokens, i);
                case "am" -> t.tag = Tagger.isNumber(word(tokens, i - 1)) ? "NN" : "VBP";
                case "a" -> {
                    // the letter, not the article: "plan A", "a/b", "a—b"
                    boolean letter = Set.of("/", "—", "–").contains(word(tokens, i + 1))
                            || t.text.equals("A") && LETTER_NAMERS.contains(Lexicon.lower(word(tokens, i - 1)));
                    t.tag = letter ? "NNP" : "DT";
                }
                default -> { }
            }
        }
    }

    private String initialTag(List<MToken> tokens, int i) {
        String s = tokens.get(i).text;
        if (s.isBlank()) return "_SP";
        if (isNumber(s)) return "CD";
        if (s.equals("US$") || s.equals("C$") || s.equals("A$")) return "$";
        if (EMOTICON.matcher(s).matches()) return ".";
        if (!hasLetterOrDigit(s)) return punctTag(tokens, i);
        if (isUrl(s)) return "ADD";
        if (s.equals("I")) return "PRP";
        String lower = Lexicon.lower(s);
        boolean allCaps = s.length() > 1 && s.equals(Lexicon.upper(s)) && !s.equals(lower);
        if (allCaps && !CLOSED.containsKey(lower) && (!isWord.test(lower) || s.length() <= 3 && !COMMON_SHORT.contains(lower))) {
            return "NNP";
        }
        String closed = CLOSED.get(lower);
        if (closed != null && !lower.equals("i")) {
            if (lower.equals("there") && !BE.contains(Lexicon.lower(word(tokens, i + 1)))) return "RB";
            if (lower.equals("her")) return isWordToken(word(tokens, i + 1)) && !CLOSED.containsKey(Lexicon.lower(word(tokens, i + 1))) ? "PRP$" : "PRP";
            if (lower.equals("like") && isSubject(tokens, i - 1)) return "VBP";
            return closed;
        }
        if (!allCaps && isCapitalized(s) && !sentenceStart(tokens, i)) return "NNP";
        return "XX";
    }

    /**
     * Noun, verb or adjective from the words before: determiners make nouns ("a use"), "to", modals and
     * subjects make verbs ("could use"), degree adverbs and linking verbs make adjectives ("stay close").
     */
    private String openClassTag(List<MToken> tokens, int i, String lower) {
        String prev = Lexicon.lower(word(tokens, i - 1));
        String next = Lexicon.lower(word(tokens, i + 1));
        int p = i - 1;
        while (p >= 0 && SKIPPABLE.contains(Lexicon.lower(tokens.get(p).text))) p--;
        String content = Lexicon.lower(word(tokens, p));
        boolean letUs = content.equals("'s") && Lexicon.lower(word(tokens, p - 1)).equals("let");
        boolean subject = isSubject(tokens, p);
        // "Can you read ...?": after an inverted subject the verb is a bare infinitive
        boolean inverted = subject && VERB_TRIGGERS.contains(Lexicon.lower(word(tokens, p - 1)))
                || subject && Set.of("have", "has", "had", "do", "does", "did").contains(Lexicon.lower(word(tokens, p - 1)));
        String prevTag = i > 0 ? tokens.get(i - 1).tag : "";
        boolean nounBefore = prevTag.equals("NN") || prevTag.equals("NNS") || prevTag.equals("NNP");
        switch (lower) {
            case "read", "reread" -> {
                if (letUs || inverted) return "VB";
                if (BE.contains(content) || Set.of("have", "has", "had", "'ve", "having").contains(content)) return "VBN";
                if (subject || nounBefore) return "VBD";
                return VERB_TRIGGERS.contains(content) || sentenceStart(tokens, i) ? "VB" : "XX";
            }
            case "lives" -> {
                boolean finite = subject || nounBefore || Set.of("what", "who", "which", "that", "where").contains(content);
                return finite && !DETERMINERS.contains(prev) ? "VBZ" : "NNS";
            }
            case "tears", "winds", "bows" -> {
                return OBJECT_STARTERS.contains(next) || Set.of("open", "apart", "through", "into", "at").contains(next) ? "VBZ" : "NNS";
            }
            case "nearby" -> {
                return DETERMINERS.contains(prev) && isOpenWord(next) ? "JJ" : "RB";
            }
            default -> { }
        }
        boolean plural = lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us") && !lower.endsWith("is");
        boolean afterDeterminer = DETERMINERS.contains(prev) && !letUs;
        boolean afterModifier = (prevTag.equals("JJ") || prevTag.equals("JJR") || prevTag.equals("JJS"))
                && DETERMINERS.contains(Lexicon.lower(word(tokens, i - 2)));
        if (DEGREE.contains(prev) || Set.of("little", "bit", "lot", "tad", "way", "even", "far").contains(prev) && lower.endsWith("er")) {
            if (plural && Set.of("more", "most", "less").contains(prev)) return "NNS";
            return lower.endsWith("er") ? "JJR" : "JJ";
        }
        if (afterDeterminer || afterModifier) {
            // a word between a determiner and another content word modifies it ("a close call")
            if (!plural && isOpenWord(next) && !next.endsWith("ing") && !afterModifier) return "JJ";
            return plural ? "NNS" : "NN";
        }
        if (VERB_TRIGGERS.contains(content) || letUs || subject
                || (sentenceStart(tokens, i) && OBJECT_STARTERS.contains(next))) {
            if (inverted || !subject) return "VB";
            if (lower.endsWith("ed")) return "VBD";
            if (plural && !Set.of("i", "you", "we", "they").contains(content)) return "VBZ";
            return "VBP";
        }
        if (LINKING.contains(content)) return lower.endsWith("er") ? "JJR" : "JJ";
        if (sentenceStart(tokens, i) && next.equals(",")) return "RB";
        if (lower.endsWith("ly")) return "RB";
        return "XX";
    }

    /** A content word: letters, not a function word. */
    private static boolean isOpenWord(String lower) {
        return !lower.isEmpty() && Character.isLetter(lower.codePointAt(0)) && !CLOSED.containsKey(lower)
                && !lower.startsWith("'");
    }

    /** "that" as a demonstrative (DT, stressed) versus a conjunction, relative pronoun or degree adverb. */
    private static String thatTag(List<MToken> tokens, int i) {
        String prev = Lexicon.lower(word(tokens, i - 1)), next = Lexicon.lower(word(tokens, i + 1));
        String after = Lexicon.lower(word(tokens, i + 2));
        if (!isWordToken(word(tokens, i + 1))) return "DT";
        if (QUANTITY.contains(next)) return "RB";
        if (CLAUSE_TAKERS.contains(prev)) {
            boolean clause = Set.of("i", "you", "we", "they", "he", "she", "it", "there").contains(next)
                    || DETERMINERS.contains(next) && !next.equals("'s")
                    || i + 1 < tokens.size() && tokens.get(i + 1).tag.equals("NNP")
                    || isWordToken(after) && (ADVERB_AFTER_THAT.contains(after) || after.endsWith("ed")
                        || next.endsWith("s") && !next.endsWith("ss"));
            return clause && !ADVERB_AFTER_THAT.contains(next) ? "IN" : "DT";
        }
        boolean nounBefore = i > 0 && isWordToken(word(tokens, i - 1)) && !CLOSED.containsKey(prev)
                && !Set.of("VB", "VBP", "VBD", "VBZ", "VBN", "VBG", "MD", "RB", "UH", "JJ").contains(tokens.get(i - 1).tag);
        if (nounBefore && (ADVERB_AFTER_THAT.contains(next) || Set.of("i", "you", "we", "they", "he", "she").contains(next))) {
            return "WDT";
        }
        boolean antecedent = ANTECEDENTS.contains(prev) || nounBefore && prev.endsWith("s") && !prev.endsWith("ss");
        if (antecedent && isOpenWord(next) && !DETERMINERS.contains(next)) return "WDT";
        if ((prev.equals("not") || prev.equals("n't")) && !isWordToken(after)) return "RB";
        return "DT";
    }

    private String usedTag(List<MToken> tokens, int i) {
        String prev = Lexicon.lower(word(tokens, i - 1));
        if (!Lexicon.lower(word(tokens, i + 1)).equals("to")) return BE.contains(prev) ? "VBN" : "VBD";
        if (BE.contains(prev) || prev.equals("get") || prev.equals("got") || prev.equals("getting")) {
            String after = Lexicon.lower(word(tokens, i + 2));
            boolean objectFollows = OBJECT_STARTERS.contains(after) || after.equals("being") || after.endsWith("ing");
            return objectFollows ? "JJ" : "VBN";
        }
        return "VBD";
    }

    private static String punctTag(List<MToken> tokens, int i) {
        String s = tokens.get(i).text;
        switch (s) {
            case ",": return ",";
            case ":", ";", "--", "---", "—", "–", "——", "...", "..", "…": return ":";
            case "(", "[", "{": return "-LRB-";
            case ")", "]", "}": return "-RRB-";
            case "“", "‘", "``": return "``";
            case "”", "’", "''": return "''";
            case "$", "£", "€", "#", "¥": return "$";
            case "%": return "NN";
            case "&", "+": return "CC";
            case "/": return "SYM";
            case "@": return "IN";
            case "-": return tokens.get(i).whitespace.isEmpty() && i > 0 && tokens.get(i - 1).whitespace.isEmpty() ? "HYPH" : ":";
            case "\"": return opensQuote(tokens, i) ? "``" : "''";
            case "'": return apostropheTag(tokens, i);
            default: break;
        }
        if (s.chars().allMatch(c -> c == '.' || c == '!' || c == '?')) return s.chars().allMatch(c -> c == '.') && s.length() > 1 ? ":" : ".";
        if (s.chars().allMatch(c -> "*~^`|\\_=<>".indexOf(c) >= 0)) return "NFP";
        return "NN";
    }

    /** A lone apostrophe: a possessive after a plural ("dogs'"), a quote mark, or an elision glued to a word ("'til"). */
    private static String apostropheTag(List<MToken> tokens, int i) {
        boolean glued = i > 0 && tokens.get(i - 1).whitespace.isEmpty() && isWordToken(tokens.get(i - 1).text);
        if (glued && Lexicon.lower(tokens.get(i - 1).text).endsWith("s")) return "POS";
        if (!opensQuote(tokens, i)) return "''";
        for (int k = i + 1; k < tokens.size(); k++) if (tokens.get(k).text.equals("'")) return "``";
        return "XX";
    }

    /** A straight quote opens when it starts the text or follows a space, and is followed by text. */
    private static boolean opensQuote(List<MToken> tokens, int i) {
        if (i == 0) return true;
        boolean afterSpace = !tokens.get(i - 1).whitespace.isEmpty() || tokens.get(i - 1).text.isBlank();
        return afterSpace && i + 1 < tokens.size() && tokens.get(i).whitespace.isEmpty();
    }

    // ---- token helpers ----

    /** Text of the token at {@code i}, or "" outside the line; whitespace tokens count as tokens, like spaCy's. */
    private static String word(List<MToken> tokens, int i) {
        return i >= 0 && i < tokens.size() ? tokens.get(i).text : "";
    }

    /** I/we/they/he/she, or you/it where a subject can stand (after the start, an auxiliary or a conjunction). */
    private static boolean isSubject(List<MToken> tokens, int i) {
        String w = Lexicon.lower(word(tokens, i));
        if (Set.of("i", "we", "they", "he", "she", "who").contains(w)) return true;
        if (!w.equals("you") && !w.equals("it")) return false;
        String before = Lexicon.lower(word(tokens, i - 1));
        return sentenceStart(tokens, i) || before.equals(",") || BE.contains(before) || VERB_TRIGGERS.contains(before)
                || Set.of("and", "but", "or", "if", "when", "because", "so", "that", "what", "where", "how", "why", "do",
                        "did", "does", "have", "has", "had").contains(before);
    }

    private static boolean sentenceStart(List<MToken> tokens, int i) {
        for (int k = i - 1; k >= 0; k--) {
            String s = tokens.get(k).text;
            if (s.isBlank() || s.equals("\"") || s.equals("“") || s.equals("'") || s.equals("(") || s.equals("*")) continue;
            return !s.isEmpty() && s.chars().allMatch(c -> c == '.' || c == '!' || c == '?' || c == '…' || c == ':');
        }
        return true;
    }

    /** A word, number or clitic ("'s", "n't"), as opposed to punctuation and space. */
    private static boolean isWordToken(String s) {
        if (s.isEmpty()) return false;
        if (Character.isLetterOrDigit(s.codePointAt(0))) return true;
        return s.length() > 1 && (s.charAt(0) == '\'' || s.charAt(0) == '’') && Character.isLetter(s.charAt(1));
    }

    private static boolean isCapitalized(String s) {
        return Character.isUpperCase(s.codePointAt(0)) && s.length() > 1 && !s.equals(Lexicon.upper(s));
    }

    static boolean isNumber(String s) {
        if (s.isEmpty()) return false;
        boolean digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Lexicon.isAsciiDigit(c)) digit = true;
            else if (c != ',' && c != '.' && !(i == 0 && (c == '-' || c == '+'))) return false;
        }
        return digit;
    }

    private static boolean hasLetterOrDigit(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isUrl(String s) {
        return s.contains("://") || s.startsWith("www.") || s.indexOf('@') > 0 && s.indexOf('.', s.indexOf('@')) > 0;
    }
}
