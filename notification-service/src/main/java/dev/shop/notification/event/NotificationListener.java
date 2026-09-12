package dev.shop.notification.event;
import dev.shop.common.event.*;
import dev.shop.notification.service.NotificationSender;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class NotificationListener {
    private final EventCodec codec; private final Inbox inbox; private final NotificationSender sender;
    public NotificationListener(EventCodec codec,Inbox inbox,NotificationSender sender) { this.codec=codec;this.inbox=inbox;this.sender=sender; }
    @KafkaListener(topics="commerce.events",autoStartup="${commerce.messaging.enabled:true}")
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.ORDER_CONFIRMED || event.type()==EventType.ORDER_CANCELLED) inbox.once(event,()-> {
            var data=codec.payload(event,SagaData.class);
            sender.send(event.aggregateId(),data.userId(),event.type().name(),"Order "+event.aggregateId()+" is "+(event.type()==EventType.ORDER_CONFIRMED?"confirmed":"cancelled: "+data.reason()));
        });
    }
}
