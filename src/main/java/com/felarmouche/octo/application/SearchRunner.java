package com.felarmouche.octo.application;

import com.felarmouche.octo.core.filter.BaseFilter;
import com.felarmouche.octo.core.finder.FinderI;
import com.felarmouche.octo.core.shared.Listing;
import com.felarmouche.octo.plugins.finder.KleinanzeigenFinder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchRunner {

    @Scheduled(fixedDelayString = "${search-runner.delay.in.milliseconds}")
    public void runSearches(){
        FinderI finder = new KleinanzeigenFinder("https://www.kleinanzeigen.de/s-28359/preis:100:400/iphone-15/k0l29r50", 20);

        List<Listing> found = finder.find();

    }

    private List<Search> findDueSearches(){
        return List.of();
    }
}
