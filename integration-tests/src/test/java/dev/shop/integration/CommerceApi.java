package dev.shop.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Black-box tests against the Compose stack, including real JWT validation. */
final class CommerceApi {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base = System.getProperty("commerce.baseUrl", "http://localhost:8080");
    private static final String PASSWORD = "Integration-password-123!";

    record Response(int status, JsonNode body) {
        JsonNode expect(int expected) {
            assertEquals(expected, status, () -> "Unexpected API response: " + body);
            return body;
        }
    }

    Response request(String method, String path, String token, String key, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (key != null) builder.header("Idempotency-Key", key);
        builder.header("Content-Type", "application/json").method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body()));
    }

    String login(String email, String password) throws Exception {
        var result = request("POST", "/api/auth/login", null, null, Map.of("email", email, "password", password)).expect(200);
        assertEquals("Bearer", result.path("tokenType").asText());
        assertEquals(900, result.path("expiresIn").asInt());
        return result.required("accessToken").asText();
    }

    String customer() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        request("POST", "/api/auth/register", null, null, Map.of("email", email, "password", PASSWORD)).expect(201);
        return login(email, PASSWORD);
    }

    String admin() throws Exception {
        String email = System.getenv("ADMIN_EMAIL"), password = System.getenv("ADMIN_PASSWORD");
        assertTrue(email != null && !email.isBlank() && password != null && !password.isBlank(),
                "Set ADMIN_EMAIL and ADMIN_PASSWORD to the same credentials used to start Compose");
        return login(email, password);
    }

    Map<String, Object> productInput() {
        return Map.of("sku", "IT-" + UUID.randomUUID(), "name", "Integration widget", "description", "Checkout fixture",
                "category", "integration", "price", 10.00);
    }

    String product(String admin, int stock) throws Exception {
        String id = request("POST", "/api/products", admin, null, productInput()).expect(201).required("id").asText();
        restock(admin, id, UUID.randomUUID().toString(), stock).expect(200);
        return id;
    }

    Response restock(String token, String product, String key, int quantity) throws Exception {
        return request("POST", "/api/inventory/" + product + "/restock", token, key, Map.of("quantity", quantity));
    }

    Map<String, Object> orderBody(String product, String mode, int quantity) {
        return Map.of("items", List.of(Map.of("productId", product, "quantity", quantity)), "paymentMode", mode);
    }

    JsonNode order(String customer, String product, String mode, String key) throws Exception {
        return request("POST", "/api/orders", customer, key, orderBody(product, mode, 1)).expect(202);
    }

    JsonNode terminal(String customer, String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        JsonNode last;
        do {
            last = request("GET", "/api/orders/" + id, customer, null, null).expect(200);
            if (Set.of("CONFIRMED", "CANCELLED").contains(last.path("state").asText())) return last;
            Thread.sleep(1000);
        } while (System.nanoTime() < deadline);
        fail("Order did not reach a terminal state: " + last);
        return last;
    }

    JsonNode await(String path, String token, Predicate<JsonNode> ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        JsonNode last;
        do {
            last = request("GET", path, token, null, null).expect(200);
            if (ready.test(last)) return last;
            Thread.sleep(1000);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for " + path + ": " + last);
        return last;
    }
}
