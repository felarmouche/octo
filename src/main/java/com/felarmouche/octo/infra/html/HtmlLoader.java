package com.felarmouche.octo.infra.html;

import org.jsoup.nodes.Document;

/** Loads an HTML page. Throws {@link HtmlLoadException} when the page can't be loaded, never returns null. */
@FunctionalInterface
public interface HtmlLoader {

    Document load(String url);
}
