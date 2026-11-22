package com.felarmouche.octo.infra.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsoupHtmlLoader implements HtmlLoader {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    @Override
    public Document load(String url) {
        try {
            return Jsoup.connect(url).userAgent(USER_AGENT).get();
        } catch (IOException | IllegalArgumentException e) {
            throw new HtmlLoadException(url, e);
        }
    }
}
