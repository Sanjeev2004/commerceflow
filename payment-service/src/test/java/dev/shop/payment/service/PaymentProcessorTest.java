package dev.shop.payment.service;

import dev.shop.common.event.SagaData;
import dev.shop.common.event.SagaData.PaymentMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentProcessorTest {

    private final PaymentProcessor processor = new PaymentProcessor();

    private SagaData data(PaymentMode mode) {
        return new SagaData(UUID.randomUUID(),
                List.of(new SagaData.Line(UUID.randomUUID(), 1, new BigDecimal("49.99"))),
                new BigDecimal("49.99"), mode, null);
    }

    @Test
    void successModeReturnsSuccess() {
        var result = processor.process(PaymentMode.SUCCESS);
        assertTrue(result.success());
        assertNull(result.reason());
    }

    @Test
    void declineModeReturnsFailureWithReason() {
        var result = processor.process(PaymentMode.DECLINE);
        assertFalse(result.success());
        assertEquals("PAYMENT_DECLINED", result.reason());
    }

    @Test
    void cancelModeReturnsFailureWithReason() {
        var result = processor.process(PaymentMode.CANCEL);
        assertFalse(result.success());
        assertEquals("PAYMENT_CANCELLED", result.reason());
    }
}
