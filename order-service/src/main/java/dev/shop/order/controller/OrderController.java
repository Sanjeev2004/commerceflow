package dev.shop.order.controller;
import dev.shop.order.dto.OrderDtos.*;
import dev.shop.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders=orders; }
    @PostMapping public ResponseEntity<View> create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") @Size(min=8,max=100) String key,@Valid @RequestBody Create input) {
        var order=orders.create(UUID.fromString(jwt.getSubject()),key,input);
        return ResponseEntity.accepted().location(URI.create("/api/orders/"+order.id())).body(order);
    }
    @GetMapping("/{id}") public View get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return orders.get(id,UUID.fromString(jwt.getSubject())); }
    @GetMapping public List<View> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return orders.list(UUID.fromString(jwt.getSubject()),page,size); }
}
