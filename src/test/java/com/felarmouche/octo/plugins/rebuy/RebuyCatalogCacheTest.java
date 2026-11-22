package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.infra.html.HtmlLoadException;
import com.felarmouche.octo.infra.html.HtmlLoader;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RebuyCatalogCacheTest {

    private static final String IPHONE_15 = RebuyEvaluatorTest.IPHONE_15;
    private static final String IPHONE_15_PRO = "https://www.rebuy.de/verkaufen/handy/apple/iphone-15-generation/iphone-15-pro";
    private static final Duration LIFETIME = Duration.ofHours(6);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-22T10:00:00Z"));
    private final CountingPages pages = new CountingPages();
    private final RebuyCatalogCache cache = new RebuyCatalogCache(pages, LIFETIME, clock);

    @Test
    void twoReferenceProductsDoNotEvictEachOther() {
        cache.get(IPHONE_15);
        cache.get(IPHONE_15_PRO);
        cache.get(IPHONE_15);
        cache.get(IPHONE_15_PRO);

        assertThat(pages.loaded).containsExactly(IPHONE_15, IPHONE_15_PRO);
    }

    @Test
    void reloadsAfterTheLifetime() {
        cache.get(IPHONE_15);
        clock.now = clock.now.plus(LIFETIME);
        cache.get(IPHONE_15);

        assertThat(pages.loaded).containsExactly(IPHONE_15, IPHONE_15);
    }

    @Test
    void usesTheLastGoodCatalogWhenRebuyCantBeReached() {
        RebuyCatalog good = cache.get(IPHONE_15);
        clock.now = clock.now.plus(LIFETIME);
        pages.failing = true;

        assertThat(cache.get(IPHONE_15)).isSameAs(good);
    }

    @Test
    void failsWithoutAnEarlierCatalog() {
        pages.failing = true;

        assertThatThrownBy(() -> cache.get(IPHONE_15)).isInstanceOf(HtmlLoadException.class);
    }

    private static class CountingPages implements HtmlLoader {
        final List<String> loaded = new ArrayList<>();
        boolean failing;

        @Override
        public Document load(String url) {
            loaded.add(url);
            if (failing) throw new HtmlLoadException(url, new IOException("503"));
            return RebuyEvaluatorTest.fixture("iphone-15.html");
        }
    }

    private static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
