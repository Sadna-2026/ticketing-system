package com.ticketing.domain.exception;

public class OptimisticLockException extends RuntimeException {

    private final String entityType;
    private final Object entityId;

    public OptimisticLockException(String entityType, Object entityId) {
        super(String.format("Optimistic lock conflict on %s with id=%s. " +
                "The entity was modified by another operation. Please retry.", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public String getEntityType() { return entityType; }
    public Object getEntityId() { return entityId; }
}

