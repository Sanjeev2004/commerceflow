package dev.shop.payment.service;
import dev.shop.common.event.*;
import dev.shop.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PaymentService {
    private final PaymentRepository payments; private final PaymentProcessor processor; private final Outbox outbox; private final MeterRegistry meters;
    public PaymentService(PaymentRepository payments,PaymentProcessor processor,Outbox outbox,MeterRegistry meters) { this.payments=payments;this.processor=processor;this.outbox=outbox;this.meters=meters; }
    @Transactional public void pay(UUID orderId,SagaData data) {
        var result=processor.process(data.paymentMode());
        if(!payments.insert(orderId,data,result.success(),result.reason())) return;
        if(!result.success()) meters.counter("commerce.payments.failures").increment();
        outbox.append(result.success()?EventType.PAYMENT_COMPLETED:EventType.PAYMENT_FAILED,orderId,data.withReason(result.reason()));
    }
}
