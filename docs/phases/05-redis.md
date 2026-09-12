# Phase 5 — Redis cache and rate limiter

```
product-service/.../config/ProductCache.java
product-service/.../event/ProductCacheListener.java
gateway/.../RateLimitFilter.java
gateway/src/main/resources/token-bucket.lua
```

Cache-aside caches only individual public product reads for 30 seconds. Writes evict after commit and durably publish PRODUCT_CHANGED for another invalidation attempt. Redis failure falls back to PostgreSQL. There is a residual concurrent stale-fill window bounded by the TTL; this cache is eventually consistent. Checkout sends `Cache-Control: no-cache` and reads the catalog database, so cached prices are not authoritative. Listing is deliberately uncached to avoid many query-key invalidation rules.

Gateway runs an atomic Redis Lua token bucket keyed by authenticated subject or direct connection IP. Redis server time avoids gateway clock skew. Public auth endpoints get 5 tokens/second with burst 10; other API routes get 20/second with burst 40. Rejection is 429 with Retry-After. Redis outage returns 503 (fail closed). Incoming forwarded identity/IP headers are not trusted; a deployment behind a trusted load balancer must explicitly configure client-IP handling.

```
mvn -pl gateway,product-service -am test
docker compose up --build -d redis gateway product-service
```
`GET /api/products/<id>` twice warms/hits the cache. Update the product as ADMIN, then read again. Burst more than 40 API requests to demonstrate 429. Stop Redis to observe gateway 503; an internal direct product read still falls back to its DB.
