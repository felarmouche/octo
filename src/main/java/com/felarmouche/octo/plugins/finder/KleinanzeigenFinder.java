package com.felarmouche.octo.plugins.finder;

import com.felarmouche.octo.application.WebScraper;
import com.felarmouche.octo.core.finder.BaseFinder;
import com.felarmouche.octo.core.finder.FindException;
import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.listing.Listing;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
public class KleinanzeigenFinder extends BaseFinder {

    private static final int MAX_PAGES = 10;
    private static final String PLUGIN_ID = "kleinanzeigen-finder";

    public KleinanzeigenFinder(String searchUrl, int maxListings) {
        super(PLUGIN_ID, searchUrl, maxListings);
    }

    @Override
    public <T> List<Listing> find(String convertTo) {
        List<Listing> listings = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES && listings.size() < getMaxListingsPerSearch(); page++) {
            Elements ads = Objects.requireNonNull(WebScraper.load(pageUrl(page))).select("article[data-adid]");
            if (ads.isEmpty()) break;

            for (Element ad : ads) {
                if (listings.size() >= getMaxListingsPerSearch()) break;
                Element linkEl = ad.selectFirst("a[href*=/s-anzeige/]");
                if (linkEl == null) continue;

                String url = linkEl.absUrl("href");
                String externalId = ad.attr("data-adid");
                try {
                    listings.add(KleinanzeigenListingConverter.convert(fetchDetails(url, externalId), convertTo));
                    Thread.sleep(500 + (long) (Math.random() * 1000)); // Drosselung gegen Blocking
                } catch (FindException e) {
                    log.error("Error while fetching details for listing {} with Plugin '{}' : {}", url, PLUGIN_ID, e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return listings;
                }
            }
        }
        return listings;
    }

    private Listing fetchDetails(String url, String externalId) {
        Document doc = WebScraper.load(url);
        if (doc == null) return null;
        String title = doc.select("#viewad-title").text();

        int price = parsePrice(doc.select("meta[itemprop=price]").attr("content"));
        if (price == 0) price = parsePrice(doc.select("#viewad-price").text());

        String description = "";
        Element descEl = doc.selectFirst("#viewad-description-text");
        if (descEl != null) {
            descEl.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
            description = descEl.wholeText().trim();
        }

        List<String> images = doc.select("[data-ix] img[src]").stream()
                .map(img -> img.absUrl("src"))
                .distinct()
                .toList();

        HashMap<String, String> productSpecificFields = parseProductSpecificFields(doc);

        Fingerprint fp = new Fingerprint(externalId, title, description, images);
        Listing listing = new Listing(externalId, title, description, images, LocalDateTime.now(), fp, price, url, productSpecificFields);
        listing.setProductSpecificFields(productSpecificFields);
        return listing;
    }

    private HashMap<String, String> parseProductSpecificFields(Document doc) {
        HashMap<String, String> fields = new LinkedHashMap<>(); // Reihenfolge bleibt erhalten
        for (Element li : doc.select("#viewad-details li")) {
            Elements spans = li.children().select("span");
            String key, value;
            if (spans.size() >= 2) {                 // aktuelles Layout: <span>Key</span><span>Value</span>
                key = spans.get(0).text();
                value = spans.get(1).text();
            } else if (spans.size() == 1) {          // älteres Layout: Key als Textknoten, Value im <span>
                key = li.ownText();
                value = spans.get(0).text();
            } else continue;
            key = key.replace(":", "").trim();
            value = value.trim();
            if (!key.isEmpty() && !value.isEmpty()) fields.put(key, value);
        }
        return fields;
    }

    private int parsePrice(String raw) {
        int cutCentsIndex = !raw.contains(".") ? raw.indexOf(",") : raw.indexOf(".");
        String withoutCents = cutCentsIndex == -1 ? raw : raw.substring(0, cutCentsIndex);
        String digits = withoutCents.replaceAll("[^0-9,]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Seite 2+ : ".../preis:100:350/seite:2/iphone-15/..."
    private String pageUrl(int page) {
        if (page == 1) return getSearchUrl();
        return getSearchUrl().replaceFirst("(/s-[^/]+(/preis:[^/]+)?)", "$1/seite:" + page);
    }
}