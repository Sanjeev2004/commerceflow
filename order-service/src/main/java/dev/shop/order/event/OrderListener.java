package dev.shop.order.event;
import dev.shop.common.event.*;
import dev.shop.order.service.OrderSaga;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@Import({OrderListener.KafkaDelivery.class, OrderListener.LocalDelivery.class})
public class OrderListener {
    private final EventCodec codec; private final Inbox inbox; private final OrderSaga saga;
    public OrderListener(EventCodec codec,Inbox inbox,OrderSaga saga) { this.codec=codec;this.inbox=inbox;this.saga=saga; }
    public void receive(String body) {
        var event=codec.decode(body);
        switch(event.type()) {
            case INVENTORY_RESERVED,INVENTORY_REJECTED,PAYMENT_COMPLETED,PAYMENT_FAILED,INVENTORY_RELEASED -> inbox.once(event,()->saga.handle(event));
            default -> { }
        }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="true", matchIfMissing=true)
    static class KafkaDelivery {
        private final OrderListener listener;
        KafkaDelivery(OrderListener listener) { this.listener=listener; }
        @KafkaListener(topics="commerce.events")
        public void receive(String body) { listener.receive(body); }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="false")
    static class LocalDelivery {
        LocalDelivery(OrderListener listener) { LocalEvents.subscribe(listener::receive); }
    }
}
