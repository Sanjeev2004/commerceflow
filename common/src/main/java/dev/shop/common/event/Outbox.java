package dev.shop.common.event;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
@Component
public class Outbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EventCodec codec;
    public Outbox(JdbcTemplate jdbc,ObjectMapper mapper,EventCodec codec) { this.jdbc=jdbc; this.mapper=mapper; this.codec=codec; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void append(EventType type,UUID aggregateId,Object payload) {
        String correlationId=MDC.get("correlationId");
        if(correlationId==null) correlationId=UUID.randomUUID().toString();
        var event=new DomainEvent(UUID.randomUUID(),1,type,aggregateId,Instant.now(),correlationId,mapper.valueToTree(payload));
        jdbc.update("INSERT INTO outbox(event_id,aggregate_id,body) VALUES (?,?,?)",event.eventId(),aggregateId,codec.encode(event));
    }
}
