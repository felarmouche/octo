package com.felarmouche.octo.plugins.finder;

import com.felarmouche.octo.core.finder.BaseFinder;
import com.felarmouche.octo.core.finder.FindException;
import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.Listing;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class KleinanzeigenFinder extends BaseFinder {

    private static final int MAX_PAGES = 10;
    private static final String PLUGIN_ID = "kleinanzeigen-finder";

    public KleinanzeigenFinder(String searchUrl, int maxListings) {
        super(PLUGIN_ID, searchUrl, maxListings);
    }

    @Override
    public List<Listing> find() {
        List<Listing> listings = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES && listings.size() < getMaxListingsPerSearch(); page++) {
            Elements ads = load(pageUrl(page)).select("article[data-adid]");
            if (ads.isEmpty()) break;

            for (Element ad : ads) {
                if (listings.size() >= getMaxListingsPerSearch()) break;
                Element linkEl = ad.selectFirst("a[href*=/s-anzeige/]");
                if (linkEl == null) continue;

                String url = linkEl.absUrl("href");
                String externalId = ad.attr("data-adid");
                try {
                    listings.add(fetchDetails(url, externalId));
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
        Document doc = load(url);

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

        Fingerprint fp = new Fingerprint(externalId, title, description, images);
        return new Listing(externalId, title, description, images, LocalDateTime.now(), fp, price, url);
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

    private Document load(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .get();
        } catch (IOException e) {
            throw new FindException("Parsing Kleinanzeigen-HTML to Document via JSOUP failed with error: " + e.getMessage(), getId());
        }
    }

    // Seite 2+ : ".../preis:100:350/seite:2/iphone-15/..."
    private String pageUrl(int page) {
        if (page == 1) return getSearchUrl();
        return getSearchUrl().replaceFirst("(/s-[^/]+(/preis:[^/]+)?)", "$1/seite:" + page);
    }
}