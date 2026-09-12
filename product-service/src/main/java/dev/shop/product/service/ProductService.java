package dev.shop.product.service;
import dev.shop.common.api.ApiException;
import dev.shop.product.dto.ProductDtos.*;
import dev.shop.product.repository.ProductRepository;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.shop.product.config.ProductCache;
import dev.shop.common.event.*;
import org.springframework.transaction.support.*;
@Service
public class ProductService {
    private final ProductRepository products;
    private final ProductCache cache;
    private final Outbox outbox;
    public ProductService(ProductRepository products,ProductCache cache,Outbox outbox) { this.products=products;this.cache=cache;this.outbox=outbox; }
    public View get(UUID id) { return get(id,false); }
    public View get(UUID id,boolean bypassCache) {
        if(!bypassCache) { var hit=cache.get(id); if(hit.isPresent()) return hit.get(); }
        var product=fresh(id);
        if(!bypassCache) cache.put(product);
        return product;
    }
    private View fresh(UUID id) { return products.find(id).filter(View::active).orElseThrow(()->ApiException.notFound("Product not found")); }
    private void changed(View product) {
        outbox.append(EventType.PRODUCT_CHANGED,product.id(),product);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.evictBestEffort(product.id()); }
        });
    }
    public Page list(String category,int page,int size,String sort) { return products.list(category,page,size,sort); }
    @Transactional public View create(Input input) {
        UUID id=UUID.randomUUID();
        try { products.insert(id,input); } catch(DuplicateKeyException ex) { throw ApiException.conflict("SKU already exists"); }
        var product=fresh(id); changed(product); return product;
    }
    @Transactional public View update(UUID id,Input input) {
        try { if(products.update(id,input)==0) throw ApiException.notFound("Product not found"); }
        catch(DuplicateKeyException ex) { throw ApiException.conflict("SKU already exists"); }
        var product=fresh(id); changed(product); return product;
    }
    @Transactional public void delete(UUID id) { if(products.delete(id)==0) throw ApiException.notFound("Product not found"); changed(products.find(id).orElseThrow()); }
}
