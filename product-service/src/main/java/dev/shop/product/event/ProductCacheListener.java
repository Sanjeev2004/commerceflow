package dev.shop.product.event;
import dev.shop.common.event.*;
import dev.shop.product.config.ProductCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@Import({ProductCacheListener.KafkaDelivery.class, ProductCacheListener.LocalDelivery.class})
public class ProductCacheListener {
    private final EventCodec codec; private final Inbox inbox; private final ProductCache cache;
    public ProductCacheListener(EventCodec codec,Inbox inbox,ProductCache cache) { this.codec=codec;this.inbox=inbox;this.cache=cache; }
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.PRODUCT_CHANGED) inbox.once(event,()->cache.evict(event.aggregateId()));
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="true", matchIfMissing=true)
    static class KafkaDelivery {
        private final ProductCacheListener listener;
        KafkaDelivery(ProductCacheListener listener) { this.listener=listener; }
        @KafkaListener(topics="commerce.events")
        public void receive(String body) { listener.receive(body); }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="false")
    static class LocalDelivery {
        LocalDelivery(ProductCacheListener listener) { LocalEvents.subscribe(listener::receive); }
    }
}
