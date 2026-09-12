package dev.shop.notification.controller;
import dev.shop.notification.dto.NotificationView;
import dev.shop.notification.repository.StoredNotificationSender;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final StoredNotificationSender notifications;
    public NotificationController(StoredNotificationSender notifications) { this.notifications=notifications; }
    @GetMapping public List<NotificationView> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return notifications.list(UUID.fromString(jwt.getSubject()),page,size); }
}
