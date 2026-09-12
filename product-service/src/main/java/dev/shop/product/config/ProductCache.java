package dev.shop.product.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shop.product.dto.ProductDtos.View;
import java.time.Duration;
import java.util.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
@Component
public class ProductCache {
    private final StringRedisTemplate redis; private final ObjectMapper mapper; private final MeterRegistry meters;
    public ProductCache(StringRedisTemplate redis,ObjectMapper mapper,MeterRegistry meters) { this.redis=redis;this.mapper=mapper;this.meters=meters; }
    private String key(UUID id) { return "products:v1:"+id; }
    public Optional<View> get(UUID id) {
        try {
            String json=redis.opsForValue().get(key(id));
            if(json!=null) { meters.counter("commerce.cache.hits").increment(); return Optional.of(mapper.readValue(json,View.class)); }
        } catch(Exception ex) { meters.counter("commerce.cache.failures").increment(); }
        meters.counter("commerce.cache.misses").increment(); return Optional.empty();
    }
    public void put(View product) {
        try { redis.opsForValue().set(key(product.id()),mapper.writeValueAsString(product),Duration.ofSeconds(30)); }
        catch(Exception ex) { meters.counter("commerce.cache.failures").increment(); }
    }
    public void evict(UUID id) { redis.delete(key(id)); }
    public void evictBestEffort(UUID id) { try { evict(id); } catch(Exception ex) { meters.counter("commerce.cache.failures").increment(); } }
}
