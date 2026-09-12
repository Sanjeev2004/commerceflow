package dev.shop.common.event;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
@Component
@ConditionalOnProperty(name="commerce.messaging.enabled",havingValue="true")
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String,String> kafka;
    private final TransactionTemplate transactions;
    private final MeterRegistry meters;
    public OutboxRelay(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,TransactionTemplate transactions,MeterRegistry meters) { this.jdbc=jdbc; this.kafka=kafka; this.transactions=transactions; this.meters=meters; }
    @Scheduled(fixedDelayString="${commerce.outbox.poll-ms:500}")
    public void publish() {
        try {
            transactions.executeWithoutResult(status->{
                // One relay per service database preserves aggregate ordering across replicas.
                if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(7419201)",Boolean.class))) return;
                var rows=jdbc.query("SELECT sequence_id,aggregate_id,body FROM outbox WHERE published_at IS NULL ORDER BY sequence_id LIMIT 25 FOR UPDATE",
                        (r,n)->new Pending(r.getLong(1),r.getObject(2,UUID.class),r.getString(3)));
                for(var row:rows) {
                    try { kafka.send("commerce.events",row.key().toString(),row.body()).get(20,TimeUnit.SECONDS); }
                    catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Outbox interrupted",ex); }
                    catch(Exception ex) { throw new IllegalStateException("Kafka publish failed",ex); }
                    jdbc.update("UPDATE outbox SET published_at=now() WHERE sequence_id=?",row.sequence());
                    meters.counter("commerce.outbox.published").increment();
                }
            });
        } catch(Exception ex) { meters.counter("commerce.outbox.failures").increment(); LoggerFactory.getLogger(getClass()).warn("Outbox retained for retry: {}",ex.getMessage()); }
    }
    private record Pending(long sequence,UUID key,String body) {}
}
