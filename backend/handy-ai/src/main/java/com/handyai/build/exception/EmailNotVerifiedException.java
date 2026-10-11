package com.handyai.build.exception;

/** Right password, but the account's email has not been confirmed yet. */
public class EmailNotVerifiedException extends ForbiddenException {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
