package dev.shop.auth.controller;
import dev.shop.auth.dto.AuthDtos.*;
import dev.shop.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody Credentials input) { return auth.register(input); }
    @PostMapping("/login") public Token login(@Valid @RequestBody Credentials input) { return auth.login(input); }
    @GetMapping("/me")
    public UserView me(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return auth.me(java.util.UUID.fromString(jwt.getSubject()));
    }
}
