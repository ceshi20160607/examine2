package com.unique.examine.plat.directory;

import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class RuntimeApproverDirectoryBridge implements RuntimeApproverDirectoryFacade {
    static final String ACTIVE_ROLE_SQL = """
            SELECT r.id
              FROM un_plat_role r
             WHERE r.id=?
               AND r.scope_type='SYSTEM'
               AND r.scope_key=?
               AND r.system_id=?
               AND (r.tenant_id IS NULL OR r.tenant_id=?)
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
               AND (mr.valid_until IS NULL OR mr.valid_until>CURRENT_TIMESTAMP(3))
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL OR mt.expires_at>CURRENT_TIMESTAMP(3))
             ORDER BY m.id
             FOR SHARE
            """;

    static final String ACTIVE_DEPARTMENT_SQL = """
            SELECT d.id
              FROM un_plat_department d
             WHERE d.id=?
               AND d.scope_type='SYSTEM'
               AND d.scope_key=?
               AND d.system_id=?
               AND (d.tenant_id IS NULL OR d.tenant_id=?)
               AND d.status='ACTIVE'
               AND d.deleted_at IS NULL
             FOR SHARE
            """;

    static final String ACTIVE_DEPARTMENT_MEMBERS_SQL = """
            SELECT DISTINCT m.id
              FROM un_plat_member_department md
              JOIN un_plat_member m
                ON m.system_id=md.system_id
               AND m.id=md.member_id
              JOIN un_plat_member_tenant mt
                ON mt.system_id=m.system_id
               AND mt.member_id=m.id
               AND mt.tenant_id=?
             WHERE md.scope_type='SYSTEM'
               AND md.scope_key=?
               AND md.system_id=?
               AND md.department_id=?
               AND (md.tenant_id IS NULL OR md.tenant_id=?)
               AND md.deleted_at IS NULL
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL OR mt.expires_at>CURRENT_TIMESTAMP(3))
             ORDER BY m.id
            FOR SHARE
            """;

    static final String ACTIVE_TENANT_MEMBER_SQL = """
            SELECT m.id
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
               AND (mt.expires_at IS NULL OR mt.expires_at>CURRENT_TIMESTAMP(3))
             FOR SHARE
            """;

    static final String ACTIVE_DEPARTMENT_LEADER_SQL = """
            SELECT a.leader_member_id AS id
              FROM un_plat_department_leader_assignment a
              JOIN un_plat_member_department md
                ON md.scope_type='SYSTEM'
               AND md.scope_key=a.system_id
               AND md.system_id=a.system_id
               AND md.tenant_id=a.tenant_id
               AND md.department_id=a.department_id
               AND md.member_id=a.leader_member_id
               AND md.deleted_at IS NULL
              JOIN un_plat_member m
                ON m.system_id=a.system_id
               AND m.id=a.leader_member_id
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
              JOIN un_plat_member_tenant mt
                ON mt.system_id=a.system_id
               AND mt.tenant_id=a.tenant_id
               AND mt.member_id=a.leader_member_id
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL OR mt.expires_at>CURRENT_TIMESTAMP(3))
             WHERE a.system_id=?
               AND a.tenant_id=?
               AND a.department_id=?
               AND a.status='ACTIVE'
             FOR SHARE
            """;

    static final String ACTIVE_REQUESTER_MANAGER_SQL = """
            SELECT a.manager_member_id AS id
              FROM un_plat_member_manager_assignment a
              JOIN un_plat_member m
                ON m.system_id=a.system_id
               AND m.id=a.manager_member_id
               AND m.status='ACTIVE'
               AND m.deleted_at IS NULL
              JOIN un_plat_member_tenant mt
                ON mt.system_id=a.system_id
               AND mt.tenant_id=a.tenant_id
               AND mt.member_id=a.manager_member_id
               AND mt.status='ACTIVE'
               AND mt.deleted_at IS NULL
               AND (mt.expires_at IS NULL OR mt.expires_at>CURRENT_TIMESTAMP(3))
             WHERE a.system_id=?
               AND a.tenant_id=?
               AND a.member_id=?
               AND a.status='ACTIVE'
             FOR SHARE
            """;

    private final JdbcTemplate jdbc;

    public RuntimeApproverDirectoryBridge(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Resolution resolveRoleMembers(long systemId, long tenantId, long roleId) {
        if (!valid(systemId, tenantId, roleId)) {
            return Resolution.missing();
        }
        var role = ids(ACTIVE_ROLE_SQL, roleId, systemId, systemId, tenantId);
        if (role.isEmpty()) {
            return Resolution.missing();
        }
        return Resolution.active(ids(
                ACTIVE_ROLE_MEMBERS_SQL,
                tenantId,
                systemId,
                roleId,
                tenantId
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Resolution resolveDepartmentMembers(
            long systemId,
            long tenantId,
            long departmentId
    ) {
        if (!valid(systemId, tenantId, departmentId)) {
            return Resolution.missing();
        }
        var department = ids(
                ACTIVE_DEPARTMENT_SQL,
                departmentId,
                systemId,
                systemId,
                tenantId
        );
        if (department.isEmpty()) {
            return Resolution.missing();
        }
        return Resolution.active(ids(
                ACTIVE_DEPARTMENT_MEMBERS_SQL,
                tenantId,
                systemId,
                systemId,
                departmentId,
                tenantId
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Resolution resolveDepartmentLeader(
            long systemId,
            long tenantId,
            long departmentId
    ) {
        if (!valid(systemId, tenantId, departmentId)) {
            return Resolution.missing();
        }
        var department = ids(
                ACTIVE_DEPARTMENT_SQL,
                departmentId,
                systemId,
                systemId,
                tenantId
        );
        if (department.isEmpty()) {
            return Resolution.missing();
        }
        return Resolution.active(ids(
                ACTIVE_DEPARTMENT_LEADER_SQL,
                systemId,
                tenantId,
                departmentId
        ));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Resolution resolveRequesterManager(
            long systemId,
            long tenantId,
            long requesterMemberId
    ) {
        if (!valid(systemId, tenantId, requesterMemberId)) {
            return Resolution.missing();
        }
        var requester = ids(
                ACTIVE_TENANT_MEMBER_SQL,
                tenantId,
                systemId,
                requesterMemberId
        );
        if (requester.isEmpty()) {
            return Resolution.missing();
        }
        return Resolution.active(ids(
                ACTIVE_REQUESTER_MANAGER_SQL,
                systemId,
                tenantId,
                requesterMemberId
        ));
    }

    private List<Long> ids(String sql, Object... arguments) {
        return jdbc.query(sql, (result, row) -> result.getLong("id"), arguments);
    }

    private static boolean valid(long systemId, long tenantId, long sourceId) {
        return systemId > 0 && tenantId > 0 && sourceId > 0;
    }
}
