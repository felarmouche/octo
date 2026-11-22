package com.felarmouche.octo.application.search;

import com.felarmouche.octo.application.search.Search.FinderSlot;
import com.felarmouche.octo.application.search.Search.NotifierSlot;
import com.felarmouche.octo.core.domain.Alert;
import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Evaluator;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.core.plugin.Notifier;
import com.felarmouche.octo.core.plugin.PluginException;
import com.felarmouche.octo.types.generic.GenericListingType;
import org.junit.jupiter.api.Test;

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
import static org.assertj.core.api.Assertions.tuple;

class SearchRunTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private final SearchMemory memory = new InMemorySearchMemory();
    private final SearchRun run = new SearchRun(memory, Clock.fixed(NOW, ZoneOffset.UTC));

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

    // --- helpers -----------------------------------------------------------------------------

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
        return new Search("search-1", "PS5 unter 250 €", new GenericListingType(), finders, evaluator, notifiers);
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
        public Result find(NoSettings settings, ListingType listingType) {
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
