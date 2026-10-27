package com.fantasyweapons.text;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Set;

/**
 * The mod's text in the player's language. Each piece of English text is its own translation key: a language file
 * ({@code assets/fantasyweapons/lang/ru_ru.json}) maps the English to its translation, and whatever has none stays in
 * English. So the English stays beside the code that shows it, and nothing is lost when a translation is missing.
 * <p>
 * Text the server sends goes as a translatable component ({@link #c}), translated by the client that reads it.
 */
public final class Tr {
    private Tr() {
    }

    /** Marks a {@link #compose composed} text: a format and its arguments, kept in one string. */
    private static final char COMPOSED = '\u0001', SEPARATOR = '\u001F';

    /** Where the English asked for is noted, while a test gathers it (see {@link #record}). */
    private static volatile Set<String> asked;

    /**
     * Notes every piece of English asked for from now on in {@code into} (null stops): the test that checks a language
     * file translates all of the mod's text gathers it so.
     */
    public static void record(@Nullable Set<String> into) {
        asked = into;
    }

    /** {@code english} in the current language (a {@link #compose composed} text is put together in it). */
    public static String s(String english) {
        if (english == null || english.isEmpty()) return english;
        if (english.charAt(0) == COMPOSED) return composed(english);
        Set<String> into = asked;
        if (into != null) into.add(english);
        return Language.getInstance().getOrDefault(english, english);
    }

    /**
     * Text made of an English format and English parts ("Prospector: %s" and a material's name) kept as one string, for
     * text stored in a definition before it is shown: wherever it is shown, {@link #s} translates the format and each
     * part and puts them together then. Parts are plain English or numbers (not composed themselves).
     */
    public static String compose(String english, Object... args) {
        StringBuilder b = new StringBuilder().append(COMPOSED).append(english);
        for (Object a : args) b.append(SEPARATOR).append(a);
        return b.toString();
    }

    private static String composed(String text) {
        String[] parts = text.substring(1).split(String.valueOf(SEPARATOR), -1);
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) args[i - 1] = s(parts[i]);
        return f(parts[0], args);
    }

    /** The English of {@code text}, a {@link #compose composed} one put together in English. */
    public static String english(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != COMPOSED) return text;
        String[] parts = text.substring(1).split(String.valueOf(SEPARATOR), -1);
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        try {
            return String.format(Locale.ROOT, parts[0], args);
        } catch (IllegalFormatException e) {
            return parts[0];
        }
    }

    /** {@link #s} with {@link String#format} arguments ({@code %s}, {@code %d}, {@code %.1f}...) filled in. */
    public static String f(String english, Object... args) {
        String text = s(english);
        try {
            return String.format(Locale.ROOT, text, args);
        } catch (IllegalFormatException e) {
            try {
                return String.format(Locale.ROOT, english, args);
            } catch (IllegalFormatException e2) {
                return text;
            }
        }
    }

    /**
     * {@code english} as a component translated by whoever shows it; its arguments go in at {@code %s} (or
     * {@code %1$s}), so numbers are formatted beforehand.
     */
    public static MutableComponent c(String english, Object... args) {
        Set<String> into = asked;
        if (into != null) into.add(english);
        return Component.translatable(english, args);
    }
}
