package dev.shop.notification.event;
import dev.shop.common.event.*;
import dev.shop.notification.service.NotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@Import({NotificationListener.KafkaDelivery.class, NotificationListener.LocalDelivery.class})
public class NotificationListener {
    private final EventCodec codec; private final Inbox inbox; private final NotificationSender sender;
    public NotificationListener(EventCodec codec,Inbox inbox,NotificationSender sender) { this.codec=codec;this.inbox=inbox;this.sender=sender; }
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.ORDER_CONFIRMED || event.type()==EventType.ORDER_CANCELLED) inbox.once(event,()-> {
            var data=codec.payload(event,SagaData.class);
            sender.send(event.aggregateId(),data.userId(),event.type().name(),"Order "+event.aggregateId()+" is "+(event.type()==EventType.ORDER_CONFIRMED?"confirmed":"cancelled: "+data.reason()));
        });
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="true", matchIfMissing=true)
    static class KafkaDelivery {
        private final NotificationListener listener;
        KafkaDelivery(NotificationListener listener) { this.listener=listener; }
        @KafkaListener(topics="commerce.events")
        public void receive(String body) { listener.receive(body); }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="false")
    static class LocalDelivery {
        LocalDelivery(NotificationListener listener) { LocalEvents.subscribe(listener::receive); }
    }
}
