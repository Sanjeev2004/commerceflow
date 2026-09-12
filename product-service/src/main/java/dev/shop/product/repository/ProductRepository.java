package dev.shop.product.repository;
import dev.shop.product.dto.ProductDtos.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
@Repository
public class ProductRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<View> mapper=(r,n)->new View(r.getObject("id",UUID.class),r.getString("sku"),r.getString("name"),r.getString("description"),r.getString("category"),r.getBigDecimal("price"),r.getBoolean("active"),r.getLong("version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    public ProductRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<View> find(UUID id) { return jdbc.query("SELECT * FROM products WHERE id=?",mapper,id).stream().findFirst(); }
    public void insert(UUID id,Input p) { jdbc.update("INSERT INTO products(id,sku,name,description,category,price) VALUES (?,?,?,?,?,?)",id,p.sku(),p.name(),p.description(),p.category(),p.price()); }
    public int update(UUID id,Input p) { return jdbc.update("UPDATE products SET sku=?,name=?,description=?,category=?,price=?,version=version+1,updated_at=now() WHERE id=? AND active",p.sku(),p.name(),p.description(),p.category(),p.price(),id); }
    public int delete(UUID id) { return jdbc.update("UPDATE products SET active=false,version=version+1,updated_at=now() WHERE id=? AND active",id); }
    public Page list(String category,int page,int size,String sort) {
        String order=switch(sort) { case "priceAsc"->"price ASC,id"; case "priceDesc"->"price DESC,id"; case "newest"->"created_at DESC,id"; default->throw new dev.shop.common.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"sort must be newest, priceAsc, or priceDesc"); };
        String filter=" WHERE active"+(category==null?"":" AND category=?");
        List<Object> args=new ArrayList<>(); if(category!=null) args.add(category);
        long count=jdbc.queryForObject("SELECT count(*) FROM products"+filter,Long.class,args.toArray());
        args.add(size); args.add((long)page*size);
        return new Page(jdbc.query("SELECT * FROM products"+filter+" ORDER BY "+order+" LIMIT ? OFFSET ?",mapper,args.toArray()),page,size,count);
    }
}
