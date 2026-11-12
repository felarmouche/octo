package com.felarmouche.octo.application;

import com.felarmouche.octo.core.filter.BaseFilter;
import com.felarmouche.octo.core.filter.FilterI;
import com.felarmouche.octo.core.finder.FinderI;
import com.felarmouche.octo.core.notifier.NotifierI;
import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.plugins.evaluator.rebuy.RebuyEvaluator;
import com.felarmouche.octo.plugins.finder.KleinanzeigenFinder;
import com.felarmouche.octo.plugins.notifier.TelegramNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchRunner {
    private final RebuyEvaluator eval;
    private final Search search;
    private final TelegramNotifier notifier;
    public SearchRunner(RebuyEvaluator eval, TelegramNotifier notifier){
        this.eval = eval;
        this.search = new Search(finder, baseFilter, eval, notifier, settings, "phone");
        this.notifier = notifier;

    }

    FinderI finder = new KleinanzeigenFinder("https://www.kleinanzeigen.de/s-28359/preis:100:400/iphone-15/k0l29r50", 20);
    SearchSettings settings = new SearchSettings(2000, "https://www.rebuy.de/verkaufen/handy/apple/iphone-15-generation/iphone-15");
    FilterI baseFilter = new BaseFilter();

    @Scheduled(fixedDelayString = "${search-runner.delay.in.milliseconds}")
    public void runSearches() {

        search.execute();
    }

    private List<Search> findDueSearches(){
        return List.of();
    }
}
