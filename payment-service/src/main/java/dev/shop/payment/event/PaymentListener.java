package dev.shop.payment.event;
import dev.shop.common.event.*;
import dev.shop.payment.service.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
@Import({PaymentListener.KafkaDelivery.class, PaymentListener.LocalDelivery.class})
public class PaymentListener {
    private final EventCodec codec; private final Inbox inbox; private final PaymentService payments;
    public PaymentListener(EventCodec codec,Inbox inbox,PaymentService payments) { this.codec=codec;this.inbox=inbox;this.payments=payments; }
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.PAYMENT_REQUESTED) inbox.once(event,()->payments.pay(event.aggregateId(),codec.payload(event,SagaData.class)));
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="true", matchIfMissing=true)
    static class KafkaDelivery {
        private final PaymentListener listener;
        KafkaDelivery(PaymentListener listener) { this.listener=listener; }
        @KafkaListener(topics="commerce.events")
        public void receive(String body) { listener.receive(body); }
    }

    @Component
    @ConditionalOnProperty(name="commerce.messaging.enabled", havingValue="false")
    static class LocalDelivery {
        LocalDelivery(PaymentListener listener) { LocalEvents.subscribe(listener::receive); }
    }
}
