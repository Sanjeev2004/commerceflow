package dev.shop.order.event;
import dev.shop.common.event.*;
import dev.shop.order.service.OrderSaga;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class OrderListener {
    private final EventCodec codec; private final Inbox inbox; private final OrderSaga saga;
    public OrderListener(EventCodec codec,Inbox inbox,OrderSaga saga) { this.codec=codec;this.inbox=inbox;this.saga=saga; }
    @KafkaListener(topics="commerce.events",autoStartup="${commerce.messaging.enabled:true}")
    public void receive(String body) {
        var event=codec.decode(body);
        switch(event.type()) {
            case INVENTORY_RESERVED,INVENTORY_REJECTED,PAYMENT_COMPLETED,PAYMENT_FAILED,INVENTORY_RELEASED -> inbox.once(event,()->saga.handle(event));
            default -> { }
        }
    }
}
