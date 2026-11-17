# CommerceFlow

A modern, production-grade distributed e-commerce platform built with **Java 21** and **Spring Boot 3.4**, featuring an asynchronous checkout Saga orchestrated across Kafka, an API Gateway with rate limiting & CORS, transactional outbox relays with inbox deduplication, Elasticsearch full-text search, Redis caching, Prometheus/Grafana observability, and an **interactive Web Storefront & Saga visualizer**.

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

---

## ✨ Key Features & Architecture

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
Copy `.env.example` to `.env`:
```powershell
Copy-Item .env.example .env
```
Fill `POSTGRES_PASSWORD`, `JWT_SECRET` (at least 32 characters), `GRAFANA_PASSWORD`, and optionally `ADMIN_EMAIL` and `ADMIN_PASSWORD`.

### 2. Choose Your Deployment Mode

#### Option A: Lightweight Development Mode (Recommended, ~1.5 GB RAM)
Uses a single consolidated PostgreSQL instance hosting isolated databases for each microservice:
```powershell
.\scripts\run.ps1 dev
# or: docker compose -f docker-compose.dev.yml up -d --build --wait
```

#### Option B: Full Production-Grade Mode (~8 GB RAM)
Runs 18 distinct containers with dedicated PostgreSQL instances per service:
```powershell
.\scripts\run.ps1 up
# or: docker compose up -d --build --wait --wait-timeout 300
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

> 💡 **Tip:** A complete collection of ready-to-run API requests is available in [`commerceflow.http`](file:///commerceflow.http) for VS Code REST Client or IntelliJ HTTP Client.

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

## 🧪 Testing & Verification

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
