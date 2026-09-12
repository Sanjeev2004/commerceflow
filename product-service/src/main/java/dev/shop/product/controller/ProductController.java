package dev.shop.product.controller;
import dev.shop.product.dto.ProductDtos.*;
import dev.shop.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service=service; }
    @GetMapping("/{id}") public View get(@PathVariable UUID id,@RequestHeader(value="Cache-Control",required=false) String cacheControl) { return service.get(id,"no-cache".equalsIgnoreCase(cacheControl)); }
    @GetMapping public Page list(@RequestParam(required=false) String category,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(defaultValue="newest") String sort) { return service.list(category,page,size,sort); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public View create(@Valid @RequestBody Input input) { return service.create(input); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public View update(@PathVariable UUID id,@Valid @RequestBody Input input) { return service.update(id,input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable UUID id) { service.delete(id); }
}
