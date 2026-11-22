package com.felarmouche.octo.application.search;

import com.felarmouche.octo.TestcontainersConfiguration;
import com.felarmouche.octo.application.search.Search.FinderSlot;
import com.felarmouche.octo.application.search.Search.NotifierSlot;
import com.felarmouche.octo.core.domain.Alert;
import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.core.exclusion.ExclusionFilterSettings;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.listingtype.PriceRange;
import com.felarmouche.octo.core.plugin.Evaluator;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.core.plugin.Notifier;
import com.felarmouche.octo.core.plugin.PluginException;
import com.felarmouche.octo.types.phone.Battery;
import com.felarmouche.octo.types.phone.PhoneAttributes;
import com.felarmouche.octo.types.phone.PhoneCondition;
import com.felarmouche.octo.types.phone.PhoneFilterSettings;
import com.felarmouche.octo.types.phone.PhoneListingType;
import com.felarmouche.octo.types.phone.Storage;
import com.felarmouche.octo.types.generic.GenericFilterSettings;
import com.felarmouche.octo.types.generic.GenericListingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/** The run of a Search against PostgreSQL. Each test runs in its own rolled-back transaction. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JdbcSearchMemory.class})
class SearchRunTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private SearchMemory memory;
    private SearchRun run;

    @BeforeEach
    void setUp() {
        run = new SearchRun(memory, Clock.fixed(NOW, ZoneOffset.UTC));
        // seen and history belong to a stored Search; the run takes its definition from the Search record
        jdbc.sql("""
                insert into search (id, active, name, listing_type, type_filter, exclusion, finders, notifiers,
                                    retention, created_at)
                values ('search-1', true, 'test', 'generic', '{}', '{}', '[]', '[]', '{}', now())""").update();
    }

    @Test
    void withoutEvaluatorEverySurvivorIsBuyAndAlerted() {
        var finder = new FakeFinder("ps5", List.of(listing("PS5 Disc", 240_00)));
        var notifier = new RecordingNotifier();

        RunStatus status = run.run(search(List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY))));

        assertThat(status.ok()).isTrue();
        assertThat(notifier.sent).singleElement().satisfies(alert -> {
            assertThat(alert.listing().title()).isEqualTo("PS5 Disc");
            assertThat(alert.verdict().decision()).isEqualTo(Decision.BUY);
            assertThat(alert.verdict().reason()).isEqualTo("Alle Filter bestanden");
        });
    }

    @Test
    void repostWithSameTextAndPriceIsDroppedButPriceDropIsNew() {
        var finder = new FakeFinder("ps5", List.of(listing("PS5 Disc", "Wie neu", 240_00, "ad-1")));
        var notifier = new RecordingNotifier();
        Search search = search(List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY)));
        run.run(search);

        // re-posted under a new ad id, with only whitespace and case changes
        finder.listings = List.of(listing("ps5  disc", " wie NEU ", 240_00, "ad-2"));
        run.run(search);
        assertThat(notifier.sent).hasSize(1);

        finder.listings = List.of(listing("PS5 Disc", "Wie neu", 220_00, "ad-2"));
        run.run(search);
        assertThat(notifier.sent).hasSize(2);
        assertThat(notifier.sent.get(1).listing().priceInCents()).contains(220_00);
    }

    @Test
    void deduplicationRemembersEveryFingerprintNotOnlyTheLastRun() {
        Listing ps5 = listing("PS5 Disc", 240_00);
        Listing controller = listing("DualSense", 40_00);
        var finder = new FakeFinder("ps5", List.of(ps5));
        var notifier = new RecordingNotifier();
        Search search = search(List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY)));

        run.run(search);
        finder.listings = List.of(controller);   // ps5 drops off the results page ...
        run.run(search);
        finder.listings = List.of(ps5, controller); // ... and comes back
        run.run(search);

        assertThat(notifier.sent).extracting(a -> a.listing().title()).containsExactly("PS5 Disc", "DualSense");
    }

    @Test
    void afterARestartListingsSeenBeforeAreNotAlertedAgain() {
        var finder = new FakeFinder("ps5", List.of(listing("PS5 Disc", 240_00)));
        var notifier = new RecordingNotifier();
        Search search = search(List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY)));
        run.run(search);

        SearchRun afterRestart = new SearchRun(new JdbcSearchMemory(jdbc), Clock.fixed(NOW, ZoneOffset.UTC));
        finder.listings = List.of(listing("PS5 Disc", 240_00), listing("DualSense", 40_00));
        afterRestart.run(search);

        assertThat(notifier.sent).extracting(a -> a.listing().title()).containsExactly("PS5 Disc", "DualSense");
    }

    @Test
    void retentionKeepsTheNewestListingsAndWhatLeftTheHistoryStaysSeen() {
        var finder = new FakeFinder("ps5", List.of(listing("A", 100_00), listing("B", 110_00), listing("C", 120_00)));
        var notifier = new RecordingNotifier();
        Search search = search(List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY)),
                new Retention(Set.of(), Optional.of(2)));

        run.run(search);
        assertThat(memory.history("search-1")).extracting(j -> j.listing().title()).containsExactly("B", "C");

        finder.listings = List.of(listing("A", 100_00), listing("D", 130_00)); // A left the history but was seen
        run.run(search);

        assertThat(memory.history("search-1")).extracting(j -> j.listing().title()).containsExactly("C", "D");
        assertThat(notifier.sent).extracting(a -> a.listing().title()).containsExactly("A", "B", "C", "D");
    }

    @Test
    void retentionCanKeepOnlyCertainDecisionsAndThenTheNewest() {
        var finder = new FakeFinder("ps5", List.of(
                listing("Gut 1", 100_00), listing("Teuer", 900_00), listing("Unklar", 200_00), listing("Gut 2", 110_00)));
        var evaluator = new FakeEvaluator(l -> switch (l.title()) {
            case "Teuer" -> new Verdict(Decision.NO_BUY, "zu teuer");
            case "Unklar" -> new Verdict(Decision.UNSURE, "unklar");
            default -> new Verdict(Decision.BUY, "gut");
        });
        Search search = new Search("search-1", "PS5",
                new Search.TypeSlot<>(new GenericListingType(), GenericFilterSettings.NONE), ExclusionFilterSettings.NONE,
                List.of(slot(finder)), Optional.of(slot(evaluator)), List.of(slot(new RecordingNotifier(), Decision.BUY)),
                new Retention(Set.of(Decision.BUY, Decision.UNSURE), Optional.of(2)));

        run.run(search);

        assertThat(memory.history("search-1")).extracting(j -> j.listing().title()).containsExactly("Unklar", "Gut 2");
    }

    @Test
    void theSameListingFromTwoFindersInOneRunIsAlertedOnce() {
        Listing ps5 = listing("PS5 Disc", 240_00);
        var notifier = new RecordingNotifier();

        run.run(search(List.of(slot(new FakeFinder("a", List.of(ps5))), slot(new FakeFinder("b", List.of(ps5)))),
                Optional.empty(), List.of(slot(notifier, Decision.BUY))));

        assertThat(notifier.sent).hasSize(1);
    }

    @Test
    void evaluatorVerdictAndDetailsReachTheAlert() {
        var finder = new FakeFinder("ps5", List.of(listing("PS5 Disc", 240_00)));
        var evaluator = new FakeEvaluator(l -> new Verdict(Decision.UNSURE, "Preis prüfen",
                List.of(new LabelledValue("Marktwert", "300 €"))));
        var notifier = new RecordingNotifier();

        run.run(search(List.of(slot(finder)), Optional.of(slot(evaluator)), List.of(slot(notifier, Decision.UNSURE))));

        assertThat(notifier.sent).singleElement().satisfies(alert -> {
            assertThat(alert.verdict().decision()).isEqualTo(Decision.UNSURE);
            assertThat(alert.values()).containsExactly(new LabelledValue("Marktwert", "300 €"));
        });
    }

    @Test
    void evaluatorFailureOnOneListingGivesNotEvaluatableAndTheRestContinues() {
        var finder = new FakeFinder("ps5", List.of(listing("Kaputt", 100_00), listing("PS5 Disc", 240_00)));
        var evaluator = new FakeEvaluator(l -> {
            if (l.title().equals("Kaputt")) throw new IllegalStateException("Bilder nicht ladbar");
            return new Verdict(Decision.BUY, "gut");
        });
        var notifier = new RecordingNotifier();
        Search search = search(List.of(slot(finder)), Optional.of(slot(evaluator)),
                List.of(slot(notifier, Decision.BUY, Decision.NOT_EVALUATABLE)));

        RunStatus status = run.run(search);

        assertThat(status.ok()).isTrue();
        assertThat(notifier.sent).extracting(a -> a.listing().title(), a -> a.verdict().decision(), a -> a.verdict().reason())
                .containsExactly(
                        tuple("Kaputt", Decision.NOT_EVALUATABLE, "Bilder nicht ladbar"),
                        tuple("PS5 Disc", Decision.BUY, "gut"));
    }

    @Test
    void notEvaluatableListingsAreRecordedButEvaluatedAgainOnTheNextRun() {
        var finder = new FakeFinder("ps5", List.of(listing("PS5 Disc", 240_00)));
        var failNext = new boolean[]{true};
        var evaluator = new FakeEvaluator(l -> {
            if (failNext[0]) throw new IllegalStateException("alle Modelle ausgefallen");
            return new Verdict(Decision.BUY, "gut");
        });
        var notifier = new RecordingNotifier();
        Search search = search(List.of(slot(finder)), Optional.of(slot(evaluator)), List.of(slot(notifier, Decision.BUY)));

        run.run(search);
        failNext[0] = false;
        run.run(search);
        run.run(search);

        assertThat(notifier.sent).extracting(a -> a.verdict().decision()).containsExactly(Decision.BUY);
        assertThat(memory.history("search-1")).extracting(j -> j.verdict().decision())
                .containsExactly(Decision.NOT_EVALUATABLE, Decision.BUY);
    }

    @Test
    void eachNotifierSlotOnlyReceivesTheDecisionsItReports() {
        var finder = new FakeFinder("ps5", List.of(listing("Gut", 100_00), listing("Unklar", 200_00), listing("Teuer", 900_00)));
        var evaluator = new FakeEvaluator(l -> switch (l.title()) {
            case "Gut" -> new Verdict(Decision.BUY, "gut");
            case "Unklar" -> new Verdict(Decision.UNSURE, "unklar");
            default -> new Verdict(Decision.NO_BUY, "zu teuer");
        });
        var onlyBuy = new RecordingNotifier();
        var buyAndUnsure = new RecordingNotifier();

        run.run(search(List.of(slot(finder)), Optional.of(slot(evaluator)),
                List.of(slot(onlyBuy, Decision.BUY), slot(buyAndUnsure, Decision.BUY, Decision.UNSURE))));

        assertThat(onlyBuy.sent).extracting(a -> a.listing().title()).containsExactly("Gut");
        assertThat(buyAndUnsure.sent).extracting(a -> a.listing().title()).containsExactly("Gut", "Unklar");
        assertThat(memory.history("search-1")).hasSize(3);
    }

    @Test
    void aFailingFinderIsRecordedAndTheOtherFindersContinue() {
        var broken = new FakeFinder("broken-finder", List.of());
        broken.failure = new PluginException("broken-finder", "Ergebnisseite nicht ladbar");
        var working = new FakeFinder("working-finder", List.of(listing("PS5 Disc", 240_00)));
        var notifier = new RecordingNotifier();

        RunStatus status = run.run(search(List.of(slot(broken), slot(working)), Optional.empty(),
                List.of(slot(notifier, Decision.BUY))));

        assertThat(notifier.sent).hasSize(1);
        assertThat(status.ok()).isFalse();
        assertThat(status.finishedAt()).isEqualTo(NOW);
        assertThat(status.messages()).singleElement().asString()
                .contains("broken-finder").contains("Ergebnisseite nicht ladbar");
    }

    @Test
    void theLastRunStatusIsStoredAndSurvivesARestart() {
        var broken = new FakeFinder("broken-finder", List.of());
        broken.failure = new PluginException("broken-finder", "Ergebnisseite nicht ladbar");
        Search search = search(List.of(slot(broken)), Optional.empty(), List.of(slot(new RecordingNotifier(), Decision.BUY)));
        assertThat(memory.lastStatus("search-1")).isEmpty();

        RunStatus failed = run.run(search);
        assertThat(new JdbcSearchMemory(jdbc).lastStatus("search-1")).contains(failed);

        broken.failure = null;
        run.run(search);
        assertThat(new JdbcSearchMemory(jdbc).lastStatus("search-1"))
                .contains(new RunStatus(NOW, true, List.of()));
    }

    @Test
    void problemsReportedByAFinderAreRecordedInTheRunStatus() {
        var finder = new FakeFinder("kleinanzeigen-finder", List.of(listing("PS5 Disc", 240_00)));
        finder.problems = List.of("Anzeige https://example.org/ad-9 übersprungen: HTTP 404");
        var notifier = new RecordingNotifier();

        RunStatus status = run.run(search(List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY))));

        assertThat(notifier.sent).hasSize(1);
        assertThat(status.ok()).isFalse();
        assertThat(status.messages()).singleElement().asString()
                .contains("kleinanzeigen-finder").contains("ad-9 übersprungen");
    }

    @Test
    void aFailingNotifierIsRecordedAndTheOtherNotifiersContinue() {
        var finder = new FakeFinder("ps5", List.of(listing("PS5 Disc", 240_00)));
        var broken = new RecordingNotifier();
        broken.failure = new IllegalStateException("Telegram antwortet 502");
        var working = new RecordingNotifier();

        RunStatus status = run.run(search(List.of(slot(finder)), Optional.empty(),
                List.of(slot(broken, Decision.BUY), slot(working, Decision.BUY))));

        assertThat(working.sent).hasSize(1);
        assertThat(memory.history("search-1")).hasSize(1);
        assertThat(status.ok()).isFalse();
        assertThat(status.messages()).singleElement().asString()
                .contains("recording-notifier").contains("Telegram antwortet 502");
    }

    @Test
    void genericPriceRangeRejectsKnownPricesOutsideItAndLetsListingsWithoutPricePass() {
        var finder = new FakeFinder("ps5", List.of(
                listing("Zu billig", 50_00), listing("Passt", 200_00), listing("Zu teuer", 300_00), listing("VB", null)));
        var notifier = new RecordingNotifier();
        Search search = new Search("search-1", "PS5",
                new Search.TypeSlot<>(new GenericListingType(),
                        new GenericFilterSettings(new PriceRange(100_00, 250_00))), ExclusionFilterSettings.NONE,
                List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY, Decision.NO_BUY)), Retention.KEEP_ALL);

        run.run(search);

        assertThat(memory.history("search-1"))
                .extracting(j -> j.listing().title(), j -> j.verdict().decision(), j -> j.verdict().reason())
                .containsExactly(
                        tuple("Zu billig", Decision.NO_BUY, "Preis 50 € < 100 €"),
                        tuple("Passt", Decision.BUY, "Alle Filter bestanden"),
                        tuple("Zu teuer", Decision.NO_BUY, "Preis 300 € > 250 €"),
                        tuple("VB", Decision.BUY, "Alle Filter bestanden"));
    }

    @Test
    void phoneFilterRejectionsAreRecordedAndRememberedAsSeen() {
        var finder = new FakeFinder("phones", List.of(
                listing("iPhone 13 128 GB", "Akkukapazität: 74 %", 300_00, "weak"),
                listing("iPhone 13 64 GB", "Akku 91 %", 300_00, "small"),
                listing("iPhone 13", "Läuft super", 300_00, "unknown")));
        var notifier = new RecordingNotifier();
        Search search = phoneSearch(new PhoneFilterSettings(null, 80, Set.of(Storage.GB_128, Storage.GB_256), null),
                finder, Optional.empty(), notifier);

        run.run(search);
        run.run(search);

        assertThat(memory.history("search-1"))
                .extracting(j -> j.listing().externalId(), j -> j.verdict().decision(), j -> j.verdict().reason())
                .containsExactly(
                        tuple("weak", Decision.NO_BUY, "Akku 74 % < 80 %"),
                        tuple("small", Decision.NO_BUY, "Speicher 64 GB nicht erlaubt"),
                        tuple("unknown", Decision.BUY, "Alle Filter bestanden"));
        assertThat(notifier.sent).extracting(a -> a.listing().externalId()).containsExactly("unknown");
    }

    @Test
    void phoneFilterUsesWhatTheFinderMappedIncludingBatteryRangesAndConditions() {
        var finder = new FakeFinder("phones", List.of(
                phoneListing("weak-range", "Akku 90 %", new PhoneAttributes(
                        Optional.empty(), Optional.of(new Battery(0, 79)), Optional.empty(), Optional.empty())),
                phoneListing("defect", "Geht nicht an", new PhoneAttributes(
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(PhoneCondition.DEFECT))),
                phoneListing("battery-defect", "Akku 95 %", new PhoneAttributes(
                        Optional.empty(), Optional.of(Battery.exact(0)), Optional.empty(), Optional.empty())),
                phoneListing("good", "Gebraucht", new PhoneAttributes(
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(PhoneCondition.GOOD)))));
        Search search = phoneSearch(new PhoneFilterSettings(null, 80, null,
                        Set.of(PhoneCondition.VERY_GOOD, PhoneCondition.GOOD)),
                finder, Optional.empty(), new RecordingNotifier());

        run.run(search);

        assertThat(memory.history("search-1"))
                .extracting(j -> j.listing().externalId(), j -> j.verdict().reason())
                .containsExactly(
                        tuple("weak-range", "Akku höchstens 79 % < 80 %"),
                        tuple("defect", "Zustand Defekt nicht erlaubt"),
                        tuple("battery-defect", "Akku 0 % < 80 %"),
                        tuple("good", "Alle Filter bestanden"));
    }

    @Test
    void phoneAlertsCarryThePhonesLabelledValuesBeforeTheEvaluatorDetails() {
        var finder = new FakeFinder("phones", List.of(phoneListing("ad-1", "iPhone 13 128GB", "Akku 91 %",
                new PhoneAttributes(Optional.empty(), Optional.empty(), Optional.of("Blau"), Optional.of(PhoneCondition.VERY_GOOD)))));
        var evaluator = new FakeEvaluator(l -> new Verdict(Decision.BUY, "gut",
                List.of(new LabelledValue("Rebuy zahlt", "310 €"))));
        var notifier = new RecordingNotifier();

        run.run(phoneSearch(PhoneFilterSettings.NONE, finder, Optional.of(slot(evaluator)), notifier));

        assertThat(notifier.sent).singleElement().extracting(Alert::values).asInstanceOf(LIST).containsExactly(
                new LabelledValue("Speicher", "128 GB"),
                new LabelledValue("Akku", "91 %"),
                new LabelledValue("Farbe", "Blau"),
                new LabelledValue("Zustand", "Sehr gut"),
                new LabelledValue("Rebuy zahlt", "310 €"));
    }

    @Test
    void theListingHistoryKeepsPhoneAttributesAndVerdictDetails() {
        var finder = new FakeFinder("phones", List.of(phoneListing("ad-1", "iPhone 13 128GB", "Akku 91 %",
                new PhoneAttributes(Optional.empty(), Optional.of(new Battery(80, 100)), Optional.of("Blau"),
                        Optional.of(PhoneCondition.VERY_GOOD)))));
        var verdict = new Verdict(Decision.BUY, "gut", List.of(new LabelledValue("Rebuy zahlt", "310 €")));

        run.run(phoneSearch(PhoneFilterSettings.NONE, finder, Optional.of(slot(new FakeEvaluator(l -> verdict))),
                new RecordingNotifier()));

        assertThat(memory.history("search-1")).singleElement().satisfies(judged -> {
            assertThat(judged.listing().attributes()).contains(new PhoneAttributes(Optional.of(Storage.GB_128),
                    Optional.of(Battery.exact(91)), Optional.of("Blau"), Optional.of(PhoneCondition.VERY_GOOD)));
            assertThat(judged.listing().priceInCents()).contains(300_00);
            assertThat(judged.listing().foundAt()).isEqualTo(NOW);
            assertThat(judged.verdict()).isEqualTo(verdict);
        });
    }

    @Test
    void exclusionRunsBeforeTheListingTypeFilterAndItsRejectionsAreRecordedAndRememberedAsSeen() {
        var finder = new FakeFinder("phones", List.of(
                listing("iPhone 13", "Display gesprungen, Akkukapazität: 74 %", 300_00, "broken-and-weak"),
                listing("iPhone 13", "Mietkauf möglich", 300_00, "own-word"),
                listing("iPhone 13", "Akkukapazität: 74 %", 300_00, "weak"),
                listing("iPhone 13", "Kein Riss, Panzerglas gesprungen", 300_00, "good")));
        var notifier = new RecordingNotifier();
        Search search = new Search("search-1", "iPhone 13",
                new Search.TypeSlot<>(new PhoneListingType(), new PhoneFilterSettings(null, 80, null, null)),
                new ExclusionFilterSettings(List.of("Mietkauf"), Set.of("damage")),
                List.of(slot(finder)), Optional.empty(), List.of(slot(notifier, Decision.BUY)), Retention.KEEP_ALL);

        run.run(search);
        run.run(search);

        assertThat(memory.history("search-1"))
                .extracting(j -> j.listing().externalId(), j -> j.verdict().decision(), j -> j.verdict().reason())
                .containsExactly(
                        tuple("broken-and-weak", Decision.NO_BUY, "Display beschädigt (\"Display gesprungen\")"),
                        tuple("own-word", Decision.NO_BUY, "Ausschlusswort (\"Mietkauf\")"),
                        tuple("weak", Decision.NO_BUY, "Akku 74 % < 80 %"),
                        tuple("good", Decision.BUY, "Alle Filter bestanden"));
    }

    @Test
    void aSearchCanOnlySelectPresetsOfItsOwnListingTypeOrCommonOnes() {
        var genericType = new Search.TypeSlot<>(new GenericListingType(), GenericFilterSettings.NONE);

        assertThatThrownBy(() -> new Search("search-1", "PS5", genericType,
                new ExclusionFilterSettings(List.of(), Set.of("damage")), List.of(), Optional.empty(), List.of(), Retention.KEEP_ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("damage");
        assertThatCode(() -> new Search("search-1", "PS5", genericType,
                new ExclusionFilterSettings(List.of(), Set.of("wanted-swap")), List.of(), Optional.empty(), List.of(), Retention.KEEP_ALL))
                .doesNotThrowAnyException();
    }

    // --- helpers -----------------------------------------------------------------------------

    private static Search phoneSearch(PhoneFilterSettings filter, FakeFinder finder,
                                      Optional<Search.EvaluatorSlot<?>> evaluator, RecordingNotifier notifier) {
        return new Search("search-1", "iPhone 13", new Search.TypeSlot<>(new PhoneListingType(), filter),
                ExclusionFilterSettings.NONE,
                List.of(slot(finder)), evaluator, List.of(slot(notifier, Decision.BUY)), Retention.KEEP_ALL);
    }

    static Listing phoneListing(String externalId, String description, PhoneAttributes mapped) {
        return phoneListing(externalId, "iPhone 13", description, mapped);
    }

    static Listing phoneListing(String externalId, String title, String description, PhoneAttributes mapped) {
        return listing(title, description, 300_00, externalId).withAttributes(mapped);
    }


    static Listing listing(String title, Integer priceInCents) {
        return listing(title, "Beschreibung von " + title, priceInCents, "id-" + title);
    }

    static Listing listing(String title, String description, Integer priceInCents, String externalId) {
        return new Listing("kleinanzeigen", externalId, title, description, "https://example.org/" + externalId,
                List.of(), NOW, Optional.ofNullable(priceInCents), Map.of(), Optional.empty());
    }

    private static Search search(List<FinderSlot<?>> finders,
                                 Optional<Search.EvaluatorSlot<?>> evaluator,
                                 List<NotifierSlot<?>> notifiers) {
        return search(finders, evaluator, notifiers, Retention.KEEP_ALL);
    }

    private static Search search(List<FinderSlot<?>> finders,
                                 Optional<Search.EvaluatorSlot<?>> evaluator,
                                 List<NotifierSlot<?>> notifiers,
                                 Retention retention) {
        return new Search("search-1", "PS5 unter 250 €",
                new Search.TypeSlot<>(new GenericListingType(), GenericFilterSettings.NONE), ExclusionFilterSettings.NONE,
                finders, evaluator, notifiers, retention);
    }

    private static FinderSlot<?> slot(FakeFinder finder) {
        return new FinderSlot<>(finder, new NoSettings());
    }

    private static NotifierSlot<?> slot(RecordingNotifier notifier, Decision... decisions) {
        return new NotifierSlot<>(notifier, Set.of(decisions), new NoSettings());
    }

    private static Search.EvaluatorSlot<?> slot(FakeEvaluator evaluator) {
        return new Search.EvaluatorSlot<>(evaluator, new NoSettings());
    }

    record NoSettings() {
    }

    static class FakeEvaluator implements Evaluator<NoSettings> {
        private final Function<Listing, Verdict> judge;

        FakeEvaluator(Function<Listing, Verdict> judge) {
            this.judge = judge;
        }

        @Override
        public Verdict evaluate(Listing listing, NoSettings settings) {
            return judge.apply(listing);
        }

        @Override
        public String id() {
            return "fake-evaluator";
        }

        @Override
        public Set<String> listingTypes() {
            return Set.of(GenericListingType.ID);
        }

        @Override
        public Class<NoSettings> settingsType() {
            return NoSettings.class;
        }
    }

    /** Returns its current Listings on every run; tests change them between runs. */
    static class FakeFinder implements Finder<NoSettings> {
        private final String id;
        List<Listing> listings;
        List<String> problems = List.of();
        RuntimeException failure;

        FakeFinder(String id, List<Listing> listings) {
            this.id = id;
            this.listings = listings;
        }

        @Override
        public Result find(NoSettings settings, ListingType<?> listingType) {
            if (failure != null) throw failure;
            return new Result(listings, problems);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Set<String> listingTypes() {
            return Set.of(GenericListingType.ID);
        }

        @Override
        public Class<NoSettings> settingsType() {
            return NoSettings.class;
        }
    }

    static class RecordingNotifier implements Notifier<NoSettings> {
        final List<Alert> sent = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void send(List<Alert> alerts, NoSettings settings) {
            if (failure != null) throw failure;
            sent.addAll(alerts);
        }

        @Override
        public String id() {
            return "recording-notifier";
        }

        @Override
        public Class<NoSettings> settingsType() {
            return NoSettings.class;
        }
    }
}
