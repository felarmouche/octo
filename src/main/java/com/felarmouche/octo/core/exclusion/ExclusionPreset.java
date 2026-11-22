package com.felarmouche.octo.core.exclusion;

import java.util.List;
import java.util.Objects;

/**
 * A ready-made, named set of exclusion rules. A Listing Type offers its own presets;
 * {@link #COMMON} are offered for every Listing Type.
 */
public record ExclusionPreset(String id, String name, List<ExclusionRule> rules) {

    public ExclusionPreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        rules = List.copyOf(rules);
    }

    /** Wanted and swap ads. Title only, since descriptions often say "tausche nicht". */
    public static final ExclusionPreset WANTED_SWAP = new ExclusionPreset("wanted-swap", "Gesuche / Tausch", List.of(
            ExclusionRule.of("Gesuch / Tausch", true, "suche|gesucht|kaufe|ankauf|tausche|nur[\\s\\-]*tausch")));

    /** The presets offered for every Listing Type. */
    public static final List<ExclusionPreset> COMMON = List.of(WANTED_SWAP);
}
