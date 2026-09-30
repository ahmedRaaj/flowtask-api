package com.flowtask.api.common.exception;

/**
 * Base type for "resource does not exist" failures; rendered as {@code 404 Not Found}.
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
