package dev.shop.auth.repository;
import dev.shop.auth.entity.UserAccount;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<UserAccount> byEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email=?", (rs,n) -> new UserAccount(rs.getObject("id", UUID.class),rs.getString("email"),rs.getString("password_hash"),rs.getString("role")),email).stream().findFirst();
    }
    public Optional<UserAccount> byId(UUID id) {
        return jdbc.query("SELECT * FROM users WHERE id=?", (rs,n) -> new UserAccount(rs.getObject("id", UUID.class),rs.getString("email"),rs.getString("password_hash"),rs.getString("role")),id).stream().findFirst();
    }
    public void insert(UserAccount user) { jdbc.update("INSERT INTO users(id,email,password_hash,role) VALUES (?,?,?,?)",user.id(),user.email(),user.passwordHash(),user.role()); }
}
