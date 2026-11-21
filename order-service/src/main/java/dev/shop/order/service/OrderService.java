package dev.shop.order.service;
import dev.shop.common.api.ApiException;
import dev.shop.common.event.*;
import dev.shop.order.dto.OrderDtos.*;
import dev.shop.order.entity.OrderState;
import dev.shop.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OrderService {
    private final OrderRepository orders; private final ProductClient products; private final Outbox outbox; private final EventCodec codec; private final MeterRegistry meters;
    public OrderService(OrderRepository orders,ProductClient products,Outbox outbox,EventCodec codec,MeterRegistry meters) { this.orders=orders;this.products=products;this.outbox=outbox;this.codec=codec;this.meters=meters; }
    @Transactional public View create(UUID userId,String key,Create input) {
        if(key==null || key.length()<8 || key.length()>100) throw new ApiException(HttpStatus.BAD_REQUEST,"Idempotency-Key must be 8 to 100 characters");
        if(input.paymentMode()==null) throw new ApiException(HttpStatus.BAD_REQUEST,"Payment mode is required");
        if(input.items()==null || input.items().isEmpty() || input.items().size()>20) throw new ApiException(HttpStatus.BAD_REQUEST,"Order must contain 1 to 20 items");
        for(var item:input.items()) {
            if(item.productId()==null) throw new ApiException(HttpStatus.BAD_REQUEST,"Product id is required");
            if(item.quantity()<1 || item.quantity()>1000) throw new ApiException(HttpStatus.BAD_REQUEST,"Quantity must be between 1 and 1000");
        }
        var items=input.items().stream().sorted(Comparator.comparing(Item::productId)).toList();
        if(items.stream().map(Item::productId).distinct().count()!=items.size()) throw new ApiException(HttpStatus.BAD_REQUEST,"Each product must occur once");
        String hash=hash(codec.encode(new Create(items,input.paymentMode())));
        orders.lockKey(userId,key);
        var existing=orders.existing(userId,key);
        if(existing.isPresent()) {
            if(!existing.get().hash().equals(hash)) throw ApiException.conflict("Idempotency key reused with different request");
            return orders.view(existing.get());
        }
        var lines=items.stream().map(item->new SagaData.Line(item.productId(),item.quantity(),products.quote(item.productId()).price())).toList();
        BigDecimal total=lines.stream().map(l->l.unitPrice().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        var data=new SagaData(userId,lines,total,input.paymentMode(),null);
        UUID id=UUID.randomUUID(); orders.insert(id,userId,key,hash,data); outbox.append(EventType.ORDER_CREATED,id,data);
        meters.counter("commerce.orders.created").increment();
        return orders.view(orders.find(id,false).orElseThrow());
    }
    public View get(UUID id,UUID userId) { return orders.view(orders.find(id,false).filter(o->o.userId().equals(userId)).orElseThrow(()->ApiException.notFound("Order not found"))); }
    public List<View> list(UUID userId,int page,int size) { return orders.list(userId,page,size); }
    public List<View> listAll(String state,int page,int size) {
        String filter=null;
        if(state!=null && !state.isBlank()) {
            filter=state.trim();
            try { OrderState.valueOf(filter); }
            catch(IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"Unknown order state"); }
        }
        return orders.listAll(filter,page,size);
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
