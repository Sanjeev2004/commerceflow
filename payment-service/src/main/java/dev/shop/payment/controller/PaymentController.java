package dev.shop.payment.controller;
import dev.shop.common.api.ApiException;
import dev.shop.payment.dto.PaymentView;
import dev.shop.payment.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentRepository payments;
    public PaymentController(PaymentRepository payments) { this.payments=payments; }
    @GetMapping("/orders/{id}") public PaymentView get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {
        return payments.find(id,UUID.fromString(jwt.getSubject())).orElseThrow(()->ApiException.notFound("Payment not found"));
    }
}
