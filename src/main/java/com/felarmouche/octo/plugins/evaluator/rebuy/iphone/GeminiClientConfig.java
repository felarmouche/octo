package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Ersetzt den automatisch konfigurierten Gemini-Client (@ConditionalOnMissingBean).
 * <p>
 * Standardmäßig hat das Google-SDK keinen Timeout und wiederholt 408/429/5xx bis zu 5x mit Backoff.
 * Das blockiert den Wechsel auf das nächste Modell. Hier: harter Timeout pro Request, keine SDK-Retries,
 * das Ausweichen übernimmt {@link GeminiRebuyIphoneAssessment}.
 */
@Configuration
public class GeminiClientConfig {

    @Bean
    public Client googleGenAiClient(GoogleGenAiConnectionProperties connection, GeminiFallbackProperties props) {
        if (!StringUtils.hasText(connection.getApiKey())) {
            throw new IllegalStateException("spring.ai.google.genai.api-key fehlt");
        }
        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(props.requestTimeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(1)
                        .httpStatusCodes(List.of()))   // keine Status-Codes -> kein Retry, kein Sleep
                .build();

        return Client.builder()
                .apiKey(connection.getApiKey())
                .httpOptions(httpOptions)
                .build();
    }
}
