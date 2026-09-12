package dev.shop.auth.entity;
import java.util.UUID;
public record UserAccount(UUID id, String email, String passwordHash, String role) {}
