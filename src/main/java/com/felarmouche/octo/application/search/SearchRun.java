package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Alert;
import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.Fingerprint;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.core.exclusion.ExclusionFilter;
import com.felarmouche.octo.core.plugin.Finder;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a Search through its fixed steps: Find, Deduplicate, Exclude, Filter by type, Evaluate, Record, Notify.
 * A failing Finder or Notifier is recorded in the run status and the rest of the run continues.
 */
@Slf4j
public class SearchRun {

    static final Verdict PASSED = new Verdict(Decision.BUY, "Alle Filter bestanden");

    private final SearchMemory memory;
    private final Clock clock;

    public SearchRun(SearchMemory memory, Clock clock) {
        this.memory = memory;
        this.clock = clock;
    }

    public RunStatus run(Search search) {
        List<String> failures = new ArrayList<>();

        List<Listing> found = find(search, failures);
        List<Listing> fresh = deduplicate(search.id(), found);
        ExclusionFilter exclusion = search.exclusionFilter();
        List<JudgedListing> judged = fresh.stream()
                .map(listing -> new JudgedListing(listing, exclude(exclusion, listing)
                        .or(() -> filterByType(search, listing))
                        .orElseGet(() -> evaluate(search, listing))))
                .toList();
        record(search, judged);
        notify(search, judged, failures);

        RunStatus status = new RunStatus(clock.instant(), failures.isEmpty(), failures);
        memory.saveStatus(search.id(), status);
        log.info("Search '{}' ran: {} found, {} new, ok={} {}",
                search.name(), found.size(), fresh.size(), status.ok(), status.messages());
        return status;
    }

    /** 1. Find: each Finder on its own, then the Listing Type completes every Listing. */
    private List<Listing> find(Search search, List<String> failures) {
        List<Listing> found = new ArrayList<>();
        for (Search.FinderSlot<?> slot : search.finders()) {
            try {
                Finder.Result result = slot.find(search.type().listingType());
                result.listings().stream().map(search.type()::complete).forEach(found::add);
                result.problems().forEach(problem -> failures.add("Finder " + slot.pluginId() + ": " + problem));
            } catch (RuntimeException e) {
                log.warn("Finder {} failed", slot.pluginId(), e);
                failures.add("Finder " + slot.pluginId() + " fehlgeschlagen: " + message(e));
            }
        }
        return found;
    }

    /** 2. Deduplicate: drop Listings the Search has already seen, and repeats within this run. */
    private List<Listing> deduplicate(String searchId, List<Listing> found) {
        Map<Fingerprint, Listing> fresh = new LinkedHashMap<>();
        for (Listing listing : found) {
            Fingerprint fingerprint = listing.fingerprint();
            if (!memory.hasSeen(searchId, fingerprint)) fresh.putIfAbsent(fingerprint, listing);
        }
        return List.copyOf(fresh.values());
    }

    /** 3. Exclude: the Exclusion Filter's rejection as NO_BUY, empty when the Listing passes. */
    private static Optional<Verdict> exclude(ExclusionFilter exclusion, Listing listing) {
        return exclusion.reject(listing).map(reason -> new Verdict(Decision.NO_BUY, reason));
    }

    /** 4. Filter by type: the Listing Type Filter's rejection as NO_BUY, empty when the Listing passes. */
    private static Optional<Verdict> filterByType(Search search, Listing listing) {
        return search.type().reject(listing).map(reason -> new Verdict(Decision.NO_BUY, reason));
    }

    /** 5. Evaluate: the Evaluator's Verdict, NOT_EVALUATABLE on failure, BUY without an Evaluator. */
    private Verdict evaluate(Search search, Listing listing) {
        if (search.evaluator().isEmpty()) return PASSED;
        try {
            return search.evaluator().get().evaluate(listing);
        } catch (RuntimeException e) {
            log.warn("Evaluation of {} failed", listing.link(), e);
            return new Verdict(Decision.NOT_EVALUATABLE, message(e));
        }
    }

    /** 6. Record: store every judged Listing and apply the retention rule, remember all Fingerprints except NOT_EVALUATABLE ones. */
    private void record(Search search, List<JudgedListing> judged) {
        memory.record(search.id(), judged, search.retention(), clock.instant());
        memory.remember(search.id(), judged.stream()
                .filter(j -> j.verdict().decision() != Decision.NOT_EVALUATABLE)
                .map(j -> j.listing().fingerprint())
                .toList());
    }

    /** 7. Notify: each notifier slot gets the Alerts whose Decision it reports. */
    private void notify(Search search, List<JudgedListing> judged, List<String> failures) {
        List<Alert> alerts = judged.stream().map(j -> alert(search, j)).toList();
        for (Search.NotifierSlot<?> slot : search.notifiers()) {
            List<Alert> reported = alerts.stream()
                    .filter(a -> slot.decisions().contains(a.verdict().decision()))
                    .toList();
            if (reported.isEmpty()) continue;
            try {
                slot.send(reported);
            } catch (RuntimeException e) {
                log.warn("Notifier {} failed", slot.pluginId(), e);
                failures.add("Notifier " + slot.pluginId() + " fehlgeschlagen: " + message(e));
            }
        }
    }

    private static Alert alert(Search search, JudgedListing judged) {
        List<LabelledValue> values = new ArrayList<>(search.type().describe(judged.listing()));
        values.addAll(judged.verdict().details());
        return new Alert(judged.listing(), judged.verdict(), values);
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
