package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.core.plugin.Evaluator;
import com.felarmouche.octo.core.plugin.PluginException;
import com.felarmouche.octo.types.phone.PhoneAttributes;
import com.felarmouche.octo.types.phone.PhoneListingType;
import com.felarmouche.octo.types.phone.Storage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Judges a phone against a Rebuy Reference Product, in this order: cheap pre-checks without AI,
 * no photos → NOT_EVALUATABLE, the AI assessment, then the Decision from Rebuy's price for the
 * assessed variant. Findings go into the Verdict's details; the Listing is never changed.
 */
@Slf4j
@Component(RebuyEvaluator.ID)
public class RebuyEvaluator implements Evaluator<RebuySettings> {

    public static final String ID = "rebuy-evaluator";

    private final RebuyAssessor assessor;
    private final RebuyCatalogCache catalogs;

    RebuyEvaluator(RebuyAssessor assessor, RebuyCatalogCache catalogs) {
        this.assessor = assessor;
        this.catalogs = catalogs;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Rebuy";
    }

    @Override
    public Set<String> listingTypes() {
        return Set.of(PhoneListingType.ID);
    }

    @Override
    public Class<RebuySettings> settingsType() {
        return RebuySettings.class;
    }

    @Override
    public Verdict evaluate(Listing listing, RebuySettings settings) {
        RebuyCatalog catalog;
        try {
            catalog = catalogs.get(settings.referenceProductUrl());
        } catch (RuntimeException e) {
            throw new PluginException(ID, "Rebuy-Katalog nicht ladbar: " + e.getMessage(), e);
        }
        PhoneAttributes phone = attributes(listing);

        Optional<Verdict> rejected = preCheck(listing, phone, catalog, settings);
        if (rejected.isPresent()) return rejected.get();
        if (listing.images().isEmpty()) return new Verdict(Decision.NOT_EVALUATABLE, "Keine Fotos vorhanden");

        RebuyAssessment assessment;
        try {
            assessment = assessor.assess(listing, catalog, settings.maxPhotos());
        } catch (RuntimeException e) {
            log.warn("KI-Bewertung nicht möglich für {}", listing.link(), e);
            return new Verdict(Decision.NOT_EVALUATABLE, "KI-Bewertung nicht möglich: " + e.getMessage());
        }
        return decide(listing, phone, assessment, catalog, settings);
    }

    /** 4. The Decision from the AI assessment and Rebuy's price for the assessed variant. */
    private static Verdict decide(Listing listing, PhoneAttributes phone, RebuyAssessment a, RebuyCatalog catalog,
                                  RebuySettings settings) {
        String reasoning = a.reasoning();
        Optional<String> colour = known(a.colour());
        Optional<Storage> storage = Storage.parse(a.storage());
        List<LabelledValue> details = new ArrayList<>();
        details.add(new LabelledValue("Rebuy-Zustand", a.grade().name()));
        colour.ifPresent(c -> details.add(new LabelledValue("Farbe (KI)", c)));
        storage.ifPresent(s -> details.add(new LabelledValue("Speicher (KI)", s.label())));
        if (a.battery() >= 0 && a.battery() <= 100) details.add(new LabelledValue("Akku (KI)", a.battery() + " %"));

        if (a.confidence() < settings.minAiConfidence())
            return new Verdict(Decision.UNSURE, "Bewertung unsicher (Sicherheit %s). %s"
                    .formatted(String.format(Locale.ROOT, "%.2f", a.confidence()), reasoning), details);
        if (a.functionalDefect())
            return new Verdict(Decision.NO_BUY, "Funktionsfehler: " + reasoning, details);
        if (a.grade() == RebuyGrade.A5)
            return new Verdict(Decision.NO_BUY, "Gerät gebrochen (A5), Rebuy nennt dafür keinen Preis. " + reasoning, details);

        Optional<Storage> variantStorage = storage.or(phone::storage);
        OptionalInt resale = variantStorage.isPresent() && colour.isPresent()
                ? catalog.price(variantStorage.get(), colour.get(), a.grade())
                : OptionalInt.empty();
        if (resale.isEmpty())
            return new Verdict(Decision.UNSURE, "Kein Rebuy-Preis für %s / %s / %s. %s".formatted(
                    variantStorage.map(Storage::label).orElse("?"), colour.orElse("?"), a.grade(), reasoning), details);
        details.add(new LabelledValue("Rebuy zahlt", euros(resale.getAsInt())));

        if (listing.priceInCents().isEmpty()) {
            details.add(new LabelledValue("Max. Einkauf", euros(Math.max(0, resale.getAsInt() - settings.minMarginInCents()))));
            return new Verdict(Decision.UNSURE, "Kein Preis angegeben. " + reasoning, details);
        }
        int price = listing.priceInCents().get();
        int profit = resale.getAsInt() - price;
        details.add(new LabelledValue("Einkauf", euros(price)));
        details.add(new LabelledValue("Gewinn", euros(profit)));
        boolean enough = profit >= settings.minMarginInCents();
        return new Verdict(enough ? Decision.BUY : Decision.NO_BUY, "Gewinn %s %s %s. %s".formatted(
                euros(profit), enough ? "≥" : "<", euros(settings.minMarginInCents()), reasoning), details);
    }

    private static Optional<String> known(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase(RebuyAssessment.UNKNOWN)
                ? Optional.empty() : Optional.of(value.strip());
    }

    /** 1. The checks without AI, from the Listing's own attributes and the Reference Product. */
    private static Optional<Verdict> preCheck(Listing listing, PhoneAttributes phone, RebuyCatalog catalog,
                                              RebuySettings settings) {
        Optional<Storage> storage = phone.storage();
        if (storage.isPresent() && !catalog.offers(storage.get()))
            return Optional.of(new Verdict(Decision.UNSURE,
                    storage.get().label() + " existiert bei diesem Modell nicht – falsches Modell?"));

        RebuyGrade best = RebuyGrade.bestPlausible(phone.condition());
        if (storage.isPresent() && catalog.bestPrice(storage, Optional.empty(), best).isEmpty())
            return Optional.of(new Verdict(Decision.NO_BUY,
                    "Rebuy kauft " + storage.get().label() + " ab " + best + " aktuell nicht an"));
        OptionalInt bestPrice = catalog.bestPrice(storage, phone.colour(), best);

        if (bestPrice.isPresent() && listing.priceInCents().isPresent()) {
            int margin = bestPrice.getAsInt() - listing.priceInCents().get();
            if (margin < settings.minMarginInCents())
                return Optional.of(new Verdict(Decision.NO_BUY, "Marge selbst bei " + best + " zu gering: " + euros(margin)));
        }
        return Optional.empty();
    }

    static String euros(int cents) {
        String sign = cents < 0 ? "−" : "";
        int abs = Math.abs(cents);
        return abs % 100 == 0 ? "%s%d €".formatted(sign, abs / 100) : "%s%d,%02d €".formatted(sign, abs / 100, abs % 100);
    }

    private static PhoneAttributes attributes(Listing listing) {
        return listing.attributes()
                .filter(PhoneAttributes.class::isInstance)
                .map(PhoneAttributes.class::cast)
                .orElse(PhoneAttributes.UNKNOWN);
    }
}
