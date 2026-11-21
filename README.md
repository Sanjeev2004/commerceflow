# CommerceFlow

CommerceFlow is a small online shop built as a set of Java services. A customer can register, browse products, keep a cart, and check out. Checkout is a distributed transaction: the order is accepted immediately, stock is reserved, a simulated payment runs, and the order is either confirmed or cancelled with the stock put back.

## Problem this project solves

A shop cannot safely take payment in one database while stock lives in another. If payment fails after the stock was already decremented, the catalog is wrong. If stock is decremented only after payment, two customers can buy the last item. CommerceFlow is a portfolio implementation of that problem: several services, each with its own data, agreeing on one checkout result.

## How it solves it

The browser talks only to the API gateway on port 8080. The gateway checks the JWT, applies a Redis rate limit, and forwards the call. Auth, catalog, inventory, orders, payments, search, and notifications are separate Spring Boot services. Each one owns a Postgres database.

Checkout does not update all of those databases in one request. The order service stores the order and an outbox row in the same transaction, then returns `202 Accepted`. A relay publishes the outbox to Kafka. Inventory reserves stock and answers with a reserved or rejected event. Payment then simulates success, decline, or cancel. The order service confirms the order or asks inventory to release the stock. Consumers record event ids so a duplicate delivery is ignored.

```text
                                  ┌───────────────────────────────┐
                                  │   Web Storefront / Client     │
                                  └──────────────┬────────────────┘
                                                 │
                                                 ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 API Gateway (:8080)                                    │
│       • Path-based Routing      • Token-Bucket Rate Limiter (Redis)      • Full CORS   │
└──────┬─────────────┬─────────────┬─────────────┬─────────────┬────────────┬────────────┘
       │             │             │             │             │            │
       ▼             ▼             ▼             ▼             ▼            ▼
┌────────────┐┌────────────┐┌────────────┐┌────────────┐┌───────────┐┌────────────┐
│Auth Service││  Product   ││ Inventory  ││   Order    ││  Payment  ││   Search   │
│   (:8081)  ││   (:8082)  ││   (:8083)  ││   (:8084)  ││  (:8085)  ││   (:8086)  │
│PostgreSQL 1││PostgreSQL 2││PostgreSQL 3││PostgreSQL 4││PostgreSQL5││PostgreSQL 6│
└────────────┘└────────────┘└──────┬─────┘└──────┬─────┘└─────┬─────┘└────────────┘
                                   │             │            │
                      Transactional Outbox ──▶ Kafka ◀────────┘
                                      (commerce.events)
```

The storefront is served by the gateway at `/`. Product text is escaped before it is inserted into the page. Cart lines live in Redis. Search reads an Elasticsearch projection of the catalog. Prometheus and Grafana are in the full Compose stack.

## What changed in this revision

About **19%** of the repository changed by line count (lines added or edited compared with the previous revision). The service boundaries, saga states, and storefront layout are the same.

- **Hosted demo.** `cloud-service` starts the gateway, auth, catalog, inventory, orders, payments, and notifications in one process. Render supplies one Postgres database (a schema per service) and one Key Value instance. With Kafka turned off, the same outbox rows are delivered in-process, so checkout still confirms or cancels. `render.yaml` and `cloud/Dockerfile` are the deploy files. The image listens on `PORT` and exposes `/health`.
- **Validation.** Cart updates cannot push a line past 1000 units, and order creation checks quantity, item count, and the idempotency key even when checkout does not pass through the controller. An unknown admin order state returns 400.
- **Rate limit.** The gateway token bucket now refills at 10 auth requests per minute and 40 API requests per minute, which is what this README already described.
- **Operations.** Compose health checks install `wget`. The health scripts call the gateway actuator on port 9080. Each service loads its own config file, and Flyway scripts live in separate folders so one classpath can migrate one schema. Local `.env.example` values are filled in so a copied env file can boot.
- **CI.** Every push and pull request compiles and runs the unit tests. The Docker Compose integration run is available with a manual workflow dispatch, using the lighter dev stack.

## What works

- Unit tests: `mvn -B -ntp verify -DskipITs`.
- Local Docker Compose, both the single-Postgres dev file and the full multi-database file: register, catalog, cart, checkout saga over Kafka, search, and notifications.
- The single-process demo against Postgres and Redis: `/health`, the storefront, register, login, seeded catalog, cart, checkout to `CONFIRMED` or `CANCELLED`, and the notification feed. This was run locally with the same jars Render builds.

## What doesn't work yet / known limitations

- The hosted demo does not run Elasticsearch, so `/api/search` returns 503 there. Search works in Docker Compose.
- The hosted demo does not run Kafka, Prometheus, or Grafana. Checkout events move inside the process instead of across a broker.
- Render's free web service has 512 MB of RAM and sleeps after 15 minutes without traffic. The demo process is about 450 MB, so there is little headroom. A cold start takes about a minute. If the service is killed for memory, change the web service plan in `render.yaml` from `free` to `1c-2g` (that plan is paid).
- Render's free Postgres database expires 30 days after it is created. Create a new one, or move it to a paid plan, when that happens. The free Key Value instance is not durable.
- The demo admin password in `render.yaml` is `Demo-admin-pass-123`. It is there so the catalog can be seeded on first boot. Change `ADMIN_PASSWORD` in the Render dashboard before treating the site as private. The JWT signing key is generated by Render and is not in git.
- Free web services on Render cannot receive private network calls, so this project is deployed as one process rather than eight public services.
- The full Compose stack is the one that shows separate databases, Kafka dead-letter topics, and the Grafana dashboard. It wants several gigabytes of RAM (the dev file is the smaller one).

## Key features in the local stack

- **Choreographed Checkout Saga**: Resilient distributed transaction across Order, Inventory, and Payment services using Kafka events.
- **Transactional Outbox & Deduplicating Inbox**: At-least-once event delivery guarantee with exactly-once consumer semantics and per-service Dead-Letter Topics (`.DLT`).
- **Interactive Web Storefront & Saga Visualizer**: Out-of-the-box browser UI served directly at `http://localhost:8080` to interact with products, shopping cart, live Saga tracking, notifications, and admin tools.
- **Resilient Security & Gateway**:
  - Stateless JWT with standard claims and role validation (`CUSTOMER`, `ADMIN`).
  - Current user profile endpoint (`GET /api/auth/me`).
  - Full cross-origin (CORS) support for web & mobile frontends.
  - Redis token-bucket rate limiting (40 req/min for APIs, 10 req/min for auth).
- **Search & Caching**: Product full-text search with Elasticsearch 8.14; read-through & invalidation caching with Redis 7.
- **Observability**: Prometheus metrics scraping, custom business counters (`commerce.orders.created`, `commerce.outbox.published`, etc.), and pre-provisioned Grafana dashboards.
- **Lightweight Dev Environment**: Run the entire stack using a single multi-tenant PostgreSQL container saving ~1.5 GB RAM (`docker-compose.dev.yml`).

---

## 🚀 Quick Start

### 1. Configure Environment

The example file already contains local-only defaults. Replace them before any shared machine.

```bash
cp .env.example .env
```

```powershell
Copy-Item .env.example .env
```

`JWT_SECRET` must be at least 32 characters. `ADMIN_EMAIL` and `ADMIN_PASSWORD` are optional; leave both blank to skip the bootstrap admin. The admin password, when set, must be at least 12 characters.

### 2. Choose Your Deployment Mode

#### Option A: Lightweight Development Mode (Recommended, ~1.5 GB RAM)
Uses a single consolidated PostgreSQL instance hosting isolated databases for each microservice:

```bash
docker compose -f docker-compose.dev.yml up -d --build --wait
```

```powershell
.\scripts\run.ps1 dev
```

#### Option B: Full multi-database mode (~8 GB RAM)
Runs separate PostgreSQL instances per service, plus Prometheus and Grafana.

```bash
docker compose up -d --build --wait --wait-timeout 300
```

```powershell
.\scripts\run.ps1 up
```

### 3. Open the Web Storefront
Open **[http://localhost:8080](http://localhost:8080)** in your browser:
- Register a customer or click **"👤 New Customer"** for a 1-click test persona.
- Browse the catalog, filter by categories, or search in real-time.
- Add items to your cart, pick a payment mode (`SUCCESS`, `DECLINE`, `CANCEL`), and execute checkout.
- Watch the **live distributed Saga pipeline** execute and compensate in real time!

---

## 🛠️ Service Topology & Ports

| Component | Port | Purpose |
|---|---|---|
| **Web Storefront & API Gateway** | `8080` | `http://localhost:8080` / `http://localhost:8080/index.html` |
| **Auth Service** | `8081` | Customer registration, login, profile (`/api/auth/me`) |
| **Product Service** | `8082` | Catalog CRUD, Redis caching |
| **Inventory Service** | `8083` | Stock reservation, release & commit |
| **Order Service** | `8084` | Saga orchestration, outbox staging |
| **Payment Service** | `8085` | Payment simulator (`SUCCESS`, `DECLINE`, `CANCEL`) |
| **Search Service** | `8086` | Elasticsearch product projections & search |
| **Notification Service** | `8087` | Customer order notification inbox |
| **Management / Actuator** | `9080-9087` | Health readiness, Prometheus endpoints |
| **Prometheus** | `9091` | Metrics scraper (`http://localhost:9091`) |
| **Grafana** | `3000` | Commerce Overview dashboard (`http://localhost:3000`) |
| **Elasticsearch** | `9200` | Search engine |
| **Redis** | `6379` | Token-bucket rate limiter & product cache |
| **Kafka Broker** | `29092` | Host bootstrap port (`kafka:9092` in Docker) |

---

## 📡 API Reference

All requests route through Gateway at `http://localhost:8080`.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register new customer account |
| `POST` | `/api/auth/login` | Public | Authenticate; returns JWT access token |
| `GET` | `/api/auth/me` | Customer / Admin | Retrieve current authenticated user profile |
| `GET` | `/api/products` | Public | Filtered & paginated product catalog |
| `GET` | `/api/products/{id}` | Public | Active product details (cached in Redis) |
| `POST` | `/api/products` | Admin | Create product (staged into outbox) |
| `PUT` | `/api/products/{id}` | Admin | Update product fields |
| `DELETE` | `/api/products/{id}` | Admin | Soft delete product |
| `GET` | `/api/inventory/{productId}`| Authenticated | Available & reserved inventory quantities |
| `POST` | `/api/inventory/{id}/restock`| Admin | Add stock (`Idempotency-Key` header) |
| `GET` | `/api/cart` | Customer | Get customer's shopping cart (Redis-backed) |
| `POST` | `/api/cart/items` | Customer | Add/increment product in shopping cart |
| `PUT` | `/api/cart/items/{productId}`| Customer | Update product quantity in cart |
| `DELETE` | `/api/cart/items/{productId}`| Customer | Remove product from cart |
| `POST` | `/api/cart/checkout` | Customer | Checkout from cart (initiates Saga, clears cart) |
| `POST` | `/api/orders` | Customer | Initiate Saga checkout directly (returns `202 Accepted`) |
| `GET` | `/api/orders` | Customer | List customer orders |
| `GET` | `/api/orders/{id}` | Customer | Get order status (`CONFIRMED`, `CANCELLED`, etc.) |
| `GET` | `/api/orders/admin` | Admin | List all platform orders (supports `?state=...` filter) |
| `GET` | `/api/payments/orders/{id}`| Customer | Inspect payment outcome |
| `GET` | `/api/search` | Public | Elasticsearch query (`q`, `category`, `minPrice`, `maxPrice`) |
| `GET` | `/api/notifications` | Customer | Order notification feed |

A ready-to-run request collection is in [`commerceflow.http`](commerceflow.http) for the VS Code REST Client or the IntelliJ HTTP Client.

---

## 🔄 Distributed Checkout Saga Flow

```text
Customer                     Order Service        Inventory Service     Payment Service
   │                               │                     │                     │
   │─── POST /api/orders ─────────▶│                     │                     │
   │    (Idempotency-Key)          │                     │                     │
   │◀── 202 Accepted ──────────────│                     │                     │
   │                               ├── ORDER_CREATED ───▶│                     │
   │                               │                     ├── (Lock & Reserve)  │
   │                               │◀── INVENTORY_RES ───┤                     │
   │                               ├── PAYMENT_REQ ───────────────────────────▶│
   │                               │                                           ├── (Execute simulator)
   │                               │◀── PAYMENT_COMPLETED (Success) ───────────┤
   │                               ├── ORDER_CONFIRMED ─▶│                     │
   │                               │                     ├── (Commit Stock)    │
   │                               │                                           │
   ▼                               ▼                                           ▼
[Happy Path: Order CONFIRMED]     [Failure Path: PAYMENT_FAILED ──▶ Compensate & Release Stock]
```

---

## Deploy on Render

Render is the host for a public URL because it supports Google sign-in, a free Postgres database, and a free Key Value (Redis-compatible) instance. The free tier cannot run Kafka, Elasticsearch, and eight separate JVMs, and free web services cannot call each other on the private network. The blueprint therefore runs the single-process demo.

Apply the blueprint from the dashboard after signing in with Google. A free web service shares the workspace's monthly free hours with any other free services already running there.

1. Open [https://dashboard.render.com](https://dashboard.render.com) and choose **Sign in with Google**.
2. Click **New**, then **Blueprint**.
3. Connect GitHub if Render asks, and grant access to `Sanjeev2004/commerceflow`.
4. Select that repository. When a branch is requested, choose `improvements` until it is merged, then use `main`.
5. Render reads `render.yaml`. Confirm three resources, all in **Oregon**, all on the **Free** plan:
   - Web service `commerceflow` (Docker, health check path `/health`)
   - Postgres `commerceflow-db` (database name `commerceflow`)
   - Key Value `commerceflow-redis`
6. Click **Apply**. The first Docker build compiles the Maven reactor and can take several minutes. The free web service then sleeps after 15 idle minutes; the first request after that takes about a minute.
7. Open the `onrender.com` URL shown on the web service page. `/` is the storefront. `/health` returns `{"status":"UP"}` when Redis answers.
8. Sign in on the storefront as `admin@example.com` / `Demo-admin-pass-123`. The catalog is seeded on first boot. Change that password in the service's **Environment** tab if the admin account should not be public, then redeploy.

Environment variables set by the blueprint:

| Variable | Value |
|---|---|
| `PORT` | Set by Render. The process listens on it. |
| `JWT_SECRET` | Generated by Render. At least 32 characters. Not stored in git. |
| `DATABASE_URL` | Internal connection string of `commerceflow-db`. |
| `REDIS_URL` | Internal connection string of `commerceflow-redis`. |
| `ADMIN_EMAIL` | `admin@example.com` |
| `ADMIN_PASSWORD` | `Demo-admin-pass-123` |

No other variables are required. `DATABASE_URL` and `REDIS_URL` must be the internal URLs from the same region so the free web service can reach them.

## Testing

```bash
mvn -B -ntp verify -DskipITs
```

```powershell
# 1. Run unit tests
mvn -B -ntp verify -DskipITs

# 2. Run PowerShell Saga end-to-end checkout demo
$env:ADMIN_EMAIL = 'admin@example.com'
$env:ADMIN_PASSWORD = 'your-admin-password'
.\scripts\demo.ps1
.\scripts\demo.ps1 -PaymentMode DECLINE

# 3. Check microservices health
.\scripts\health-check.ps1
```

---

## 💻 Developer Scripts & Makefile

- `make dev` or `.\scripts\run.ps1 dev`: Start lightweight stack
- `make up` or `.\scripts\run.ps1 up`: Start full 18-container stack
- `make health` or `.\scripts\run.ps1 health`: Ping all actuator and infrastructure health endpoints
- `make down` or `.\scripts\run.ps1 down`: Stop containers
- `make clean` or `.\scripts\run.ps1 clean`: Stop containers and wipe volumes
