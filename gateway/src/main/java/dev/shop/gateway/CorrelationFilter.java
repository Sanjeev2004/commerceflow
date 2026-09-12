package dev.shop.gateway;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
@Component
public class CorrelationFilter implements WebFilter, Ordered {
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain) {
        String id=exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if(id==null||!id.matches("[a-zA-Z0-9_-]{1,64}")) id=UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Correlation-ID",id);
        return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().headers(h->{h.remove("X-User-ID");h.remove("X-User-Roles");}).header("X-Correlation-ID",id).build()).build());
    }
}
