package com.abco.taxassessment.exception;

/** Thrown when OpenAI response fails JSON Schema strict validation. Non-retryable after max attempts. */
public class OpenAiSchemaViolationException extends BusinessException {

    public OpenAiSchemaViolationException(String operation, String details) {
        super("OPENAI_SCHEMA_VIOLATION",
              "OpenAI response for operation '" + operation + "' failed schema validation: " + details);
    }
}
