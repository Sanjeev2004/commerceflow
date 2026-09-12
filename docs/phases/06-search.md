# Phase 6 — Elasticsearch read model

```
search-service/.../search/{controller,dto,repository,service,event}
notification-service/.../notification/{controller,dto,repository,service,event}
```

Product writes full revisioned snapshots into Kafka through its outbox. Search applies them to the `products` index using external versioning; duplicates are safe and old revisions cannot replace newer ones. Soft-delete tombstones remain indexed with active=false, preventing late updates from resurrecting deleted products. Search filters active=true. Mapping uses text for name/description, keyword for category/SKU, scaled-float for two-decimal search prices. PostgreSQL remains the monetary source of truth.

Search keeps an inbox database. Elasticsearch writes occur before the inbox transaction commits; after a crash, replay converges safely because the same external version is idempotent. This is not a distributed transaction. Index creation is lazy and idempotent. Search may lag until Kafka delivery and Elasticsearch refresh (~1 second).

Notification consumes terminal order events and stores simulated email messages through a `NotificationSender` port. A unique (order_id,kind) key prevents duplicate simulated notifications. A real provider would require a delivery outbox and provider idempotency keys; replacing the implementation with an HTTP call alone would lose this guarantee.

```
mvn -pl search-service,notification-service -am compile
docker compose up --build -d elasticsearch search-service notification-service
```
```
GET /api/search?q=keyboard&category=electronics&minPrice=10&maxPrice=100&page=0&size=20
GET /api/notifications (Authorization: Bearer <customer-token>)
```
Gate: bounded result window, no user-generated Elasticsearch DSL, version checks on projection, notifications scoped to the JWT subject. For reindexing after index loss, see the operations runbook; inbox deduplication must be accounted for.
