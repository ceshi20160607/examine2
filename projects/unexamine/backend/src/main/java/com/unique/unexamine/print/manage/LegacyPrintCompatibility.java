package com.unique.unexamine.print.manage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 只承认升级时固定的旧模板清单；正式发布 PRINT 后永久退出兼容。
 * 新装库清单为空，升级后的新模块和新模板不能借用旧打印入口。
 */
@Component
public class LegacyPrintCompatibility {
    private final JdbcTemplate jdbc;

    public LegacyPrintCompatibility(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前系统、租户和模块仍有可用的升级前模板时，返回一个模板标识用于运行态投影。 */
    public Long activeTemplateId(Long systemId, Long tenantId, Long moduleId) {
        List<Long> ids = jdbc.query("""
                SELECT l.template_id FROM un_print_legacy_template l
                JOIN un_print_template_publication p ON p.template_id=l.template_id
                WHERE l.system_id=? AND l.owner_tenant_id=? AND l.module_id=?
                  AND l.retired_time IS NULL ORDER BY l.template_id LIMIT 1
                """, (result, index) -> result.getLong(1), systemId, tenantId, moduleId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /** 兼容只覆盖升级时固定的模板，不允许升级后新发布的模板绕过动作发布。 */
    public boolean permitsTemplate(Long systemId, Long tenantId, Long moduleId, Long templateId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_print_legacy_template
                WHERE system_id=? AND owner_tenant_id=? AND module_id=? AND template_id=?
                  AND retired_time IS NULL
                """, Integer.class, systemId, tenantId, moduleId, templateId);
        return count != null && count > 0;
    }

    /** 与正式动作发布处于同一事务，回滚发布失败时不会提前撤销旧入口。 */
    public void retire(Long systemId, Long tenantId, Long moduleId) {
        jdbc.update("""
                UPDATE un_print_legacy_template SET retired_time=CURRENT_TIMESTAMP(3)
                WHERE system_id=? AND owner_tenant_id=? AND module_id=? AND retired_time IS NULL
                """, systemId, tenantId, moduleId);
    }
}
