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
        if (raw == null) return null;
        String s = raw.replace('\r', ' ').replace('\n', ' ').trim();
        // strip leaked thinking / role prefixes / quotes
        s = s.replaceAll("(?is)<think>.*?</think>", "").trim();
        if (s.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT) + ":")) s = s.substring(name.length() + 1).trim();
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
        int max = GFConfig.AI_MAX_RESPONSE_CHARS.get();
        if (s.length() > max) {
            String cut = s.substring(0, max);
            int end = Math.max(cut.lastIndexOf(". "), Math.max(cut.lastIndexOf("! "), cut.lastIndexOf("? ")));
            s = end > max / 3 ? cut.substring(0, end + 1) : cut.trim() + "...";
        }
        return new Result(s, emotion);
    }
}
