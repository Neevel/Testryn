package com.testryn.common.error;

/**
 * Thrown when a requested resource does not exist. Translated to HTTP 404 by
 * {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException("%s not found: %s".formatted(entity, id));
    }
}
