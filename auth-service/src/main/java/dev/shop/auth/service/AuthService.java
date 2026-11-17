package dev.shop.auth.service;
import dev.shop.auth.dto.AuthDtos.*;
import dev.shop.auth.entity.UserAccount;
import dev.shop.auth.repository.UserRepository;
import dev.shop.common.api.ApiException;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String dummyHash;
    public AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder) {
        this.users=users; this.passwords=passwords; this.encoder=encoder; this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public UserView register(Credentials input) { return create(input, "CUSTOMER"); }
    public UserView create(Credentials input, String role) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiException(HttpStatus.BAD_REQUEST,"Password must fit within 72 UTF-8 bytes");
        var user = new UserAccount(UUID.randomUUID(),input.email().trim().toLowerCase(Locale.ROOT), passwords.encode(input.password()),role);
        try { users.insert(user); } catch (DuplicateKeyException ex) { throw ApiException.conflict("Email already registered"); }
        return new UserView(user.id(), user.email(), user.role());
    }
    public Token login(Credentials input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
        var user = users.byEmail(input.email().trim().toLowerCase(Locale.ROOT));
        boolean matches = passwords.matches(input.password(),user.map(UserAccount::passwordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) throw new ApiException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("commerce-auth").audience(List.of("commerce-api")).subject(user.get().id().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles",List.of(user.get().role())).build();
        return new Token(encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue(),"Bearer",900);
    }
    public UserView me(UUID userId) {
        var user = users.byId(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return new UserView(user.id(), user.email(), user.role());
    }
}
