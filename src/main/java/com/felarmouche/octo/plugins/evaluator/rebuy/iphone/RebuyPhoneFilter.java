package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import com.felarmouche.octo.core.shared.Verdict;
import com.felarmouche.octo.core.shared.Verdict.Decision;
import com.felarmouche.octo.core.shared.listing.PhoneListing;
import com.felarmouche.octo.core.shared.listing.PhoneListing.Memory;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.Properties.RebuyGrade;

import java.util.*;

public final class RebuyPhoneFilter {

    /** Sicher bekannter Akku darunter -> raus. */
    public static final int MIN_BATTERY_HEALTH = 80;

    private RebuyPhoneFilter() {}

    private record Outcome(boolean keep, Verdict verdict) {}

    private static Outcome keep()                { return new Outcome(true, null); }
    private static Outcome unsure(String reason) { return new Outcome(true, new Verdict(Decision.UNSURE, reason)); }
    private static Outcome reject(String reason) { return new Outcome(false, new Verdict(Decision.NO_BUY, reason)); }

    public static List<PhoneListing> filter(int minMarginInCents, List<PhoneListing> listings, RebuyIPhoneCatalog catalog) {
        List<PhoneListing> candidates = new ArrayList<>();
        for (PhoneListing l : listings) {
            Outcome o = check(minMarginInCents, l, catalog);
            if (o.verdict() != null) l.setVerdict(o.verdict());
            if (o.keep()) candidates.add(l);
        }
        return candidates;
    }

    private static Outcome check(int minMarginInCents, PhoneListing l, RebuyIPhoneCatalog catalog) {
        String condition = normalize(l.getCondition());

        // 1. Definitiv defekt
        if (condition.equals("defekt")) {
            return reject("Zustand laut Anzeige: Defekt");
        }
        Optional<String> damage = DamageTextFilter.findReason(l.getTitle(), l.getDescription(), null);
        if (damage.isPresent()) {
            return reject(damage.get());
        }

        // 2. Akku sicher zu schwach (0 = unbekannt -> nicht filtern)
        int battery = l.getBatteryHealth();
        if (battery > 0 && battery < MIN_BATTERY_HEALTH) {
            return reject("Akku " + battery + " % < " + MIN_BATTERY_HEALTH + " %");
        }

        // 3. Kein Preis -> KI
        if (l.getPriceInEuro() <= 0) {
            return unsure("Kein Preis angegeben (VB / 0 €)");
        }

        // 4. Speicher: Feld zuerst, sonst Titel/Beschreibung
        Memory memory = l.getMemory() != null && l.getMemory() != Memory.UNKNOWN
                ? l.getMemory()
                : Memory.fromListing(l);
        boolean memoryKnown = memory != Memory.UNKNOWN;
        if (memoryKnown && !catalog.offersStorage(memory)) {
            return unsure(memory.getLabel() + " existiert bei diesem Modell nicht – falsches Modell?");
        }

        // 5. Farbe: Feld zuerst, sonst Titel/Beschreibung
        String color = catalog.detectColor(l.getColor(), null)
                .or(() -> catalog.detectColor(l.getTitle(), l.getDescription()))
                .orElse(null);

        // 6. Bestmöglicher Rebuy-Grade laut Verkäuferangabe
        RebuyGrade bestGrade = bestPlausibleGrade(condition);

        OptionalInt bestPrice = catalog.bestCasePurchasePriceInCent(memory, color, bestGrade);
        if (bestPrice.isEmpty()) {
            return memoryKnown
                    ? reject("Rebuy kauft diese Variante (ab " + bestGrade + ") aktuell nicht an")
                    : unsure("Kein Rebuy-Preis ermittelbar");
        }

        // 7. Marge im besten Fall
        int margin = bestPrice.getAsInt() - l.getPriceInEuro() * 100;
        if (margin < minMarginInCents) {
            return reject("Marge selbst bei " + bestGrade + " zu gering: " + margin / 100 + " €");
        }
        return keep();
    }

    /**
     * Optimistisch: bester Rebuy-Grade, der zur Angabe noch passt.
     * Unbekannt -> A0 (kein Filter).
     */
    private static RebuyGrade bestPlausibleGrade(String condition) {
        return switch (condition) {
            case "sehr gut"   -> RebuyGrade.A1;
            case "gut"        -> RebuyGrade.A2;
            case "in ordnung" -> RebuyGrade.A3;
            default           -> RebuyGrade.A0;   // "neu" oder unbekannt
        };
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}