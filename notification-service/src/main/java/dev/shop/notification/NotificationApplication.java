package dev.shop.notification;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.notification","dev.shop.common"})
public class NotificationApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(NotificationApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "notification-service"));
        app.run(args);
    }
}
