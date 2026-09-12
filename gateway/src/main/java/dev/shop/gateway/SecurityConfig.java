package dev.shop.gateway;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
@Configuration
public class SecurityConfig {
    @Bean ReactiveJwtDecoder decoder(@Value("${JWT_SECRET}") String secret) {
        if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        var decoder=NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("commerce-auth"),new JwtClaimValidator<List<String>>("aud",aud->aud!=null&&aud.contains("commerce-api"))));
        return decoder;
    }
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable).authorizeExchange(a->a
                .pathMatchers("/actuator/health/**","/actuator/prometheus").permitAll()
                .pathMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login").permitAll()
                .pathMatchers(HttpMethod.GET,"/api/products/**","/api/search/**").permitAll().anyExchange().authenticated())
                .oauth2ResourceServer(o->o.jwt(j->{}).authenticationEntryPoint((exchange,ex)->GatewayErrors.write(exchange,401,"Valid bearer token required")))
                .build();
    }
}
