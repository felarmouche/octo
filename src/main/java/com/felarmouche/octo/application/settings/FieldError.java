package com.felarmouche.octo.application.settings;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param field the path of the offending field, e.g. {@code finders[0].settings.maxListings};
 *              null, and left out of the JSON, when the error belongs to no field of a Search
 */
public record FieldError(@JsonInclude(JsonInclude.Include.NON_NULL) String field, String message) {
}
