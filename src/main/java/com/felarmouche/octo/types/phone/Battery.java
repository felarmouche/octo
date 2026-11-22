package com.felarmouche.octo.types.phone;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What is known about a phone's battery health, in percent: an exact value ({@code min == max})
 * or a range from a marketplace's hint such as "Schwach (< 80%)". A battery marked defect is exactly 0 %.
 */
public record Battery(int min, int max) {

    private static final Pattern BELOW = Pattern.compile("(<|≤|<=)\\s*(\\d{1,3})");
    private static final Pattern ABOVE = Pattern.compile("(>|≥|>=)\\s*(\\d{1,3})");
    private static final Pattern BETWEEN = Pattern.compile("(\\d{1,3})\\s*%?\\s*[-–]\\s*(\\d{1,3})");
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,3})");

    public Battery {
        if (min < 0 || max > 100 || min > max)
            throw new IllegalArgumentException("battery range must lie within 0..100: " + min + ".." + max);
    }

    public static Battery exact(int percent) {
        return new Battery(percent, percent);
    }

    /**
     * A marketplace's battery hint: "Defekt", "Schwach (< 80%)", "Sehr gut (> 90 %)", "80 - 90 %" or "87 %".
     * Empty when the hint has no number.
     */
    public static Optional<Battery> fromHint(String hint) {
        if (hint == null) return Optional.empty();
        if (hint.strip().toLowerCase(Locale.ROOT).startsWith("defekt")) return Optional.of(exact(0));
        Matcher m;
        if ((m = BELOW.matcher(hint)).find())
            return Optional.of(new Battery(0, clamp(number(m, 2) - (m.group(1).equals("<") ? 1 : 0))));
        if ((m = ABOVE.matcher(hint)).find())
            return Optional.of(new Battery(clamp(number(m, 2) + (m.group(1).equals(">") ? 1 : 0)), 100));
        if ((m = BETWEEN.matcher(hint)).find()) {
            int a = clamp(number(m, 1)), b = clamp(number(m, 2));
            return Optional.of(new Battery(Math.min(a, b), Math.max(a, b)));
        }
        if ((m = NUMBER.matcher(hint)).find()) return Optional.of(exact(clamp(number(m, 1))));
        return Optional.empty();
    }

    public boolean isExact() {
        return min == max;
    }

    /** True when the battery is known to be below the given percent, even at its best. */
    boolean isBelow(int percent) {
        return max < percent;
    }

    boolean contains(int percent) {
        return percent >= min && percent <= max;
    }

    /** "87 %", "< 80 %", "> 90 %" or "80–90 %". */
    public String label() {
        if (isExact()) return min + " %";
        if (min == 0) return "< " + (max + 1) + " %";
        if (max == 100) return "> " + (min - 1) + " %";
        return min + "–" + max + " %";
    }

    private static int number(Matcher m, int group) {
        return Integer.parseInt(m.group(group));
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
