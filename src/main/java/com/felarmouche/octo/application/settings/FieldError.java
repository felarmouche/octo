package com.felarmouche.octo.application.settings;

/** @param field the path of the offending field, e.g. {@code finders[0].settings.maxListings} */
public record FieldError(String field, String message) {
}
