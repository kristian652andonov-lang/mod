package com.aigirlfriend.client.voice.g2p;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pronounces what misaki's lexicon cannot, so names like "Shap1n" or "Kristian" are spoken instead of skipped.
 * In order: a small table of chat words (interjections, acronyms, Minecraft terms), accent folding ("café"),
 * stretched words ("Sooooo"), clitics ("Steve'll"), suffixes on known stems ("comfiest", "bookshelves"),
 * compounds of two known words ("redstone"), letter-by-letter spelling for vowel-less acronyms, and finally
 * English letter-to-sound rules with stress placement and vowel reduction. The rules were written for this
 * mod from general English spelling patterns; no code or data comes from espeak or other GPL sources.
 * Output uses misaki's phoneme alphabet (American or British).
 */
final class LetterToSound {
    private static final String V = "[aeiouy]";
    private static final String C = "[bcdfghjklmnpqrstvwxz]";
    /** A silent final e, possibly with an inflection: "made", "makes", "maker", "lately". */
    private static final String MAGIC_E = "e(#|s#|d#|r#|rs#|ly#|ful#|ness#|less#|ment#|ments#)";
    private static final Pattern RUNS = Pattern.compile("[\\p{L}]+(?:['’][\\p{L}]+)*|[0-9]+(?:[.,][0-9]+)*|.", Pattern.DOTALL);
    private static final Pattern PLAIN_WORD = Pattern.compile("\\p{L}+(?:['’]\\p{L}+)*");
    private static final Pattern STRETCH = Pattern.compile("(\\p{L})\\1{2,}");
    /** Consonant clusters that can begin an English word; anything else at the start reads as an acronym ("dna"). */
    private static final Pattern ONSET = Pattern.compile("([aeiouy]|(bl|br|ch|cl|cr|dr|dw|fl|fr|gh|gl|gn|gr|kl|kn|kr|ph|pl"
            + "|pr|ps|qu|sc|sch|scr|sh|shr|sk|sl|sm|sn|sp|spl|spr|squ|st|str|sw|th|thr|tr|tw|vl|wh|wr|[bcdfghjklmnpqrstvwxz])"
            + "[aeiouy]).*");
    /** Consonant clusters that can end an English word. */
    private static final Pattern CODA = Pattern.compile(".*([aeiouy]|[aeiouy](b|bs|ch|ck|cks|ct|cht|d|ds|f|ff|ft|g|gs|ght|h|k"
            + "|ks|l|lch|ld|lf|lk|ll|lm|lp|ls|lt|lth|m|mb|mn|mp|mps|ms|n|nce|nch|nd|nds|ng|ngs|nk|nks|ns|nt|nth|nts|nx|p|ph|ps"
            + "|pt|pth|r|rb|rch|rd|rds|rf|rg|rk|rks|rl|rm|rn|rp|rs|rst|rt|rth|rve|s|sh|sk|sm|sp|ss|st|sts|t|tch|th|ts|tt|v|w"
            + "|wl|wn|ws|x|z|zz))");
    private static final Pattern CLITIC = Pattern.compile("(?i)(.+?)('ll|'d|'s|'ve|'re|'m|n't|s')$");
    private static final Map<Character, List<Rule>> RULES = new HashMap<>();
    /** Chat and game words misaki has no entry for, transcribed for this mod (American phonemes). */
    private static final Map<String, String> CHAT = new HashMap<>();
    /** Chat abbreviations read as the words they stand for. */
    private static final Map<String, String> SAY_AS = new HashMap<>();
    private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]");
    private static final Pattern STRESSED_ENDING = Pattern.compile("(ee|eer|een|ese|ette|ique|oon|aire|esque|eau|eed|eem)$");
    /** Unstressed prefixes: the root after them takes the stress ("abide", "proceed", "unroll"). */
    private static final Pattern PREFIX = Pattern.compile("(a|be|de|re|pre|pro|un|dis|mis|con|com|ex|en|em|in|im|ob|sub|per|non)"
            + "(?=[bcdfghjklmnpqrstvwxz])");
    /** Prefixes that keep a secondary stress ("unroll" ˌʌnɹˈOl). */
    private static final Pattern STRONG_PREFIX = Pattern.compile("(un|non|mis|out|over|under|super)");
    /** Endings that put the stress just before them, with the number of vowel sounds each ending has. */
    private static final Map<String, Integer> PRE_STRESS = new HashMap<>();
    private static final Pattern PRE_STRESS_ENDING = Pattern.compile("(tions?|sions?|cians?|cial|tial|tious|cious|ics?|ical"
            + "|ically|ity|ities|ify|ified|ial|ians?|ious|eous|ual|uous|ium|ias?|iac|itis)$");

    private static final Pattern GRAPH = Pattern.compile("\\[[a-z]+]");

    static {
        for (String e : "tion tions sion sions cian cians cial tial tious cious ic ics".split(" ")) PRE_STRESS.put(e, 1);
        for (String e : "ical ity ities ify ified ial ian ians ious eous ual uous ium ia ias iac itis".split(" ")) PRE_STRESS.put(e, 2);
        PRE_STRESS.put("ically", 3);
    }

    private record Rule(String graph, Pattern left, Pattern right, String out) {}

    static {
        rules(
                // suffixes and endings first, they decide how a word ends
                "[tion]=ʃən", "[tial]=ʃəl", "[tious]=ʃəs", "[ture]=ʧəɹ", "[cial]=ʃəl", "[cian]=ʃən", "[cious]=ʃəs",
                "[ssion]=ʃən", "V[sion]=ʒən", "[sion]=ʃən", "V[sure]=ʒəɹ", "[sure]=ʃəɹ",
                "#C+[able]#=Abᵊl", "[able]#=əbᵊl", "[ible]#=əbᵊl", "[ation]=Aʃən", "[ations]=Aʃənz", "[ous]#=əs",
                "[ism]#=ɪzəm", "[ness]#=nəs", "[less]#=ləs", "[ment]#=mənt", "[ful]#=fᵊl", "[ing]#=ɪŋ", "[ings]#=ɪŋz",
                "C[ly]#=li", "[ity]#=ᵻti", "[ic]#=ɪk", "[ics]#=ɪks", "[ical]#=ɪkᵊl", "C[age]#=ɪʤ", "[ive]#=ɪv",
                "#C+[ian]#=Iən", "[ian]#=iən", "[ia]#=iə", "[ium]#=iəm",
                "C[us]#=əs", "C[um]#=əm", "[ey]#=i", "VC[ie]#=i", "C[le]#=ᵊl", "C[les]#=ᵊlz",
                "(t|d)[ed]#=ᵻd", "V[a-z]*(p|k|f|s|x|ch|sh|c)[ed]#=t", "V[a-z]*C[ed]#=d",
                "(s|z|x|ch|sh|ce|ge)[es]#=ᵻz", "V[a-z]*C[ied]#=Id", "V[a-z]*C[ies]#=iz",
                // a silent e before a suffix: "tenseless", "lovely", "statement"
                "V[a-z]*C[e](less|ness|ful|ment|ments|ly|board|man|men|land|wood|side)#=",
                // a
                "[augh]=ɔ", "[aa]=ɑ", "[au]=ɔ", "[aw]=ɔ", "[air]=ɛɹ", "[ai]=A", "[ay]=A", "[are]#=ɛɹ", "[a]rrV=æ",
                "[ar]=ɑɹ", "[al]k=ɔ", "[all]=ɔl", "w[a]C=ɑ", "[a]C" + MAGIC_E + "=A", "[a]C(le|les)#=A", "[a]tor#=A",
                "#C+[a]#=ɑ", "[a]#=ə", "[a]=æ",
                // e
                "[eau]=O", "[eigh]=A", "c[ei]=i", "[ei]=A", "[ey]=A", "[eer]=ɪɹ", "[ee]=i", "[ear]=ɪɹ", "[ea]=i",
                "[eu]=u", "[ew]=u", "[ere]#=ɪɹ", "[e]rr=ɛ", "[e]rV=ɛ", "[er]=ɜɹ", "[e]C" + MAGIC_E + "=i",
                "#C*[e]#=i", "V[a-z]*[e]#=", "[e]=ɛ",
                // i
                "[igh]=I", "[ie]#=I", "[ie]=i", "[ire]#=Iəɹ", "[ir]=ɜɹ", "[i]C" + MAGIC_E + "=I", "[i]nd#=I",
                "[i]ld=I", "[i]gn=I", "#C+[i](a|o|u)=I", "[i](a|e|o|u)=i", "[i]#=i", "[i]C(a|o|u)=I", "[i]=ɪ",
                // o
                "[ought]=ɔt", "[ough]=O", "[oa]=O", "[oe]#=O", "[oor]=ɔɹ", "[oo]k=ʊ", "[oo]=u", "[our]=ɔɹ",
                "[ou]=W", "[ow]#=O", "[ow]=W", "[oi]=Y", "[oy]=Y", "[ore]#=ɔɹ", "[or]=ɔɹ", "[o]ld=O",
                "[o]ll=O", "[o]C" + MAGIC_E + "=O", "[o]#=O", "[o]V=O", "[o]C(a|o|u|y#)=O", "[o]=ɑ",
                // u
                "[ui]=u", "[ue]#=u", "[ure]#=ʊɹ", "[ur]=ɜɹ", "(b|c|f|h|k|m|p|v)[u]C" + MAGIC_E + "=ju",
                "[u]C" + MAGIC_E + "=u", "(b|c|f|g|h|k|m|p|v)[u]C(a|e|i|o|u)=ju", "[u]C(a|e|i|o|u)=u",
                "[u]V=u", "[u]#=u", "(b|p|f|w)[u](sh|ll)=ʊ", "[u]=ʌ",
                // y
                "#[y]V=j", "V[y](a|e|o|u)=j", "#C+[y]#=I", "[y]#=i", "[y]C" + MAGIC_E + "=I", "[y]C=ɪ", "[y]=i",
                // consonants
                "[bb]=b", "m[b]#=", "[b]=b",
                "[ch]r=k", "s[ch]=k", "[ch]=ʧ", "[ck]=k", "[cc](e|i|y)=ks", "[cc]=k", "[c](e|i|y)=s", "[c]=k",
                "[dd]=d", "[dge]=ʤ", "[d]=d",
                "[ff]=f", "[f]=f",
                "#[gh]=ɡ", "[gh]=", "[gn]#=n", "#[gn]=n", "[gg]=ɡ", "[g](e|i|y)=ʤ", "[g]=ɡ",
                "V[h]#=", "[h]=h",
                "[j]=ʤ",
                "#[kn]=n", "[kk]=k", "[k]=k",
                "[ll]=l", "[l]=l",
                "[mm]=m", "[m]=m",
                "[nn]=n", "[ng]=ŋ", "[n](k|q|x|c(?!e|i|y|h))=ŋ", "[n]=n",
                "[ph]=f", "[pp]=p", "#[ps]=s", "[p]=p",
                "[que]#=k", "[qu]=kw", "[q]=k",
                "[rr]=ɹ", "#[rh]=ɹ", "[r]=ɹ",
                "[sch]=sk", "[sh]=ʃ", "[ss]=s", "V[s](e#|es#|ed#|er|ers|ing|y#|ia|ian|m)=z",
                "(b|d|g|l|m|n|r|v|w|y|e|a|o|i|u)[s]#=z", "[s]=s",
                "[tch]=ʧ", "[th]=θ", "[tt]=t", "[t]=t",
                "[v]=v",
                "#[wr]=ɹ", "[wh]=w", "[w]=w",
                "#[x]=z", "[x]=ks",
                "[zz]=z", "[z]=z");
        chat("hehe=hˈihi", "ehehe=ɛhˈihi", "hihi=hˈihi", "teehee=tˌihˈi", "hehehe=hˈihihi", "hmph=hˈʌmf",
                "mmh=mm", "mhmm=hmm", "uhm=ˈʌm", "erm=ˈɜɹm", "eww=ˈiw", "bleh=blˈɛ", "achoo=ɑʧˈu", "shh=ʃ",
                "tsk=tˈɪsk", "brr=bɹˈɜɹ", "zz=z", "ss=s", "nya=njˈɑ", "uwu=ˈuwu", "owo=ˈOwO", "mwah=mwˈɑ",
                "heh=hˈɛ", "woah=wˈO", "welp=wˈɛlp", "oof=ˈuf", "pfft=pf",
                "lol=ˌɛlˌOˈɛl", "omg=ˌOˌɛmʤˈi", "afk=ˌAˌɛfkˈA", "irl=ˌIˌɑɹˈɛl", "gg=ʤˌiʤˈi", "smh=ˌɛsˌɛmˈAʧ",
                "ikr=ˌIkˌAˈɑɹ", "kk=kˌAkˈA",
                "netherite=nˈɛðəɹˌIt", "illager=ˈɪləʤəɹ", "illagers=ˈɪləʤəɹz", "shulker=ʃˈʌlkəɹ",
                "shulkers=ʃˈʌlkəɹz", "ghast=ɡˈæst", "ghasts=ɡˈæsts", "piglin=pˈɪɡlɪn", "piglins=pˈɪɡlɪnz",
                "hoglin=hˈɑɡlɪn", "hoglins=hˈɑɡlɪnz", "mooshroom=mˈuʃɹˌum", "mooshrooms=mˈuʃɹˌumz",
                "enderman=ˈɛndəɹmˌæn", "endermen=ˈɛndəɹmˌɛn", "minecart=mˈInkˌɑɹt", "minecarts=mˈInkˌɑɹts",
                "zoe=zˈOi", "chloe=klˈOi", "résumé=ɹˈɛzəmˌA", "jalapeño=hˌɑləpˈAnjO", "jalapeños=hˌɑləpˈAnjOz",
                "til=tˈɪl", "c'mere=kəmˈɪɹ", "comfiest=kˈʌmfiəst", "st=sˈAnt", "jr=ʤˈunjəɹ", "sr=sˈinjəɹ", "ms=mˈɪz");
        sayAs("idk=I don't know", "btw=by the way", "tbh=to be honest", "imo=in my opinion", "ngl=not gonna lie",
                "brb=be right back", "rn=right now", "ty=thank you", "np=no problem", "pls=please", "plz=please",
                "thx=thanks", "thnx=thanks", "ur=your", "bc=because", "gr8=great", "l8r=later", "2nite=tonight",
                "4ever=forever", "b4=before");
    }

    private final Lexicon lexicon;

    LetterToSound(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    /** One written word: letters and inner apostrophes, lower-case, Capitalised or ALL-CAPS ("Steve'll", "comfiest"). */
    static boolean plainWord(String text) {
        if (!PLAIN_WORD.matcher(text).matches()) return false;
        String rest = text.substring(1);
        return rest.equals(Lexicon.lower(rest)) || text.equals(Lexicon.upper(text));
    }

    /** Whether a token is worth a guess: it has letters; symbols and emoji stay silent. */
    static boolean wants(String text) {
        for (int i = 0; i < text.length(); i++) if (Character.isLetter(text.charAt(i))) return true;
        return false;
    }

    String phonemize(MToken token, Lexicon.Context ctx) {
        String text = token.text.replace('’', '\'').replace('‘', '\'');
        String table = table(text, ctx);
        if (table != null) return table;
        StringBuilder out = new StringBuilder();
        Matcher m = RUNS.matcher(text);
        while (m.find()) {
            String run = m.group();
            String ps;
            if (Character.isLetter(run.codePointAt(0))) {
                ps = word(run, ctx, 0);
            } else if (Lexicon.isAsciiDigit(run.charAt(0))) {
                MToken number = new MToken(run, "CD", "");
                ps = lexicon.apply(number, ctx);
            } else {
                String symbol = Lexicon.SYMBOLS.get(run);
                ps = symbol == null ? null : lexicon.lookup(symbol, null, null, ctx);
            }
            if (ps == null || ps.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(ps);
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** One run of letters (with inner apostrophes). */
    private String word(String w, Lexicon.Context ctx, int depth) {
        String table = table(w, ctx);
        if (table != null) return table;
        String known = known(w, ctx);
        if (known != null) return known;
        String folded = fold(w);
        if (!folded.equals(w)) {
            table = table(folded, ctx);
            if (table != null) return table;
            known = known(folded, ctx);
            if (known != null) return known;
            w = folded;
        }
        String lower = Lexicon.lower(w);
        if (depth < 2) {
            String unstretched = unstretch(w, ctx, depth);
            if (unstretched != null) return unstretched;
        }
        Matcher clitic = CLITIC.matcher(w);
        if (clitic.matches() && depth < 2) {
            String stem = word(clitic.group(1), ctx, depth + 1);
            String tail = Lexicon.lower(clitic.group(2));
            if (stem != null && !stem.isEmpty()) {
                if (tail.equals("'s") || tail.equals("s'")) return plural(stem);
                String suffix = lexicon.lookup(tail.equals("n't") ? "n't" : tail, null, null, ctx);
                if (suffix != null) return stem + suffix;
            }
        }
        boolean vowelless = lower.chars().noneMatch(c -> "aeiouy".indexOf(c) >= 0);
        if (vowelless || lower.length() <= 5 && (!ONSET.matcher(lower).matches() || !CODA.matcher(lower).matches())) {
            String spelled = lexicon.getNNP(w);
            if (spelled != null) return spelled;
        }
        String derived = derive(lower, ctx);
        if (derived != null) return derived;
        String compound = compound(lower, ctx);
        if (compound != null) return compound;
        String guess = rules(lower);
        return guess.isEmpty() ? null : british(guess);
    }

    /** This class's own tables: transcribed chat words, then abbreviations read out in full. */
    private String table(String w, Lexicon.Context ctx) {
        String lower = Lexicon.lower(w);
        String chat = CHAT.get(lower);
        if (chat != null) return british(chat);
        String text = SAY_AS.get(lower);
        return text == null ? null : sentence(text, ctx);
    }

    /** A short expansion ("idk" = "I don't know"), read right to left with context like the main pipeline. */
    private String sentence(String text, Lexicon.Context ctx) {
        String[] words = text.split(" ");
        String[] ps = new String[words.length];
        for (int i = words.length - 1; i >= 0; i--) {
            MToken tk = new MToken(words[i], words[i].equals("I") ? "PRP" : words[i].equals("to") ? "TO" : "XX", "");
            ps[i] = lexicon.apply(tk, ctx);
            ctx = EnglishG2P.nextContext(ctx, ps[i], tk);
        }
        StringBuilder b = new StringBuilder();
        for (String p : ps) {
            if (p == null) continue;
            if (b.length() > 0) b.append(' ');
            b.append(p);
        }
        return b.toString();
    }

    /**
     * Tokens this class should read even though misaki has an answer: chat shorthand ("b4" is not "B four") and
     * ALL-CAPS chat words that misaki would spell letter by letter ("HEHE").
     */
    boolean overrides(String text) {
        String lower = Lexicon.lower(text);
        if (SAY_AS.containsKey(lower)) return true;
        return text.length() > 1 && text.equals(Lexicon.upper(text)) && CHAT.containsKey(lower);
    }

    /** The lexicon's answer for a spelling variant: as written, lower-case, ALL-CAPS acronym, or Capitalised. */
    private String known(String w, Lexicon.Context ctx) {
        for (String variant : new String[] {w, Lexicon.lower(w), Lexicon.upper(w), Lexicon.capitalize(w)}) {
            if (variant.length() > 1 && variant.equals(Lexicon.upper(variant)) && !lexicon.inGolds(variant)) continue;
            String ps = lexicon.apply(new MToken(variant, "XX", ""), ctx);
            if (ps != null) return ps;
        }
        return null;
    }

    /** "Sooooo" is "so", "Hmmmm" is "hmm", "hahaha" is "ha" three times. */
    private String unstretch(String w, Lexicon.Context ctx, int depth) {
        String lower = Lexicon.lower(w);
        String two = STRETCH.matcher(lower).replaceAll("$1$1");
        String one = STRETCH.matcher(lower).replaceAll("$1").replace("aa", "a");
        for (String shorter : new String[] {two, one}) {
            if (shorter.equals(lower) || shorter.length() < 2) continue;
            if (CHAT.containsKey(shorter) || known(shorter, ctx) != null) return word(shorter, ctx, depth + 1);
        }
        for (int unit = 2; unit <= 3; unit++) {
            if (lower.length() < unit * 3 || lower.length() % unit != 0) continue;
            String piece = lower.substring(0, unit);
            if (!piece.repeat(lower.length() / unit).equals(lower)) continue;
            String ps = known(piece, ctx);
            if (ps == null) ps = rules(piece);
            String plain = Phonemes.applyStress(ps, -2.0);
            StringBuilder b = new StringBuilder(Phonemes.applyStress(plain, 2.0));
            for (int k = 1; k < lower.length() / unit; k++) b.append(plain);
            return british(b.toString());
        }
        return null;
    }

    /** Known stem plus a suffix misaki has no rule for. */
    private String derive(String w, Lexicon.Context ctx) {
        String[][] endings = {{"iest", "y", "iəst"}, {"ier", "y", "iəɹ"}, {"ied", "y", "d"}, {"ily", "y", "əli"},
                {"iness", "y", "inəs"}, {"ves", "f", "vz"}, {"ves", "fe", "vz"}, {"est", "", "əst"}, {"er", "", "əɹ"},
                {"ers", "", "əɹz"}, {"ly", "", "li"}, {"ness", "", "nəs"}, {"less", "", "ləs"}, {"ful", "", "fᵊl"},
                {"able", "", "əbᵊl"}, {"able", "e", "əbᵊl"}, {"ish", "", "ɪʃ"}, {"y", "", "i"}, {"ie", "", "i"},
                {"ite", "", "ˌIt"}, {"s", "", null}, {"es", "", null}, {"ed", "", null}, {"ing", "", null},
                {"ing", "e", null}};
        for (String[] e : endings) {
            int minStem = e[2] != null && e[0].length() <= 3 && !e[0].startsWith("i") ? 4 : 3;
            if (!w.endsWith(e[0]) || w.length() - e[0].length() < minStem) continue;
            String base = w.substring(0, w.length() - e[0].length());
            for (String stem : new String[] {base + e[1], undouble(base) + e[1]}) {
                String ps = stem.equals(w) ? null : known(stem, ctx);
                if (ps == null && e[2] == null && CHAT.containsKey(stem)) ps = british(CHAT.get(stem));
                if (ps == null) continue;
                if (e[1].startsWith("f")) ps = ps.endsWith("f") ? Lexicon.cut(ps, 1) : ps;
                if (e[2] == null) {
                    return switch (e[0]) {
                        case "s", "es" -> plural(ps);
                        case "ed" -> past(ps);
                        default -> ps + "ɪŋ";
                    };
                }
                if (e[1].equals("y") && ps.endsWith("i")) ps = Lexicon.cut(ps, 1);
                return ps + british(e[2]);
            }
        }
        return null;
    }

    /** Two known words written together ("redstone", "treehouse"): the first keeps the main stress. */
    private String compound(String w, Lexicon.Context ctx) {
        for (int cut = w.length() - 3; cut >= 3; cut--) {
            String head = w.substring(0, cut), tail = w.substring(cut);
            if (head.length() < 4 && tail.length() < 5) continue;
            if (!lexicon.inGolds(head) || !(lexicon.inGolds(tail) || tail.equals("man") || tail.equals("men"))) continue;
            String a = known(head, ctx), b = known(tail, ctx);
            if (a == null || b == null) continue;
            return Phonemes.applyStress(a, 2.0) + Phonemes.applyStress(b, -1.0);
        }
        return null;
    }

    private String plural(String ps) {
        if (ps.isEmpty()) return ps;
        char last = ps.charAt(ps.length() - 1);
        if ("ptkfθ".indexOf(last) >= 0) return ps + "s";
        if ("szʃʒʧʤ".indexOf(last) >= 0) return ps + (lexicon.british ? "ɪz" : "ᵻz");
        return ps + "z";
    }

    private String past(String ps) {
        if (ps.isEmpty()) return ps;
        char last = ps.charAt(ps.length() - 1);
        if ("pkfθʃsʧ".indexOf(last) >= 0) return ps + "t";
        if (last == 'd' || last == 't') return ps + (lexicon.british ? "ɪd" : "ᵻd");
        return ps + "d";
    }

    private static String undouble(String s) {
        int n = s.length();
        return n >= 2 && s.charAt(n - 1) == s.charAt(n - 2) ? s.substring(0, n - 1) : s;
    }

    /** Strips accents: "café" to "cafe", "Zoë" to "Zoe". */
    private static String fold(String w) {
        String d = Normalizer.normalize(w, Normalizer.Form.NFKD);
        StringBuilder b = new StringBuilder(d.length());
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) b.append(c);
        }
        return b.toString().replace("ß", "ss").replace("æ", "ae").replace("œ", "oe").replace("ø", "o");
    }

    // ---- letter-to-sound rules ----

    /** Applies the spelling rules, then places stress and reduces unstressed vowels. Lower-case input. */
    static String rules(String word) {
        String w = "#" + NON_LETTERS.matcher(word).replaceAll("") + "#";
        StringBuilder ps = new StringBuilder();
        int i = 1;
        while (i < w.length() - 1) {
            List<Rule> candidates = RULES.get(w.charAt(i));
            Rule hit = null;
            if (candidates != null) {
                String before = w.substring(0, i);
                for (Rule r : candidates) {
                    if (!w.startsWith(r.graph, i)) continue;
                    if (r.left != null && !r.left.matcher(before).find()) continue;
                    if (r.right != null && !r.right.matcher(w).region(i + r.graph.length(), w.length()).lookingAt()) continue;
                    hit = r;
                    break;
                }
            }
            if (hit == null) {
                i++;
                continue;
            }
            ps.append(hit.out);
            i += hit.graph.length();
        }
        return stress(ps.toString(), word);
    }

    private static boolean nucleus(char c) {
        return Phonemes.isVowel(c) || c == 'ᵊ';
    }

    private static boolean reduced(char c) {
        return c == 'ə' || c == 'ᵻ' || c == 'ᵊ';
    }

    /**
     * Primary stress from English habits: stress-taking endings ("-ee", "-ique"), the syllable before "-tion" or
     * "-ic", the root after an unstressed prefix ("abide", "unroll"), the penult of long words in -a/-o
     * ("banana"), else the first full vowel. Then a secondary stress on a long vowel two or more syllables later
     * (or on the first syllable when the primary comes late), and reduction of the other short vowels.
     */
    static String stress(String ps, String word) {
        List<Integer> nuclei = new ArrayList<>();
        for (int i = 0; i < ps.length(); i++) if (nucleus(ps.charAt(i))) nuclei.add(i);
        if (nuclei.isEmpty()) return ps;
        int n = nuclei.size(), primary = -1;
        Matcher prefix = PREFIX.matcher(word);
        boolean prefixed = n >= 2 && prefix.lookingAt() && word.length() - prefix.end() >= 3;
        if (STRESSED_ENDING.matcher(word).find()) {
            primary = n - 1;
        } else {
            Matcher m = PRE_STRESS_ENDING.matcher(word);
            if (m.find() && n > PRE_STRESS.get(m.group())) {
                primary = n - PRE_STRESS.get(m.group()) - 1;
            } else if (prefixed) {
                primary = 1;
            } else if (n >= 3 && (word.endsWith("a") || word.endsWith("o"))) {
                primary = n - 2;
            }
        }
        if (primary < 0 || reduced(ps.charAt(nuclei.get(primary)))) {
            primary = 0;
            while (primary < n - 1 && reduced(ps.charAt(nuclei.get(primary)))) primary++;
        }
        int secondary = -1;
        for (int k = n - 1; k >= primary + 2; k--) {
            if ("AIOWYiuɑɔ".indexOf(ps.charAt(nuclei.get(k))) >= 0) {
                secondary = k;
                break;
            }
        }
        boolean strongPrefix = prefixed && STRONG_PREFIX.matcher(word).lookingAt();
        if (secondary < 0 && primary >= 1 && (primary >= 2 || strongPrefix) && !reduced(ps.charAt(nuclei.get(0)))) {
            secondary = 0;
        }
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (int i = 0; i < ps.length(); i++) {
            char c = ps.charAt(i);
            if (next < n && nuclei.get(next) == i) {
                if (next == primary) {
                    out.append(Phonemes.PRIMARY);
                } else if (next == secondary) {
                    out.append(Phonemes.SECONDARY);
                } else if (!finalClosedCluster(ps, i)) {
                    c = reduce(ps, i, next == n - 1);
                }
                next++;
            }
            out.append(c);
        }
        return flap(out.toString());
    }

    /**
     * Unstressed short vowels become schwa; so do "i" in an open middle syllable ("laminitis"), a non-final
     * "o" ("Kokoro") and "u" after a glide ("popular"). Not before a final consonant cluster ("zorblax").
     */
    private static char reduce(String ps, int i, boolean last) {
        char c = ps.charAt(i);
        switch (c) {
            case 'æ', 'ɑ', 'ʌ', 'ɛ', 'ɔ', 'ɜ':
                return 'ə';
            case 'ɪ':
                return !last && i + 2 < ps.length() && !nucleus(ps.charAt(i + 1)) && nucleus(ps.charAt(i + 2)) ? 'ə' : c;
            case 'O':
                return last ? c : 'ə';
            case 'u':
                return !last && i > 0 && ps.charAt(i - 1) == 'j' ? 'ə' : c;
            default:
                return c;
        }
    }

    private static boolean finalClosedCluster(String ps, int i) {
        int consonants = 0;
        for (int k = i + 1; k < ps.length(); k++) {
            if (nucleus(ps.charAt(k))) return false;
            consonants++;
        }
        return consonants >= 2;
    }

    /** American flap: t between a vowel (or r) and an unstressed vowel, as in "city", "party". */
    private static String flap(String ps) {
        StringBuilder b = new StringBuilder(ps);
        for (int i = 1; i + 1 < b.length(); i++) {
            char prev = b.charAt(i - 1), next = b.charAt(i + 1);
            if (b.charAt(i) == 't' && (Phonemes.isVowel(prev) || prev == 'ɹ') && Phonemes.isVowel(next)) b.setCharAt(i, 'ɾ');
        }
        return b.toString();
    }

    /** American rule output to British: no r after vowels, long vowels marked, GOAT as Q, TRAP as a. */
    private String british(String ps) {
        if (!lexicon.british || ps == null) return ps;
        String s = ps.replace('ɾ', 't').replace('ᵻ', 'ɪ').replace('O', 'Q').replace('æ', 'a');
        s = s.replaceAll("ɑɹ(?![" + Phonemes.VOWELS + "ˈˌ])", "ɑː").replaceAll("ɔɹ(?![" + Phonemes.VOWELS + "ˈˌ])", "ɔː")
                .replaceAll("ɛɹ(?![" + Phonemes.VOWELS + "ˈˌ])", "ɛː").replaceAll("ɪɹ(?![" + Phonemes.VOWELS + "ˈˌ])", "ɪə")
                .replaceAll("ʊɹ(?![" + Phonemes.VOWELS + "ˈˌ])", "ʊə").replaceAll("ɜɹ(?![" + Phonemes.VOWELS + "ˈˌ])", "ɜː")
                .replaceAll("əɹ(?![" + Phonemes.VOWELS + "ˈˌ])", "ə");
        s = s.replaceAll("([ˈˌ])i(?!ː)", "$1iː").replaceAll("([ˈˌ])u(?!ː)", "$1uː").replaceAll("([ˈˌ])ɔ(?![ːɹ])", "$1ɔː");
        // PALM before a vowel or at the end ("mwah", "spa"), LOT before a consonant ("hot")
        s = s.replaceAll("ɑ(?![ːɹbdfɡhjklmnpstvwzðŋʃʒʤʧθ])", "ɑː");
        return s.replaceAll("ɑ(?!ː)", "ɒ");
    }

    // ---- rule table parsing ----

    private static void rules(String... specs) {
        for (String spec : specs) {
            // the graph is the first [...] holding only letters (contexts use [a-z] and (x|y) forms)
            Matcher g = GRAPH.matcher(spec);
            if (!g.find()) throw new IllegalArgumentException(spec);
            int open = g.start(), close = g.end() - 1, eq = spec.lastIndexOf('=');
            String left = spec.substring(0, open), graph = spec.substring(open + 1, close);
            String right = spec.substring(close + 1, eq), out = spec.substring(eq + 1);
            Pattern l = left.isEmpty() ? null : Pattern.compile("(?:" + classes(left) + ")$");
            Pattern r = right.isEmpty() ? null : Pattern.compile(classes(right));
            RULES.computeIfAbsent(graph.charAt(0), k -> new ArrayList<>()).add(new Rule(graph, l, r, out));
        }
    }

    private static String classes(String context) {
        return context.replace("V", V).replace("C", C);
    }

    private static void chat(String... entries) {
        for (String e : entries) {
            int eq = e.indexOf('=');
            CHAT.put(e.substring(0, eq).toLowerCase(Locale.ROOT), e.substring(eq + 1));
        }
    }

    private static void sayAs(String... entries) {
        for (String e : entries) {
            int eq = e.indexOf('=');
            SAY_AS.put(e.substring(0, eq), e.substring(eq + 1));
        }
    }
}
