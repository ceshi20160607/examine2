package com.unique.examine.web.ops;

import com.unique.examine.plat.manage.audit.UnifiedAuditModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedOperationsHealthProviderTest {
    @TempDir
    Path storage;

    @Test
    void returnsBoundedReadinessWithoutConfigurationValues() {
        var jdbc = new HealthJdbcTemplate();
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new PropertySource<Map<String, String>>(
                "test", Map.of(
                "examine.build.version", "test-release",
                "examine.file.storage.mode", "LOCAL",
                "examine.file.storage.local.root", storage.toString(),
                "examine.file.storage.s3.secret-key", "MUST_NOT_LEAK")) {
            @Override public Object getProperty(String name) { return source.get(name); }
        });
        var provider = new UnifiedOperationsHealthProvider(
                jdbc, ignored -> Health.up().build(), environment,
                Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneOffset.UTC));

        var summary = provider.inspect(UnifiedAuditModels.Scope.system(41, 73));

        assertThat(summary.version()).isEqualTo("test-release");
        assertThat(summary.flywayVersion()).isEqualTo("8.93.0");
        assertThat(summary.components()).extracting(component -> component.code())
                .containsExactly("VERSION", "DB", "REDIS", "FILE_STORAGE", "JOB",
                        "OUTBOX", "EVENT", "OPENAPI", "AI");
        assertThat(summary.toString()).doesNotContain(storage.toString(), "MUST_NOT_LEAK");
        assertThat(jdbc.queries).allSatisfy((sql, parameters) -> {
            if (sql.contains("un_sys_") || sql.contains("un_event_")
                    || sql.contains("un_openapi_application") || sql.contains("un_ai_agent_policy")) {
                assertThat(parameters).containsExactly(41L, 73L);
            }
        });
    }

    @Test
    void infrastructureFailuresAreCoarsenedAndNeverExposeExceptionText() {
        var provider = new UnifiedOperationsHealthProvider(
                new HealthJdbcTemplate(), ignored -> { throw new IllegalStateException("secret-host"); },
                new StandardEnvironment(), Clock.systemUTC());

        var summary = provider.inspect(UnifiedAuditModels.Scope.platform());

        assertThat(summary.components()).filteredOn(component -> component.code().equals("DB")
                        || component.code().equals("REDIS"))
                .allMatch(component -> component.status().equals("DEGRADED"));
        assertThat(summary.toString()).doesNotContain("secret-host");
    }

    private static final class HealthJdbcTemplate extends JdbcTemplate {
        private final Map<String, Object[]> queries = new LinkedHashMap<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queries.put(sql, args);
            Object value = sql.contains("flyway_schema_history") ? "8.93.0" : 0L;
            return requiredType.cast(value);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return queryForObject(sql, requiredType, new Object[0]);
        }
    }
}
