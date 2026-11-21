package dev.shop.common.event;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "false")
public class LocalOutboxRelay {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MeterRegistry meters;

    public LocalOutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${commerce.outbox.poll-ms:200}")
    public void publish() {
        try {
            transactions.executeWithoutResult(status -> {
                // One database hosts every schema in single-process mode, so the lock is per schema.
                if (!Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(7419201, hashtext(current_schema()))", Boolean.class))) return;
                var rows = jdbc.query("SELECT sequence_id, aggregate_id, body FROM outbox WHERE published_at IS NULL ORDER BY sequence_id LIMIT 25 FOR UPDATE",
                        (r, n) -> new Pending(r.getLong(1), r.getObject(2, UUID.class), r.getString(3)));
                for (var row : rows) {
                    LocalEvents.publish(row.body());
                    jdbc.update("UPDATE outbox SET published_at=now() WHERE sequence_id=?", row.sequence());
                    meters.counter("commerce.outbox.published").increment();
                }
            });
        } catch (Exception ex) {
            meters.counter("commerce.outbox.failures").increment();
            LoggerFactory.getLogger(getClass()).warn("Outbox retained for retry: {}", ex.getMessage());
        }
    }

    private record Pending(long sequence, UUID key, String body) {}
}
