package com.felarmouche.octo.application.settings;

import java.util.List;

/** Settings that can't be saved or run, with the fields at fault. */
public class InvalidSettingsException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidSettingsException(List<FieldError> errors) {
        super(errors.stream().map(e -> e.field() + ": " + e.message()).reduce((a, b) -> a + "; " + b).orElse(""));
        this.errors = List.copyOf(errors);
    }

    public InvalidSettingsException(String field, String message) {
        this(List.of(new FieldError(field, message)));
    }

    public List<FieldError> errors() {
        return errors;
    }
}
