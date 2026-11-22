package com.felarmouche.octo.plugins.rebuy;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The AI's assessment of one phone from its photos and text. */
public record RebuyAssessment(
        @JsonPropertyDescription("Kurze Begründung in maximal 2 Sätzen, welches Bauteil die Kategorie bestimmt")
        String reasoning,

        @JsonPropertyDescription("Rebuy-Zustandskategorie A0 bis A5")
        RebuyGrade grade,

        @JsonPropertyDescription("Exakt einer der vorgegebenen Farbwerte oder UNKNOWN")
        String colour,

        @JsonPropertyDescription("Exakt einer der vorgegebenen Speicherwerte oder UNKNOWN")
        String storage,

        @JsonPropertyDescription("Akkukapazität in Prozent zwischen 0 und 100, oder -1 falls nicht bestimmbar")
        int battery,

        @JsonPropertyDescription("Sicherheit der Bewertung zwischen 0.0 und 1.0")
        double confidence,

        @JsonPropertyDescription("true bei einem Funktionsfehler (Display-Störung, Face ID oder Touch ID, Kamera, Akku mit Hinweis Service), sonst false")
        boolean functionalDefect) {

    public static final String UNKNOWN = "UNKNOWN";
    public static final int UNKNOWN_BATTERY = -1;

    public RebuyAssessment {
        reasoning = reasoning == null ? "" : reasoning.strip();
        colour = colour == null ? UNKNOWN : colour;
        storage = storage == null ? UNKNOWN : storage;
    }
}
