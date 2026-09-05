package com.unique.unexamine.authorization.manage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OrganizationRelationRepository {
    private final JdbcTemplate jdbc;

    public OrganizationRelationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, MemberRelation> byTenant(Long tenantId) {
        Map<Long, MemberRelation> result = new LinkedHashMap<>();
        List<MemberRelation> rows = jdbc.query(
                "select tenant_member_id, manager_tenant_member_id, position_title, version "
                        + "from sys_member_reporting_line where tenant_id = ? order by tenant_member_id",
                (row, rowNumber) -> new MemberRelation(
                        row.getLong("tenant_member_id"),
                        row.getObject("manager_tenant_member_id", Long.class),
                        row.getString("position_title"),
                        row.getInt("version")), tenantId);
        rows.forEach(row -> result.put(row.tenantMemberId(), row));
        return result;
    }

    public void upsert(Long systemId, Long tenantId, Long tenantMemberId,
                       Long managerTenantMemberId, String positionTitle) {
        jdbc.update("insert into sys_member_reporting_line(system_id, tenant_id, tenant_member_id, "
                        + "manager_tenant_member_id, position_title) values (?, ?, ?, ?, ?) "
                        + "on duplicate key update manager_tenant_member_id=values(manager_tenant_member_id), "
                        + "position_title=values(position_title), version=version+1",
                systemId, tenantId, tenantMemberId, managerTenantMemberId, emptyToNull(positionTitle));
    }

    public List<Long> subordinateSystemMemberIds(Long tenantId, Long managerTenantMemberId) {
        Map<Long, MemberRelation> relations = byTenant(tenantId);
        java.util.LinkedHashSet<Long> tenantMemberIds = new java.util.LinkedHashSet<>();
        tenantMemberIds.add(managerTenantMemberId);
        boolean changed;
        do {
            changed = false;
            for (MemberRelation relation : relations.values()) {
                if (relation.managerTenantMemberId() != null
                        && tenantMemberIds.contains(relation.managerTenantMemberId())
                        && tenantMemberIds.add(relation.tenantMemberId())) {
                    changed = true;
                }
            }
        } while (changed);
        if (tenantMemberIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(tenantMemberIds.size(), "?"));
        return jdbc.queryForList("select system_member_id from sys_tenant_member where tenant_id=? "
                        + "and status='ACTIVE' and id in (" + placeholders + ") order by id",
                Long.class, prepend(tenantId, tenantMemberIds.toArray()));
    }

    private Object[] prepend(Long tenantId, Object[] values) {
        Object[] parameters = new Object[values.length + 1];
        parameters[0] = tenantId;
        System.arraycopy(values, 0, parameters, 1, values.length);
        return parameters;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record MemberRelation(Long tenantMemberId, Long managerTenantMemberId,
                                 String positionTitle, Integer version) {
    }
}
