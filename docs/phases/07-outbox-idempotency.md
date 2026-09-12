# Phase 7 — Transactional outbox, idempotency, retries, dead-letter queue

```
common/.../event/Outbox.java
common/.../event/Inbox.java
common/.../event/OutboxRelay.java
common/.../config/MessagingConfig.java
common/src/main/resources/db/common/V1__messaging.sql
```

## Outbox Pattern

Every service that publishes Kafka events does so through a transactional outbox. The event row is written in the **same database transaction** as the domain mutation. A separate relay polls unpublished outbox rows and publishes them to Kafka. This eliminates the dual-write problem: we never write to Postgres and Kafka in the same transaction.

### Relay mechanics
- `OutboxRelay` polls every 500ms for unpublished rows (`WHERE published_at IS NULL`), ordered by sequence.
- It attempts a PostgreSQL advisory lock (`pg_try_advisory_xact_lock`) so only one relay replica publishes per database; another replica skips that poll rather than blocking.
- After successful Kafka send (`acks=all`), the row is marked `published_at`.
- If Kafka is down, rows remain unpublished and are retried on next poll. No data is lost.

### Event types and domains

| Event | Source | Key | Aggregate |
|-------|--------|-----|-----------|
| PRODUCT_CHANGED | Product Service | product UUID | Product |
| ORDER_CREATED | Order Service | order UUID | Order |
| INVENTORY_RESERVED | Inventory Service | order UUID | Order |
| INVENTORY_REJECTED | Inventory Service | order UUID | Order |
| PAYMENT_REQUESTED | Order Service | order UUID | Order |
| PAYMENT_COMPLETED | Payment Service | order UUID | Order |
| PAYMENT_FAILED | Payment Service | order UUID | Order |
| INVENTORY_RELEASE_REQUESTED | Order Service | order UUID | Order |
| INVENTORY_RELEASED | Inventory Service | order UUID | Order |
| ORDER_CONFIRMED | Order Service | order UUID | Order |
| ORDER_CANCELLED | Order Service | order UUID | Order |

## Idempotency

### Consumers (Inbox pattern)
Every Kafka consumer inserts the event UUID into a `processed_events` table before executing business logic. If the row already exists (duplicate delivery), the handler is skipped. This is inside the same transaction as the domain mutation.

### Restocking
Inventory uses an `Idempotency-Key` header (UUID). The key is stored in a `restocks` table. Duplicate requests with the same key are silently acknowledged; requests with the same key but different payload are rejected as 409.

### Order creation
Order uses `(user_id, idempotency_key)` unique constraint plus a SHA-256 hash of the request body. Same key + same body = return existing order. Same key + different body = 409 Conflict.

### Payment
Payment uses `(order_id)` unique constraint. Only the first payment event for an order is persisted.

## Retry and DLQ

### DefaultErrorHandler
- Exponential backoff: 1s → 2s → 4s → 8s (4 retries max).
- After max retries, the message is published to `commerce.events.<consumer-group>.DLT`.
- A `MeterRegistry` counter tracks consumer failures.
- DLQ messages are retained for 30 days for inspection.

### Consumer groups per service
Each service has its own Spring application name, hence its own consumer group. This means all replicas of the same service share a group and load-balance partitions.

## Eventual consistency windows

1. **Order created → Inventory reserved**: ~100-500ms depending on Kafka lag.
2. **Payment processed → Order confirmed**: Same range.
3. **Product changed → Search index updated**: Same range + Elasticsearch refresh (~1s).
4. **Product written → Redis cache evicted**: Same range + Redis TTL (max 30s residual).

All user-visible flows are designed so that polling a resource yields the correct state once the workflow completes. No flow depends on instant cross-service consistency.

## Run
```
docker compose up --build
# Submit an order, watch events:
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic commerce.events --from-beginning

# Check outbox status:
docker compose exec postgres-order psql -U commerce -d order_db -c \
  "SELECT count(*) FILTER (WHERE published_at IS NULL) AS pending FROM outbox;"
```
