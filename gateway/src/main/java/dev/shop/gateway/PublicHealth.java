package dev.shop.gateway;

import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PublicHealth {
    private final ReactiveStringRedisTemplate redis;

    public PublicHealth(ReactiveStringRedisTemplate redis) { this.redis = redis; }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return redis.hasKey("health:ping")
                .timeout(Duration.ofSeconds(2))
                .map(ignored -> ResponseEntity.ok(Map.of("status", "UP")))
                .onErrorReturn(ResponseEntity.status(503).body(Map.of("status", "DOWN")));
    }
}
