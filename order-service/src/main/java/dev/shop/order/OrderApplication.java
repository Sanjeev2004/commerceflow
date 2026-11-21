package dev.shop.order;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.order","dev.shop.common"})
public class OrderApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(OrderApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "order-service"));
        app.run(args);
    }
}
