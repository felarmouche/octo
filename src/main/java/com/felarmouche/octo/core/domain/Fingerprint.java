package com.felarmouche.octo.core.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The identity of a Listing's content on one marketplace: a SHA-256 over the normalised
 * marketplace, title, description and price. External id and images are not part of it,
 * so a re-post is recognised, while a price change makes a new Listing.
 */
public record Fingerprint(String value) {

    public Fingerprint {
        Objects.requireNonNull(value, "value");
    }

    public static Fingerprint of(String marketplace, String title, String description, Optional<Integer> priceInCents) {
        String content = String.join("\u0000",
                normalise(marketplace),
                normalise(title),
                normalise(description),
                priceInCents.map(String::valueOf).orElse(""));
        return new Fingerprint(sha256(content));
    }

    private static String normalise(String text) {
        if (text == null) return "";
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String sha256(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
