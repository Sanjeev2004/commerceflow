package dev.shop.common.event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
@Component
public class EventCodec {
    private final ObjectMapper mapper;
    public EventCodec(ObjectMapper mapper) { this.mapper=mapper; }
    public String encode(Object value) {
        try { return mapper.writeValueAsString(value); } catch(JsonProcessingException ex) { throw new IllegalArgumentException("Cannot serialize event",ex); }
    }
    public DomainEvent decode(String value) {
        try { return mapper.readValue(value,DomainEvent.class); } catch(JsonProcessingException ex) { throw new IllegalArgumentException("Invalid event JSON",ex); }
    }
    public <T> T payload(DomainEvent event,Class<T> type) { return mapper.convertValue(event.payload(),type); }
}
