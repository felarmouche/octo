package com.felarmouche.octo.plugins.kleinanzeigen;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.ListingAttributes;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.infra.html.HtmlLoader;
import com.felarmouche.octo.types.generic.GenericListingType;
import com.felarmouche.octo.types.phone.Battery;
import com.felarmouche.octo.types.phone.PhoneAttributes;
import com.felarmouche.octo.types.phone.PhoneCondition;
import com.felarmouche.octo.types.phone.PhoneListingType;
import com.felarmouche.octo.types.phone.Storage;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reads Kleinanzeigen's search result pages and each ad's detail page.
 * A failing results page ends this Finder's run; a failing detail page only skips that ad.
 * Both are reported as problems, together with the Listings found so far.
 */
@Slf4j
@Component(KleinanzeigenFinder.ID)
public class KleinanzeigenFinder implements Finder<KleinanzeigenSettings> {

    public static final String ID = "kleinanzeigen-finder";
    static final String MARKETPLACE = "kleinanzeigen";
    private static final int MAX_PAGES = 10;

    private final HtmlLoader pages;
    private final Clock clock;
    private final Duration pauseBetweenAds;

    @Autowired
    public KleinanzeigenFinder(HtmlLoader pages) {
        this(pages, Clock.systemUTC(), Duration.ofMillis(500));
    }

    /** @param pauseBetweenAds the minimum pause between two detail pages (plus up to 1 s jitter), against blocking */
    public KleinanzeigenFinder(HtmlLoader pages, Clock clock, Duration pauseBetweenAds) {
        this.pages = pages;
        this.clock = clock;
        this.pauseBetweenAds = pauseBetweenAds;
    }

    @Override
    public Result find(KleinanzeigenSettings settings, ListingType<?> listingType) {
        List<Listing> listings = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        String previousPageUrl = null;
        for (int page = 1; page <= MAX_PAGES && listings.size() < settings.maxListings(); page++) {
            String pageUrl = pageUrl(settings.searchUrl(), page);
            if (pageUrl.equals(previousPageUrl)) break; // URL can't be paged
            previousPageUrl = pageUrl;

            Elements ads;
            try {
                ads = pages.load(pageUrl).select("article[data-adid]");
            } catch (RuntimeException e) {
                problems.add("Ergebnisseite " + pageUrl + " nicht ladbar: " + e.getMessage());
                break;
            }
            if (ads.isEmpty()) break;

            for (Element ad : ads) {
                if (listings.size() >= settings.maxListings()) break;
                Element linkEl = ad.selectFirst("a[href*=/s-anzeige/]");
                if (linkEl == null) continue;

                String url = linkEl.absUrl("href");
                try {
                    listings.add(fetchDetails(url, ad.attr("data-adid"), listingType));
                } catch (RuntimeException e) {
                    log.warn("Skipping Kleinanzeigen ad {}: {}", url, e.getMessage());
                    problems.add("Anzeige " + url + " übersprungen: " + e.getMessage());
                }
                if (!pause()) return new Result(listings, problems);
            }
        }
        return new Result(listings, problems);
    }

    private Listing fetchDetails(String url, String externalId, ListingType<?> listingType) {
        Document doc = pages.load(url);
        String title = doc.select("#viewad-title").text();
        if (title.isBlank()) throw new IllegalStateException("keine Anzeigenseite (kein Titel)");

        int price = parsePrice(doc.select("meta[itemprop=price]").attr("content"));
        if (price == 0) price = parsePrice(doc.select("#viewad-price").text());

        String description = "";
        Element descEl = doc.selectFirst("#viewad-description-text");
        if (descEl != null) {
            descEl.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
            description = descEl.wholeText().strip().replaceAll("[ \\t]*\\n[ \\t]*", "\n");
        }

        List<String> images = doc.select("[data-ix] img[src]").stream()
                .map(img -> img.absUrl("src"))
                .distinct()
                .toList();

        Map<String, String> details = parseMarketplaceDetails(doc);
        return new Listing(MARKETPLACE, externalId, title, description, url, images, clock.instant(),
                price > 0 ? Optional.of(price * 100) : Optional.empty(),
                details, attributes(listingType, details));
    }

    /** Maps only Kleinanzeigen's own fields; text reading is left to the Listing Type. */
    private static Optional<ListingAttributes> attributes(ListingType<?> listingType, Map<String, String> details) {
        if (!listingType.id().equals(PhoneListingType.ID)) return Optional.empty();
        return Optional.of(new PhoneAttributes(
                Storage.parse(details.get("Speicher (GB)")),
                Battery.fromHint(details.get("Akkuzustand")),
                Optional.ofNullable(details.get("Farbe")),
                phoneCondition(details.get("Zustand"))));
    }

    private static Optional<PhoneCondition> phoneCondition(String wording) {
        if (wording == null) return Optional.empty();
        return Optional.ofNullable(switch (wording.strip().toLowerCase(Locale.ROOT)) {
            case "neu" -> PhoneCondition.NEW;
            case "sehr gut" -> PhoneCondition.VERY_GOOD;
            case "gut" -> PhoneCondition.GOOD;
            case "in ordnung" -> PhoneCondition.OK;
            case "defekt" -> PhoneCondition.DEFECT;
            default -> null;
        });
    }

    private static Map<String, String> parseMarketplaceDetails(Document doc) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Element li : doc.select("#viewad-details li")) {
            Elements spans = li.children().select("span");
            String key, value;
            if (spans.size() >= 2) {                 // current layout: <span>Key</span><span>Value</span>
                key = spans.get(0).text();
                value = spans.get(1).text();
            } else if (spans.size() == 1) {          // older layout: key as text node, value in <span>
                key = li.ownText();
                value = spans.get(0).text();
            } else continue;
            key = key.replace(":", "").trim();
            value = value.trim();
            if (!key.isEmpty() && !value.isEmpty()) fields.put(key, value);
        }
        return fields;
    }

    /** Whole euros from "240.00", "240 €", "230 € VB"; 0 when there is no price. */
    private static int parsePrice(String raw) {
        int cutCentsIndex = !raw.contains(".") ? raw.indexOf(",") : raw.indexOf(".");
        String withoutCents = cutCentsIndex == -1 ? raw : raw.substring(0, cutCentsIndex);
        String digits = withoutCents.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Page 2+: ".../s-preis:100:350/seite:2/iphone-15/..." */
    private static String pageUrl(String searchUrl, int page) {
        if (page == 1) return searchUrl;
        return searchUrl.replaceFirst("(/s-[^/]+(/preis:[^/]+)?)", "$1/seite:" + page);
    }

    /** @return false when interrupted */
    private boolean pause() {
        if (pauseBetweenAds.isZero()) return true;
        try {
            Thread.sleep(pauseBetweenAds.toMillis() + ThreadLocalRandom.current().nextLong(1000));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Kleinanzeigen";
    }

    @Override
    public Set<String> listingTypes() {
        return Set.of(GenericListingType.ID, PhoneListingType.ID);
    }

    @Override
    public Class<KleinanzeigenSettings> settingsType() {
        return KleinanzeigenSettings.class;
    }
}
