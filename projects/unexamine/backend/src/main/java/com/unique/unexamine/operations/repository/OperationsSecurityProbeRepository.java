package com.unique.unexamine.operations.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
public class OperationsSecurityProbeRepository {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Path storageRoot;

    public OperationsSecurityProbeRepository(
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            @Value("${app.file.storage-root}") String storageRoot) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public SecuritySignals securitySignals(long systemId) {
        long weakPasswords = count("select count(*) from plat_account_credential c "
                + "join sys_member sm on sm.account_id=c.account_id "
                + "where sm.system_id=? and c.password_hash not like 'pbkdf2-sha256$%'", systemId);
        long unreferencedApplicationSecrets = count("select count(*) from app_credential c "
                + "join app_definition a on a.id=c.application_id where a.owner_system_id=? "
                + "and c.status='ACTIVE' and (c.signing_secret_ref is null or c.signing_secret_ref='')", systemId);
        long boundaryViolations = count("select count(*) from sys_tenant_member tm "
                + "left join sys_member sm on sm.id=tm.system_member_id and sm.system_id=tm.system_id "
                + "left join sys_tenant t on t.id=tm.tenant_id and t.system_id=tm.system_id "
                + "where tm.system_id=? and (sm.id is null or t.id is null)", systemId);
        return new SecuritySignals(weakPasswords, unreferencedApplicationSecrets, boundaryViolations);
    }

    public ProbeResult listQuery(long systemId, long tenantId, int pageSize, int timeoutMillis) {
        long started = System.nanoTime();
        String query = "select id,job_type,status,created_at from job_background force index (idx_job_background_context) "
                + "where system_id=? and tenant_id=? order by created_at desc limit ?";
        List<Map<String, Object>> rows = jdbc.queryForList(query, systemId, tenantId, pageSize + 1);
        List<Map<String, Object>> explain = jdbc.queryForList("explain " + query, systemId, tenantId, pageSize + 1);
        String index = explain.isEmpty() ? "" : String.valueOf(explain.getFirst().getOrDefault("key", ""));
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("index", index);
        metric.put("indexUsed", !index.isBlank() && !"null".equalsIgnoreCase(index));
        metric.put("serverPagination", true);
        metric.put("pageSize", pageSize);
        metric.put("rowsReturned", Math.min(rows.size(), pageSize));
        metric.put("hasNext", rows.size() > pageSize);
        metric.put("timeoutMillis", timeoutMillis);
        metric.put("queryShape", "SYSTEM_TENANT_INDEXED_LIMIT");
        return result("LIST_QUERY", started, metric);
    }

    public ProbeResult fileStream(int bytes, int timeoutMillis) {
        long started = System.nanoTime();
        Path probe = null;
        long read = 0;
        try {
            Files.createDirectories(storageRoot);
            probe = Files.createTempFile(storageRoot, ".performance-", ".probe");
            byte[] chunk = new byte[Math.min(bytes, 8192)];
            try (BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(probe))) {
                int remaining = bytes;
                while (remaining > 0) {
                    int length = Math.min(remaining, chunk.length);
                    output.write(chunk, 0, length);
                    remaining -= length;
                }
            }
            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(probe))) {
                while (input.read(chunk) >= 0) read += chunk.length;
            }
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("streaming", true);
            metric.put("configuredBytes", bytes);
            metric.put("resourceBytes", Files.size(probe));
            metric.put("readCompleted", read >= bytes);
            metric.put("timeoutMillis", timeoutMillis);
            metric.put("cleanup", "DELETED");
            return result("FILE_STREAM", started, metric);
        } catch (Exception exception) {
            throw new IllegalStateException("File performance probe failed", exception);
        } finally {
            if (probe != null) {
                try { Files.deleteIfExists(probe); } catch (Exception ignored) { }
            }
        }
    }

    public ProbeResult jobQueue(long systemId, long tenantId, int timeoutMillis) {
        long started = System.nanoTime();
        Map<String, Long> states = new LinkedHashMap<>();
        jdbc.queryForList("select status,count(*) total from job_background where system_id=? and tenant_id=? "
                + "group by status", systemId, tenantId).forEach(row ->
                states.put(String.valueOf(row.get("status")), ((Number) row.get("total")).longValue()));
        String pong;
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            pong = connection.ping();
        }
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("queue", states);
        metric.put("queued", states.getOrDefault("QUEUED", 0L));
        metric.put("failed", states.getOrDefault("FAILED", 0L) + states.getOrDefault("PERMANENT_FAILED", 0L));
        metric.put("redis", pong);
        metric.put("backgroundExecution", true);
        metric.put("timeoutMillis", timeoutMillis);
        return result("JOB_QUEUE", started, metric);
    }

    public ProbeResult statistics(long systemId, long tenantId, int timeoutMillis) {
        long started = System.nanoTime();
        List<Map<String, Object>> groups = jdbc.queryForList("select event_category,count(*) total from audit_event "
                + "where system_id=? and tenant_id=? group by event_category limit 50", systemId, tenantId);
        String key = "ops:performance:statistics:" + systemId + ":" + tenantId + ":" + UUID.randomUUID();
        redis.opsForValue().set(key, Integer.toString(groups.size()), Duration.ofSeconds(30));
        String readBack = redis.opsForValue().get(key);
        redis.delete(key);
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("aggregateInDatabase", true);
        metric.put("groupCount", groups.size());
        metric.put("cacheIsolatedBySystemTenant", true);
        metric.put("cacheReadBack", readBack != null);
        metric.put("cacheTtlSeconds", 30);
        metric.put("timeoutMillis", timeoutMillis);
        return result("STATISTICS", started, metric);
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private ProbeResult result(String code, long started, Map<String, Object> metric) {
        long millis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        metric.put("latencyMillis", millis);
        return new ProbeResult(code, millis, metric);
    }

    public record SecuritySignals(long weakPasswords, long unreferencedApplicationSecrets, long boundaryViolations) { }
    public record ProbeResult(String code, long latencyMillis, Map<String, Object> metric) { }
}
