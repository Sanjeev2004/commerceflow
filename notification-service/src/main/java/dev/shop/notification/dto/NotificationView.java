package dev.shop.notification.dto;
import java.time.Instant;
import java.util.UUID;
public record NotificationView(UUID id,UUID orderId,String kind,String message,Instant createdAt) {}
