package dev.shop.payment.event;
import dev.shop.common.event.*;
import dev.shop.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class PaymentListener {
    private final EventCodec codec; private final Inbox inbox; private final PaymentService payments;
    public PaymentListener(EventCodec codec,Inbox inbox,PaymentService payments) { this.codec=codec;this.inbox=inbox;this.payments=payments; }
    @KafkaListener(topics="commerce.events",autoStartup="${commerce.messaging.enabled:true}")
    public void receive(String body) {
        var event=codec.decode(body);
        if(event.type()==EventType.PAYMENT_REQUESTED) inbox.once(event,()->payments.pay(event.aggregateId(),codec.payload(event,SagaData.class)));
    }
}
