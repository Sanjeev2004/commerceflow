package dev.shop.cloud;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class CloudUrls {
    private CloudUrls() {}

    public record Database(String host, String jdbcBase, String username, String password) {
        public String jdbc(String schema) {
            String separator = jdbcBase.contains("?") ? "&" : "?";
            return jdbcBase + separator + "currentSchema=" + schema + "&sslmode=" + (renderHost(host) ? "require" : "prefer");
        }
    }

    public record Redis(String host, int port, String password, boolean ssl) {}

    public static boolean renderHost(String host) {
        return host.startsWith("dpg-") || host.contains("render.com");
    }

    public static Database database(String url) {
        String raw = url.trim();
        if (raw.startsWith("postgres://")) raw = "postgresql://" + raw.substring("postgres://".length());
        if (!raw.startsWith("postgresql://")) throw new IllegalArgumentException("DATABASE_URL must be a postgres URL");
        URI uri = URI.create(raw.replaceFirst("^postgresql://", "http://"));
        if (uri.getHost() == null || uri.getUserInfo() == null || !uri.getUserInfo().contains(":")) {
            throw new IllegalArgumentException("DATABASE_URL must include a host, username, and password");
        }
        int colon = uri.getUserInfo().indexOf(':');
        String user = decode(uri.getUserInfo().substring(0, colon));
        String pass = decode(uri.getUserInfo().substring(colon + 1));
        String name = uri.getPath() == null ? "" : uri.getPath();
        if (name.startsWith("/")) name = name.substring(1);
        int slash = name.indexOf('/');
        if (slash >= 0) name = name.substring(0, slash);
        if (name.isBlank()) throw new IllegalArgumentException("DATABASE_URL must include a database name");
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        return new Database(uri.getHost(), "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + name, user, pass);
    }

    public static Redis redis(String url) {
        String raw = url.trim();
        boolean ssl = raw.startsWith("rediss://");
        if (raw.startsWith("redis://")) raw = "http://" + raw.substring("redis://".length());
        else if (raw.startsWith("rediss://")) raw = "http://" + raw.substring("rediss://".length());
        else throw new IllegalArgumentException("REDIS_URL must start with redis:// or rediss://");
        URI uri = URI.create(raw);
        if (uri.getHost() == null) throw new IllegalArgumentException("REDIS_URL must include a host");
        String password = null;
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            int colon = uri.getUserInfo().indexOf(':');
            String secret = colon >= 0 ? uri.getUserInfo().substring(colon + 1) : uri.getUserInfo();
            if (!secret.isBlank()) password = decode(secret);
        }
        int port = uri.getPort() == -1 ? 6379 : uri.getPort();
        return new Redis(uri.getHost(), port, password, ssl);
    }

    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
}
