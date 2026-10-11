package com.handyai.build.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, Object identifier) {
        return new ResourceNotFoundException(what + " '" + identifier + "' was not found");
    }
}
