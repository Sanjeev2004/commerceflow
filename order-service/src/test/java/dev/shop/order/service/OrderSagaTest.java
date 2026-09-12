package dev.shop.order.service;

import dev.shop.common.event.SagaData;
import dev.shop.common.event.SagaData.PaymentMode;
import dev.shop.order.entity.OrderState;
import dev.shop.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import dev.shop.common.event.EventCodec;
import dev.shop.common.event.Outbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaTest {

    @Mock private OrderRepository orders;
    @Mock private Outbox outbox;
    private EventCodec codec = new EventCodec(new com.fasterxml.jackson.databind.ObjectMapper());
    private MeterRegistry meters = new SimpleMeterRegistry();
    private OrderSaga saga;

    private OrderRepository.Stored stored(OrderState state, String reason) {
        var data = new SagaData(UUID.randomUUID(),
                List.of(new SagaData.Line(UUID.randomUUID(), 1, BigDecimal.TEN)),
                BigDecimal.TEN, PaymentMode.SUCCESS, reason);
        return new OrderRepository.Stored(UUID.randomUUID(), UUID.randomUUID(), "hash", state, data, reason,
                java.time.Instant.now(), java.time.Instant.now());
    }

    @BeforeEach
    void setup() {
        saga = new OrderSaga(orders, outbox, codec);
    }

    @Test
    void inventoryReservedTransitionsToPaymentPending() {
        var order = stored(OrderState.CREATED, null);
        when(orders.find(order.id(), true)).thenReturn(Optional.of(order));

        var event = new dev.shop.common.event.DomainEvent(UUID.randomUUID(), 1,
                dev.shop.common.event.EventType.INVENTORY_RESERVED, order.id(),
                java.time.Instant.now(), "corr-1",
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(order.data()));

        saga.handle(event);

        verify(orders).transition(order.id(), OrderState.INVENTORY_RESERVED, null);
        verify(outbox).append(dev.shop.common.event.EventType.PAYMENT_REQUESTED, order.id(), order.data());
        verify(orders).transition(order.id(), OrderState.PAYMENT_PENDING, null);
    }

    @Test
    void paymentCompletedConfirmsOrder() {
        var order = stored(OrderState.PAYMENT_PENDING, null);
        when(orders.find(order.id(), true)).thenReturn(Optional.of(order));

        var event = new dev.shop.common.event.DomainEvent(UUID.randomUUID(), 1,
                dev.shop.common.event.EventType.PAYMENT_COMPLETED, order.id(),
                java.time.Instant.now(), "corr-2",
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(order.data()));

        saga.handle(event);

        verify(orders).transition(order.id(), OrderState.CONFIRMED, null);
        verify(outbox).append(dev.shop.common.event.EventType.ORDER_CONFIRMED, order.id(), order.data());
    }

    @Test
    void paymentFailedTriggersInventoryRelease() {
        var order = stored(OrderState.PAYMENT_PENDING, null);
        when(orders.find(order.id(), true)).thenReturn(Optional.of(order));

        var event = new dev.shop.common.event.DomainEvent(UUID.randomUUID(), 1,
                dev.shop.common.event.EventType.PAYMENT_FAILED, order.id(),
                java.time.Instant.now(), "corr-3",
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(order.data().withReason("PAYMENT_DECLINED")));

        saga.handle(event);

        verify(orders).reason(order.id(), "PAYMENT_DECLINED");
        verify(outbox).append(dev.shop.common.event.EventType.INVENTORY_RELEASE_REQUESTED, order.id(), order.data().withReason("PAYMENT_DECLINED"));
    }

    @Test
    void duplicateEventIsNoOp() {
        var order = stored(OrderState.CONFIRMED, null);
        when(orders.find(order.id(), true)).thenReturn(Optional.of(order));

        var event = new dev.shop.common.event.DomainEvent(UUID.randomUUID(), 1,
                dev.shop.common.event.EventType.PAYMENT_COMPLETED, order.id(),
                java.time.Instant.now(), "corr-4",
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(order.data()));

        saga.handle(event);

        verifyNoInteractions(outbox);
        verify(orders, never()).transition(any(), any(), any());
    }
}
