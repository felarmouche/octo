package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Slf4j
@Component
public class RebuyCatalogProvider {

    private record Cached(String url, RebuyIPhoneCatalog catalog, Instant loadedAt) { }

    private final Duration ttl;
    private Cached cached;

    public RebuyCatalogProvider(@Value("${octo.rebuy.catalog-ttl:PT6H}") Duration ttl) {
        this.ttl = ttl;
    }

    public synchronized RebuyIPhoneCatalog get(String url) {
        Objects.requireNonNull(url, "rebuyUrl must not be null");
        Instant now = Instant.now();

        if (cached != null && cached.url().equals(url) && cached.loadedAt().plus(ttl).isAfter(now)) {
            return cached.catalog();
        }
        try {
            RebuyIPhoneCatalog fresh = RebuyIPhoneCatalog.load(url);
            cached = new Cached(url, fresh, now);
            log.info("Rebuy-Katalog geladen von {}", url);
            return fresh;
        } catch (RuntimeException e) {
            if (cached != null && cached.url().equals(url)) {
                log.warn("Rebuy-Katalog konnte nicht aktualisiert werden, nutze Stand von {}",
                        cached.loadedAt(), e);
                return cached.catalog();
            }
            throw e;
        }
    }
}