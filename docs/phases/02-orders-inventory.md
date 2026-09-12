# Phase 2 — Order and inventory transaction boundaries

```
inventory-service/.../inventory/{controller,dto,repository,service,event}
order-service/.../order/{controller,dto,entity,repository,service,event}
```

Inventory owns available/reserved counts and reservation records. Order owns price snapshots, basket rows and transition history. External product IDs are logical references, not foreign keys across databases. All money is USD with `BigDecimal`/`NUMERIC`; no floating-point arithmetic. The catalog supplies prices, never the client.

## Strong consistency gate
Reservation acquires a per-order advisory transaction lock, then locks all stock rows in sorted product UUID order using `SELECT ... FOR UPDATE`. It checks the whole basket before decrementing anything. Two buyers of the final unit serialize on that row; one succeeds and the other records REJECTED. CHECK constraints prevent negative available/reserved values. Completion and compensation update the original reservation, not quantities supplied in a later event.

Restocking is an additive admin operation with a UUID idempotency key and a restock ledger. Product IDs are operator-supplied logical references; orphan stock cannot be purchased because Order validates against Product Service. A stricter provisioning workflow is a future improvement.

Order creation uses a user-scoped idempotency key, database uniqueness and a canonical request hash. Reusing the key with different contents is HTTP 409. Same-request retries return the same order, even if its state advanced. A row lock serializes Saga responses.

## Run and requests
```
mvn -pl inventory-service,order-service -am test
docker compose up --build -d inventory-service order-service
```
Final runtime includes Phase 3 event transport; this document describes the local transaction boundary first.
```
POST /api/inventory/<product-id>/restock
Authorization: Bearer <admin-token>
Idempotency-Key: <random-uuid>
{"quantity":1}

POST /api/orders
Authorization: Bearer <customer-token>
Idempotency-Key: order-request-001
{"items":[{"productId":"<product-id>","quantity":1}],"paymentMode":"SUCCESS"}
```
Response: 202 with a `Location` header. Poll `GET /api/orders/<order-id>`. The API does not promise that payment is complete when creation returns.
