package dev.shop.search.dto;
import java.math.BigDecimal;
import java.util.*;
public final class SearchDtos {
    private SearchDtos() {}
    public record Hit(UUID id,String sku,String name,String description,String category,BigDecimal price) {}
    public record Page(List<Hit> content,long totalElements,int page,int size) {}
}
