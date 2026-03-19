package com.abco.taxassessment.exception;

import java.util.UUID;

/** Thrown when a statement upload is detected as a duplicate. Maps to HTTP 409. */
public class DuplicateStatementException extends BusinessException {

    public DuplicateStatementException(UUID existingStatementId) {
        super("DUPLICATE_STATEMENT",
              "Statement already exists with id: " + existingStatementId + ". Statement quarantined.");
    }
}
