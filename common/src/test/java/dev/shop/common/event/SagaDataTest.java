package dev.shop.common.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SagaDataTest {

    @Test
    void validSagaData() {
        UUID pid = UUID.randomUUID();
        var data = new SagaData(UUID.randomUUID(),
                List.of(new SagaData.Line(pid, 3, new BigDecimal("10.00"))),
                new BigDecimal("30.00"), SagaData.PaymentMode.SUCCESS, null);
        assertEquals(1, data.lines().size());
        assertEquals(new BigDecimal("30.00"), data.total());
    }

    @Test
    void rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                new SagaData(UUID.randomUUID(),
                        List.of(new SagaData.Line(UUID.randomUUID(), 0, new BigDecimal("10.00"))),
                        new BigDecimal("0.00"), SagaData.PaymentMode.SUCCESS, null));
    }

    @Test
    void rejectsMismatchedTotal() {
        assertThrows(IllegalArgumentException.class, () ->
                new SagaData(UUID.randomUUID(),
                        List.of(new SagaData.Line(UUID.randomUUID(), 2, new BigDecimal("10.00"))),
                        new BigDecimal("15.00"), SagaData.PaymentMode.SUCCESS, null));
    }

    @Test
    void rejectsDuplicateProductLines() {
        UUID pid = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                new SagaData(UUID.randomUUID(),
                        List.of(new SagaData.Line(pid, 1, new BigDecimal("10.00")),
                                new SagaData.Line(pid, 2, new BigDecimal("10.00"))),
                        new BigDecimal("30.00"), SagaData.PaymentMode.SUCCESS, null));
    }

    @Test
    void withReasonCreatesNewInstance() {
        var data = new SagaData(UUID.randomUUID(),
                List.of(new SagaData.Line(UUID.randomUUID(), 1, new BigDecimal("10.00"))),
                new BigDecimal("10.00"), SagaData.PaymentMode.DECLINE, null);
        var updated = data.withReason("PAYMENT_DECLINED");
        assertEquals("PAYMENT_DECLINED", updated.reason());
        assertNull(data.reason());
    }
}
