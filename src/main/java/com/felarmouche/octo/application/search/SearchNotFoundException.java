package com.felarmouche.octo.application.search;

public class SearchNotFoundException extends RuntimeException {
    public SearchNotFoundException(String id) {
        super("Search not found: " + id);
    }
}
