package dev.shop.inventory.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public final class InventoryDtos {
    private InventoryDtos() {}
    public record Stock(UUID productId,int available,int reserved) {}
    public record Restock(@Min(1) @Max(1000000) int quantity) {}
}
