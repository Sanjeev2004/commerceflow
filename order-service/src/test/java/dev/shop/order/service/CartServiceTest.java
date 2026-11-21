package dev.shop.order.service;

import dev.shop.common.api.ApiException;
import dev.shop.common.event.SagaData.PaymentMode;
import dev.shop.order.dto.CartDtos.AddItem;
import dev.shop.order.dto.OrderDtos;
import dev.shop.order.entity.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ProductClient products;
    @Mock private OrderService orders;

    private CartService cart;

    @BeforeEach
    void setup() {
        when(redis.opsForHash()).thenReturn(hashOps);
        cart = new CartService(redis, products, orders);
    }

    @Test
    void getCartReturnsEmptyCartWhenNoItemsInRedis() {
        var userId = UUID.randomUUID();
        when(hashOps.entries("cart:" + userId)).thenReturn(Collections.emptyMap());

        var result = cart.getCart(userId);

        assertEquals(userId, result.userId());
        assertTrue(result.items().isEmpty());
        assertEquals(BigDecimal.ZERO, result.subtotal());
        assertEquals(0, result.totalQuantity());
    }

    @Test
    void getCartCalculatesSubtotalAndQuotes() {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(hashOps.entries("cart:" + userId)).thenReturn(Map.of(productId.toString(), "2"));
        when(products.quote(productId)).thenReturn(new ProductClient.Quote(productId, "KB-1", "Keyboard", BigDecimal.valueOf(50.00), true));

        var result = cart.getCart(userId);

        assertEquals(1, result.items().size());
        assertEquals(2, result.totalQuantity());
        assertEquals(BigDecimal.valueOf(100.00), result.subtotal());
        assertEquals("Keyboard", result.items().get(0).name());
    }

    @Test
    void addItemIncrementsRedisAndRefreshesTtl() {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(products.quote(productId)).thenReturn(new ProductClient.Quote(productId, "KB-1", "Keyboard", BigDecimal.valueOf(50.00), true));
        when(hashOps.entries("cart:" + userId)).thenReturn(Map.of(productId.toString(), "1"));

        var result = cart.addItem(userId, new AddItem(productId, 1));

        verify(hashOps).increment("cart:" + userId, productId.toString(), 1);
        verify(redis).expire(eq("cart:" + userId), any(Duration.class));
        assertEquals(1, result.items().size());
    }

    @Test
    void addItemRejectsQuantityThatWouldExceedLimit() {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(products.quote(productId)).thenReturn(new ProductClient.Quote(productId, "KB-1", "Keyboard", BigDecimal.valueOf(50.00), true));
        when(hashOps.get("cart:" + userId, productId.toString())).thenReturn("1000");

        var ex = assertThrows(ApiException.class, () -> cart.addItem(userId, new AddItem(productId, 1)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        verify(hashOps, never()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    void checkoutThrowsBadRequestWhenCartIsEmpty() {
        var userId = UUID.randomUUID();
        when(hashOps.entries("cart:" + userId)).thenReturn(Collections.emptyMap());

        var ex = assertThrows(ApiException.class, () -> cart.checkout(userId, "key-12345", PaymentMode.SUCCESS));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    }

    @Test
    void checkoutClearsCartUponOrderCreation() {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(hashOps.entries("cart:" + userId)).thenReturn(Map.of(productId.toString(), "1"));

        var mockView = new OrderDtos.View(UUID.randomUUID(), userId, OrderState.CREATED, BigDecimal.TEN, List.of(), null, Instant.now(), Instant.now(), List.of());
        when(orders.create(eq(userId), eq("key-12345"), any(OrderDtos.Create.class))).thenReturn(mockView);

        var result = cart.checkout(userId, "key-12345", PaymentMode.SUCCESS);

        assertNotNull(result);
        verify(orders).create(eq(userId), eq("key-12345"), any(OrderDtos.Create.class));
        verify(redis).delete("cart:" + userId);
    }
}
