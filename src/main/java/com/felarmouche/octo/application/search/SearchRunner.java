package com.felarmouche.octo.application.search;

import com.felarmouche.octo.application.settings.InvalidSettingsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs every active Search on its own Schedule. A tick starts each Search that is due, each on its own
 * virtual thread, so a slow Search doesn't delay the others. A Search still running is not started again,
 * and its next run is planned from the end of the run.
 */
@Slf4j
@Component
public class SearchRunner {

    private final SearchService searches;
    private final SearchMemory memory;
    private final SearchRun run;
    private final Clock clock;
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public SearchRunner(SearchService searches, SearchMemory memory, Clock clock) {
        this.searches = searches;
        this.memory = memory;
        this.clock = clock;
        this.run = new SearchRun(memory, clock);
    }

    @Scheduled(initialDelayString = "${octo.scheduler.first-tick-after:PT10S}", fixedDelayString = "PT30S")
    void tick() {
        startDue();
    }

    /** Starts every due Search that isn't running yet. @return the threads it started */
    public List<Thread> startDue() {
        Instant now = clock.instant();
        List<Thread> started = new ArrayList<>();
        for (StoredSearch due : searches.due(now)) {
            String id = due.id();
            if (!running.add(id)) continue;
            try {
                started.add(Thread.ofVirtual().name("search-" + id).start(() -> {
                    try {
                        // read again now that it is claimed: a run that ended since the query has planned the next
                        searches.find(id)
                                .filter(s -> s.active() && !s.nextRun().isAfter(now))
                                .ifPresent(stored -> runDue(stored, now));
                    } finally {
                        running.remove(id);
                    }
                }));
            } catch (RuntimeException e) {
                running.remove(id);
                throw e;
            }
        }
        return started;
    }

    private void runDue(StoredSearch stored, Instant now) {
        Instant allowed;
        try {
            allowed = searches.schedule(stored).firstRunFrom(now, clock.getZone());
        } catch (RuntimeException e) {
            // tried again on the next tick
            log.warn("Schedule of Search {} could not be read", stored.id(), e);
            fail(stored, "Zeitplan ungültig: " + message(e));
            return;
        }
        if (allowed.isAfter(now)) {
            // the run was missed while Octo was down, and the Schedule doesn't allow it now
            searches.planRun(stored.id(), allowed);
            return;
        }
        runOnce(stored);
        try {
            searches.planNextRun(stored.id(), clock.instant());
        } catch (RuntimeException e) {
            log.warn("Next run of Search {} could not be planned", stored.id(), e);
        }
    }

    private void runOnce(StoredSearch stored) {
        try {
            run.run(searches.build(stored));
        } catch (InvalidSettingsException e) {
            // e.g. a Plugin was removed since the Search was saved
            fail(stored, "Ungültige Einstellungen: " + e.getMessage());
        } catch (RuntimeException e) {
            // e.g. the database failed while recording; the other Searches still run
            log.warn("Search {} could not run", stored.id(), e);
            fail(stored, "Lauf fehlgeschlagen: " + message(e));
        }
    }

    /** Does nothing when the Search was deleted meanwhile. */
    private void fail(StoredSearch stored, String message) {
        try {
            memory.saveStatus(stored.id(), new RunStatus(clock.instant(), false, List.of(message)));
        } catch (RuntimeException e) {
            log.warn("Run status of Search {} could not be saved", stored.id(), e);
        }
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
