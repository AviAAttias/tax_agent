package com.abco.taxassessment.exception;

/** Signals an OpenAI 429 Too Many Requests response. Retryable (§11.2). */
public class OpenAiRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public OpenAiRateLimitException(long retryAfterSeconds) {
        super("OpenAI rate limit exceeded. Retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
