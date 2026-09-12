# Phase 1 — Auth, catalog, PostgreSQL, gateway

## Structure
```
pom.xml
common/src/main/{java/dev/shop/common/{api,config},resources}
auth-service/src/main/{java/dev/shop/auth/{controller,service,repository,dto,entity,config},resources/db/migration}
product-service/src/main/{java/dev/shop/product/{controller,service,repository,dto},resources/db/migration}
gateway/src/main/{java/dev/shop/gateway,resources}
```

Gateway is reactive; business services use servlet Spring MVC and JDBC. Each service owns its database. Flyway installs constraints and indexes. JDBC records are mapped to explicit API DTOs; no persistence entity is serialized implicitly. Product deletion is a soft delete and SKU remains unique. There are no cross-database joins.

Auth issues 15-minute HS256 JWTs with issuer, audience, subject and roles. Every service validates tokens independently; client-supplied identity headers are ignored. Public registration always creates CUSTOMER. An optional environment-only bootstrap creates the first ADMIN. BCrypt cost is 12, and UTF-8 password input is limited to BCrypt's 72-byte maximum.

## Run/checkpoint
Prerequisites: Java 21 and Maven 3.9+, or Docker Desktop for the final Compose workflow.
```
mvn -pl auth-service,product-service,gateway -am test
```
For local JVM execution set `DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`; each process gets its own DB URL. Set `MANAGEMENT_SERVER_PORT` uniquely when running multiple services on the host. After `mvn package -DskipTests`, run `java -jar <service>/target/<service>-1.0.0-SNAPSHOT.jar` for each service. Compose infrastructure and phase-specific startup commands are supplied in the root README.

## API examples
```
POST /api/auth/register
{"email":"customer@example.com","password":"a-long-demo-passphrase"}

POST /api/auth/login
{"email":"customer@example.com","password":"a-long-demo-passphrase"}

POST /api/products (Authorization: Bearer <admin-token>)
{"sku":"KB-001","name":"Mechanical Keyboard","description":"USB-C","category":"electronics","price":79.99}

GET /api/products?page=0&size=20&sort=priceAsc&category=electronics
```

Consistency gate: registration uniqueness is enforced by PostgreSQL; login uses a generic failure response; admin mutations are checked inside Product Service; pagination and sorting are bounded/allow-listed. Later phases add messaging and caching to the product transaction.
