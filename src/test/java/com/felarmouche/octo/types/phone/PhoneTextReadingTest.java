package com.felarmouche.octo.types.phone;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.ListingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading storage and battery from title and description, constrained by a Finder's range hint. */
class PhoneTextReadingTest {

    private final PhoneListingType phone = new PhoneListingType();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "iPhone 13 128GB Blau         | ''                          | GB_128",
            "iPhone 15 Pro 1 TB           | ''                          | TB_1",
            "iPhone 13 256 GB             | Früher hatte ich 64 GB      | GB_256",
            "iPhone 13                    | Speicher: 512 GB, wie neu   | GB_512",
            "iPhone 13 mit 100 GB Daten   | 64GB Variante               | GB_64",
    })
    void readsStorageFromTitleThenDescription(String title, String description, Storage expected) {
        assertThat(complete(title, description, PhoneAttributes.UNKNOWN).storage()).contains(expected);
    }

    @Test
    void unknownStorageIsAbsent() {
        assertThat(complete("iPhone 13", "Top Zustand", PhoneAttributes.UNKNOWN).storage()).isEmpty();
    }

    @Test
    void storageTheFinderMappedIsKept() {
        var mapped = new PhoneAttributes(Optional.of(Storage.GB_128), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(complete("iPhone 13 64GB", "", mapped).storage()).contains(Storage.GB_128);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Akkukapazität: 87 %                  | 87",
            "Batterie 88%                         | 88",
            "Battery Health 91%                   | 91",
            "99% Batteriezustand                  | 99",
            "70 Prozent Batterie                  | 70",
            "Hat noch 85% Kapazität vom Akku      | 85",
    })
    void readsBatteryWithTheExistingPatterns(String description, int expected) {
        assertThat(complete("iPhone 13", description, PhoneAttributes.UNKNOWN).battery()).contains(Battery.exact(expected));
    }

    @Test
    void batteryIsReadFromDescriptionBeforeTitle() {
        assertThat(complete("iPhone 13 Akku 90%", "Akku 88 %", PhoneAttributes.UNKNOWN).battery())
                .contains(Battery.exact(88));
    }

    @Test
    void unknownBatteryIsAbsent() {
        assertThat(complete("iPhone 13", "Läuft super, 2 Jahre alt", PhoneAttributes.UNKNOWN).battery()).isEmpty();
    }

    @Test
    void aRangeHintAcceptsATextValueInsideIt() {
        assertThat(complete("iPhone 13", "Akku 74 %", hint("Schwach (< 80%)")).battery()).contains(Battery.exact(74));
    }

    @Test
    void aRangeHintRejectsATextValueOutsideItAndStaysARange() {
        assertThat(complete("iPhone 13", "Akku 90 %", hint("Schwach (< 80%)")).battery()).contains(new Battery(0, 79));
    }

    @Test
    void aBatteryMarkedDefectStaysZeroWhateverTheTextSays() {
        assertThat(complete("iPhone 13", "Akku 95 %", hint("Defekt")).battery()).contains(Battery.exact(0));
    }

    @Test
    void aListingWithoutAttributesGetsThemFromTheText() {
        Listing completed = phone.complete(listing("iPhone 13 128 GB", "Akku 91 %", Optional.empty()));

        assertThat(completed.attributes()).contains(new PhoneAttributes(
                Optional.of(Storage.GB_128), Optional.of(Battery.exact(91)), Optional.empty(), Optional.empty()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Schwach (< 80%)    | 0   | 79",
            "Sehr gut (> 90 %)  | 91  | 100",
            "Sehr gut (≥ 90 %)  | 90  | 100",
            "Gut (80 - 90%)     | 80  | 90",
            "87 %               | 87  | 87",
            "Defekt             | 0   | 0",
            "defekt             | 0   | 0",
    })
    void rangeHints(String hint, int min, int max) {
        assertThat(Battery.fromHint(hint)).contains(new Battery(min, max));
    }

    @Test
    void aHintWithoutNumbersIsUnknown() {
        assertThat(Battery.fromHint("Unbekannt")).isEmpty();
    }

    // --- helpers -----------------------------------------------------------------------------

    private PhoneAttributes complete(String title, String description, PhoneAttributes mapped) {
        return (PhoneAttributes) phone.complete(listing(title, description, Optional.of(mapped))).attributes().orElseThrow();
    }

    private static PhoneAttributes hint(String batteryHint) {
        return new PhoneAttributes(Optional.empty(), Battery.fromHint(batteryHint), Optional.empty(), Optional.empty());
    }

    private static Listing listing(String title, String description, Optional<PhoneAttributes> attributes) {
        return new Listing("kleinanzeigen", "1", title, description, "https://example.org/1", List.of(),
                Instant.parse("2026-11-22T10:00:00Z"), Optional.of(300_00), Map.of(), attributes.map(ListingAttributes.class::cast));
    }
}
