package dev.shop.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloudUrlsTest {
    @Test
    void parsesRenderPostgresUrl() {
        var db = CloudUrls.database("postgresql://commerce:s3cret@dpg-abc123-a/commerceflow");
        assertEquals("commerce", db.username());
        assertEquals("s3cret", db.password());
        assertTrue(db.jdbc("auth").contains("currentSchema=auth"));
        assertTrue(db.jdbc("auth").contains("sslmode=require"));
    }

    @Test
    void parsesLocalRedisWithoutPassword() {
        var redis = CloudUrls.redis("redis://localhost:6379");
        assertEquals("localhost", redis.host());
        assertEquals(6379, redis.port());
        assertNull(redis.password());
        assertFalse(redis.ssl());
    }

    @Test
    void parsesTlsRedisPassword() {
        var redis = CloudUrls.redis("rediss://:p%40ss@red-example.render.com:6380");
        assertEquals("p@ss", redis.password());
        assertEquals(6380, redis.port());
        assertTrue(redis.ssl());
    }
}
