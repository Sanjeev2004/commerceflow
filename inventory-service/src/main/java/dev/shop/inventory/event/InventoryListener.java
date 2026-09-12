package dev.shop.inventory.event;
import dev.shop.common.event.*;
import dev.shop.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class InventoryListener {
    private final EventCodec codec; private final Inbox inbox; private final InventoryService inventory;
    public InventoryListener(EventCodec codec,Inbox inbox,InventoryService inventory) { this.codec=codec;this.inbox=inbox;this.inventory=inventory; }
    @KafkaListener(topics="commerce.events",autoStartup="${commerce.messaging.enabled:true}")
    public void receive(String body) {
        var event=codec.decode(body);
        switch(event.type()) {
            case ORDER_CREATED -> inbox.once(event,()->inventory.reserve(event.aggregateId(),codec.payload(event,SagaData.class)));
            case INVENTORY_RELEASE_REQUESTED -> inbox.once(event,()->inventory.release(event.aggregateId(),codec.payload(event,SagaData.class)));
            case ORDER_CONFIRMED -> inbox.once(event,()->inventory.commit(event.aggregateId()));
            default -> { }
        }
    }
}
