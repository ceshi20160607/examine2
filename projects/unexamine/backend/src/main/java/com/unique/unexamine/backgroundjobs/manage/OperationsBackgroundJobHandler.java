package com.unique.unexamine.backgroundjobs.manage;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class OperationsBackgroundJobHandler implements BackgroundJobHandler {
    public static final String HEALTH_SCAN = "SYSTEM_HEALTH_SCAN";
    public static final String STATISTICS_REFRESH = "SYSTEM_STATISTICS_REFRESH";

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public OperationsBackgroundJobHandler(JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    @Override
    public Set<String> jobTypes() {
        return Set.of(HEALTH_SCAN, STATISTICS_REFRESH);
    }

    @Override
    public String name(String jobType) {
        return HEALTH_SCAN.equals(jobType) ? "基础依赖健康检查" : "系统数据统计刷新";
    }

    @Override
    public String description(String jobType) {
        return HEALTH_SCAN.equals(jobType)
                ? "异步检查数据库、Redis 和迁移版本并保存逐项结果。"
                : "异步统计当前系统的成员、业务记录和审计事件。";
    }

    @Override
    public long estimateTotal(String jobType, Map<String, Object> parameters) {
        return 3;
    }

    @Override
    public Map<String, Object> execute(String jobType, Map<String, Object> parameters,
                                       BackgroundJobExecution execution) {
        if (HEALTH_SCAN.equals(jobType)) {
            return healthScan(execution);
        }
        if (STATISTICS_REFRESH.equals(jobType)) {
            return statistics(parameters, execution);
        }
        throw new IllegalArgumentException("Unsupported operations job type: " + jobType);
    }

    private Map<String, Object> healthScan(BackgroundJobExecution execution) {
        Integer database = jdbc.queryForObject("select 1", Integer.class);
        execution.itemSucceeded("database", 1L, Map.of("reachable", database != null && database == 1), 1, 3);

        String pong;
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            pong = connection.ping();
        }
        execution.itemSucceeded("redis", 2L, Map.of("response", pong == null ? "UNKNOWN" : pong), 2, 3);

        String migration = jdbc.queryForObject(
                "select version from flyway_schema_history where success=1 order by installed_rank desc limit 1",
                String.class);
        execution.itemSucceeded("database-migration", 3L, Map.of("version", migration), 3, 3);
        return Map.of("healthy", true, "checks", 3, "migrationVersion", migration);
    }

    private Map<String, Object> statistics(Map<String, Object> parameters, BackgroundJobExecution execution) {
        long systemId = number(parameters.get("systemId"), "systemId");
        long tenantId = number(parameters.get("tenantId"), "tenantId");
        Map<String, Object> summary = new LinkedHashMap<>();

        Long members = jdbc.queryForObject("select count(*) from sys_tenant_member where system_id=? and tenant_id=?",
                Long.class, systemId, tenantId);
        summary.put("members", members == null ? 0 : members);
        execution.itemSucceeded("members", 1L, Map.of("count", summary.get("members")), 1, 3);

        Long records = jdbc.queryForObject("select count(*) from biz_record where system_id=? and tenant_id=?",
                Long.class, systemId, tenantId);
        summary.put("businessRecords", records == null ? 0 : records);
        execution.itemSucceeded("business-records", 2L, Map.of("count", summary.get("businessRecords")), 2, 3);

        Long audits = jdbc.queryForObject("select count(*) from audit_event where system_id=? and tenant_id=?",
                Long.class, systemId, tenantId);
        summary.put("auditEvents", audits == null ? 0 : audits);
        execution.itemSucceeded("audit-events", 3L, Map.of("count", summary.get("auditEvents")), 3, 3);
        return summary;
    }

    private long number(Object value, String name) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Missing numeric job parameter: " + name);
    }
}
