package com.handyai.build.exception;

import java.util.Map;

public class BadRequestException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public BadRequestException(String message) {
        this(message, null);
    }

    /** For checks that bean validation cannot express, so the form can still mark each field. */
    public BadRequestException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
