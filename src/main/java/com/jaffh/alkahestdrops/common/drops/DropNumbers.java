package com.jaffh.alkahestdrops.common.drops;

import java.util.Locale;

/**
 * Formats the numbers of drops the same way in commands and on the client.
 */
public final class DropNumbers {

    /**
     * Prevents instantiation of this utility class.
     */
    private DropNumbers() {
    }

    /**
     * Formats a number with two decimals, dropping trailing zeros.
     *
     * @param value Number to format.
     * @return Such as {@code "1.5"} or {@code "3"}.
     */
    public static String number(double value) {
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        return formatted.replaceAll("\\.?0+$", "");
    }

    /**
     * Formats a 0–1 ratio as a percentage.
     *
     * @param ratio Ratio between 0 and 1.
     * @return Such as {@code "12.5%"}.
     */
    public static String percent(double ratio) {
        return number(ratio * 100.0) + "%";
    }

    /**
     * Formats a count range.
     *
     * @param range Range to format. Never null.
     * @return Such as {@code "2"} when min equals max, or {@code "1–3"}.
     */
    public static String range(DropCount range) {
        return range.min() == range.max() ? String.valueOf(range.min()) : range.min() + "–" + range.max();
    }
}
