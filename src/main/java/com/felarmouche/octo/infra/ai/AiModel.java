package com.felarmouche.octo.infra.ai;

/** Ein einzelner Aufruf an genau ein Modell, ohne Ausweichen. Das Ausweichen übernimmt {@link AiClient}. */
public interface AiModel {

    /** @return die Antwort, oder null bei leerer Antwort */
    <T> T call(GeminiFallbackProperties.Model model, AiRequest<T> request);
}
