package dev.shop.order.service;

import dev.shop.common.api.ApiException;
import dev.shop.common.event.SagaData.PaymentMode;
import dev.shop.order.dto.CartDtos.*;
import dev.shop.order.dto.OrderDtos;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CartService {
    private static final Duration CART_TTL = Duration.ofDays(7);
    static final int MAX_QUANTITY = 1000;
    private final StringRedisTemplate redis;
    private final ProductClient products;
    private final OrderService orders;

    public CartService(StringRedisTemplate redis, ProductClient products, OrderService orders) {
        this.redis = redis;
        this.products = products;
        this.orders = orders;
    }

    private String key(UUID userId) {
        return "cart:" + userId;
    }

    public CartView getCart(UUID userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        List<CartItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQty = 0;

        for (var entry : entries.entrySet()) {
            UUID productId = UUID.fromString(entry.getKey().toString());
            int quantity = storedQuantity(entry.getValue());
            totalQty += quantity;
            try {
                var quote = products.quote(productId);
                BigDecimal lineTotal = quote.price().multiply(BigDecimal.valueOf(quantity));
                subtotal = subtotal.add(lineTotal);
                items.add(new CartItem(productId, quote.name(), quantity, quote.price(), lineTotal, quote.active()));
            } catch (Exception ex) {
                // If product was deleted or unavailable, mark as unavailable
                items.add(new CartItem(productId, "Unavailable Product", quantity, BigDecimal.ZERO, BigDecimal.ZERO, false));
            }
        }
        return new CartView(userId, items, subtotal, totalQty);
    }

    public CartView addItem(UUID userId, AddItem input) {
        if (input.quantity() < 1 || input.quantity() > MAX_QUANTITY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity must be between 1 and 1000");
        }
        products.quote(input.productId());
        String redisKey = key(userId);
        int existing = storedQuantity(redis.opsForHash().get(redisKey, input.productId().toString()));
        if (existing + input.quantity() > MAX_QUANTITY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cart quantity cannot exceed 1000 for one product");
        }
        redis.opsForHash().increment(redisKey, input.productId().toString(), input.quantity());
        redis.expire(redisKey, CART_TTL);
        return getCart(userId);
    }

    public CartView updateQuantity(UUID userId, UUID productId, int quantity) {
        if (quantity > MAX_QUANTITY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity must be between 0 and 1000");
        }
        String redisKey = key(userId);
        if (quantity <= 0) {
            redis.opsForHash().delete(redisKey, productId.toString());
        } else {
            products.quote(productId);
            redis.opsForHash().put(redisKey, productId.toString(), String.valueOf(quantity));
            redis.expire(redisKey, CART_TTL);
        }
        return getCart(userId);
    }

    public CartView removeItem(UUID userId, UUID productId) {
        redis.opsForHash().delete(key(userId), productId.toString());
        return getCart(userId);
    }

    public void clearCart(UUID userId) {
        redis.delete(key(userId));
    }

    public OrderDtos.View checkout(UUID userId, String idempotencyKey, PaymentMode paymentMode) {
        Map<Object, Object> entries = redis.opsForHash().entries(key(userId));
        if (entries.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shopping cart is empty");
        }

        List<OrderDtos.Item> items = new ArrayList<>();
        for (var entry : entries.entrySet()) {
            UUID productId = UUID.fromString(entry.getKey().toString());
            int quantity = storedQuantity(entry.getValue());
            items.add(new OrderDtos.Item(productId, quantity));
        }

        var order = orders.create(userId, idempotencyKey, new OrderDtos.Create(items, paymentMode));
        redis.delete(key(userId));
        return order;
    }

    private int storedQuantity(Object raw) {
        if (raw == null) return 0;
        try {
            int quantity = Integer.parseInt(raw.toString());
            if (quantity < 0) throw new NumberFormatException("negative");
            return quantity;
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cart quantity is invalid");
        }
    }
}
