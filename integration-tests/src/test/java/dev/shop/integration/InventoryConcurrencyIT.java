package dev.shop.integration;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryConcurrencyIT {
    private final CommerceApi api = new CommerceApi();

    @Test void restockIsIdempotentAndRequiresAdmin() throws Exception {
        String admin = api.admin(), product = api.product(admin, 1), key = UUID.randomUUID().toString();
        api.restock(admin, product, key, 5).expect(200);
        assertEquals(6, api.restock(admin, product, key, 5).expect(200).path("available").asInt());
        api.restock(admin, product, key, 6).expect(409);
        api.restock(api.customer(), product, UUID.randomUUID().toString(), 1).expect(403);
    }

    @Test void concurrentOrdersCannotOversellOneUnit() throws Exception {
        String admin = api.admin(), product = api.product(admin, 1), customer = api.customer();
        var start = new CountDownLatch(1);
        var orders = new ArrayList<Future<String>>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) orders.add(executor.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return api.order(customer, product, "SUCCESS", UUID.randomUUID().toString()).required("id").asText();
            }));
            start.countDown();
            int confirmed = 0, cancelled = 0;
            for (var future : orders) {
                var result = api.terminal(customer, future.get(30, TimeUnit.SECONDS));
                if (result.path("state").asText().equals("CONFIRMED")) confirmed++;
                else {
                    cancelled++;
                    assertEquals("INSUFFICIENT_STOCK", result.path("cancellationReason").asText());
                }
            }
            assertEquals(1, confirmed);
            assertEquals(3, cancelled);
        }
        assertEquals(0, api.request("GET", "/api/inventory/" + product, admin, null, null).expect(200).path("available").asInt());
    }
}
