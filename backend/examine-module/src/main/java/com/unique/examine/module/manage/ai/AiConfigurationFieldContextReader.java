package com.unique.examine.module.manage.ai;

import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/** Resolves one live configuration draft identity without crossing modules. */
@Component
public class AiConfigurationFieldContextReader {
    static final String CONTEXT_SQL = """
            SELECT r.id AS root_id,r.draft_revision,
                   m.id AS module_id,m.module_code,
                   COALESCE(MAX(f.sort_order),-10)+10 AS next_sort_order,
                   SUM(CASE WHEN f.field_code=? THEN 1 ELSE 0 END) AS code_count
            FROM un_module_config_root r
            JOIN un_module_definition m
              ON m.system_id=r.system_id
             AND m.deleted_at IS NULL
            LEFT JOIN un_module_field f
              ON f.system_id=m.system_id
             AND f.module_id=m.id
             AND f.deleted_at IS NULL
            WHERE r.system_id=? AND m.module_code=?
            GROUP BY r.id,r.draft_revision,m.id,m.module_code
            """;
    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            "system.admin.access", "module.config.manage");

    private final EffectivePermissionFacade authorization;
    private final JdbcTemplate jdbc;

    public AiConfigurationFieldContextReader(
            EffectivePermissionFacade authorization, JdbcTemplate jdbc) {
        this.authorization = Objects.requireNonNull(
                authorization, "authorization");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Transactional(readOnly = true)
    public Snapshot resolve(Access access) {
        Objects.requireNonNull(access, "access");
        var live = authorization.evaluateSystem(
                access.systemId(), access.tenantId(), access.memberId());
        if (live.epoch() != access.authorizationEpoch()
                || !live.permissions().equals(access.effectivePermissions())) {
            throw new BusinessException(
                    "AI_CONFIG_AUTHORIZATION_STALE",
                    "Configuration authorization changed",
                    HttpStatus.CONFLICT);
        }
        if (!live.permissions().containsAll(REQUIRED_PERMISSIONS)) {
            throw new BusinessException(
                    "AI_CONFIG_PERMISSION_DENIED",
                    "System administration and configuration permissions are required",
                    HttpStatus.FORBIDDEN);
        }
        var rows = jdbc.query(CONTEXT_SQL, (value, row) -> new Snapshot(
                        value.getLong("root_id"),
                        value.getLong("module_id"),
                        value.getString("module_code"),
                        value.getLong("draft_revision"),
                        value.getLong("next_sort_order"),
                        value.getLong("code_count") > 0,
                        live.epoch(), live.permissions()),
                access.fieldCode(), access.systemId(), access.moduleCode());
        if (rows.size() != 1) {
            throw new BusinessException(
                    "AI_CONFIG_MODULE_UNAVAILABLE",
                    "The configuration draft module is unavailable",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    public record Access(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            String fieldCode
    ) {
        public Access {
            if (accountId <= 0 || systemId <= 0 || tenantId <= 0
                    || memberId <= 0 || authorizationEpoch <= 0) {
                throw new IllegalArgumentException(
                        "Configuration access identity is invalid");
            }
            effectivePermissions = Set.copyOf(
                    Objects.requireNonNull(
                            effectivePermissions, "effectivePermissions"));
            if (moduleCode == null
                    || !moduleCode.matches("^[a-z][a-z0-9_]{1,63}$")
                    || fieldCode == null
                    || !fieldCode.matches("^[a-z][a-z0-9_]{1,63}$")) {
                throw new IllegalArgumentException(
                        "Configuration module or field code is invalid");
            }
        }
    }

    public record Snapshot(
            long configRootId,
            long moduleId,
            String moduleCode,
            long draftRevision,
            long nextSortOrder,
            boolean fieldCodeExists,
            long authorizationEpoch,
            Set<String> effectivePermissions
    ) {
        public Snapshot {
            if (configRootId <= 0 || moduleId <= 0 || draftRevision < 0
                    || nextSortOrder < 0 || nextSortOrder > Integer.MAX_VALUE
                    || authorizationEpoch <= 0) {
                throw new IllegalArgumentException(
                        "Configuration snapshot is invalid");
            }
            if (moduleCode == null
                    || !moduleCode.matches("^[a-z][a-z0-9_]{1,63}$")) {
                throw new IllegalArgumentException("moduleCode is invalid");
            }
            effectivePermissions = Set.copyOf(effectivePermissions);
        }

        int sortOrder() {
            return Math.toIntExact(nextSortOrder);
        }
    }
}
