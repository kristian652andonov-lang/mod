package com.aigirlfriend.ai;

import com.aigirlfriend.companion.Emotion;
import com.aigirlfriend.config.GFConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans and validates AI output. Rejects anything that breaks character, tries to issue commands, includes
 * links, or is explicit; trims to the configured length at a sentence boundary. The only thing parsed from the
 * reply besides text is an optional cosmetic emotion tag.
 */
public final class ResponseValidator {
    public record Result(String text, Emotion emotion) {}

    private static final Pattern TAG = Pattern.compile("^\\s*\\[([a-zA-Z]+)]\\s*");
    private static final Pattern BAD = Pattern.compile(
            "(as an ai|language model|i'?m an ai|i am an ai|openai|anthropic|chatgpt|claude|system prompt|https?://|www\\.|/give |/tp |/kill |/op |/execute|<\\|)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT = Pattern.compile(
            "\\b(sex|sexual|nude|naked|porn|nsfw|orgasm|erotic|horny|genital\\w*|breasts?)\\b", Pattern.CASE_INSENSITIVE);

    private ResponseValidator() {}

    @Nullable
    public static Result validate(@Nullable String raw, String name) {
        return validate(raw, name, null);
    }

    /**
     * @param playerName if given, a reply that starts by speaking as the player is rejected, and an unfinished
     *                   last sentence (generation cut off by the token limit) is dropped
     */
    @Nullable
    public static Result validate(@Nullable String raw, String name, @Nullable String playerName) {
        if (raw == null) return null;
        String s = raw.replace('\r', ' ').replace('\n', ' ').trim();
        // strip leaked thinking / role prefixes / quotes
        s = s.replaceAll("(?is)<think>.*?</think>", "").trim();
        String lower = s.toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith(n + ":")) s = s.substring(name.length() + 1).trim();
        // she addressed herself: "Iris, you..." loses the name, "Hey Iris!" greets the player instead
        Matcher self = Pattern.compile("^((?:hey|hi|hello|oh|aw+|well|good (?:morning|night|evening))[ ,]+)?" + Pattern.quote(name) + "\\b[,!.]?\\s*",
                Pattern.CASE_INSENSITIVE).matcher(s);
        if (self.find() && self.end() < s.length()) {
            String greeting = self.group(1);
            s = greeting != null && playerName != null && !playerName.isEmpty()
                    ? greeting.trim().replaceAll(",$", "") + " " + playerName + "! " + s.substring(self.end())
                    : capitalize(s.substring(self.end()));
        }
        if (playerName != null && !playerName.isEmpty()) {
            // "...about you, Iris." when Iris is her: she means the player
            s = Pattern.compile(",\\s*" + Pattern.quote(name) + "(?=[.!?~]*$|[.!?,]\\s)", Pattern.CASE_INSENSITIVE).matcher(s)
                    .replaceAll(Matcher.quoteReplacement(", " + playerName));
            if (s.toLowerCase(Locale.ROOT).startsWith(playerName.toLowerCase(Locale.ROOT) + ":")) return null;
            // never let her go on to write the player's next line
            Matcher other = Pattern.compile("\\s*\\b" + Pattern.quote(playerName) + ":", Pattern.CASE_INSENSITIVE).matcher(s);
            if (other.find()) s = s.substring(0, other.start()).trim();
        }
        Emotion emotion = Emotion.NONE;
        Matcher m = TAG.matcher(s);
        if (m.find()) {
            emotion = Emotion.byName(m.group(1));
            s = s.substring(m.end()).trim();
        }
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("“") && s.endsWith("”"))) s = s.substring(1, s.length() - 1).trim();
        s = s.replaceAll("\\s{2,}", " ");
        s = s.replaceAll("[\\p{So}\\p{Cn}]", "").trim();
        if (s.length() < 2) return null;
        if (BAD.matcher(s).find() || EXPLICIT.matcher(s).find()) return null;
        if (playerName != null) s = dropUnfinishedSentence(s);
        int max = GFConfig.AI_MAX_RESPONSE_CHARS.get();
        if (s.length() > max) {
            String cut = s.substring(0, max);
            int end = Math.max(cut.lastIndexOf(". "), Math.max(cut.lastIndexOf("! "), cut.lastIndexOf("? ")));
            s = end > max / 3 ? cut.substring(0, end + 1) : cut.trim() + "...";
        }
        return new Result(s, emotion);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String dropUnfinishedSentence(String s) {
        char last = s.charAt(s.length() - 1);
        if (".!?\"')~".indexOf(last) >= 0) return s;
        int end = Math.max(s.lastIndexOf(". "), Math.max(s.lastIndexOf("! "), s.lastIndexOf("? ")));
        return end > 8 ? s.substring(0, end + 1) : s;
    }
}
