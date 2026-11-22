package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.exclusion.ExclusionFilterSettings;
import com.felarmouche.octo.core.exclusion.ExclusionPreset;
import com.felarmouche.octo.plugins.kleinanzeigen.KleinanzeigenFinder;
import com.felarmouche.octo.plugins.kleinanzeigen.KleinanzeigenSettings;
import com.felarmouche.octo.plugins.rebuy.RebuyEvaluator;
import com.felarmouche.octo.plugins.rebuy.RebuySettings;
import com.felarmouche.octo.plugins.telegram.TelegramNotifier;
import com.felarmouche.octo.plugins.telegram.TelegramSettings;
import com.felarmouche.octo.types.generic.GenericFilterSettings;
import com.felarmouche.octo.types.generic.GenericListingType;
import com.felarmouche.octo.types.phone.PhoneCondition;
import com.felarmouche.octo.types.phone.PhoneFilterSettings;
import com.felarmouche.octo.types.phone.PhoneListingType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Searches built in code on the new run, until Searches are stored and scheduled individually. */
@Component
public class InCodeSearches {

    private final SearchRun run = new SearchRun(new InMemorySearchMemory(), Clock.systemUTC());
    private final List<Search> searches;

    public InCodeSearches(KleinanzeigenFinder kleinanzeigen, RebuyEvaluator rebuy, TelegramNotifier telegram,
                          @Value("${telegram.bot.chat-id}") String chatId) {
        TelegramSettings chat = new TelegramSettings(chatId);
        Search ps5 = new Search(
                "ps5-under-250",
                "PS5 unter 250 €",
                new Search.TypeSlot<>(new GenericListingType(), GenericFilterSettings.NONE),
                new ExclusionFilterSettings(List.of(), Set.of(ExclusionPreset.WANTED_SWAP.id())),
                List.of(new Search.FinderSlot<>(kleinanzeigen,
                        new KleinanzeigenSettings("https://www.kleinanzeigen.de/s-preis::250/ps5/k0", 20))),
                Optional.empty(),
                List.of(new Search.NotifierSlot<>(telegram, Set.of(Decision.BUY), chat)));
        Search iphone15 = new Search(
                "iphone-15-rebuy",
                "iPhone 15 für Rebuy",
                new Search.TypeSlot<>(new PhoneListingType(), new PhoneFilterSettings(null, 80, null,
                        Set.of(PhoneCondition.NEW, PhoneCondition.VERY_GOOD, PhoneCondition.GOOD, PhoneCondition.OK))),
                new ExclusionFilterSettings(List.of(), Set.of(ExclusionPreset.WANTED_SWAP.id(), "damage")),
                List.of(new Search.FinderSlot<>(kleinanzeigen,
                        new KleinanzeigenSettings("https://www.kleinanzeigen.de/s-28359/preis:100:400/iphone-15/k0l29r50", 20))),
                Optional.of(new Search.EvaluatorSlot<>(rebuy, new RebuySettings(
                        "https://www.rebuy.de/verkaufen/handy/apple/iphone-15-generation/iphone-15", 20_00, 0.7, 4))),
                List.of(new Search.NotifierSlot<>(telegram, Set.of(Decision.BUY, Decision.UNSURE), chat)));
        this.searches = List.of(ps5, iphone15);
    }

    @Scheduled(initialDelayString = "${octo.in-code-searches.first-run-after:PT10S}",
            fixedDelayString = "${octo.in-code-searches.interval:PT5M}")
    public void runAll() {
        searches.forEach(run::run);
    }
}
