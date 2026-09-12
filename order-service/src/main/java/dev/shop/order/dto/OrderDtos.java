package dev.shop.order.dto;
import dev.shop.common.event.SagaData;
import dev.shop.common.event.SagaData.PaymentMode;
import dev.shop.order.entity.OrderState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class OrderDtos {
    private OrderDtos() {}
    public record Item(@NotNull UUID productId,@Min(1) @Max(1000) int quantity) {}
    public record Create(@NotEmpty @Size(max=20) List<@Valid Item> items,@NotNull PaymentMode paymentMode) {}
    public record View(UUID id,UUID userId,OrderState state,BigDecimal total,List<SagaData.Line> items,String cancellationReason,Instant createdAt,Instant updatedAt,List<String> history) {}
}
