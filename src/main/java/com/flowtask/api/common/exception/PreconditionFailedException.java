package com.flowtask.api.common.exception;

/**
 * Base type for failed client preconditions such as a stale {@code If-Match} version; rendered as
 * {@code 412 Precondition Failed}.
 */
public abstract class PreconditionFailedException extends RuntimeException {

    protected PreconditionFailedException(String message) {
        super(message);
    }
}
