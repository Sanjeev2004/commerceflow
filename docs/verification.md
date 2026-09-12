# Verification record

Verified on September 12, 2026 with JDK 21.0.8, Maven 3.9.9, PostgreSQL 16, Redis 7, Kafka 3.9.1, and Elasticsearch 8.14.3.

## Passed

- `mvn -B -ntp verify -DskipITs`: all 11 reactor modules built; **16 unit tests passed**.
- Gateway repackaged after adding Redis to its readiness group; its readiness endpoint successfully checked Redis during live startup.
- **9 black-box integration cases passed** against host JVM services and real infrastructure containers:
  - Auth/product authorization: 1.
  - Checkout success, idempotency, two payment-failure modes, and atomic insufficient-stock basket: 4.
  - Restock idempotency/authorization and concurrent oversell prevention: 2.
  - Payment/order/notification ownership and eventual inventory commit: 1.
  - Search creation, filtering, update, invalid range, and deletion: 1.
- `scripts/demo.ps1` completed for `SUCCESS`, `DECLINE`, and `CANCEL`, with the expected terminal order state and restored stock for failed payments.
- Compose configuration validation passed.

Integration cases ran in three batches to fit the available machine memory:

```sh
mvn -B -ntp -pl integration-tests verify -Dit.test=AuthAndProductIT,CheckoutIT,InventoryConcurrencyIT
mvn -B -ntp -pl integration-tests verify '-Dit.test=SearchAndNotificationIT#paymentAndNotificationsAreVisibleOnlyToOwner'
mvn -B -ntp -pl integration-tests verify '-Dit.test=SearchAndNotificationIT#searchProjectsUpdatesAndDeletion'
```

The local infrastructure used Podman under WSL; the Java applications ran on Windows with constrained heaps. The source-building Dockerfile and GitHub Actions workflow were added but were not executed end to end in this environment. Grafana dashboard provisioning was added; visual rendering was not checked.
