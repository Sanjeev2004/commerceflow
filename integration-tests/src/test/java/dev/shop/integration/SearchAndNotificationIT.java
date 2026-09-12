package dev.shop.integration;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchAndNotificationIT {
    private final CommerceApi api = new CommerceApi();

    @Test void searchProjectsUpdatesAndDeletion() throws Exception {
        String admin = api.admin(), category = "search-" + UUID.randomUUID();
        var input = new HashMap<String, Object>(api.productInput());
        input.put("category", category);
        String id = api.request("POST", "/api/products", admin, null, input).expect(201).required("id").asText();
        String path = "/api/search?category=" + category + "&minPrice=5&maxPrice=15";
        var found = api.await(path, null, body -> body.path("totalElements").asInt() == 1);
        assertEquals(id, found.path("content").get(0).path("id").asText());
        input.put("name", "Updated searchable widget");
        api.request("PUT", "/api/products/" + id, admin, null, input).expect(200);
        api.await(path, null, body -> body.path("content").size() == 1 &&
                body.path("content").get(0).path("name").asText().equals("Updated searchable widget"));
        api.request("GET", "/api/search?minPrice=20&maxPrice=10", null, null, null).expect(400);
        api.request("DELETE", "/api/products/" + id, admin, null, null).expect(204);
        api.await(path, null, body -> body.path("totalElements").asInt() == 0);
    }

    @Test void paymentAndNotificationsAreVisibleOnlyToOwner() throws Exception {
        String admin = api.admin(), owner = api.customer(), stranger = api.customer();
        String product = api.product(admin, 1);
        String id = api.order(owner, product, "SUCCESS", UUID.randomUUID().toString()).required("id").asText();
        assertEquals("CONFIRMED", api.terminal(owner, id).path("state").asText());
        api.request("GET", "/api/payments/orders/" + id, owner, null, null).expect(200);
        api.request("GET", "/api/payments/orders/" + id, stranger, null, null).expect(404);
        api.request("GET", "/api/orders/" + id, stranger, null, null).expect(404);
        var notifications = api.await("/api/notifications", owner, body -> body.size() == 1);
        assertEquals(id, notifications.get(0).path("orderId").asText());
        assertEquals("ORDER_CONFIRMED", notifications.get(0).path("kind").asText());
        assertEquals(0, api.request("GET", "/api/notifications", stranger, null, null).expect(200).size());
        api.await("/api/inventory/" + product, admin, body -> body.path("reserved").asInt() == 0);
    }
}
