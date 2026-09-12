package dev.shop.inventory.controller;
import dev.shop.inventory.dto.InventoryDtos.*;
import dev.shop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventory;
    public InventoryController(InventoryService inventory) { this.inventory=inventory; }
    @GetMapping("/{id}") public Stock stock(@PathVariable UUID id) { return inventory.stock(id); }
    @PostMapping("/{id}/restock") @PreAuthorize("hasRole('ADMIN')")
    public Stock restock(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Restock input) { return inventory.restock(key,id,input.quantity()); }
}
