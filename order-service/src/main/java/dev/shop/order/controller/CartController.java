package dev.shop.order.controller;

import dev.shop.order.dto.CartDtos.*;
import dev.shop.order.dto.OrderDtos;
import dev.shop.order.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartView get(@AuthenticationPrincipal Jwt jwt) {
        return cart.getCart(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartView addItem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddItem input) {
        return cart.addItem(UUID.fromString(jwt.getSubject()), input);
    }

    @PutMapping("/items/{productId}")
    public CartView updateQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId, @Valid @RequestBody UpdateQuantity input) {
        return cart.updateQuantity(UUID.fromString(jwt.getSubject()), productId, input.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartView removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return cart.removeItem(UUID.fromString(jwt.getSubject()), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) {
        cart.clearCart(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderDtos.View> checkout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) String key,
            @Valid @RequestBody CheckoutInput input) {
        var order = cart.checkout(UUID.fromString(jwt.getSubject()), key, input.paymentMode());
        return ResponseEntity.accepted().location(URI.create("/api/orders/" + order.id())).body(order);
    }
}
