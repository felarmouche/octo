package com.felarmouche.octo.core.shared.listing;

import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.Verdict;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
public class PhoneListing extends Listing {
    private String color;
    private Memory memory;
    private int batteryHealth;
    private String condition;

    public PhoneListing(String id, String externalId, String title, int priceInEuro, String link, String description, List<String> images, LocalDateTime scrapedAt, Fingerprint fingerprint, Verdict verdict, HashMap<String, String> productSpecificFields, String color, Memory memory, int batteryHealth, String condition) {
        super(id, externalId, title, priceInEuro, link, description, images, scrapedAt, fingerprint, verdict, productSpecificFields);
        this.memory = memory;
        this.color = color;
        this.batteryHealth = batteryHealth;
        this.condition = condition;
    }


    @Getter
    public enum Memory {
        UNKNOWN("unknown", 0),
        GB_64("64GB", 64),
        GB_128("128GB", 128),
        GB_256("256GB", 256),
        GB_512("512GB", 512),
        TB_1("1TB", 1024),
        TB_2("2TB", 2048);

        private final String label;
        private final int sizeInGb;

        Memory(String label, int sizeInGb) {
            this.label = label;
            this.sizeInGb = sizeInGb;
        }

        public static Memory fromString(String value) {
            if (value == null) return UNKNOWN;
            String v = value.replace(" ", "");
            return Arrays.stream(values())
                    .filter(m -> m.label.equalsIgnoreCase(v))
                    .findFirst()
                    .orElse(UNKNOWN);
        }

        public static Memory fromText(String text) {
            if (text == null) return UNKNOWN;
            Matcher m = Pattern.compile("(?i)(\\d{2,4})\\s*(gb|tb)").matcher(text);
            while (m.find()) {
                Memory mem = fromString(m.group(1) + m.group(2).toUpperCase());
                if (mem != UNKNOWN) return mem;
            }
            return UNKNOWN;
        }

        public static Memory fromListing(Listing listing) {
            Memory t = fromText(listing.getTitle());
            return t != UNKNOWN ? t : fromText(listing.getDescription());
        }

    }


}
