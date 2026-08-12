package com.unique.examine.plat.directory;

import com.unique.examine.core.api.KpiSubjectDirectoryFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Tenant-hiding JDBC projection for KPI subject validation and visibility. */
@Service
public class KpiSubjectDirectoryBridge implements KpiSubjectDirectoryFacade {
    static final String ACTIVE_MEMBER_SQL = """
            SELECT m.display_name
              FROM un_plat_member m
              JOIN un_plat_member_tenant mt
                ON mt.system_id=m.system_id
               AND mt.member_id=m.id
               AND mt.tenant_id=?
             WHERE m.system_id=?
               AND m.id=?
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL
                    OR mt.expires_at>CURRENT_TIMESTAMP(3))
             FOR SHARE
            """;

    static final String ACTIVE_DEPARTMENT_SQL = """
            SELECT d.name
              FROM un_plat_department d
             WHERE d.scope_type='SYSTEM'
               AND d.scope_key=?
               AND d.system_id=?
               AND (d.tenant_id IS NULL OR d.tenant_id=?)
               AND d.id=?
               AND d.status='ACTIVE'
               AND d.deleted_at IS NULL
             FOR SHARE
            """;

    static final String ACTIVE_ROLE_SQL = """
            SELECT r.name
              FROM un_plat_role r
             WHERE r.scope_type='SYSTEM'
               AND r.scope_key=?
               AND r.system_id=?
               AND (r.tenant_id IS NULL OR r.tenant_id=?)
               AND r.id=?
               AND r.status='ACTIVE'
               AND r.deleted_at IS NULL
             FOR SHARE
            """;

    static final String ACTIVE_ROLE_MEMBERS_SQL = """
            SELECT DISTINCT m.id
              FROM un_plat_member_role mr
              JOIN un_plat_member m
                ON m.system_id=mr.system_id
               AND m.id=mr.member_id
              JOIN un_plat_member_tenant mt
                ON mt.system_id=m.system_id
               AND mt.member_id=m.id
               AND mt.tenant_id=?
             WHERE mr.system_id=?
               AND mr.role_id=?
               AND (mr.tenant_id IS NULL OR mr.tenant_id=?)
               AND mr.valid_from<=CURRENT_TIMESTAMP(3)
               AND (mr.valid_until IS NULL
                    OR mr.valid_until>CURRENT_TIMESTAMP(3))
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL
                    OR mt.expires_at>CURRENT_TIMESTAMP(3))
             ORDER BY m.id
             FOR SHARE
            """;

    static final String CURRENT_DEPARTMENTS_SQL = """
            SELECT DISTINCT d.id
              FROM un_plat_member_department md
              JOIN un_plat_department d
                ON d.scope_type=md.scope_type
               AND d.scope_key=md.scope_key
               AND d.id=md.department_id
               AND (d.tenant_id IS NULL OR d.tenant_id=?)
             WHERE md.scope_type='SYSTEM'
               AND md.scope_key=?
               AND md.system_id=?
               AND md.member_id=?
               AND (md.tenant_id IS NULL OR md.tenant_id=?)
               AND md.deleted_at IS NULL
               AND d.status='ACTIVE'
               AND d.deleted_at IS NULL
             ORDER BY d.id
             FOR SHARE
            """;

    static final String CURRENT_ROLES_SQL = """
            SELECT DISTINCT r.id
              FROM un_plat_member_role mr
              JOIN un_plat_role r
                ON r.system_id=mr.system_id
               AND r.id=mr.role_id
             WHERE mr.system_id=?
               AND mr.member_id=?
               AND (mr.tenant_id IS NULL OR mr.tenant_id=?)
               AND mr.valid_from<=CURRENT_TIMESTAMP(3)
               AND (mr.valid_until IS NULL
                    OR mr.valid_until>CURRENT_TIMESTAMP(3))
               AND r.scope_type='SYSTEM'
               AND r.scope_key=?
               AND (r.tenant_id IS NULL OR r.tenant_id=?)
               AND r.status='ACTIVE'
               AND r.deleted_at IS NULL
             ORDER BY r.id
             FOR SHARE
            """;

    private final JdbcTemplate jdbc;

    public KpiSubjectDirectoryBridge(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public SubjectResolution resolve(
            long systemId,
            long tenantId,
            SubjectType subjectType,
            long subjectId
    ) {
        Objects.requireNonNull(subjectType, "subjectType");
        if (!valid(systemId, tenantId, subjectId)) {
            return SubjectResolution.missing(subjectType, subjectId);
        }
        return switch (subjectType) {
            case MEMBER -> resolveMember(
                    systemId, tenantId, subjectId);
            case DEPARTMENT -> resolveDepartment(
                    systemId, tenantId, subjectId);
            case ROLE -> resolveRole(systemId, tenantId, subjectId);
        };
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public CurrentMembership currentMembership(
            long systemId,
            long tenantId,
            long memberId
    ) {
        if (!valid(systemId, tenantId, memberId)
                || names(ACTIVE_MEMBER_SQL, tenantId, systemId, memberId)
                .isEmpty()) {
            return CurrentMembership.missing();
        }
        return CurrentMembership.active(
                ids(CURRENT_DEPARTMENTS_SQL,
                        tenantId, systemId, systemId, memberId, tenantId),
                ids(CURRENT_ROLES_SQL,
                        systemId, memberId, tenantId, systemId, tenantId));
    }

    private SubjectResolution resolveMember(
            long systemId,
            long tenantId,
            long subjectId
    ) {
        var names = names(
                ACTIVE_MEMBER_SQL, tenantId, systemId, subjectId);
        return names.isEmpty()
                ? SubjectResolution.missing(SubjectType.MEMBER, subjectId)
                : SubjectResolution.active(
                SubjectType.MEMBER, subjectId, names.getFirst(), List.of());
    }

    private SubjectResolution resolveDepartment(
            long systemId,
            long tenantId,
            long subjectId
    ) {
        var names = names(ACTIVE_DEPARTMENT_SQL,
                systemId, systemId, tenantId, subjectId);
        return names.isEmpty()
                ? SubjectResolution.missing(
                SubjectType.DEPARTMENT, subjectId)
                : SubjectResolution.active(
                SubjectType.DEPARTMENT, subjectId,
                names.getFirst(), List.of());
    }

    private SubjectResolution resolveRole(
            long systemId,
            long tenantId,
            long subjectId
    ) {
        var names = names(ACTIVE_ROLE_SQL,
                systemId, systemId, tenantId, subjectId);
        if (names.isEmpty()) {
            return SubjectResolution.missing(SubjectType.ROLE, subjectId);
        }
        return SubjectResolution.active(
                SubjectType.ROLE, subjectId, names.getFirst(),
                ids(ACTIVE_ROLE_MEMBERS_SQL,
                        tenantId, systemId, subjectId, tenantId));
    }

    private List<String> names(String sql, Object... arguments) {
        return jdbc.query(sql,
                (result, row) -> result.getString(1), arguments);
    }

    private List<Long> ids(String sql, Object... arguments) {
        return jdbc.query(sql,
                (result, row) -> result.getLong(1), arguments);
    }

    private static boolean valid(
            long systemId,
            long tenantId,
            long subjectId
    ) {
        return systemId > 0 && tenantId > 0 && subjectId > 0;
    }
}
