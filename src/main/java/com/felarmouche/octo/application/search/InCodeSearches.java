package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.plugins.kleinanzeigen.KleinanzeigenFinder;
import com.felarmouche.octo.plugins.kleinanzeigen.KleinanzeigenSettings;
import com.felarmouche.octo.plugins.telegram.TelegramNotifier;
import com.felarmouche.octo.plugins.telegram.TelegramSettings;
import com.felarmouche.octo.types.generic.GenericFilterSettings;
import com.felarmouche.octo.types.generic.GenericListingType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Searches built in code on the new run, until Searches are stored and scheduled individually.
 * Runs next to the old iPhone/Rebuy {@code SearchRunner}.
 */
@Component
public class InCodeSearches {

    private final SearchRun run = new SearchRun(new InMemorySearchMemory(), Clock.systemUTC());
    private final Search ps5;

    public InCodeSearches(KleinanzeigenFinder kleinanzeigen, TelegramNotifier telegram,
                          @Value("${telegram.bot.chat-id}") String chatId) {
        this.ps5 = new Search(
                "ps5-under-250",
                "PS5 unter 250 €",
                new Search.TypeSlot<>(new GenericListingType(), GenericFilterSettings.NONE),
                List.of(new Search.FinderSlot<>(kleinanzeigen,
                        new KleinanzeigenSettings("https://www.kleinanzeigen.de/s-preis::250/ps5/k0", 20))),
                Optional.empty(),
                List.of(new Search.NotifierSlot<>(telegram, Set.of(Decision.BUY), new TelegramSettings(chatId))));
    }

    @Scheduled(initialDelayString = "${octo.in-code-searches.first-run-after:PT10S}",
            fixedDelayString = "${octo.in-code-searches.interval:PT5M}")
    public void runAll() {
        run.run(ps5);
    }
}
