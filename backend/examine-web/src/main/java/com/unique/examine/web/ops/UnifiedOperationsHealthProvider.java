package com.unique.examine.web.ops;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.plat.manage.audit.OperationsHealthModels;
import com.unique.examine.plat.manage.audit.OperationsHealthQuery;
import com.unique.examine.plat.manage.audit.UnifiedAuditModels;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Bounded operations readiness projection. Raw health details and configuration values never leave this adapter. */
@Component
public final class UnifiedOperationsHealthProvider implements OperationsHealthQuery {
    private static final String AVAILABLE = "AVAILABLE";
    private static final String DEGRADED = "DEGRADED";
    private static final String UNCONFIGURED = "UNCONFIGURED";

    private final JdbcTemplate jdbc;
    private final Function<String, HealthComponent> health;
    private final Environment environment;
    private final Clock clock;

    @Autowired
    public UnifiedOperationsHealthProvider(
            JdbcTemplate jdbc,
            HealthEndpoint health,
            Environment environment
    ) {
        this(jdbc, health::healthForPath, environment, Clock.systemUTC());
    }

    UnifiedOperationsHealthProvider(
            JdbcTemplate jdbc,
            Function<String, HealthComponent> health,
            Environment environment,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.health = health;
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public OperationsHealthModels.Summary inspect(UnifiedAuditModels.Scope scope) {
        var flywayVersion = flywayVersion();
        var components = new ArrayList<OperationsHealthModels.Component>();
        components.add(release(flywayVersion));
        components.add(infrastructure("DB", "Database", "db",
                "Database probe succeeded.", "Check database availability and migration status."));
        components.add(infrastructure("REDIS", "Redis", "redis",
                "Redis probe succeeded.", "Check Redis availability and application credentials."));
        components.add(fileStorage());
        components.add(job(scope));
        components.add(outbox(scope));
        components.add(event(scope));
        components.add(openApi(scope));
        components.add(ai(scope));
        var overall = components.stream().anyMatch(component -> DEGRADED.equals(component.status()))
                ? DEGRADED : AVAILABLE;
        return new OperationsHealthModels.Summary(
                clock.instant(), overall, version(), flywayVersion, components);
    }

    private OperationsHealthModels.Component release(String flywayVersion) {
        if (flywayVersion == null) {
            return component("VERSION", "Version / Flyway", DEGRADED,
                    "Application version is available, but the Flyway version could not be read.",
                    "Verify database access and Flyway schema history.");
        }
        return component("VERSION", "Version / Flyway", AVAILABLE,
                "Application and database schema versions are readable.", "No action required.");
    }

    private OperationsHealthModels.Component infrastructure(
            String code, String label, String path, String availableSummary, String hint
    ) {
        try {
            HealthComponent value = health.apply(path);
            if (value != null && value.getStatus() != null && "UP".equals(value.getStatus().getCode())) {
                return component(code, label, AVAILABLE, availableSummary, "No action required.");
            }
        } catch (RuntimeException ignored) {
            // Coarse state only. The exception may contain credentials or endpoint details.
        }
        return component(code, label, DEGRADED, label + " probe is not ready.", hint);
    }

    private OperationsHealthModels.Component fileStorage() {
        var mode = environment.getProperty("examine.file.storage.mode", "LOCAL").trim().toUpperCase();
        if ("LOCAL".equals(mode)) {
            try {
                var root = Path.of(environment.getProperty(
                        "examine.file.storage.local.root", "./data/files")).toAbsolutePath().normalize();
                if (Files.isDirectory(root) && Files.isReadable(root) && Files.isWritable(root)) {
                    return component("FILE_STORAGE", "File storage", AVAILABLE,
                            "Local file storage is readable and writable.", "No action required.");
                }
            } catch (RuntimeException ignored) {
                // Do not return the configured path.
            }
            return component("FILE_STORAGE", "File storage", DEGRADED,
                    "Local file storage is not ready.",
                    "Create the configured storage directory and verify read/write permissions.");
        }
        if ("S3".equals(mode)) {
            var ready = configured("examine.file.storage.s3.endpoint")
                    && configured("examine.file.storage.s3.region")
                    && configured("examine.file.storage.s3.bucket")
                    && configured("examine.file.storage.s3.access-key")
                    && configured("examine.file.storage.s3.secret-key");
            return ready
                    ? component("FILE_STORAGE", "File storage", AVAILABLE,
                    "Object storage configuration is present.", "Run an upload smoke test after credential rotation.")
                    : component("FILE_STORAGE", "File storage", UNCONFIGURED,
                    "Object storage configuration is incomplete.",
                    "Configure endpoint, region, bucket and credential references.");
        }
        return component("FILE_STORAGE", "File storage", UNCONFIGURED,
                "File storage mode is not supported.", "Configure LOCAL or S3 storage mode.");
    }

    private OperationsHealthModels.Component job(UnifiedAuditModels.Scope scope) {
        var failed = countScoped("un_sys_job", "status IN ('FAILED','PARTIAL')", scope);
        if (failed == null) return unavailable("JOB", "Jobs", "Verify job tables and database migrations.");
        if (failed > 0) return component("JOB", "Jobs", DEGRADED,
                failed + " failed or partial jobs require attention.",
                "Open the task log, inspect the correlation ID and retry after fixing the cause.");
        return component("JOB", "Jobs", AVAILABLE, "No failed jobs in the current scope.", "No action required.");
    }

    private OperationsHealthModels.Component outbox(UnifiedAuditModels.Scope scope) {
        var dead = countScoped("un_sys_outbox_event", "status='DEAD'", scope);
        if (dead == null) return unavailable("OUTBOX", "Outbox", "Verify outbox tables and publisher migrations.");
        if (dead > 0) return component("OUTBOX", "Outbox", DEGRADED,
                dead + " dead outbox events require attention.",
                "Inspect publisher health and replay eligible events after fixing the cause.");
        return component("OUTBOX", "Outbox", AVAILABLE, "No dead outbox events in the current scope.", "No action required.");
    }

    private OperationsHealthModels.Component event(UnifiedAuditModels.Scope scope) {
        var failed = countScoped("un_event_message_delivery_log", "status='FAILED'", scope);
        if (failed == null) return unavailable("EVENT", "Events / messages", "Verify event delivery migrations.");
        if (failed > 0) return component("EVENT", "Events / messages", DEGRADED,
                failed + " failed message deliveries require attention.",
                "Review the delivery log and channel configuration, then retry safely.");
        return component("EVENT", "Events / messages", AVAILABLE,
                "No failed message deliveries in the current scope.", "No action required.");
    }

    private OperationsHealthModels.Component openApi(UnifiedAuditModels.Scope scope) {
        Long enabled;
        if (scope.type() == ContextType.PLATFORM) {
            enabled = safeCount("SELECT COUNT(*) FROM un_platform_openapi_application WHERE status='ACTIVE'", List.of());
        } else {
            enabled = safeCount("SELECT COUNT(*) FROM un_openapi_application "
                            + "WHERE system_id=? AND tenant_id=? AND status='ACTIVE'",
                    List.of(scope.systemId(), scope.tenantId()));
        }
        if (enabled == null) return unavailable("OPENAPI", "OpenAPI", "Verify OpenAPI migrations.");
        if (enabled == 0) return component("OPENAPI", "OpenAPI", UNCONFIGURED,
                "No active OpenAPI applications in the current scope.",
                "Create an application only when external access is required.");
        return component("OPENAPI", "OpenAPI", AVAILABLE,
                enabled + " active OpenAPI applications are configured.", "Review scopes and rotate credentials periodically.");
    }

    private OperationsHealthModels.Component ai(UnifiedAuditModels.Scope scope) {
        Long enabled;
        if (scope.type() == ContextType.PLATFORM) {
            enabled = safeCount("SELECT COUNT(*) FROM un_platform_ai_policy "
                    + "WHERE status='PUBLISHED' AND active_version_id IS NOT NULL", List.of());
        } else {
            enabled = safeCount("SELECT COUNT(*) FROM un_ai_agent_policy "
                            + "WHERE system_id=? AND tenant_id=? AND enabled=1 AND active_version_id IS NOT NULL",
                    List.of(scope.systemId(), scope.tenantId()));
        }
        if (enabled == null) return unavailable("AI", "AI", "Verify AI configuration migrations.");
        if (enabled == 0) return component("AI", "AI", UNCONFIGURED,
                "No published AI policy is active in the current scope.",
                "Configure a provider and publish a checked policy when AI is required.");
        return component("AI", "AI", AVAILABLE, "An active AI policy is published.",
                "Monitor quotas and provider readiness.");
    }

    private Long countScoped(String table, String condition, UnifiedAuditModels.Scope scope) {
        if (scope.type() == ContextType.PLATFORM) {
            return safeCount("SELECT COUNT(*) FROM " + table
                    + " WHERE system_id IS NULL AND tenant_id IS NULL AND " + condition, List.of());
        }
        return safeCount("SELECT COUNT(*) FROM " + table
                        + " WHERE system_id=? AND tenant_id=? AND " + condition,
                List.of(scope.systemId(), scope.tenantId()));
    }

    private Long safeCount(String sql, List<Object> parameters) {
        try {
            return jdbc.queryForObject(sql, Long.class, parameters.toArray());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String flywayVersion() {
        try {
            return jdbc.queryForObject("SELECT version FROM flyway_schema_history "
                    + "WHERE success=1 ORDER BY installed_rank DESC LIMIT 1", String.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String version() {
        var configured = environment.getProperty("examine.build.version");
        if (configured != null && !configured.isBlank()) return configured.trim();
        var implementation = UnifiedOperationsHealthProvider.class.getPackage().getImplementationVersion();
        return implementation == null || implementation.isBlank() ? "development" : implementation;
    }

    private boolean configured(String property) {
        var value = environment.getProperty(property);
        return value != null && !value.isBlank();
    }

    private static OperationsHealthModels.Component unavailable(String code, String label, String hint) {
        return component(code, label, DEGRADED, label + " status could not be read.", hint);
    }

    private static OperationsHealthModels.Component component(
            String code, String label, String status, String summary, String hint
    ) {
        return new OperationsHealthModels.Component(code, label, status, summary, hint);
    }
}
