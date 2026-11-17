package dev.shop.order.dto;

import dev.shop.common.event.SagaData.PaymentMode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public final class CartDtos {
    private CartDtos() {}

    public record AddItem(@NotNull UUID productId, @Min(1) @Max(1000) int quantity) {}
    public record UpdateQuantity(@Min(0) @Max(1000) int quantity) {}
    public record CartItem(UUID productId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, boolean active) {}
    public record CartView(UUID userId, List<CartItem> items, BigDecimal subtotal, int totalQuantity) {}
    public record CheckoutInput(@NotNull PaymentMode paymentMode) {}
}
