package com.felarmouche.octo.infra.ai;

import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Ein Aufruf an ein KI-Modell.
 *
 * @param userTemplate Vorlage mit {platzhaltern}, die aus {@code params} befüllt werden
 * @param isComplete   unvollständige Antworten zählen als Fehlschlag des Modells, das nächste wird probiert
 */
public record AiRequest<T>(String systemPrompt,
                           String userTemplate,
                           Map<String, Object> params,
                           List<Media> images,
                           Class<T> responseType,
                           Predicate<T> isComplete) {

    public AiRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        Objects.requireNonNull(userTemplate, "userTemplate must not be null");
        Objects.requireNonNull(responseType, "responseType must not be null");
        params = params == null ? Map.of() : Map.copyOf(params);
        images = images == null ? List.of() : List.copyOf(images);
        isComplete = isComplete == null ? response -> true : isComplete;
    }

    public AiRequest(String systemPrompt, String userTemplate, Map<String, Object> params,
                     List<Media> images, Class<T> responseType) {
        this(systemPrompt, userTemplate, params, images, responseType, null);
    }
}
