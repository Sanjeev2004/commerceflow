package dev.shop.common.event;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
public record DomainEvent(UUID eventId, int schemaVersion, EventType type, UUID aggregateId,
                          Instant occurredAt, String correlationId, JsonNode payload) {
    public DomainEvent {
        if(eventId==null || schemaVersion!=1 || type==null || aggregateId==null || occurredAt==null || payload==null
                || correlationId==null || !correlationId.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid event envelope");
    }
}
