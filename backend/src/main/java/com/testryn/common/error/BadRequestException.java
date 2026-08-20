package com.testryn.common.error;

/**
 * Thrown for semantically invalid requests that Bean Validation cannot express
 * (e.g. cross-field rules). Translated to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
