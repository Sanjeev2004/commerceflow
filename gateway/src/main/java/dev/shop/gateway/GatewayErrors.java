package dev.shop.gateway;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
@Component
@Order(-2)
public class GatewayErrors implements WebExceptionHandler {
    public Mono<Void> handle(ServerWebExchange exchange,Throwable error) {
        if(exchange.getResponse().isCommitted()) return Mono.error(error);
        int status=error instanceof ResponseStatusException ex?ex.getStatusCode().value():503;
        return write(exchange,status,status==404?"Route not found":"Request could not be completed");
    }
    static Mono<Void> write(ServerWebExchange exchange,int status,String detail) {
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String id=exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        byte[] body=("{\"status\":"+status+",\"detail\":\""+detail+"\",\"correlationId\":\""+id+"\"}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
