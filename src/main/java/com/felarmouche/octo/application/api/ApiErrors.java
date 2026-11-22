package com.felarmouche.octo.application.api;

import com.felarmouche.octo.application.search.SearchNotFoundException;
import com.felarmouche.octo.application.settings.FieldError;
import com.felarmouche.octo.application.settings.InvalidSettingsException;
import com.felarmouche.octo.application.settings.SettingsCodec;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

import java.util.List;

/** Validation errors as 400 with the field path, unknown Searches as 404. */
@RestControllerAdvice
class ApiErrors {

    /** @param errors each with the path of the offending field, e.g. {@code finders[0].settings.maxListings} */
    record ErrorResponse(List<FieldError> errors) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalid(InvalidSettingsException e) {
        return new ErrorResponse(e.errors());
    }

    /** A body that isn't JSON or doesn't fit the Search's shape, e.g. an unknown Decision. */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse unreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof JacksonException json) {
            String path = SettingsCodec.jsonPath(json);
            return new ErrorResponse(List.of(new FieldError(path.startsWith(".") ? path.substring(1) : path,
                    json.getOriginalMessage())));
        }
        return new ErrorResponse(List.of(new FieldError("", "unreadable request body")));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound(SearchNotFoundException e) {
    }
}
