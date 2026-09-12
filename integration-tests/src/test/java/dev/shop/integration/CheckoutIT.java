package dev.shop.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutIT {
    private final CommerceApi api = new CommerceApi();

    @Test void checkoutAndOrderIdempotency() throws Exception {
        String customer = api.customer(), product = api.product(api.admin(), 2), key = UUID.randomUUID().toString();
        String id = api.order(customer, product, "SUCCESS", key).required("id").asText();
        assertEquals(id, api.order(customer, product, "SUCCESS", key).path("id").asText());
        api.request("POST", "/api/orders", customer, key, api.orderBody(product, "SUCCESS", 2)).expect(409);
        var result = api.terminal(customer, id);
        assertEquals("CONFIRMED", result.path("state").asText());
        assertEquals(10.0, result.path("total").asDouble());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECLINE", "CANCEL"})
    void paymentFailureRestoresInventory(String mode) throws Exception {
        String admin = api.admin(), customer = api.customer(), product = api.product(admin, 1);
        String id = api.order(customer, product, mode, UUID.randomUUID().toString()).required("id").asText();
        var result = api.terminal(customer, id);
        assertEquals("CANCELLED", result.path("state").asText());
        assertEquals(mode.equals("DECLINE") ? "PAYMENT_DECLINED" : "PAYMENT_CANCELLED", result.path("cancellationReason").asText());
        var stock = api.request("GET", "/api/inventory/" + product, admin, null, null).expect(200);
        assertEquals(1, stock.path("available").asInt());
        assertEquals(0, stock.path("reserved").asInt());
    }

    @Test void insufficientBasketDoesNotReserveOtherItemsOrTakePayment() throws Exception {
        String admin = api.admin(), customer = api.customer(), stocked = api.product(admin, 2);
        String empty = api.request("POST", "/api/products", admin, null, api.productInput()).expect(201).required("id").asText();
        var body = Map.of("paymentMode", "SUCCESS", "items", List.of(
                Map.of("productId", stocked, "quantity", 1), Map.of("productId", empty, "quantity", 1)));
        String id = api.request("POST", "/api/orders", customer, UUID.randomUUID().toString(), body)
                .expect(202).required("id").asText();
        var order = api.terminal(customer, id);
        assertEquals("CANCELLED", order.path("state").asText());
        assertEquals("INSUFFICIENT_STOCK", order.path("cancellationReason").asText());
        var stock = api.request("GET", "/api/inventory/" + stocked, admin, null, null).expect(200);
        assertEquals(2, stock.path("available").asInt());
        assertEquals(0, stock.path("reserved").asInt());
        api.request("GET", "/api/payments/orders/" + id, customer, null, null).expect(404);
    }
}
