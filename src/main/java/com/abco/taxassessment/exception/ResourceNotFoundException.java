package com.abco.taxassessment.exception;

import java.util.UUID;

/** Maps to HTTP 404. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceType, UUID id) {
        super("RESOURCE_NOT_FOUND", resourceType + " not found: " + id);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("RESOURCE_NOT_FOUND", resourceType + " not found: " + identifier);
    }
}
