package com.felarmouche.octo.core.shared.listing;

import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.Verdict;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
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
    private HashMap<String,String> productSpecificFields;

    public Listing(String externalId, String title, String description, List<String> images, LocalDateTime scrapedAt, Fingerprint fingerprint, int priceInEuro, String link, HashMap<String,String> productSpecificFields) {
        this.title = title;
        this.description = description;
        this.images = images;
        this.scrapedAt = scrapedAt;
        this.fingerprint = fingerprint;
        this.externalId = externalId;
        this.link = link;
        this.priceInEuro = priceInEuro;
        this.productSpecificFields = productSpecificFields;
    }

    @Override
    public String toString() {
        return
                "Title: " + title + "\n" +
                "Price: " + priceInEuro + " EUR\n" +
                "Link: " + link + "\n" +
                "Description: " + description + "\n" +
                "Images: " + images + "\n" +
                "Product-specific fields: " + productSpecificFields;
    }

}
