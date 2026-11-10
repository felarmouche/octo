package com.felarmouche.octo.plugins.evaluator.rebuy;

import com.felarmouche.octo.application.SearchSettings;
import com.felarmouche.octo.core.evaluator.BaseEvaluator;
import com.felarmouche.octo.core.shared.Verdict;
import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.core.shared.listing.PhoneListing;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.ConditionAssessment;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.Properties.RebuyGrade;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.GeminiRebuyIphoneAssessment;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.RebuyCatalogProvider;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.RebuyIPhoneCatalog;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.RebuyPhoneFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

@Component
@Slf4j
public class RebuyEvaluator extends BaseEvaluator {

    private static final String PLUGIN_ID = "rebuy-evaluator";
    private static final double MIN_CONFIDENCE = 0.7;

    private final GeminiRebuyIphoneAssessment ai;
    private final RebuyCatalogProvider catalogProvider;

    public RebuyEvaluator(GeminiRebuyIphoneAssessment ai, RebuyCatalogProvider catalogProvider) {
        super(PLUGIN_ID);
        this.ai = ai;
        this.catalogProvider = catalogProvider;
    }

    @Override
    public void evaluate(List<Listing> listings, SearchSettings settings) {
        List<PhoneListing> phones = listings.stream()
                .filter(PhoneListing.class::isInstance)
                .map(PhoneListing.class::cast)
                .toList();

        if (phones.size() < listings.size()) {
            log.warn("{} Listings sind kein PhoneListing und werden ignoriert",
                    listings.size() - phones.size());
        }
        if (phones.isEmpty()) {
            return;
        }

        RebuyIPhoneCatalog catalog = catalogProvider.get(settings.getRebuyUrl());
        int minMarginInCents = settings.getMinMarginInCents();

        List<PhoneListing> qualified = RebuyPhoneFilter.filter(minMarginInCents, phones, catalog);

        Set<PhoneListing> qualifiedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        qualifiedSet.addAll(qualified);
        phones.stream()
                .filter(p -> !qualifiedSet.contains(p))
                .forEach(p -> p.setVerdict(new Verdict(Verdict.Decision.NO_BUY,
                        "Marge selbst im besten Zustand unter " + formatCents(minMarginInCents))));

        qualified.forEach(phone -> evaluateSafely(phone, catalog, minMarginInCents));
    }

    private void evaluateSafely(PhoneListing phone, RebuyIPhoneCatalog catalog, int minMarginInCents) {
        try {
            phone.setVerdict(decide(phone, catalog, minMarginInCents));
        } catch (RuntimeException e) {
            log.error("KI-Bewertung fehlgeschlagen für {}", phone.getTitle(), e);
            phone.setVerdict(new Verdict(Verdict.Decision.UNSURE,
                    "KI-Bewertung fehlgeschlagen: " + e.getMessage()));
        }
    }

    private Verdict decide(PhoneListing phone, RebuyIPhoneCatalog catalog, int minMarginInCents) {
        List<String> imageUrls = phone.getImages();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return new Verdict(Verdict.Decision.UNSURE, "Keine Fotos vorhanden");
        }

        ConditionAssessment a = ai.assessCondition(phone, catalog);
        phone.setBatteryHealth(a.batteryCapacity());
        phone.setCondition(a.condition().toString());
        phone.setColor(a.color());
        phone.setMemory(a.memory());

        if (a.confidence() < MIN_CONFIDENCE) {
            return new Verdict(Verdict.Decision.UNSURE,
                    "Bewertung unsicher (confidence %.2f). %s".formatted(a.confidence(), a.reasoning()));
        }

        if (a.reasoning().startsWith("FUNKTIONSFEHLER")) {
            return new Verdict(Verdict.Decision.NO_BUY, a.reasoning());
        }

        // Rebuy liefert für A5 (gebrochen) keinen Ankaufspreis im HTML -> Marge nicht berechenbar
        if (a.condition() == RebuyGrade.A5) {
            return new Verdict(Verdict.Decision.NO_BUY,
                    "Gerät gebrochen (A5), Rebuy-Ankaufspreis dafür unbekannt. " + a.reasoning());
        }

        OptionalInt sellingPrice = catalog.purchasePriceInCent(a.memory(), a.color(), a.condition());
        if (sellingPrice.isEmpty()) {
            return new Verdict(Verdict.Decision.UNSURE,
                    "Kein Rebuy-Preis für %s / %s / %s. %s"
                            .formatted(a.memory(), a.color(), a.condition(), a.reasoning()));
        }

        int buyingPriceInCents = phone.getPriceInEuro() * 100;
        int profitInCents = profitInCents(buyingPriceInCents, sellingPrice.getAsInt());
        Verdict.Decision decision = profitInCents >= minMarginInCents
                ? Verdict.Decision.BUY
                : Verdict.Decision.NO_BUY;

        String reasoning = "Zustand %s, Rebuy zahlt %s, Einkauf %s, Gewinn %s (min. %s). %s".formatted(
                a.condition(),
                formatCents(sellingPrice.getAsInt()),
                formatCents(buyingPriceInCents),
                formatCents(profitInCents),
                formatCents(minMarginInCents),
                a.reasoning());

        return new Verdict(decision, reasoning);
    }

    static int profitInCents(int buyingPriceInCents, int sellingPriceInCents) {
        return sellingPriceInCents - buyingPriceInCents;
    }

    private static String formatCents(int cents) {
        return "%.2f €".formatted(cents / 100.0);
    }
}