package com.felarmouche.octo.application;

import com.felarmouche.octo.core.finder.FinderI;
import com.felarmouche.octo.plugins.evaluator.rebuy.RebuyEvaluator;
import com.felarmouche.octo.plugins.finder.KleinanzeigenFinder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchRunner {
    private final RebuyEvaluator eval;
    public SearchRunner(RebuyEvaluator eval){
        this.eval = eval;

    }

    @Scheduled(fixedDelayString = "${search-runner.delay.in.milliseconds}")
    public void runSearches() {
        FinderI finder = new KleinanzeigenFinder("https://www.kleinanzeigen.de/s-28359/preis:100:400/iphone-15/k0l29r50", 20);
        SearchSettings settings = new SearchSettings(2000, "https://www.rebuy.de/verkaufen/handy/apple/iphone-15-generation/iphone-15");
        Search search = new Search(finder, null, eval, null, settings, "phone");
        search.execute();
    }

    private List<Search> findDueSearches(){
        return List.of();
    }
}
