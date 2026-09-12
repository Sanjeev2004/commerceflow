package dev.shop.auth.config;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.shop.auth.dto.AuthDtos.Credentials;
import dev.shop.auth.repository.UserRepository;
import dev.shop.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
@Configuration
public class AuthConfig {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }
    @Bean JwtEncoder encoder(@Value("${security.jwt.secret}") String secret) { return new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8))); }
    @Bean ApplicationRunner bootstrapAdmin(AuthService auth, UserRepository users, @Value("${auth.admin-email}") String email, @Value("${auth.admin-password}") String password) {
        return args -> {
            if (email.isBlank() && password.isBlank()) return;
            if (!email.contains("@") || password.length()<12) throw new IllegalArgumentException("Bootstrap admin requires valid email and password of at least 12 characters");
            if (users.byEmail(email.trim().toLowerCase(Locale.ROOT)).isEmpty()) {
                try { auth.create(new Credentials(email,password),"ADMIN"); }
                catch (dev.shop.common.api.ApiException ex) { if (ex.status() != org.springframework.http.HttpStatus.CONFLICT) throw ex; }
            }
        };
    }
}
