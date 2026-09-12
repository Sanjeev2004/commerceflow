package dev.shop.inventory.repository;
import dev.shop.common.api.ApiException;
import dev.shop.common.event.SagaData.Line;
import dev.shop.inventory.dto.InventoryDtos.Stock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;
    public InventoryRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Stock stock(UUID productId) { return jdbc.query("SELECT * FROM inventory WHERE product_id=?",(r,n)->new Stock(productId,r.getInt("available"),r.getInt("reserved")),productId).stream().findFirst().orElse(new Stock(productId,0,0)); }
    public void restock(UUID id,UUID productId,int quantity) {
        jdbc.update("INSERT INTO inventory(product_id) VALUES (?) ON CONFLICT DO NOTHING",productId);
        if(jdbc.update("INSERT INTO restocks(id,product_id,quantity) VALUES (?,?,?) ON CONFLICT DO NOTHING",id,productId,quantity)==0) {
            boolean same=Boolean.TRUE.equals(jdbc.queryForObject("SELECT product_id=? AND quantity=? FROM restocks WHERE id=?",Boolean.class,productId,quantity,id));
            if(!same) throw ApiException.conflict("Restock idempotency key reused with different request");
            return;
        }
        jdbc.update("UPDATE inventory SET available=available+?,updated_at=now() WHERE product_id=?",quantity,productId);
    }
    public Optional<String> reservation(UUID orderId) { return jdbc.query("SELECT status FROM reservations WHERE order_id=? FOR UPDATE",(r,n)->r.getString(1),orderId).stream().findFirst(); }
    public boolean reserve(UUID orderId,List<Line> lines) {
        // PostgreSQL orders UUIDs as unsigned bytes; Java UUID.compareTo uses signed longs.
        // Match finish()'s database ordering to prevent reserve/release deadlocks.
        var sorted=lines.stream().sorted(Comparator.comparing(line->line.productId().toString())).toList();
        boolean enough=true;
        // Check every row before any decrement; consistent lock ordering prevents basket deadlocks.
        for(var line:sorted) {
            var available=jdbc.query("SELECT available FROM inventory WHERE product_id=? FOR UPDATE",(r,n)->r.getInt(1),line.productId());
            if(available.isEmpty()||available.getFirst()<line.quantity()) enough=false;
        }
        jdbc.update("INSERT INTO reservations(order_id,status) VALUES (?,?)",orderId,enough?"RESERVED":"REJECTED");
        if(!enough) return false;
        for(var line:sorted) {
            jdbc.update("UPDATE inventory SET available=available-?,reserved=reserved+?,updated_at=now() WHERE product_id=?",line.quantity(),line.quantity(),line.productId());
            jdbc.update("INSERT INTO reservation_items(order_id,product_id,quantity) VALUES (?,?,?)",orderId,line.productId(),line.quantity());
        }
        return true;
    }
    public void finish(UUID orderId,boolean release) {
        var lines=jdbc.query("SELECT product_id,quantity FROM reservation_items WHERE order_id=? ORDER BY product_id",(r,n)->new Stock(r.getObject(1,UUID.class),r.getInt(2),0),orderId);
        for(var line:lines) jdbc.update("UPDATE inventory SET reserved=reserved-?,available=available+?,updated_at=now() WHERE product_id=?",line.available(),release?line.available():0,line.productId());
        jdbc.update("UPDATE reservations SET status=?,updated_at=now() WHERE order_id=?",release?"RELEASED":"COMMITTED",orderId);
    }
    public void lockOrder(UUID id) { jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,id.toString()); }
}
