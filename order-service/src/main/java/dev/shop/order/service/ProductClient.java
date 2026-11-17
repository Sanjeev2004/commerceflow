package dev.shop.order.service;
import dev.shop.common.api.ApiException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class ProductClient {
    private final RestClient client;
    public record Quote(UUID id, String sku, String name, BigDecimal price, boolean active) {}
    public ProductClient(@Value("${commerce.product-url}") String baseUrl) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()); factory.setReadTimeout(Duration.ofSeconds(2));
        client=RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    public Quote quote(UUID id) {
        try {
            var quote=client.get().uri("/api/products/{id}",id).header("Cache-Control","no-cache").header("X-Correlation-ID",java.util.Objects.requireNonNullElse(org.slf4j.MDC.get("correlationId"),UUID.randomUUID().toString())).retrieve().body(Quote.class);
            if(quote==null || !quote.active()) throw ApiException.notFound("Product unavailable");
            return quote;
        } catch(HttpClientErrorException.NotFound ex) { throw ApiException.notFound("Product "+id+" unavailable"); }
        catch(RestClientException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Catalog unavailable; retry with the same idempotency key"); }
    }
}
