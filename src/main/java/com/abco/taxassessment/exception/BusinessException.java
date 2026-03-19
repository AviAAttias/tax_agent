package com.abco.taxassessment.exception;

/**
 * Base class for domain business rule violations.
 * These are non-retryable (§11.2 ignoreExceptions) and map to 4xx responses.
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
