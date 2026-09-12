package dev.shop.search.controller;
import dev.shop.search.dto.SearchDtos.Page;
import dev.shop.search.service.SearchService;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService search;
    public SearchController(SearchService search) { this.search=search; }
    @GetMapping public Page search(@RequestParam(required=false) @Size(max=200) String q,@RequestParam(required=false) @Size(max=100) String category,
            @RequestParam(required=false) @DecimalMin("0") BigDecimal minPrice,@RequestParam(required=false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return search.search(q,category,minPrice,maxPrice,page,size);
    }
}
