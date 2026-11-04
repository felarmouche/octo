package com.felarmouche.octo.core.shared;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Objects;

@AllArgsConstructor
public class Fingerprint {
    private String externalId;
    private String title;
    private String description;
    private List<String> images;

    @Override
    public int hashCode() {
        return Objects.hash(externalId, title, description, images);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Fingerprint that = (Fingerprint) o;
        return that.hashCode() == hashCode();
    }
}
