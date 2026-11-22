package com.felarmouche.octo.application.search;

import com.felarmouche.octo.application.settings.InvalidSettingsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/** Runs every active stored Search, one after the other, on one global interval. */
@Slf4j
@Component
public class SearchRunner {

    private final SearchService searches;
    private final SearchMemory memory;
    private final SearchRun run;
    private final Clock clock = Clock.systemUTC();

    public SearchRunner(SearchService searches, SearchMemory memory) {
        this.searches = searches;
        this.memory = memory;
        this.run = new SearchRun(memory, clock);
    }

    @Scheduled(initialDelayString = "${octo.searches.first-run-after:PT10S}",
            fixedDelayString = "${octo.searches.interval:PT5M}")
    public void runAll() {
        for (StoredSearch stored : searches.active()) {
            try {
                run.run(searches.build(stored));
            } catch (InvalidSettingsException e) {
                // e.g. a Plugin was removed since the Search was saved
                fail(stored, "Ungültige Einstellungen: " + e.getMessage());
            } catch (RuntimeException e) {
                // e.g. the database failed while recording; the other Searches still run
                log.warn("Search {} could not run", stored.id(), e);
                fail(stored, "Lauf fehlgeschlagen: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
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
}
