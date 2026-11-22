package com.felarmouche.octo.core.exclusion;

import com.felarmouche.octo.core.domain.Listing;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExclusionFilterTest {

    @Test
    void ownWordRejectsWithTheWordAsMatchedText() {
        ExclusionFilter filter = ExclusionFilter.of(new ExclusionFilterSettings(List.of("Mietkauf"), Set.of()), List.of());

        assertThat(filter.reject(listing("iPhone 13", "Auch Mietkauf möglich")))
                .contains("Ausschlusswort (\"Mietkauf\")");
    }

    @Test
    void ownWordsGetTheSameNegationAndAccessoryHandlingAndMatchWholeWordsOnly() {
        ExclusionFilter filter = ExclusionFilter.of(
                new ExclusionFilterSettings(List.of("kratzer", "Riss"), Set.of()), List.of());

        assertThat(filter.reject(listing("iPhone 13", "Keine Kratzer"))).isEmpty();
        assertThat(filter.reject(listing("iPhone 13", "Riss im Panzerglas"))).isEmpty();
        assertThat(filter.reject(listing("iPhone 13", "Rissfrei"))).isEmpty();
        assertThat(filter.reject(listing("iPhone 13", "Ein Kratzer am Rand"))).contains("Ausschlusswort (\"Kratzer\")");
    }

    @Test
    void theTitleIsCheckedBeforeTheDescription() {
        ExclusionFilter filter = ExclusionFilter.of(
                new ExclusionFilterSettings(List.of("Mietkauf", "Ratenkauf"), Set.of()), List.of());

        assertThat(filter.reject(listing("Ratenkauf möglich", "Mietkauf möglich")))
                .contains("Ausschlusswort (\"Ratenkauf\")");
    }

    @Test
    void whenSeveralRulesMatchPresetsWinInTheOrderOfferedThenOwnWords() {
        // ten presets that all match "iPhone"; selected in a Set, which has no order
        List<ExclusionPreset> available = IntStream.range(0, 10)
                .mapToObj(i -> preset("p" + i, ExclusionRule.of("Regel " + i, false, "iphone")))
                .toList();
        Set<String> all = Set.of("p9", "p3", "p7", "p0", "p5", "p1", "p8", "p2", "p6", "p4");

        ExclusionFilter filter = ExclusionFilter.of(new ExclusionFilterSettings(List.of("iPhone"), all), available);

        assertThat(filter.reject(listing("iPhone 13", ""))).contains("Regel 0 (\"iPhone\")");
    }

    @Test
    void aPresetThatIsNotOfferedCannotBeSelected() {
        List<ExclusionPreset> available = List.of(preset("wanted-swap", ExclusionRule.of("Gesuch", true, "suche")));

        assertThatThrownBy(() -> ExclusionFilter.of(new ExclusionFilterSettings(List.of(), Set.of("damage")), available))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("damage");
    }

    private static ExclusionPreset preset(String id, ExclusionRule rule) {
        return new ExclusionPreset(id, id, List.of(rule));
    }

    static Listing listing(String title, String description) {
        return new Listing("kleinanzeigen", "ad-1", title, description, "https://example.org/ad-1",
                List.of(), Instant.EPOCH, Optional.empty(), Map.of(), Optional.empty());
    }
}
