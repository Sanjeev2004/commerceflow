package dev.shop.inventory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.inventory","dev.shop.common"})
public class InventoryApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(InventoryApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "inventory-service"));
        app.run(args);
    }
}
