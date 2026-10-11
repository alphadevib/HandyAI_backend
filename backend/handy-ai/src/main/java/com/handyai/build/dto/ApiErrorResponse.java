package com.handyai.build.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(Instant timestamp, int status, String error, String message,
                               String path, Map<String, String> fieldErrors, String code) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null, null);
    }

    /** With a machine-readable {@code code} the frontend can act on, e.g. EMAIL_NOT_VERIFIED. */
    public static ApiErrorResponse coded(int status, String error, String message, String path,
                                         String code) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null, code);
    }

    public static ApiErrorResponse validation(String message, String path,
                                              Map<String, String> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), 400, "Bad Request", message, path, fieldErrors,
                null);
    }
}
