# Phase 4 — Payments and compensation

```
payment-service/.../payment/{controller,dto,repository,service,event}
order-service/.../service/OrderSaga.java
inventory-service/.../service/InventoryService.java
```

`PaymentProcessor` is a deterministic simulator. SUCCESS completes; DECLINE and CANCEL fail with distinct reasons. There are no card fields or real charges. One payment per order is enforced by a database unique constraint. A new event ID for an already processed business command still cannot create a second payment.

Success: PAYMENT_COMPLETED → order CONFIRMED → ORDER_CONFIRMED → inventory COMMITTED (reserved decreases; available was already decremented) and notification.

Failure: PAYMENT_FAILED → store cancellation reason and emit INVENTORY_RELEASE_REQUESTED → inventory atomically restores available/decreases reserved and emits INVENTORY_RELEASED → order CANCELLED → notification. Cancellation is not advertised before stock restoration. Insufficient stock cancels directly, without creating a payment.

```
mvn -pl inventory-service,order-service,payment-service -am test
docker compose up --build -d payment-service
```
Submit Phase 2's order body with `"paymentMode":"DECLINE"`, using a **new** idempotency key. Poll the order and `GET /api/inventory/<product-id>`; quantity should return to its original value. `GET /api/payments/orders/<order-id>` is owner-only.

Gate: state guards reject conflicting responses. Duplicate events cannot repeat stock or money effects. There is deliberately no arbitrary post-payment cancel/refund endpoint; refunds require a separate compensating payment workflow.
