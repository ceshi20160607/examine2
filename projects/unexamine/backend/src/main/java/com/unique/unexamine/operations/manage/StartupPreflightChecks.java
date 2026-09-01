package com.unique.unexamine.operations.manage;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StartupPreflightChecks {
    static final String DEVELOPMENT_SECRET = "unexamine-local-application-secret-master-key-change-in-production";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Flyway flyway;
    private final String storageRoot;
    private final String masterKey;
    private final boolean allowDevelopmentDefaults;

    public StartupPreflightChecks(
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            Flyway flyway,
            @Value("${app.file.storage-root:}") String storageRoot,
            @Value("${app.security.application-secret-master-key:}") String masterKey,
            @Value("${app.preflight.allow-development-defaults:false}") boolean allowDevelopmentDefaults) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.flyway = flyway;
        this.storageRoot = storageRoot;
        this.masterKey = masterKey;
        this.allowDevelopmentDefaults = allowDevelopmentDefaults;
    }

    public List<Check> inspect() {
        return List.of(database(), redis(), fileStorage(), secret(), migration());
    }

    private Check database() {
        long started = System.nanoTime();
        try {
            Integer value = jdbc.queryForObject("select 1", Integer.class);
            if (value == null || value != 1) return failed("DATABASE", "数据库", "数据库探测没有返回预期结果");
            return passed("DATABASE", "数据库", "数据库连接与只读探测正常", latency(started));
        } catch (RuntimeException exception) {
            return failed("DATABASE", "数据库", "数据库不可连接（" + reason(exception) + "）");
        }
    }

    private Check redis() {
        long started = System.nanoTime();
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            if (!"PONG".equalsIgnoreCase(pong)) return failed("REDIS", "Redis", "Redis PING 未返回 PONG");
            return passed("REDIS", "Redis", "Redis 连接与 PING 正常", latency(started));
        } catch (RuntimeException exception) {
            return failed("REDIS", "Redis", "Redis 不可连接（" + reason(exception) + "）");
        }
    }

    private Check fileStorage() {
        if (storageRoot == null || storageRoot.isBlank()) {
            return failed("FILE_STORAGE", "文件存储", "缺少 APP_FILE_STORAGE_ROOT 文件存储配置");
        }
        long started = System.nanoTime();
        Path probe = null;
        try {
            Path root = Path.of(storageRoot).toAbsolutePath().normalize();
            Files.createDirectories(root);
            probe = Files.createTempFile(root, ".preflight-", ".probe");
            Files.writeString(probe, "ready");
            return passed("FILE_STORAGE", "文件存储", "文件存储目录可创建、写入与清理", latency(started));
        } catch (Exception exception) {
            return failed("FILE_STORAGE", "文件存储", "文件存储不可写（" + reason(exception) + "）");
        } finally {
            if (probe != null) {
                try { Files.deleteIfExists(probe); } catch (Exception ignored) { }
            }
        }
    }

    private Check secret() {
        if (masterKey == null || masterKey.isBlank()) {
            return failed("APPLICATION_SECRET", "应用签名密钥", "缺少 APP_APPLICATION_SECRET_MASTER_KEY 密钥配置");
        }
        if (masterKey.length() < 32) {
            return failed("APPLICATION_SECRET", "应用签名密钥", "APP_APPLICATION_SECRET_MASTER_KEY 长度不足 32 个字符");
        }
        if (DEVELOPMENT_SECRET.equals(masterKey) && !allowDevelopmentDefaults) {
            return failed("APPLICATION_SECRET", "应用签名密钥", "应用签名主密钥仍为开发默认值");
        }
        return passed("APPLICATION_SECRET", "应用签名密钥",
                DEVELOPMENT_SECRET.equals(masterKey) ? "密钥已配置（仅允许本地开发默认值）" : "密钥已通过引用配置且长度合规",
                Map.of("configured", true, "valueExposed", false));
    }

    private Check migration() {
        long started = System.nanoTime();
        try {
            MigrationInfo current = flyway.info().current();
            MigrationInfo[] pending = flyway.info().pending();
            if (current == null) return failed("DATABASE_MIGRATION", "数据库迁移", "数据库没有已应用的 Flyway 版本");
            if (pending.length > 0) return failed("DATABASE_MIGRATION", "数据库迁移", "数据库仍有 " + pending.length + " 个待应用迁移");
            Map<String, Object> metric = new LinkedHashMap<>(latency(started));
            metric.put("version", current.getVersion().getVersion());
            metric.put("pending", 0);
            return passed("DATABASE_MIGRATION", "数据库迁移", "数据库迁移已应用到实际运行版本", metric);
        } catch (RuntimeException exception) {
            return failed("DATABASE_MIGRATION", "数据库迁移", "数据库迁移状态不可读取（" + reason(exception) + "）");
        }
    }

    private Map<String, Object> latency(long started) {
        return Map.of("latencyMillis", Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    private Check passed(String code, String name, String message, Map<String, Object> metric) {
        return new Check(code, name, "DEPENDENCY", "PASSED", message, metric, true);
    }

    private Check failed(String code, String name, String message) {
        return new Check(code, name, "DEPENDENCY", "FAILED", message, Map.of("valueExposed", false), true);
    }

    private String reason(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String name = current.getClass().getSimpleName();
        return name.isBlank() ? "连接或配置错误" : name;
    }

    public record Check(
            String code,
            String name,
            String category,
            String status,
            String message,
            Map<String, Object> metric,
            boolean critical) {
        public boolean passed() {
            return "PASSED".equals(status);
        }
    }
}
