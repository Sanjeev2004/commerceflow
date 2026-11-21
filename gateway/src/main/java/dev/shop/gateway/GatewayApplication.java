package dev.shop.gateway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(GatewayApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "gateway"));
        app.run(args);
    }
}
