package dev.shop.auth;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"dev.shop.auth", "dev.shop.common"})
public class AuthApplication { public static void main(String[] args) { SpringApplication.run(AuthApplication.class, args); } }
