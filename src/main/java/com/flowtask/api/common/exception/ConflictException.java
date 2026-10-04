package com.flowtask.api.common.exception;

/**
 * Base type for requests that conflict with the current state of a resource; rendered as
 * {@code 409 Conflict}.
 */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }
}
