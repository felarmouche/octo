package com.felarmouche.octo.application.api;

import com.felarmouche.octo.application.search.SearchNotFoundException;
import com.felarmouche.octo.application.settings.FieldError;
import com.felarmouche.octo.application.settings.InvalidSettingsException;
import com.felarmouche.octo.application.settings.SettingsCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

import java.util.List;

/**
 * Every error of the API has the same body, {@code {errors: [{field?, message}]}}, with {@code field} only where
 * an input field is at fault. Validation errors are 400, unknown Searches 404, failures 500. The 401 without a
 * session comes from {@link ApiSecurity}.
 */
@Slf4j
@RestControllerAdvice
class ApiErrors {

    /** @param errors each with the path of the offending field, e.g. {@code finders[0].settings.maxListings} */
    record ErrorResponse(List<FieldError> errors) {
        /** One error that belongs to no field. */
        static ErrorResponse of(String message) {
            return new ErrorResponse(List.of(new FieldError(null, message)));
        }
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
            String field = path.isEmpty() ? null : path.startsWith(".") ? path.substring(1) : path;
            return new ErrorResponse(List.of(new FieldError(field, json.getOriginalMessage())));
        }
        return ErrorResponse.of("unreadable request body");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound(SearchNotFoundException e) {
        return ErrorResponse.of(e.getMessage());
    }

    /** Input a constructor refused that validation let through, e.g. a time window that ends before it starts. */
    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalid(IllegalArgumentException e) {
        return ErrorResponse.of(e.getMessage() != null ? e.getMessage() : "invalid request");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorResponse forbidden(AccessDeniedException e) {
        return ErrorResponse.of("forbidden");
    }

    /**
     * What Spring MVC refuses itself (e.g. an unknown endpoint, a wrong method or a missing parameter) with its
     * status; anything else is a 500 that is logged and shows nothing of its cause.
     */
    @ExceptionHandler
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse refused) {
            String detail = refused.getBody().getDetail();
            return ResponseEntity.status(refused.getStatusCode()).headers(refused.getHeaders())
                    .body(ErrorResponse.of(detail != null ? detail : refused.getStatusCode().toString()));
        }
        log.error("request failed", e);
        return ResponseEntity.internalServerError().body(ErrorResponse.of("internal error"));
    }
}
