package dev.shop.notification.repository;
import dev.shop.notification.dto.NotificationView;
import dev.shop.notification.service.NotificationSender;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class StoredNotificationSender implements NotificationSender {
    private final JdbcTemplate jdbc;
    public StoredNotificationSender(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void send(UUID orderId,UUID userId,String kind,String message) {
        jdbc.update("INSERT INTO notifications(id,order_id,user_id,kind,message) VALUES (?,?,?,?,?) ON CONFLICT(order_id,kind) DO NOTHING",UUID.randomUUID(),orderId,userId,kind,message);
    }
    public List<NotificationView> list(UUID userId,int page,int size) {
        return jdbc.query("SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",(r,n)->new NotificationView(r.getObject("id",UUID.class),r.getObject("order_id",UUID.class),r.getString("kind"),r.getString("message"),r.getTimestamp("created_at").toInstant()),userId,size,(long)page*size);
    }
}
