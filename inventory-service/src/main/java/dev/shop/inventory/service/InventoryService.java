package dev.shop.inventory.service;
import dev.shop.common.event.*;
import dev.shop.inventory.dto.InventoryDtos.Stock;
import dev.shop.inventory.repository.InventoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InventoryService {
    private final InventoryRepository inventory;
    private final Outbox outbox;
    public InventoryService(InventoryRepository inventory,Outbox outbox) { this.inventory=inventory; this.outbox=outbox; }
    public Stock stock(UUID id) { return inventory.stock(id); }
    @Transactional public Stock restock(UUID key,UUID productId,int quantity) { inventory.restock(key,productId,quantity); return inventory.stock(productId); }
    @Transactional public void reserve(UUID orderId,SagaData data) {
        inventory.lockOrder(orderId);
        if(inventory.reservation(orderId).isPresent()) return;
        boolean reserved=inventory.reserve(orderId,data.lines());
        outbox.append(reserved?EventType.INVENTORY_RESERVED:EventType.INVENTORY_REJECTED,orderId,reserved?data:data.withReason("INSUFFICIENT_STOCK"));
    }
    @Transactional public void release(UUID orderId,SagaData data) {
        inventory.lockOrder(orderId);
        String state=inventory.reservation(orderId).orElseThrow(()->new IllegalStateException("Reservation not yet available"));
        if(state.equals("RELEASED")) return;
        if(!state.equals("RESERVED")) throw new IllegalStateException("Cannot release "+state);
        inventory.finish(orderId,true);
        outbox.append(EventType.INVENTORY_RELEASED,orderId,data);
    }
    @Transactional public void commit(UUID orderId) {
        inventory.lockOrder(orderId);
        String state=inventory.reservation(orderId).orElseThrow(()->new IllegalStateException("Missing reservation"));
        if(state.equals("COMMITTED")) return;
        if(!state.equals("RESERVED")) throw new IllegalStateException("Cannot commit "+state);
        inventory.finish(orderId,false);
    }
}
