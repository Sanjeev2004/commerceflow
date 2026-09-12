# Phase 3 — Asynchronous transport

```
common/.../event/{DomainEvent,EventType,SagaData,EventCodec,Outbox,Inbox,OutboxRelay}
common/.../config/MessagingConfig.java
{inventory,order}-service/.../event/*Listener.java
```

One `commerce.events` topic with six partitions keeps the first version easy to inspect. Every service has its own consumer group; replicas of that service share the group. Keys are order IDs for Saga events and product IDs for catalog events. Consumers ignore event types they do not own. Event envelopes include a UUID event ID, schema version, aggregate ID, UTC timestamp and correlation ID.

Order creation writes ORDER_CREATED. Inventory responds with INVENTORY_RESERVED or INVENTORY_REJECTED. Order records INVENTORY_RESERVED in history and then PAYMENT_PENDING with PAYMENT_REQUESTED atomically; the intermediate state is visible in history rather than through a separate polling window.

The transactionally persisted publisher/inbox boundary is introduced immediately instead of adding an unsafe dual-write version; Phase 7 documents failure recovery in depth.

```
mvn -pl inventory-service,order-service -am test
docker compose up -d kafka
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic commerce.events --from-beginning
```
Use Phase 2's create-order request. Until Payment Service runs, an in-stock order remains PAYMENT_PENDING. Kafka outage leaves committed outbox rows ready for retry; it does not erase orders.
