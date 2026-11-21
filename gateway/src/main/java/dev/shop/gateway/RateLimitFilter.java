package dev.shop.gateway;
import java.security.Principal;
import java.util.*;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
@Component
public class RateLimitFilter implements WebFilter,Ordered {
    private final ReactiveStringRedisTemplate redis;
    private final DefaultRedisScript<List> script=new DefaultRedisScript<>();
    public RateLimitFilter(ReactiveStringRedisTemplate redis) { this.redis=redis;script.setLocation(new ClassPathResource("token-bucket.lua"));script.setResultType(List.class); }
    public int getOrder() { return -50; }
    public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain) {
        String path=exchange.getRequest().getPath().value();
        if(!path.startsWith("/api/")) return chain.filter(exchange);
        boolean auth=path.startsWith("/api/auth/");
        String ip=exchange.getRequest().getRemoteAddress()==null?"unknown":exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return exchange.getPrincipal().map(Principal::getName).defaultIfEmpty("ip:"+ip).flatMap(identity-> {
            String key="rate:"+(auth?"auth:":"api:")+identity;
            // Token bucket refill is tokens per second. Bursts match the published per-minute limits.
            return redis.execute(script,List.of(key),List.of(auth?"0.1666667":"0.6666667",auth?"10":"40")).single()
                    .map(result->((Number)result.getFirst()).longValue()==1)
                    .onErrorMap(ex->new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Rate limiter unavailable",ex))
                    .flatMap(allowed->{
                        if(allowed) return chain.filter(exchange);
                        exchange.getResponse().getHeaders().set("Retry-After","1");
                        return GatewayErrors.write(exchange,429,"Rate limit exceeded");
                    });
        });
    }
}
