package com.felarmouche.octo.core.shared;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@Setter
@Getter
public class Listing {
    private String id;
    private String externalId;
    private String title;
    private int priceInEuro;
    private String link;
    private String description;
    private List<String> images;
    private LocalDateTime scrapedAt;
    private Fingerprint fingerprint;
    private Verdict verdict;

    public Listing(String externalId, String title, String description, List<String> images, LocalDateTime scrapedAt, Fingerprint fingerprint, int priceInEuro, String link) {
        this.title = title;
        this.description = description;
        this.images = images;
        this.scrapedAt = scrapedAt;
        this.fingerprint = fingerprint;
        this.externalId = externalId;
        this.link = link;
        this.priceInEuro = priceInEuro;
    }
}
