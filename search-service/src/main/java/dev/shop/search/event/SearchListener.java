package dev.shop.search.event;
import dev.shop.common.event.*;
import dev.shop.search.repository.SearchIndex;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@Import({SearchListener.KafkaDelivery.class, SearchListener.LocalDelivery.class})
public class SearchListener {
    private final EventCodec codec; private final Inbox inbox; private final SearchIndex index;
    public SearchListener(EventCodec codec,Inbox inbox,SearchIndex index) { this.codec=codec;this.inbox=inbox;this.index=index; }
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.PRODUCT_CHANGED) inbox.once(event,()->index.project(event.aggregateId(),event.payload()));
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="true", matchIfMissing=true)
    static class KafkaDelivery {
        private final SearchListener listener;
        KafkaDelivery(SearchListener listener) { this.listener=listener; }
        @KafkaListener(topics="commerce.events")
        public void receive(String body) { listener.receive(body); }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="false")
    static class LocalDelivery {
        LocalDelivery(SearchListener listener) { LocalEvents.subscribe(listener::receive); }
    }
}
