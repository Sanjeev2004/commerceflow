package dev.shop.payment.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record PaymentView(UUID id,UUID orderId,BigDecimal amount,String status,String reason) {}
