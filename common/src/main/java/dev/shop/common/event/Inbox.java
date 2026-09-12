package dev.shop.common.event;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class Inbox {
    private final JdbcTemplate jdbc;
    public Inbox(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional
    public void once(DomainEvent event,Runnable action) {
        if(jdbc.update("INSERT INTO processed_events(event_id) VALUES (?) ON CONFLICT DO NOTHING",event.eventId())==0) return;
        try(var ignored=MDC.putCloseable("correlationId",event.correlationId())) { action.run(); }
    }
}
