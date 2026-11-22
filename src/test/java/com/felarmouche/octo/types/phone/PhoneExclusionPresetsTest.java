package com.felarmouche.octo.types.phone;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.exclusion.ExclusionFilter;
import com.felarmouche.octo.core.exclusion.ExclusionFilterSettings;
import com.felarmouche.octo.core.exclusion.ExclusionPreset;
import com.felarmouche.octo.types.generic.GenericListingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneExclusionPresetsTest {

    private static final ExclusionFilter PHONE_PRESETS = ExclusionFilter.of(
            new ExclusionFilterSettings(List.of(), Set.of("damage", "wanted-swap")),
            new PhoneListingType().availableExclusionPresets());

    @ParameterizedTest(name = "title \"{0}\", description \"{1}\" → {2}")
    @CsvSource(delimiter = '|', nullValues = "PASS", textBlock = """
            iPhone 13         | Display gesprungen, sonst top          | Display beschädigt ("Display gesprungen")
            iPhone 13         | Display: kein Riss                     | PASS
            iPhone 13         | Display ist nicht gebrochen            | PASS
            iPhone 13         | Panzerglas gesprungen, Display heil    | PASS
            iPhone 13         | Hülle kaputt, Handy einwandfrei        | PASS
            iPhone 13         | Defekt: nein                           | PASS
            iPhone 13         | Geht nicht an                          | Defekt ("Geht nicht an")
            iPhone 13         | Rückseite gerissen                     | Rückseite beschädigt ("Rückseite gerissen")
            iPhone 13         | Hatte einen Wasserschaden              | Wasserschaden ("Wasserschaden")
            iPhone 13         | iCloud gesperrt                        | iCloud / Aktivierungssperre ("iCloud gesperrt")
            iPhone 13         | Face ID geht nicht                     | Face ID defekt ("Face ID geht nicht")
            iPhone 13 Dummy   | Nur Deko                               | Attrappe ("Dummy")
            iPhone 13         | Für Bastler                            | Für Bastler / Ersatzteile ("Bastler")
            suche iPhone      | Biete bis 300 €                        | Gesuch / Tausch ("suche")
            iPhone 13         | suche iPhone                           | PASS
            Suche defektes iPhone | -                                  | Gesuch / Tausch ("Suche")
            """)
    void phonePresetsTable(String title, String description, String expected) {
        assertThat(PHONE_PRESETS.reject(listing(title, description))).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    void phonesOfferTheCommonPresetsThenDamageAndGenericOnlyTheCommonOnes() {
        assertThat(new PhoneListingType().availableExclusionPresets()).extracting(ExclusionPreset::id)
                .containsExactly("wanted-swap", "damage");
        assertThat(new GenericListingType().availableExclusionPresets()).extracting(ExclusionPreset::id)
                .containsExactly("wanted-swap");
    }

    private static Listing listing(String title, String description) {
        return new Listing("kleinanzeigen", "ad-1", title, description, "https://example.org/ad-1",
                List.of(), Instant.EPOCH, Optional.empty(), Map.of(), Optional.empty());
    }
}
