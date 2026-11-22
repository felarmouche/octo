package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.infra.html.HtmlLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Rebuy catalogs, cached per Reference Product URL for a configured lifetime, so Searches with
 * different Reference Products don't evict each other. When a reload fails, the last good catalog is used.
 */
@Slf4j
@Component
class RebuyCatalogCache {

    private record Cached(RebuyCatalog catalog, Instant loadedAt) {
    }

    private final HtmlLoader pages;
    private final Duration lifetime;
    private final Clock clock;
    private final Map<String, Cached> byUrl = new HashMap<>();

    @Autowired
    RebuyCatalogCache(HtmlLoader pages, @Value("${octo.rebuy.catalog-ttl:PT6H}") Duration lifetime) {
        this(pages, lifetime, Clock.systemUTC());
    }

    RebuyCatalogCache(HtmlLoader pages, Duration lifetime, Clock clock) {
        this.pages = pages;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    /** @throws RuntimeException when the catalog can't be loaded and none was loaded before */
    synchronized RebuyCatalog get(String url) {
        Instant now = clock.instant();
        Cached cached = byUrl.get(url);
        if (cached != null && cached.loadedAt().plus(lifetime).isAfter(now)) return cached.catalog();
        try {
            RebuyCatalog fresh = RebuyCatalog.parse(pages.load(url));
            byUrl.put(url, new Cached(fresh, now));
            log.info("Rebuy-Katalog geladen von {}", url);
            return fresh;
        } catch (RuntimeException e) {
            if (cached == null) throw e;
            log.warn("Rebuy-Katalog {} nicht aktualisierbar, nutze Stand von {}", url, cached.loadedAt(), e);
            return cached.catalog();
        }
    }
}
