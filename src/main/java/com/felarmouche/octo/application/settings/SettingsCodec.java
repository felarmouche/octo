package com.felarmouche.octo.application.settings;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.List;

/**
 * Turns stored settings JSON into a typed settings record and validates it, and back.
 * Used for Plugin Settings, the Listing Type Filter, the Exclusion Filter and the retention rule.
 */
@Component
public class SettingsCodec {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // a left-out number must be refused, not become 0
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final Validator validator;

    public SettingsCodec(Validator validator) {
        this.validator = validator;
    }

    /**
     * @param json absent or null means no settings given, i.e. {@code {}}
     * @param path where the settings sit in the Search, prefixed to every field in the errors
     * @throws InvalidSettingsException when the JSON doesn't fit the record or violates its constraints
     */
    public <S extends Record> S decode(JsonNode json, Class<S> type, String path) {
        JsonNode source = json == null || json.isNull() || json.isMissingNode() ? JSON.createObjectNode() : json;
        S settings;
        try {
            settings = JSON.treeToValue(source, type);
        } catch (JacksonException e) {
            throw new InvalidSettingsException(path + jsonPath(e), message(e));
        }
        List<FieldError> errors = validator.validate(settings).stream()
                .map(v -> new FieldError(path + "." + v.getPropertyPath(), v.getMessage()))
                .sorted(Comparator.comparing(FieldError::field))
                .toList();
        if (!errors.isEmpty()) throw new InvalidSettingsException(errors);
        return settings;
    }

    public JsonNode encode(Record settings) {
        return JSON.valueToTree(settings);
    }

    /** The JSON path of a Jackson error, e.g. {@code .price.minInCents} or {@code [1]}. */
    public static String jsonPath(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) path.append('.').append(ref.getPropertyName());
            else if (ref.getIndex() >= 0) path.append('[').append(ref.getIndex()).append(']');
        }
        return path.toString();
    }

    /** A constructor's own check (e.g. min above max) is more readable than Jackson's wrapper message. */
    public static String message(JacksonException e) {
        Throwable cause = e.getCause();
        return cause instanceof IllegalArgumentException && cause.getMessage() != null
                ? cause.getMessage() : e.getOriginalMessage();
    }
}
