# Distributed Commerce

A Java 21 / Spring Boot e-commerce backend with an API gateway, independently owned PostgreSQL databases, Kafka checkout orchestration, Redis caching and rate limiting, Elasticsearch search, and Prometheus/Grafana observability.

## Features

- Customer registration and login, 15-minute JWTs, and administrator-only catalog and stock management.
- Product creation, editing, soft deletion, filtering, sorting, and pagination.
- Multi-item orders with server-side prices, idempotency keys, and concurrent stock reservation.
- Asynchronous checkout with successful payment, simulated decline/cancellation, and inventory compensation.
- Transactional outbox, consumer inbox deduplication, retries, and per-service dead-letter topics.
- Eventually consistent product search and customer-scoped stored notifications.

Payments are a deterministic simulator (`SUCCESS`, `DECLINE`, `CANCEL`); notifications are stored simulated messages. The application interface is the REST API and each business service's Swagger UI.

## Quick start

Install Docker Desktop (Linux containers) with Compose v2. Allow roughly 8 GB of memory for the full stack. Java and Maven are only needed for host-side development and tests; Compose builds the application from source.

1. Copy `.env.example` to `.env` (`Copy-Item .env.example .env` in PowerShell, `cp .env.example .env` in Bash).
2. Fill `POSTGRES_PASSWORD`, `JWT_SECRET` (at least 32 random ASCII characters), and `GRAFANA_PASSWORD`. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` (at least 12 characters) to enable the administrator needed to create products. Keep `POSTGRES_USER=commerce`.
3. Start everything from the project root:

```sh
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
```

The first build downloads Maven dependencies and container images. Databases migrate automatically. Administrator bootstrap runs once for an email; changing its environment password does not reset an existing account.

| Component | Local URL / port |
|---|---|
| Gateway API | http://localhost:8080/api/products |
| Auth / Product / Inventory | 8081 / 8082 / 8083 |
| Order / Payment / Search / Notification | 8084 / 8085 / 8086 / 8087 |
| Swagger UI | `http://localhost:<business-service-port>/swagger-ui/index.html` |
| Management | 9080 (gateway), 9081–9087 (corresponding services) |
| Prometheus | http://localhost:9091 |
| Grafana | http://localhost:3000 (user `admin`, password from `.env`) |
| PostgreSQL | Auth 5433, Product 5434, Inventory 5435, Order 5436, Payment 5437, Notification 5438, Search 5439 |
| Redis / Elasticsearch | 6379 / 9200 |
| Kafka host bootstrap | `localhost:29092` (containers use `kafka:9092`) |

Management exposes `/actuator/health`, `/actuator/health/readiness`, and `/actuator/prometheus`. Grafana provisions a Prometheus data source and Commerce Overview dashboard.

### Run a checkout demonstration (PowerShell)

Use the same administrator credentials configured in `.env`:

```powershell
$env:ADMIN_EMAIL = 'your-admin@example.com'
$env:ADMIN_PASSWORD = 'your-admin-password'
.\scripts\demo.ps1
.\scripts\demo.ps1 -PaymentMode DECLINE
```

The script creates a customer and a product, adds stock, places an order, and waits for its terminal state. Each run creates distinct demo data.

## API reference

All paths below are relative to `http://localhost:8080`. Send JSON bodies with `Content-Type: application/json`. Protected requests use `Authorization: Bearer <accessToken>`.

| Method and path | Access | Purpose |
|---|---|---|
| `POST /api/auth/register` | Public | `{ "email": "...", "password": "..." }` → customer |
| `POST /api/auth/login` | Public | Credentials → `accessToken`, `tokenType`, `expiresIn` |
| `GET /api/products` | Public | `category`, `page`, `size`, `sort` |
| `GET /api/products/{id}` | Public | Active product |
| `POST /api/products` | Admin | Create product |
| `PUT /api/products/{id}` | Admin | Replace editable product fields |
| `DELETE /api/products/{id}` | Admin | Soft delete |
| `GET /api/inventory/{productId}` | Authenticated | Available and reserved quantity |
| `POST /api/inventory/{productId}/restock` | Admin | `{ "quantity": 5 }`, UUID `Idempotency-Key` header |
| `POST /api/orders` | Authenticated | Create checkout, `Idempotency-Key` header; returns 202 |
| `GET /api/orders` | Owner | Paginated order list |
| `GET /api/orders/{id}` | Owner | Order state, totals, cancellation reason |
| `GET /api/payments/orders/{orderId}` | Owner | Payment outcome |
| `GET /api/search` | Public | `q`, `category`, `minPrice`, `maxPrice`, `page`, `size` |
| `GET /api/notifications` | Owner | Paginated stored messages |

Product input:

```json
{"sku":"KB-001","name":"Mechanical Keyboard","description":"USB-C","category":"electronics","price":79.99}
```

Order input:

```json
{"items":[{"productId":"<product-uuid>","quantity":1}],"paymentMode":"SUCCESS"}
```

Reuse the same idempotency key when retrying the **same** order or restock request. Reusing it with different input returns 409. Each product must occur once in an order. Prices come from Product Service, not the request. Poll the order until `CONFIRMED` or `CANCELLED`; a 202 response means the workflow has been accepted, not completed.

## Build and test

With JDK 21 and Maven 3.9+:

```sh
# Compile all modules, run unit tests, package executable JARs
mvn -B -ntp verify -DskipITs

# Against the running full stack; export ADMIN_EMAIL and ADMIN_PASSWORD first
mvn -B -ntp -pl integration-tests verify
```

Maven does not load `.env`; supply administrator credentials in the test process environment. Override the target with `-Dcommerce.baseUrl=http://host:8080`. Integration tests create isolated, randomly named records and exercise real JWT validation, authorization, search updates/deletes, notifications, payment compensation, idempotency, basket atomicity, and concurrent oversell prevention. Reports appear in each module's `target/surefire-reports` or `target/failsafe-reports`.

`.github/workflows/verify.yml` runs unit tests, builds and starts Compose, runs integration tests, and uploads reports.

### Host JVM development

Start infrastructure:

```sh
docker compose up -d postgres-auth postgres-product postgres-inventory postgres-order postgres-payment postgres-notification postgres-search redis kafka elasticsearch
mvn package -DskipTests
```

Run each executable JAR in its own terminal with `POSTGRES_USER=commerce`, `POSTGRES_PASSWORD`, and the same `JWT_SECRET`. Set `DB_URL=jdbc:postgresql://localhost:<database-port>/<name>_db` using the table above. Give each process its own `MANAGEMENT_SERVER_PORT` (9081–9087, gateway 9080). Service URLs default to localhost, Kafka to localhost:29092, Redis to localhost:6379, and Elasticsearch to localhost:9200.

```sh
java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar
```

For the foundation checkpoint start Auth, Product, and Gateway plus their databases, Redis, and Kafka. Add Inventory and Order for reservation, Payment for terminal checkout states, then Search and Notification for read models. The phase documents explain these boundaries; the current runtime uses Kafka and outbox delivery throughout.

## Architecture and operations

```text
Client → Gateway → Auth / Product / Inventory / Order / Payment / Search / Notification
                                    │
                         Order DB + outbox
                                    ↓
                         Kafka commerce.events
                     ↙              ↓              ↘
                Inventory        Payment       Read models
```

Each business service owns a PostgreSQL database, including Search's inbox database. Product snapshots are projected into Elasticsearch using external versions. Consumer business changes and inbox markers commit together; publisher business changes and outbox rows commit together. Delivery is at least once with idempotent handling.

See [the operations runbook](docs/operations.md) for recovery, dead letters, and search rebuilding, and [the phase documents](docs/phases/01-foundation.md) for implementation details.

```sh
docker compose logs -f order-service inventory-service payment-service
docker compose down
```

Normal shutdown preserves PostgreSQL, Kafka, Elasticsearch, and observability volumes. `docker compose down -v` deletes the project's stored data and starts the next run from scratch.
