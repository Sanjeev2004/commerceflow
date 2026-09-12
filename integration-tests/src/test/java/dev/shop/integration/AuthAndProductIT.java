package dev.shop.integration;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthAndProductIT {
    private final CommerceApi api = new CommerceApi();

    @Test void registrationLoginAndProductAuthorization() throws Exception {
        String email = UUID.randomUUID() + "@example.com", password = "Integration-password-123!";
        var credentials = Map.of("email", email, "password", password);
        var user = api.request("POST", "/api/auth/register", null, null, credentials).expect(201);
        assertEquals("CUSTOMER", user.path("role").asText());
        assertEquals(email, user.path("email").asText());
        api.request("POST", "/api/auth/register", null, null, credentials).expect(409);
        String customer = api.login(email, password);
        api.request("POST", "/api/auth/login", null, null, Map.of("email", email, "password", "Wrong-password-123!")).expect(401);
        api.request("GET", "/api/products/" + UUID.randomUUID(), null, null, null).expect(404);
        api.request("POST", "/api/products", customer, null, api.productInput()).expect(403);
        String id = api.product(api.admin(), 1);
        assertEquals(id, api.request("GET", "/api/products/" + id, null, null, null).expect(200).path("id").asText());
    }
}
