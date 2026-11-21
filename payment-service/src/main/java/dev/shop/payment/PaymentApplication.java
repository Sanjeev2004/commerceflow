package dev.shop.payment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.payment","dev.shop.common"})
public class PaymentApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(PaymentApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "payment-service"));
        app.run(args);
    }
}
