package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "gemini")
public record GeminiFallbackProperties(List<String> fallbackModels, Duration requestTimeout) {

    public GeminiFallbackProperties {
        if (fallbackModels == null || fallbackModels.isEmpty()) {
            fallbackModels = List.of("gemini-3.8-flash");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            requestTimeout = Duration.ofSeconds(30);
        }
    }
}
