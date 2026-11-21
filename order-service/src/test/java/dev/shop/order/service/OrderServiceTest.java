package dev.shop.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shop.common.api.ApiException;
import dev.shop.common.event.EventCodec;
import dev.shop.common.event.Outbox;
import dev.shop.common.event.SagaData.PaymentMode;
import dev.shop.order.dto.OrderDtos.Create;
import dev.shop.order.dto.OrderDtos.Item;
import dev.shop.order.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OrderServiceTest {
    @Test
    void createRejectsQuantityAboveLimit() {
        var service = new OrderService(mock(OrderRepository.class), new ProductClient("http://127.0.0.1:9"),
                mock(Outbox.class), new EventCodec(new ObjectMapper()), new SimpleMeterRegistry());
        var input = new Create(List.of(new Item(UUID.randomUUID(), 1001)), PaymentMode.SUCCESS);

        var error = assertThrows(ApiException.class, () -> service.create(UUID.randomUUID(), "idempotency-key", input));

        assertEquals(400, error.status().value());
    }

    @Test
    void listAllRejectsUnknownState() {
        var service = new OrderService(mock(OrderRepository.class), new ProductClient("http://127.0.0.1:9"),
                mock(Outbox.class), new EventCodec(new ObjectMapper()), new SimpleMeterRegistry());

        var error = assertThrows(ApiException.class, () -> service.listAll("SHIPPED", 0, 20));

        assertEquals(400, error.status().value());
    }
}
