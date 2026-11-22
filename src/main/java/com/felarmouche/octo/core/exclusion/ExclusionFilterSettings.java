package com.felarmouche.octo.core.exclusion;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

/**
 * The Exclusion Filter of a Search.
 *
 * @param words   the user's own excluded words; blank ones are ignored
 * @param presets ids of the selected Exclusion Presets
 */
public record ExclusionFilterSettings(
        @Schema(title = "Ausgeschlossene Wörter") List<String> words,
        @Schema(title = "Ausschluss-Vorlagen") Set<String> presets) {

    public static final ExclusionFilterSettings NONE = new ExclusionFilterSettings(null, null);

    public ExclusionFilterSettings {
        words = words == null ? List.of() : words.stream().filter(w -> w != null && !w.isBlank()).toList();
        presets = presets == null ? Set.of() : Set.copyOf(presets);
    }
}
