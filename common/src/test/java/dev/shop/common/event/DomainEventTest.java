package dev.shop.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainEventTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void validEventSerializesAndDeserializes() throws Exception {
        var data = new SagaData(
                UUID.randomUUID(),
                List.of(new SagaData.Line(UUID.randomUUID(), 2, new BigDecimal("29.99"))),
                new BigDecimal("59.98"),
                SagaData.PaymentMode.SUCCESS, null
        );
        var event = new DomainEvent(UUID.randomUUID(), 1, EventType.ORDER_CREATED,
                UUID.randomUUID(), Instant.now(), "abc-123", mapper.valueToTree(data));

        String json = mapper.writeValueAsString(event);
        DomainEvent decoded = mapper.readValue(json, DomainEvent.class);

        assertEquals(event.eventId(), decoded.eventId());
        assertEquals(event.type(), decoded.type());
        assertEquals(event.aggregateId(), decoded.aggregateId());
        assertEquals(data.total(), decoded.payload().path("total").decimalValue());
    }

    @Test
    void rejectsInvalidCorrelationId() {
        assertThrows(IllegalArgumentException.class, () ->
                new DomainEvent(UUID.randomUUID(), 1, EventType.ORDER_CREATED,
                        UUID.randomUUID(), Instant.now(), "", mapper.createObjectNode()));
    }

    @Test
    void rejectsWrongSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                new DomainEvent(UUID.randomUUID(), 2, EventType.ORDER_CREATED,
                        UUID.randomUUID(), Instant.now(), "abc", mapper.createObjectNode()));
    }
}
