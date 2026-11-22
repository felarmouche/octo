package com.felarmouche.octo.core.exclusion;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One rule of the Exclusion Filter: a match rejects the Listing with the reason.
 *
 * @param titleOnly true when the rule is only checked against the title
 */
public record ExclusionRule(String reason, Pattern pattern, boolean titleOnly) {

    public ExclusionRule {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(pattern, "pattern");
    }

    /** A rule from a regex, case-insensitive and starting at a word boundary. */
    public static ExclusionRule of(String reason, boolean titleOnly, String regex) {
        return new ExclusionRule(reason, Pattern.compile("(?iu)(?<![\\p{L}])(?:" + regex + ")"), titleOnly);
    }

    /** The user's own word, matched literally on word boundaries in title and description. */
    static ExclusionRule word(String word) {
        return of("Ausschlusswort", false, Pattern.quote(word.trim()) + "(?![\\p{L}])");
    }
}
