# Operations runbook

## Health and diagnosis

Run `docker compose ps` and `docker compose logs <service>`. Application readiness checks are provided by the shared Dockerfile. Gateway readiness includes Redis because every API request requires its rate limiter. Business-service readiness signals that Spring has started; inspect the aggregate `/actuator/health` and infrastructure health checks for dependency failures. Search creates its index lazily on the first search or projection.

Requests carry `X-Correlation-ID`; service logs include it. Poll orders for asynchronous progress. The outbox relay retries Kafka delivery every 500 ms; consumers retry four times with exponential backoff before routing to a group-specific DLT.

```sh
docker compose exec postgres-order psql -U commerce -d order_db -c "SELECT count(*) AS pending, min(created_at) AS oldest FROM outbox WHERE published_at IS NULL;"
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --all-groups --describe
```

Prometheus scrapes all eight application management ports. Commerce Overview in Grafana displays request rates, latency, JVM memory, publication rate, consumer failures, and payment failures. Counters appear after their first occurrence.

## Dependency outages

- **Kafka:** orders and product changes remain committed in their databases and unpublished outbox rows retry after recovery. Do not recreate a checkout with a new key merely because its state is pending.
- **Redis:** catalog reads fall back to PostgreSQL. Gateway rate limiting fails closed with 503 until Redis recovers.
- **Elasticsearch:** search responds with 503; failed projections retry and may enter the Search DLT. Recover the dependency and replay affected records.
- **Payment/inventory consumer:** inspect its DLT if an order remains `PAYMENT_PENDING`. Payment decline is a normal simulated business outcome and releases stock before cancellation.
- **Changed database password:** PostgreSQL initialization variables only apply to an empty data volume. Update the existing database role password deliberately, or reset disposable development data with `docker compose down -v`.

## Dead-letter inspection and replay

Topic names are `commerce.events.<spring.application.name>.DLT`, with six partitions and 30-day retention. For example:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic commerce.events.order-service.DLT --from-beginning --property print.headers=true --property print.key=true
```

Fix the cause before replay. Preserve the original JSON event body, event ID, and aggregate ID. Publish the original body to `commerce.events` with its aggregate UUID as the Kafka key. For a single inspected record, use the producer below and enter `<aggregate-uuid>|<original-json-body>` on one line:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic commerce.events --property parse.key=true --property key.separator='|'
```

The failed consumer's transaction rolled back, so its inbox permits retry. Other consumers suppress already processed event IDs. Do not assign a new event ID or manually change order state to bypass a failed workflow. A DLT is not automatically drained after recovery.

## Rebuild Search after index loss

PostgreSQL Product data is authoritative. The project retains all Product outbox snapshots; Kafka's normal event retention is seven days, so resetting Kafka offsets alone is insufficient for an old catalog. Elasticsearch external versions make replaying retained snapshots safe, including deleted-product tombstones.

During a maintenance window, pause catalog writes, then:

```sh
docker compose stop search-service product-service
docker compose exec postgres-search psql -U commerce -d search_db -c "TRUNCATE processed_events;"
docker compose exec postgres-product psql -U commerce -d product_db -c "UPDATE outbox SET published_at=NULL;"
docker compose start search-service product-service
```

The Product relay republishes retained events and Search rebuilds its index. Its inbox must be cleared because it otherwise skips events already processed before index loss. Other service inboxes still suppress duplicates. Monitor the Product outbox count and Search consumer lag, then compare search results with active catalog products before resuming writes. This procedure assumes Product outbox history has not been pruned; if it has, a database-snapshot backfill is required first.

## Persistence and existing installations

Compose persists seven PostgreSQL databases, Kafka logs, Elasticsearch data, Prometheus, and Grafana. Back up database volumes before maintenance. Search now uses `postgres-search/search_db`, independent of Product's migration history. On an existing installation, use the Search rebuild procedure after switching databases so historical events older than Kafka retention are also projected.

For external clients Kafka advertises `localhost:29092`. Container clients use `kafka:9092`. For access from another machine, change the external advertised hostname and the client's bootstrap address together.
