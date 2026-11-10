package com.felarmouche.octo.plugins.finder;

import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.core.shared.listing.PhoneListing;
import com.felarmouche.octo.core.shared.listing.SupportedListings;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class KleinanzeigenListingConverter {

    public static Listing convert(Listing toConvert, String convertTo) {
        SupportedListings convertingType = null;
        try {
            convertingType = SupportedListings.get(convertTo);
        } catch (IllegalArgumentException e) {
            log.warn("Could not convert Listing to unsupported type: {}. Supported types: {}", convertTo, SupportedListings.values());
        }
        return toConvertableType(convertingType, toConvert);
    }

    private static Listing toConvertableType(SupportedListings type, Listing toConvert) {
        return switch (type) {
            case Phone -> convertToPhone(toConvert);
            case null -> toConvert;
        };
    }

    private static final String KEY_COLOR = "Farbe";
    private static final String KEY_MEMORY = "Speicher (GB)";
    private static final String KEY_BATTERY = "Akkuzustand";
    private static final String KEY_CONDITION = "Zustand";

    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})\\s*%?");

    private static PhoneListing convertToPhone(Listing l) {
        Map<String, String> f = l.getProductSpecificFields() != null
                ? l.getProductSpecificFields() : Map.of();

        String color = f.get(KEY_COLOR);
        String condition = f.get(KEY_CONDITION);

        PhoneListing.Memory memory = PhoneListing.Memory.fromString(f.get(KEY_MEMORY)); // "128 GB" -> GB_128
        if (memory == PhoneListing.Memory.UNKNOWN) memory = PhoneListing.Memory.fromListing(l);

        int batteryHealth = parseBatteryHealth(f.get(KEY_BATTERY), l.getDescription(), l.getTitle());

        return new PhoneListing(
                l.getId(), l.getExternalId(), l.getTitle(), l.getPriceInEuro(), l.getLink(),
                l.getDescription(), l.getImages(), l.getScrapedAt(), l.getFingerprint(),
                l.getVerdict(), l.getProductSpecificFields(),
                color, memory, batteryHealth, condition);
    }

    private static final String BATTERY_WORD = "(?:akku|batterie|battery)";

    // "Akkukapazität: 87 %", "Batterie 88%", "Battery Health 91%"
    private static final Pattern BATTERY_WORD_FIRST = Pattern.compile(
            "(?i)" + BATTERY_WORD + "\\w*\\D{0,40}?\\b(\\d{2,3})\\s*(?:%|prozent)");

    // "99% Batteriezustand", "70 Prozent Batterie", "85% Kapazität vom Akku"
    private static final Pattern BATTERY_NUMBER_FIRST = Pattern.compile(
            "(?i)\\b(\\d{2,3})\\s*(?:%|prozent)[^\\d.,;!?\\n]{0,15}?" + BATTERY_WORD);

    private static int parseBatteryHealth(String field, String description, String title) {
        int min = 0, max = 100;
        if (field != null) {
            String f = field.trim().toLowerCase(Locale.ROOT);
            if (f.startsWith("defekt")) return 1;          // 1 = sicher schlecht (0 bleibt "unbekannt")
            if (f.startsWith("schwach")) {                  // "Schwach (< 80%)"
                min = 1;
                max = 79;
            } else {
                List<Integer> nums = new ArrayList<>();
                Matcher m = PERCENT.matcher(field);
                while (m.find()) nums.add(Integer.parseInt(m.group(1)));
                if (!nums.isEmpty()) {
                    min = Collections.min(nums);
                    max = Collections.max(nums);
                }
            }
        }
        for (String text : new String[]{description, title}) {
            int exact = findBatteryInText(text);
            if (exact >= min && exact <= max && exact > 0) return exact;
        }
        return min;
    }

    /**
     * Liefert die Prozentzahl oder -1.
     */
    private static int findBatteryInText(String text) {
        if (text == null) return -1;
        for (Pattern p : List.of(BATTERY_WORD_FIRST, BATTERY_NUMBER_FIRST)) {
            Matcher m = p.matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }
}
