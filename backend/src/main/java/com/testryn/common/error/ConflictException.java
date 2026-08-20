package com.testryn.common.error;

/**
 * Thrown when a request conflicts with the current state of a resource (e.g. a
 * uniqueness violation the caller should have avoided). Translated to HTTP 409 by
 * {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
