package dev.shop.search.event;
import dev.shop.common.event.*;
import dev.shop.search.repository.SearchIndex;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class SearchListener {
    private final EventCodec codec; private final Inbox inbox; private final SearchIndex index;
    public SearchListener(EventCodec codec,Inbox inbox,SearchIndex index) { this.codec=codec;this.inbox=inbox;this.index=index; }
    @KafkaListener(topics="commerce.events",autoStartup="${commerce.messaging.enabled:true}")
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.PRODUCT_CHANGED) inbox.once(event,()->index.project(event.aggregateId(),event.payload()));
    }
}
