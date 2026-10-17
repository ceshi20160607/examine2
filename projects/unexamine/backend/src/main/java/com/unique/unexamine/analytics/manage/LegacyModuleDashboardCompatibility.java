package com.unique.unexamine.analytics.manage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 只承认 V23 升级时已发布的模块首页清单；新首页不继承旧入口权限。
 * 查询始终复核当前系统、租户、模块和有效发布指针，不能借用主租户页面。
 */
@Component
public class LegacyModuleDashboardCompatibility {
    private final JdbcTemplate jdbc;

    public LegacyModuleDashboardCompatibility(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前租户有至少一张仍有效的升级前模块首页时，允许投影旧入口。 */
    public boolean hasPublishedDashboard(Long systemId, Long tenantId, Long moduleId) {
        return matches(systemId, tenantId, moduleId, null);
    }

    /** 旧 LIST 权限只覆盖清单中的原首页，不允许读取升级后新建的同模块页面。 */
    public boolean permitsDashboard(Long systemId, Long tenantId, Long moduleId, Long dashboardId) {
        return dashboardId != null && matches(systemId, tenantId, moduleId, dashboardId);
    }

    private boolean matches(Long systemId, Long tenantId, Long moduleId, Long dashboardId) {
        long selectedId = dashboardId == null ? 0L : dashboardId;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_ana_legacy_module_dashboard l
                JOIN un_ana_dashboard d ON d.id=l.dashboard_id
                  AND d.system_id=l.system_id AND d.owner_tenant_id=l.tenant_id
                  AND d.module_id=l.module_id AND d.dashboard_kind='MODULE_HOME'
                JOIN un_ana_dashboard_publication p ON p.dashboard_id=d.id
                JOIN un_ana_dashboard_version v ON v.id=p.current_version_id
                  AND v.dashboard_id=d.id AND v.scope_type=d.scope_type
                  AND v.scope_owner_id=d.scope_owner_id
                WHERE l.system_id=? AND l.tenant_id=? AND l.module_id=?
                  AND (?=0 OR l.dashboard_id=?)
                """, Integer.class, systemId, tenantId, moduleId, selectedId, selectedId);
        return count != null && count > 0;
    }
}
