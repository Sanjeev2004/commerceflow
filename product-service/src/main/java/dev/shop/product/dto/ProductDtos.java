package dev.shop.product.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class ProductDtos {
    private ProductDtos() {}
    public record Input(@NotBlank @Size(max=80) String sku, @NotBlank @Size(max=200) String name,
            @NotNull @Size(max=5000) String description, @NotBlank @Size(max=100) String category,
            @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price) {}
    public record View(UUID id,String sku,String name,String description,String category,BigDecimal price,boolean active,long version,Instant createdAt,Instant updatedAt) {}
    public record Page(List<View> content,int page,int size,long totalElements) {}
}
