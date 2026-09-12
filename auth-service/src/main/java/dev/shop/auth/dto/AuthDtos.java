package dev.shop.auth.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class AuthDtos {
    private AuthDtos() {}
    public record Credentials(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12,max=72) String password) {}
    public record UserView(UUID id, String email, String role) {}
    public record Token(String accessToken, String tokenType, long expiresIn) {}
}
