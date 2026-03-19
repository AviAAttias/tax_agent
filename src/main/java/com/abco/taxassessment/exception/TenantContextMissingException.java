package com.abco.taxassessment.exception;

/**
 * Thrown when tenant context is required but not established.
 * Maps to HTTP 401 via GlobalExceptionHandler.
 */
public class TenantContextMissingException extends RuntimeException {

    public TenantContextMissingException(String message) {
        super(message);
    }
}
