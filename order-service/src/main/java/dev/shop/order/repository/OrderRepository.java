package dev.shop.order.repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shop.common.event.*;
import dev.shop.order.dto.OrderDtos.View;
import dev.shop.order.entity.OrderState;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
@Repository
public class OrderRepository {
    public record Stored(UUID id,UUID userId,String hash,OrderState state,SagaData data,String reason,java.time.Instant createdAt,java.time.Instant updatedAt) {}
    private final JdbcTemplate jdbc; private final EventCodec codec; private final ObjectMapper mapper;
    public OrderRepository(JdbcTemplate jdbc,EventCodec codec,ObjectMapper mapper) { this.jdbc=jdbc;this.codec=codec;this.mapper=mapper; }
    private RowMapper<Stored> row() { return (r,n)-> {
        SagaData data;
        try { data=mapper.readValue(r.getString("saga_data"),SagaData.class); } catch(Exception ex) { throw new IllegalStateException(ex); }
        return new Stored(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("request_hash"),OrderState.valueOf(r.getString("state")),data,r.getString("cancellation_reason"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }; }
    public void lockKey(UUID userId,String key) { jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,userId+":"+key); }
    public Optional<Stored> existing(UUID userId,String key) { return jdbc.query("SELECT * FROM orders WHERE user_id=? AND idempotency_key=?",row(),userId,key).stream().findFirst(); }
    public Optional<Stored> find(UUID id,boolean lock) { return jdbc.query("SELECT * FROM orders WHERE id=?"+(lock?" FOR UPDATE":""),row(),id).stream().findFirst(); }
    public void insert(UUID id,UUID userId,String key,String hash,SagaData data) {
        jdbc.update("INSERT INTO orders(id,user_id,idempotency_key,request_hash,state,total,saga_data) VALUES (?,?,?,?,'CREATED',?,?)",id,userId,key,hash,data.total(),codec.encode(data));
        for(var line:data.lines()) jdbc.update("INSERT INTO order_items(order_id,product_id,quantity,unit_price) VALUES (?,?,?,?)",id,line.productId(),line.quantity(),line.unitPrice());
        history(id,OrderState.CREATED);
    }
    public void transition(UUID id,OrderState state,String reason) {
        jdbc.update("UPDATE orders SET state=?,cancellation_reason=?,updated_at=now() WHERE id=?",state.name(),reason,id); history(id,state);
    }
    public void reason(UUID id,String reason) { jdbc.update("UPDATE orders SET cancellation_reason=?,updated_at=now() WHERE id=?",reason,id); }
    private void history(UUID id,OrderState state) { jdbc.update("INSERT INTO order_transitions(order_id,state) VALUES (?,?)",id,state.name()); }
    public View view(Stored order) { return new View(order.id(),order.userId(),order.state(),order.data().total(),order.data().lines(),order.reason(),order.createdAt(),order.updatedAt(),jdbc.query("SELECT state FROM order_transitions WHERE order_id=? ORDER BY id",(r,n)->r.getString(1),order.id())); }
    public List<View> list(UUID userId,int page,int size) { return jdbc.query("SELECT * FROM orders WHERE user_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",row(),userId,size,(long)page*size).stream().map(this::view).toList(); }
    public List<View> listAll(String state, int page, int size) {
        if (state != null && !state.isBlank()) {
            return jdbc.query("SELECT * FROM orders WHERE state=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?", row(), state, size, (long)page*size).stream().map(this::view).toList();
        }
        return jdbc.query("SELECT * FROM orders ORDER BY created_at DESC,id LIMIT ? OFFSET ?", row(), size, (long)page*size).stream().map(this::view).toList();
    }
}
