package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.types.phone.PhoneAttributes;
import com.felarmouche.octo.types.phone.PhoneCondition;
import com.felarmouche.octo.types.phone.Storage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RebuyEvaluatorTest {

    static final String IPHONE_15 = "https://www.rebuy.de/verkaufen/handy/apple/iphone-15-generation/iphone-15";
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private static final RebuySettings SETTINGS = new RebuySettings(IPHONE_15, 50_00, 0.7, 4);

    private final FakeAssessor ai = new FakeAssessor();
    private final RebuyEvaluator evaluator = new RebuyEvaluator(ai,
            new RebuyCatalogCache(url -> fixture("iphone-15.html"), Duration.ofHours(6), Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void storageTheReferenceProductDoesNotOfferIsUnsureWithoutAiCall() {
        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.TB_1, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.UNSURE);
        assertThat(verdict.reason()).isEqualTo("1 TB existiert bei diesem Modell nicht – falsches Modell?");
        assertThat(ai.calls).isEmpty();
    }

    @Test
    void marginTooLowEvenAtTheBestGradeTheConditionAllowsIsNoBuyWithoutAiCall() {
        // GOOD → at best A2: Rebuy pays at most 400 € for 128 GB, 360 € leaves 40 € < 50 €
        Verdict verdict = evaluator.evaluate(phone(360_00, Storage.GB_128, PhoneCondition.GOOD), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.NO_BUY);
        assertThat(verdict.reason()).isEqualTo("Marge selbst bei A2 zu gering: 40 €");
        assertThat(ai.calls).isEmpty();
    }

    @Test
    void bestPlausibleGradeComesFromThePhoneCondition() {
        ai.next = assessment(RebuyGrade.A1, "Schwarz", "128 GB", 0.9, false);

        // VERY_GOOD → at best A1: 450 € − 360 € leaves enough to ask the AI
        evaluator.evaluate(phone(360_00, Storage.GB_128, PhoneCondition.VERY_GOOD), SETTINGS);

        assertThat(ai.calls).hasSize(1);
    }

    @Test
    void variantRebuyDoesNotBuyIsNoBuyWithoutAiCall() {
        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_512, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.NO_BUY);
        assertThat(verdict.reason()).isEqualTo("Rebuy kauft 512 GB ab A0 aktuell nicht an");
        assertThat(ai.calls).isEmpty();
    }

    @Test
    void colourRebuyDoesNotBuyStillGoesToTheAiWhenOtherColoursOfTheStorageAreBought() {
        ai.next = assessment(RebuyGrade.A2, "Schwarz", "256 GB", 0.9, false);
        Listing blue = phone(300_00, Storage.GB_256, null).withAttributes(new PhoneAttributes(
                Optional.of(Storage.GB_256), Optional.empty(), Optional.of("Blau"), Optional.empty()));

        evaluator.evaluate(blue, SETTINGS);

        assertThat(ai.calls).hasSize(1);
    }

    @Test
    void maxPurchasePriceIsNeverNegative() {
        ai.next = assessment(RebuyGrade.A2, "Schwarz", "128 GB", 0.9, false);

        Verdict verdict = evaluator.evaluate(phone(null, Storage.GB_128, null), new RebuySettings(IPHONE_15, 500_00, 0.7, 4));

        assertThat(verdict.decision()).isEqualTo(Decision.UNSURE);
        assertThat(verdict.details()).contains(new LabelledValue("Max. Einkauf", "0 €"));
    }

    @Test
    void listingWithoutPhotosIsNotEvaluatable() {
        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null, List.of()), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.NOT_EVALUATABLE);
        assertThat(verdict.reason()).isEqualTo("Keine Fotos vorhanden");
        assertThat(ai.calls).isEmpty();
    }

    @Test
    void failingAssessmentIsNotEvaluatable() {
        ai.failure = new IllegalStateException("Alle Gemini-Modelle fehlgeschlagen: gemini-x: 503");

        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.NOT_EVALUATABLE);
        assertThat(verdict.reason()).isEqualTo("KI-Bewertung nicht möglich: Alle Gemini-Modelle fehlgeschlagen: gemini-x: 503");
    }

    @Test
    void enoughProfitAtTheAssessedVariantIsBuyWithDetails() {
        ai.next = assessment(RebuyGrade.A2, "Schwarz", "128 GB", 0.9, false);

        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.BUY);
        assertThat(verdict.reason()).isEqualTo("Gewinn 100 € ≥ 50 €. Display ohne Kratzer.");
        assertThat(verdict.details()).containsExactly(
                new LabelledValue("Rebuy-Zustand", "A2"),
                new LabelledValue("Farbe (KI)", "Schwarz"),
                new LabelledValue("Speicher (KI)", "128 GB"),
                new LabelledValue("Akku (KI)", "91 %"),
                new LabelledValue("Rebuy zahlt", "400 €"),
                new LabelledValue("Einkauf", "300 €"),
                new LabelledValue("Gewinn", "100 €"));
    }

    @Test
    void tooLittleProfitAtTheAssessedVariantIsNoBuy() {
        ai.next = assessment(RebuyGrade.A3, "Schwarz", "128 GB", 0.9, false);

        Verdict verdict = evaluator.evaluate(phone(360_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.NO_BUY);
        assertThat(verdict.reason()).isEqualTo("Gewinn −10 € < 50 €. Display ohne Kratzer.");
        assertThat(verdict.details()).contains(new LabelledValue("Gewinn", "−10 €"));
    }

    @Test
    void listingWithoutPriceIsAssessedButNeverBuy() {
        ai.next = assessment(RebuyGrade.A2, "Schwarz", "128 GB", 0.9, false);

        Verdict verdict = evaluator.evaluate(phone(null, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.UNSURE);
        assertThat(verdict.reason()).isEqualTo("Kein Preis angegeben. Display ohne Kratzer.");
        assertThat(verdict.details()).contains(
                new LabelledValue("Rebuy zahlt", "400 €"),
                new LabelledValue("Max. Einkauf", "350 €"));
        assertThat(verdict.details()).extracting(LabelledValue::label).doesNotContain("Einkauf", "Gewinn");
    }

    @Test
    void lowAiConfidenceIsUnsureWithTheAiReasoning() {
        ai.next = assessment(RebuyGrade.A2, "Schwarz", "128 GB", 0.5, false);

        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.UNSURE);
        assertThat(verdict.reason()).isEqualTo("Bewertung unsicher (Sicherheit 0.50). Display ohne Kratzer.");
        assertThat(verdict.details()).contains(new LabelledValue("Rebuy-Zustand", "A2"));
    }

    @Test
    void functionalDefectIsNoBuy() {
        ai.next = new RebuyAssessment("Face ID defekt laut Verkäufer.", RebuyGrade.A1, "Schwarz", "128 GB", 91, 0.9, true);

        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.NO_BUY);
        assertThat(verdict.reason()).isEqualTo("Funktionsfehler: Face ID defekt laut Verkäufer.");
    }

    @Test
    void brokenPhoneIsNoBuy() {
        ai.next = assessment(RebuyGrade.A5, "Schwarz", "128 GB", 0.9, false);

        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.NO_BUY);
        assertThat(verdict.reason()).isEqualTo("Gerät gebrochen (A5), Rebuy nennt dafür keinen Preis. Display ohne Kratzer.");
    }

    @Test
    void noRebuyPriceForTheAssessedVariantIsUnsure() {
        ai.next = assessment(RebuyGrade.A2, "Rosa", "128 GB", 0.9, false);

        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.UNSURE);
        assertThat(verdict.reason()).isEqualTo("Kein Rebuy-Preis für 128 GB / Rosa / A2. Display ohne Kratzer.");
    }

    @Test
    void storageTheAiCannotTellFallsBackToTheListing() {
        ai.next = new RebuyAssessment("Display ohne Kratzer.", RebuyGrade.A2, "Schwarz",
                RebuyAssessment.UNKNOWN, RebuyAssessment.UNKNOWN_BATTERY, 0.9, false);

        Verdict verdict = evaluator.evaluate(phone(300_00, Storage.GB_128, null), SETTINGS);

        assertThat(verdict.decision()).isEqualTo(Decision.BUY);
        assertThat(verdict.details()).extracting(LabelledValue::label).doesNotContain("Speicher (KI)", "Akku (KI)");
    }

    // ------------------------------------------------------------------ helpers

    static RebuyAssessment assessment(RebuyGrade grade, String colour, String storage, double confidence,
                                      boolean functionalDefect) {
        return new RebuyAssessment("Display ohne Kratzer.", grade, colour, storage, 91, confidence, functionalDefect);
    }

    static Listing phone(Integer priceInCents, Storage storage, PhoneCondition condition) {
        return phone(priceInCents, storage, condition, List.of("https://img/1.jpg"));
    }

    static Listing phone(Integer priceInCents, Storage storage, PhoneCondition condition, List<String> images) {
        return new Listing("kleinanzeigen", "ad-1", "iPhone 15", "Gut erhalten", "https://ka/ad-1", images, NOW,
                Optional.ofNullable(priceInCents), Map.of(),
                Optional.of(new PhoneAttributes(Optional.ofNullable(storage), Optional.empty(), Optional.empty(),
                        Optional.ofNullable(condition))));
    }

    static Document fixture(String name) {
        try (InputStream in = RebuyEvaluatorTest.class.getResourceAsStream("/rebuy/" + name)) {
            return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Answers with {@link #next}, or throws {@link #failure} when set. */
    static class FakeAssessor implements RebuyAssessor {
        final List<Listing> calls = new ArrayList<>();
        RebuyAssessment next;
        RuntimeException failure;

        @Override
        public RebuyAssessment assess(Listing listing, RebuyCatalog catalog, int maxPhotos) {
            calls.add(listing);
            if (failure != null) throw failure;
            return next;
        }
    }
}
