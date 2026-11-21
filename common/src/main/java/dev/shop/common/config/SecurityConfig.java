package dev.shop.common.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("commerce-auth"),
                new JwtClaimValidator<java.util.List<String>>("aud", aud -> aud != null && aud.contains("commerce-api"))));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter(); roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(roles);
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/health", "/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/search/**").permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> { res.setStatus(401); res.setContentType("application/problem+json"); res.getWriter().write("{\"status\":401,\"detail\":\"Valid bearer token required\"}"); }))
                .build();
    }
}
