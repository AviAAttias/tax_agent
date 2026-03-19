package com.abco.taxassessment.event.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all agent pipeline events (§9.4).
 *
 * Mandatory fields per domain event schema:
 *   eventId, eventType, version, occurredAt, tenantId, aggregateType, aggregateId
 *
 * Consumers re-establish TenantContext from tenantId in the Kafka message header (§9.5).
 */
public abstract class BaseAgentEvent {

    private String eventId;
    private String eventType;
    private int version;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt;
    private UUID tenantId;
    private String aggregateType;
    private UUID aggregateId;

    protected BaseAgentEvent() {}

    protected BaseAgentEvent(String eventType, int version, UUID tenantId,
                              String aggregateType, UUID aggregateId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.version = version;
        this.occurredAt = Instant.now();
        this.tenantId = tenantId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public int getVersion() { return version; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getTenantId() { return tenantId; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setVersion(int version) { this.version = version; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
}
