package com.aigirlfriend.client.voice.g2p;

/**
 * English numbers as words, in the shape misaki got from num2words ("one thousand, two hundred and
 * thirty-four"); misaki splits that text on non-letters and looks every word up in the lexicon.
 * Written from scratch against num2words' observable output, not from its (LGPL) source.
 */
final class NumberWords {
    private static final String[] LOW = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    private static final String[] SCALES = {"thousand", "million", "billion", "trillion", "quadrillion", "quintillion"};
    private static final String[][] ORDINALS = {{"one", "first"}, {"two", "second"}, {"three", "third"}, {"four", "fourth"},
            {"five", "fifth"}, {"six", "sixth"}, {"seven", "seventh"}, {"eight", "eighth"}, {"nine", "ninth"},
            {"ten", "tenth"}, {"eleven", "eleventh"}, {"twelve", "twelfth"}};

    private NumberWords() {}

    /** Cardinal words for 0 <= n < 10^21. */
    static String cardinal(long n) {
        if (n < 0) return "minus " + cardinal(-n);
        if (n < 20) return LOW[(int) n];
        if (n < 100) return TENS[(int) (n / 10)] + (n % 10 == 0 ? "" : "-" + LOW[(int) (n % 10)]);
        if (n < 1000) return LOW[(int) (n / 100)] + " hundred" + (n % 100 == 0 ? "" : " and " + cardinal(n % 100));
        long scale = 1000;
        int index = 0;
        while (index + 1 < SCALES.length && n / scale >= 1000) {
            scale *= 1000;
            index++;
        }
        long rest = n % scale;
        String head = cardinal(n / scale) + " " + SCALES[index];
        if (rest == 0) return head;
        return head + (rest < 100 ? " and " : ", ") + cardinal(rest);
    }

    static String ordinal(long n) {
        String words = cardinal(n);
        int cut = Math.max(words.lastIndexOf(' '), words.lastIndexOf('-')) + 1;
        String last = words.substring(cut);
        for (String[] pair : ORDINALS) {
            if (pair[0].equals(last)) return words.substring(0, cut) + pair[1];
        }
        if (last.endsWith("y")) last = last.substring(0, last.length() - 1) + "ie";
        return words.substring(0, cut) + last + "th";
    }

    /** Years read in pairs ("nineteen ninety", "nineteen oh-five"), except where that sounds wrong ("two thousand and five"). */
    static String year(long n) {
        long high = n / 100, low = n % 100;
        if (high == 0 || (high % 10 == 0 && low < 10) || high >= 100) return cardinal(n);
        String lowText = low == 0 ? "hundred" : low < 10 ? "oh-" + cardinal(low) : cardinal(low);
        return cardinal(high) + " " + lowText;
    }
}
