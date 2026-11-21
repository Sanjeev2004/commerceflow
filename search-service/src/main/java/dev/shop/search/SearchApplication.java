package dev.shop.search;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages={"dev.shop.search","dev.shop.common"})
public class SearchApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(SearchApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.config.name", "search-service"));
        app.run(args);
    }
}
