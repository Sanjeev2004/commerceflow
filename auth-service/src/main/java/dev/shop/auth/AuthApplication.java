package dev.shop.auth;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"dev.shop.auth", "dev.shop.common"})
public class AuthApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(AuthApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "auth-service"));
        app.run(args);
    }
}
