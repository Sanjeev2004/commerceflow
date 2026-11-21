package dev.shop.inventory.event;
import dev.shop.common.event.*;
import dev.shop.inventory.service.InventoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@Import({InventoryListener.KafkaDelivery.class, InventoryListener.LocalDelivery.class})
public class InventoryListener {
    private final EventCodec codec; private final Inbox inbox; private final InventoryService inventory;
    public InventoryListener(EventCodec codec,Inbox inbox,InventoryService inventory) { this.codec=codec;this.inbox=inbox;this.inventory=inventory; }
    public void receive(String body) {
        var event=codec.decode(body);
        switch(event.type()) {
            case ORDER_CREATED -> inbox.once(event,()->inventory.reserve(event.aggregateId(),codec.payload(event,SagaData.class)));
            case INVENTORY_RELEASE_REQUESTED -> inbox.once(event,()->inventory.release(event.aggregateId(),codec.payload(event,SagaData.class)));
            case ORDER_CONFIRMED -> inbox.once(event,()->inventory.commit(event.aggregateId()));
            default -> { }
        }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="true", matchIfMissing=true)
    static class KafkaDelivery {
        private final InventoryListener listener;
        KafkaDelivery(InventoryListener listener) { this.listener=listener; }
        @KafkaListener(topics="commerce.events")
        public void receive(String body) { listener.receive(body); }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="false")
    static class LocalDelivery {
        LocalDelivery(InventoryListener listener) { LocalEvents.subscribe(listener::receive); }
    }
}
