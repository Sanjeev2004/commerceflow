package dev.shop.auth.service;

import dev.shop.auth.dto.AuthDtos.Credentials;
import dev.shop.auth.entity.UserAccount;
import dev.shop.auth.repository.UserRepository;
import dev.shop.common.api.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test void loginRejectsUtf8PasswordsBeyondBcryptLimit() {
        var users = mock(UserRepository.class);
        var encoder = mock(JwtEncoder.class);
        var passwords = new BCryptPasswordEncoder(4);
        String valid = "é".repeat(36);
        when(users.byEmail("user@example.com")).thenReturn(Optional.of(new UserAccount(
                UUID.randomUUID(), "user@example.com", passwords.encode(valid), "CUSTOMER")));
        var auth = new AuthService(users, passwords, encoder);

        var error = assertThrows(ApiException.class,
                () -> auth.login(new Credentials("user@example.com", valid + "x")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.status());
        verifyNoInteractions(encoder);
    }

    @Test void meReturnsUserProfileWhenFound() {
        var users = mock(UserRepository.class);
        var encoder = mock(JwtEncoder.class);
        var passwords = new BCryptPasswordEncoder(4);
        var userId = UUID.randomUUID();
        when(users.byId(userId)).thenReturn(Optional.of(new UserAccount(
                userId, "user@example.com", "hash", "CUSTOMER")));
        var auth = new AuthService(users, passwords, encoder);

        var profile = auth.me(userId);
        assertEquals(userId, profile.id());
        assertEquals("user@example.com", profile.email());
        assertEquals("CUSTOMER", profile.role());
    }

    @Test void meThrowsNotFoundWhenUserDoesNotExist() {
        var users = mock(UserRepository.class);
        var encoder = mock(JwtEncoder.class);
        var passwords = new BCryptPasswordEncoder(4);
        var userId = UUID.randomUUID();
        when(users.byId(userId)).thenReturn(Optional.empty());
        var auth = new AuthService(users, passwords, encoder);

        var error = assertThrows(ApiException.class, () -> auth.me(userId));
        assertEquals(HttpStatus.NOT_FOUND, error.status());
    }
}
