package dev.shop.product.event;
import dev.shop.common.event.*;
import dev.shop.product.config.ProductCache;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class ProductCacheListener {
    private final EventCodec codec; private final Inbox inbox; private final ProductCache cache;
    public ProductCacheListener(EventCodec codec,Inbox inbox,ProductCache cache) { this.codec=codec;this.inbox=inbox;this.cache=cache; }
    @KafkaListener(topics="commerce.events",autoStartup="${commerce.messaging.enabled:true}")
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.PRODUCT_CHANGED) inbox.once(event,()->cache.evict(event.aggregateId()));
    }
}
