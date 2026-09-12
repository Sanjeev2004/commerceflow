package dev.shop.notification.service;
import java.util.UUID;
public interface NotificationSender {
    void send(UUID orderId,UUID userId,String kind,String message);
}
