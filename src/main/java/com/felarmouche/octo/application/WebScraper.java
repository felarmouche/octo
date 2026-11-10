package com.felarmouche.octo.application;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

@Slf4j
public class WebScraper {

    public static Document load(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .get();
        } catch (IOException e) {
            log.error("Parsing HTML to Document via JSOUP failed with error: {}", e.getMessage());
            return null;
        }
    }
}
