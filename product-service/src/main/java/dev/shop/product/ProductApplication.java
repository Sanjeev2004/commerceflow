package dev.shop.product;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.product","dev.shop.common"})
public class ProductApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(ProductApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "product-service"));
        app.run(args);
    }
}
