package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.felarmouche.octo.core.shared.listing.PhoneListing;

public record ConditionAssessment(
        @JsonPropertyDescription("Kurze Begründung in maximal 2 Sätzen, welches Bauteil die Kategorie bestimmt")
        String reasoning,

        @JsonPropertyDescription("Rebuy-Zustandskategorie A0 bis A5")
        Properties.RebuyGrade condition,

        @JsonPropertyDescription("Exakt einer der vorgegebenen Farbwerte oder UNKNOWN_COLOR")
        String color,

        @JsonPropertyDescription("Exakt einer der vorgegebenen Speicherwerte oder UNKNOWN")
        PhoneListing.Memory memory,

        @JsonPropertyDescription("Akkukapazität in Prozent zwischen 0 und 100, oder -1 falls nicht bestimmbar")
        int batteryCapacity,

        @JsonPropertyDescription("Sicherheit der Bewertung zwischen 0.0 und 1.0")
        double confidence


) {
    public ConditionAssessment {
        reasoning = reasoning == null ? "" : reasoning;
        color = color == null ? GeminiRebuyIphoneAssessment.UNKNOWN_COLOR : color;
        memory = memory == null ? PhoneListing.Memory.UNKNOWN : memory;
    }
}