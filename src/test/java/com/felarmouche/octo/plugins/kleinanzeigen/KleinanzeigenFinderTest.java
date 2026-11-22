package com.felarmouche.octo.plugins.kleinanzeigen;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.infra.html.HtmlLoadException;
import com.felarmouche.octo.infra.html.HtmlLoader;
import com.felarmouche.octo.types.generic.GenericListingType;
import com.felarmouche.octo.types.phone.Battery;
import com.felarmouche.octo.types.phone.PhoneAttributes;
import com.felarmouche.octo.types.phone.PhoneCondition;
import com.felarmouche.octo.types.phone.PhoneListingType;
import com.felarmouche.octo.types.phone.Storage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KleinanzeigenFinderTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private static final String SEARCH_URL = "https://www.kleinanzeigen.de/s-preis::250/ps5/k0";
    private static final String PAGE_2_URL = "https://www.kleinanzeigen.de/s-preis::250/seite:2/ps5/k0";
    private static final String PAGE_3_URL = "https://www.kleinanzeigen.de/s-preis::250/seite:3/ps5/k0";
    private static final String AD_1001 = "https://www.kleinanzeigen.de/s-anzeige/ps5-disc-edition-mit-controller/1001-279-1234";
    private static final String AD_1002 = "https://www.kleinanzeigen.de/s-anzeige/playstation-5-digital/1002-279-1234";
    private static final String AD_1003 = "https://www.kleinanzeigen.de/s-anzeige/ps5-slim/1003-279-1234";
    private static final String PHONE_SEARCH_URL = "https://www.kleinanzeigen.de/s-iphone-13/k0";

    private final FixturePages pages = new FixturePages(Map.of(
            SEARCH_URL, "results-page-1.html",
            PAGE_2_URL, "results-page-2.html",
            PAGE_3_URL, "results-empty.html",
            AD_1001, "detail-1001.html",
            AD_1002, "detail-1002.html",
            AD_1003, "detail-1003.html",
            PHONE_SEARCH_URL, "results-phones.html",
            "https://www.kleinanzeigen.de/s-iphone-13/seite:2/k0", "results-empty.html",
            "https://www.kleinanzeigen.de/s-anzeige/iphone-13-128-gb-blau/2001-173-1234", "detail-2001.html",
            "https://www.kleinanzeigen.de/s-anzeige/iphone-13-64gb-defekt/2002-173-1234", "detail-2002.html"));

    private final KleinanzeigenFinder finder =
            new KleinanzeigenFinder(pages, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ZERO);

    @Test
    void turnsResultAndDetailPagesIntoListings() {
        Finder.Result result = find(10);

        assertThat(result.problems()).isEmpty();
        assertThat(result.listings()).extracting(Listing::externalId).containsExactly("1001", "1002", "1003");

        Listing ps5 = result.listings().getFirst();
        assertThat(ps5.marketplace()).isEqualTo("kleinanzeigen");
        assertThat(ps5.title()).isEqualTo("PS5 Disc Edition mit Controller");
        assertThat(ps5.link()).isEqualTo(AD_1001);
        assertThat(ps5.priceInCents()).contains(240_00);
        assertThat(ps5.description()).isEqualTo(
                "Verkaufe meine PS5 Disc Edition.\nEin Controller ist dabei.\nKeine Garantie, Privatverkauf.");
        assertThat(ps5.images()).containsExactly(
                "https://img.kleinanzeigen.de/api/v1/prod-ads/images/aa/1001-a?rule=$_59.AUTO",
                "https://img.kleinanzeigen.de/api/v1/prod-ads/images/aa/1001-b?rule=$_59.AUTO");
        assertThat(ps5.marketplaceDetails()).containsExactlyInAnyOrderEntriesOf(
                Map.of("Art", "PlayStation", "Versand", "Nur Abholung"));
        assertThat(ps5.foundAt()).isEqualTo(NOW);
        assertThat(ps5.attributes()).isEmpty();
    }

    @Test
    void vbOrZeroBecomesNoPriceAndPriceFallsBackToThePriceText() {
        List<Listing> listings = find(10).listings();

        assertThat(listings.get(1).priceInCents()).isEmpty();          // meta 0, text "VB"
        assertThat(listings.get(2).priceInCents()).contains(230_00);   // no meta, text "230 € VB"
    }

    @Test
    void stopsAtMaxListingsWithoutLoadingMorePages() {
        List<Listing> listings = find(2).listings();

        assertThat(listings).extracting(Listing::externalId).containsExactly("1001", "1002");
        assertThat(pages.loaded).doesNotContain(PAGE_2_URL);
    }

    @Test
    void aFailingDetailPageOnlySkipsThatAd() {
        pages.fail(AD_1002);

        Finder.Result result = find(10);

        assertThat(result.listings()).extracting(Listing::externalId).containsExactly("1001", "1003");
        assertThat(result.problems()).singleElement().asString().contains(AD_1002).contains("HTTP 404");
    }

    @Test
    void aFailingResultsPageEndsTheRunWithAProblemAndKeepsWhatWasFound() {
        pages.fail(PAGE_2_URL);

        Finder.Result result = find(10);

        assertThat(result.listings()).extracting(Listing::externalId).containsExactly("1001", "1002");
        assertThat(result.problems()).singleElement().asString().contains(PAGE_2_URL);
    }

    @Test
    void anUnreachableFirstPageGivesNoListingsAndAProblem() {
        pages.fail(SEARCH_URL);

        Finder.Result result = find(10);

        assertThat(result.listings()).isEmpty();
        assertThat(result.problems()).singleElement().asString().contains(SEARCH_URL);
    }

    @Test
    void forPhonesMapsItsOwnFieldsOntoPhoneAttributesWithoutReadingTheText() {
        List<Listing> phones = finder.find(new KleinanzeigenSettings(PHONE_SEARCH_URL, 10), new PhoneListingType()).listings();

        assertThat(phones).extracting(Listing::attributes).containsExactly(
                // the "74 %" in the description is left to the phone Listing Type
                Optional.of(new PhoneAttributes(Optional.of(Storage.GB_128), Optional.of(new Battery(0, 79)),
                        Optional.of("Blau"), Optional.of(PhoneCondition.VERY_GOOD))),
                // the "64GB" in the title is left to the phone Listing Type
                Optional.of(new PhoneAttributes(Optional.empty(), Optional.of(Battery.exact(0)),
                        Optional.empty(), Optional.of(PhoneCondition.DEFECT))));
    }

    // --- helpers -----------------------------------------------------------------------------

    private Finder.Result find(int maxListings) {
        return finder.find(new KleinanzeigenSettings(SEARCH_URL, maxListings), new GenericListingType());
    }

    /** Serves saved HTML fixtures by URL; any other URL fails like an unreachable page. */
    static class FixturePages implements HtmlLoader {
        private final Map<String, String> fixtures;
        final List<String> loaded = new ArrayList<>();

        FixturePages(Map<String, String> fixtures) {
            this.fixtures = new HashMap<>(fixtures);
        }

        void fail(String url) {
            fixtures.remove(url);
        }

        @Override
        public Document load(String url) {
            loaded.add(url);
            String fixture = fixtures.get(url);
            if (fixture == null) throw new HtmlLoadException(url, new IOException("HTTP 404"));
            try (InputStream in = getClass().getResourceAsStream("/kleinanzeigen/" + fixture)) {
                return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), url);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
