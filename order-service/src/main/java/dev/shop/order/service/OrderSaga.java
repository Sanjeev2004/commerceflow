package dev.shop.order.service;
import dev.shop.common.event.*;
import dev.shop.order.entity.OrderState;
import dev.shop.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OrderSaga {
    private final OrderRepository orders; private final Outbox outbox; private final EventCodec codec;
    public OrderSaga(OrderRepository orders,Outbox outbox,EventCodec codec) { this.orders=orders;this.outbox=outbox;this.codec=codec; }
    @Transactional public void handle(DomainEvent event) {
        var order=orders.find(event.aggregateId(),true).orElseThrow(()->new IllegalStateException("Order not found"));
        if(order.state()==OrderState.CONFIRMED || order.state()==OrderState.CANCELLED) return;
        var data=order.data();
        switch(event.type()) {
            case INVENTORY_RESERVED -> {
                if(order.state()!=OrderState.CREATED) return;
                orders.transition(order.id(),OrderState.INVENTORY_RESERVED,null);
                outbox.append(EventType.PAYMENT_REQUESTED,order.id(),data);
                orders.transition(order.id(),OrderState.PAYMENT_PENDING,null);
            }
            case INVENTORY_REJECTED -> {
                require(order.state(),OrderState.CREATED);
                orders.transition(order.id(),OrderState.CANCELLED,"INSUFFICIENT_STOCK");
                outbox.append(EventType.ORDER_CANCELLED,order.id(),data.withReason("INSUFFICIENT_STOCK"));
            }
            case PAYMENT_COMPLETED -> {
                require(order.state(),OrderState.PAYMENT_PENDING);
                if(order.reason()!=null) throw new IllegalStateException("Payment result conflicts with compensation");
                orders.transition(order.id(),OrderState.CONFIRMED,null);
                outbox.append(EventType.ORDER_CONFIRMED,order.id(),data);
            }
            case PAYMENT_FAILED -> {
                require(order.state(),OrderState.PAYMENT_PENDING);
                if(order.reason()!=null) return;
                String reason=codec.payload(event,SagaData.class).reason();
                if(reason==null) reason="PAYMENT_FAILED";
                orders.reason(order.id(),reason);
                outbox.append(EventType.INVENTORY_RELEASE_REQUESTED,order.id(),data.withReason(reason));
            }
            case INVENTORY_RELEASED -> {
                require(order.state(),OrderState.PAYMENT_PENDING);
                if(order.reason()==null) throw new IllegalStateException("No compensation requested");
                orders.transition(order.id(),OrderState.CANCELLED,order.reason());
                outbox.append(EventType.ORDER_CANCELLED,order.id(),data.withReason(order.reason()));
            }
            default -> throw new IllegalArgumentException("Not a Saga response");
        }
    }
    private void require(OrderState actual,OrderState expected) { if(actual!=expected) throw new IllegalStateException("Expected "+expected+" but was "+actual); }
}
