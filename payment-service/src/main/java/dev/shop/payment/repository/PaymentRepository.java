package dev.shop.payment.repository;
import dev.shop.common.event.SagaData;
import dev.shop.payment.dto.PaymentView;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;
    public PaymentRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean insert(UUID orderId,SagaData data,boolean success,String reason) {
        return jdbc.update("INSERT INTO payments(id,order_id,user_id,amount,status,reason) VALUES (?,?,?,?,?,?) ON CONFLICT(order_id) DO NOTHING",UUID.randomUUID(),orderId,data.userId(),data.total(),success?"COMPLETED":"FAILED",reason)==1;
    }
    public Optional<PaymentView> find(UUID orderId,UUID userId) {
        return jdbc.query("SELECT * FROM payments WHERE order_id=? AND user_id=?",(r,n)->new PaymentView(r.getObject("id",UUID.class),orderId,r.getBigDecimal("amount"),r.getString("status"),r.getString("reason")),orderId,userId).stream().findFirst();
    }
}
