package com.felarmouche.octo.infra.ai;

import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Ausweichkette der Gemini-Modelle. Reihenfolge = Priorität: jeder Aufruf beginnt beim ersten Modell,
 * das nicht gerade in der Abkühlphase ist.
 *
 * @param models         Modelle mit modellspezifischen Optionen (nicht jedes Modell kennt jedes Thinking-Level)
 * @param requestTimeout harter Timeout pro Request
 * @param cooldown       wie lange ein überlastetes Modell (429/503/Timeout) übersprungen wird
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiFallbackProperties(List<Model> models, Duration requestTimeout, Duration cooldown) {

    /**
     * @param thinkingLevel null = kein Thinking-Level senden, Standard des Modells gilt
     */
    public record Model(String name, GoogleGenAiThinkingLevel thinkingLevel) {
        public Model {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("gemini.models[].name fehlt");
            }
        }
    }

    public GeminiFallbackProperties {
        if (models == null || models.isEmpty()) {
            models = List.of(new Model("gemini-3.6-flash", GoogleGenAiThinkingLevel.MINIMAL));
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            requestTimeout = Duration.ofSeconds(30);
        }
        if (cooldown == null || cooldown.isNegative()) {
            cooldown = Duration.ofSeconds(60);
        }
    }
}
