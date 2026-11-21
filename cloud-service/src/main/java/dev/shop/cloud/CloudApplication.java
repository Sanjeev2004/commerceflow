package dev.shop.cloud;

import dev.shop.auth.AuthApplication;
import dev.shop.gateway.GatewayApplication;
import dev.shop.inventory.InventoryApplication;
import dev.shop.notification.NotificationApplication;
import dev.shop.order.OrderApplication;
import dev.shop.payment.PaymentApplication;
import dev.shop.product.ProductApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;

/**
 * One JVM for hosts that provide a single web process, Postgres, and Redis.
 * Checkout events move through the in-process outbox instead of Kafka.
 * Elasticsearch search stays on the Docker Compose stack.
 */
public final class CloudApplication {
    private CloudApplication() {}

    public static void main(String[] args) {
        try {
            start(args);
        } catch (Throwable ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void start(String[] args) throws Exception {
        String databaseUrl = System.getenv("DATABASE_URL");
        String redisUrl = System.getenv("REDIS_URL");
        String jwt = System.getenv("JWT_SECRET");
        if (databaseUrl == null || databaseUrl.isBlank() || redisUrl == null || redisUrl.isBlank() || jwt == null || jwt.length() < 32) {
            System.err.println("DATABASE_URL, REDIS_URL, and JWT_SECRET (at least 32 characters) are required");
            System.exit(1);
        }
        var database = CloudUrls.database(databaseUrl);
        var redis = CloudUrls.redis(redisUrl);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        record Service(String schema, Class<?> type, String config, int port, String migrations) {}
        List<Service> services = List.of(
                new Service("auth", AuthApplication.class, "auth-service", 8081, "classpath:db/common,classpath:db/migration-auth"),
                new Service("product", ProductApplication.class, "product-service", 8082, "classpath:db/common,classpath:db/migration-product"),
                new Service("inventory", InventoryApplication.class, "inventory-service", 8083, "classpath:db/common,classpath:db/migration-inventory"),
                new Service("order_svc", OrderApplication.class, "order-service", 8084, "classpath:db/common,classpath:db/migration-order"),
                new Service("payment", PaymentApplication.class, "payment-service", 8085, "classpath:db/common,classpath:db/migration-payment"),
                new Service("notification", NotificationApplication.class, "notification-service", 8087, "classpath:db/common,classpath:db/migration-notification"));
        for (var service : services) {
            startServlet(service.type(), service.config(), service.port(), service.schema(), service.migrations(), database, redis, jwt);
            System.out.println("Started " + service.config() + " on " + service.port());
        }
        startGateway(port, redis, jwt);
        System.out.println("Storefront listening on " + port);
        seed(port);
    }

    private static void startServlet(Class<?> type, String config, int port, String schema, String migrations, CloudUrls.Database database, CloudUrls.Redis redis, String jwt) {
        var app = new SpringApplication(type);
        app.setDefaultProperties(shared(config, database, schema, redis, jwt));
        var args = new java.util.ArrayList<>(java.util.Arrays.asList(serviceArgs(port, "servlet")));
        args.add("--spring.flyway.locations=" + migrations);
        app.run(args.toArray(String[]::new));
    }

    private static void startGateway(int port, CloudUrls.Redis redis, String jwt) {
        var app = new SpringApplication(GatewayApplication.class);
        var props = new HashMap<String, Object>();
        props.put("spring.config.name", "gateway");
        props.put("JWT_SECRET", jwt);
        props.put("REDIS_HOST", redis.host());
        props.put("spring.data.redis.host", redis.host());
        props.put("spring.data.redis.port", String.valueOf(redis.port()));
        props.put("spring.data.redis.ssl.enabled", Boolean.toString(redis.ssl()));
        if (redis.password() != null) props.put("spring.data.redis.password", redis.password());
        app.setDefaultProperties(props);
        app.run("--server.port=" + port,
                "--spring.main.web-application-type=reactive",
                "--spring.main.banner-mode=off",
                "--management.server.port=0",
                "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthContributorAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    private static Map<String, Object> shared(String config, CloudUrls.Database database, String schema, CloudUrls.Redis redis, String jwt) {
        var props = new HashMap<String, Object>();
        props.put("spring.config.name", config);
        props.put("DB_URL", database.jdbc(schema));
        props.put("POSTGRES_USER", database.username());
        props.put("POSTGRES_PASSWORD", database.password());
        props.put("JWT_SECRET", jwt);
        props.put("REDIS_HOST", redis.host());
        props.put("spring.data.redis.host", redis.host());
        props.put("spring.data.redis.port", String.valueOf(redis.port()));
        props.put("spring.data.redis.ssl.enabled", Boolean.toString(redis.ssl()));
        props.put("spring.flyway.schemas", schema);
        props.put("spring.flyway.default-schema", schema);
        props.put("spring.flyway.create-schemas", "true");
        if (redis.password() != null) props.put("spring.data.redis.password", redis.password());
        return props;
    }

    private static String servletExcludes() {
        return String.join(",",
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayResilience4JCircuitBreakerAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayNoLoadBalancerClientAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration",
                "org.springframework.cloud.gateway.discovery.GatewayDiscoveryClientAutoConfiguration",
                "org.springframework.cloud.gateway.config.SimpleUrlHandlerMappingGlobalCorsAutoConfiguration",
                "org.springframework.cloud.gateway.config.GatewayReactiveLoadBalancerClientAutoConfiguration",
                "org.springframework.cloud.gateway.config.LocalResponseCacheAutoConfiguration");
    }

    private static String[] serviceArgs(int port, String webType) {
        return new String[] {
                "--server.port=" + port,
                "--spring.main.web-application-type=" + webType,
                "--spring.main.banner-mode=off",
                "--management.server.port=0",
                "--commerce.messaging.enabled=false",
                "--spring.cloud.gateway.enabled=false",
                "--spring.autoconfigure.exclude=" + servletExcludes(),
                "--spring.datasource.hikari.maximum-pool-size=2",
                "--server.tomcat.threads.max=16"
        };
    }

    private static void seed(int port) {
        String email = System.getenv().getOrDefault("ADMIN_EMAIL", "");
        String password = System.getenv().getOrDefault("ADMIN_PASSWORD", "");
        if (email.isBlank() || password.isBlank()) {
            System.out.println("ADMIN_EMAIL and ADMIN_PASSWORD are blank, so the demo catalog was not seeded");
            return;
        }
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        String base = "http://127.0.0.1:" + port;
        try {
            String token = login(client, base, email, password);
            if (catalogSize(client, base) > 0) {
                System.out.println("Catalog already has products");
                return;
            }
            for (var item : demoProducts()) {
                String created = send(client, base, "POST", "/api/products", token, null, item.json());
                String id = jsonField(created, "id");
                send(client, base, "POST", "/api/inventory/" + id + "/restock", token, UUID.randomUUID().toString(),
                        "{\"quantity\":" + item.stock() + "}");
            }
            System.out.println("Seeded demo catalog");
        } catch (Exception ex) {
            System.out.println("Demo catalog was not seeded: " + ex.getMessage());
        }
    }

    private record Demo(String json, int stock) {}

    private static List<Demo> demoProducts() {
        return List.of(
                new Demo(product("KB-100", "Desk keyboard", "A compact keyboard for everyday work.", "Electronics", "49.00"), 25),
                new Demo(product("MS-200", "Wireless mouse", "A quiet mouse with a USB receiver.", "Electronics", "19.00"), 40),
                new Demo(product("MUG-01", "Ceramic mug", "A 12 ounce mug for the desk.", "Home", "12.50"), 30),
                new Demo(product("NB-10", "Grid notebook", "An 80 page notebook.", "Stationery", "8.00"), 50));
    }

    private static String product(String sku, String name, String description, String category, String price) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"description\":\"" + description
                + "\",\"category\":\"" + category + "\",\"price\":" + price + "}";
    }

    private static String login(HttpClient client, String base, String email, String password) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                String body = send(client, base, "POST", "/api/auth/login", null, null,
                        "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
                return jsonField(body, "accessToken");
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(1000);
            }
        }
        throw last == null ? new IllegalStateException("login failed") : last;
    }

    private static long catalogSize(HttpClient client, String base) throws Exception {
        String body = send(client, base, "GET", "/api/products", null, null, null);
        String marker = "\"totalElements\":";
        int at = body.indexOf(marker);
        if (at < 0) return 0;
        int start = at + marker.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
        return Long.parseLong(body.substring(start, end));
    }

    private static String send(HttpClient client, String base, String method, String path, String token, String idempotencyKey, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) throw new IllegalStateException(method + " " + path + " -> " + response.statusCode() + " " + response.body());
        return response.body();
    }

    private static String jsonField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int at = json.indexOf(marker);
        if (at < 0) throw new IllegalStateException("Missing " + field);
        int start = at + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
