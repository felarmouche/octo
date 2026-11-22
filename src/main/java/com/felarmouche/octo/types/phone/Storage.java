package com.felarmouche.octo.types.phone;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The storage sizes a phone can have. */
public enum Storage {
    GB_64(64, "64 GB"),
    GB_128(128, "128 GB"),
    GB_256(256, "256 GB"),
    GB_512(512, "512 GB"),
    TB_1(1024, "1 TB"),
    TB_2(2048, "2 TB");

    private static final Pattern IN_TEXT = Pattern.compile("(?i)(?<!\\d)(\\d{1,4})\\s*(gb|tb)");
    private static final Pattern VALUE = Pattern.compile("(?i)^\\s*(\\d{1,4})\\s*(gb|tb)?\\s*$");

    private final int gigabytes;
    private final String label;

    Storage(int gigabytes, String label) {
        this.gigabytes = gigabytes;
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** A structured field value like "128 GB", "128" (GB) or "1 TB"; empty when it names no known size. */
    public static Optional<Storage> parse(String value) {
        if (value == null) return Optional.empty();
        Matcher m = VALUE.matcher(value);
        return m.matches() ? of(Integer.parseInt(m.group(1)), m.group(2)) : Optional.empty();
    }

    /** The first known size mentioned in a text, e.g. "iPhone 13 128GB"; sizes like "100 GB" are skipped. */
    static Optional<Storage> inText(String text) {
        if (text == null) return Optional.empty();
        Matcher m = IN_TEXT.matcher(text);
        while (m.find()) {
            Optional<Storage> storage = of(Integer.parseInt(m.group(1)), m.group(2));
            if (storage.isPresent()) return storage;
        }
        return Optional.empty();
    }

    private static Optional<Storage> of(int amount, String unit) {
        boolean terabytes = unit != null && unit.toLowerCase(Locale.ROOT).equals("tb");
        int gigabytes = terabytes ? amount * 1024 : amount;
        return Arrays.stream(values()).filter(s -> s.gigabytes == gigabytes).findFirst();
    }
}
