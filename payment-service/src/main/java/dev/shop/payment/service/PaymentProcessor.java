package dev.shop.payment.service;
import dev.shop.common.event.SagaData.PaymentMode;
import org.springframework.stereotype.Component;
@Component
public class PaymentProcessor {
    public record Result(boolean success,String reason) {}
    public Result process(PaymentMode mode) {
        return switch(mode) { case SUCCESS->new Result(true,null); case DECLINE->new Result(false,"PAYMENT_DECLINED"); case CANCEL->new Result(false,"PAYMENT_CANCELLED"); };
    }
}
