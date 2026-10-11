package com.handyai.build.exception;

/** Signed in, but not allowed to do this. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
