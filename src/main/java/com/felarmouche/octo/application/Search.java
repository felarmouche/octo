package com.felarmouche.octo.application;

import com.felarmouche.octo.core.evaluator.EvaluatorException;
import com.felarmouche.octo.core.evaluator.EvaluatorI;
import com.felarmouche.octo.core.filter.FilterException;
import com.felarmouche.octo.core.filter.FilterI;
import com.felarmouche.octo.core.finder.FindException;
import com.felarmouche.octo.core.finder.FinderI;
import com.felarmouche.octo.core.notifier.NotifierException;
import com.felarmouche.octo.core.notifier.NotifierI;
import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.Verdict;
import com.felarmouche.octo.core.shared.listing.Listing;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Search {
    private final FinderI finder;
    private final FilterI filter;
    private final EvaluatorI evaluator;
    private final NotifierI notifier;
    private final SearchSettings settings;
    private final String listingType;

    Map<Fingerprint, Listing> last = new HashMap<>();

    public Search(FinderI finder, FilterI filter, EvaluatorI evaluator, NotifierI notifier, SearchSettings settings, String listingType) {
        this.finder = finder;
        this.filter = filter;
        this.evaluator = evaluator;
        this.notifier = notifier;
        this.settings = settings;
        this.listingType = listingType;
    }

    public void execute() {
        try {
            log.info("Starting new search...");
            List<Listing> found = finder.find(listingType);

            List<Listing> filtered = filter != null
                    ? filter.filter(found, last)
                    : found;

            updateLast(found);

            // setVerdict() for each Listing
            evaluator.evaluate(filtered, settings);
            evaluateAgain(filtered);

            notifier.notify(filtered, Set.of(Verdict.Decision.BUY, Verdict.Decision.UNSURE));
        } catch (FindException e) {
            log.error("Finding listings with finder {} failed with message: {}", e.getPluginId(), e.getMessage());
        } catch (FilterException e) {
            log.error("Filtering listings with filter {} failed with message: {}", e.getPluginId(), e.getMessage());
        } catch (EvaluatorException e) {
            log.error("Evaluation of listings with evaluator {} failed with message: {}", e.getPluginId(), e.getMessage());
        } catch (NotifierException e) {
            log.error("Sending notification with {} failed with message: {}", e.getPluginId(), e.getMessage());
        } catch (Exception e) {
            log.error("Search failed with error message: {}", e.getMessage());
        }


    }


    private void evaluateAgain(List<Listing> listings) {
        Set<Fingerprint> notEvaluatable = listings.stream()
                .filter(it -> it.getVerdict().decision().equals(Verdict.Decision.NOT_EVALUATABLE))
                .map(Listing::getFingerprint).collect(Collectors.toSet());
        notEvaluatable.forEach(it -> last.remove(it));
    }

    private void updateLast(List<Listing> listings) {
        last.clear();
        listings.forEach(it -> last.put(it.getFingerprint(), it));
    }


}
