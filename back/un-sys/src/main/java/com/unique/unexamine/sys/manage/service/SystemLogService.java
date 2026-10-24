package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.manage.bo.SystemLogSearchBO;
import org.springframework.stereotype.Service;

/** 系统操作日志的统一业务筛选；基础日志 Service 只处理主表 CRUD。 */
@Service
public class SystemLogService {
    private final SystemManageService systems;
    private final IUnSysLogOptService logs;

    /** 复用系统管理授权与生成的主表服务。 */
    public SystemLogService(SystemManageService systems, IUnSysLogOptService logs) {
        this.systems = systems;
        this.logs = logs;
    }

    /** 按有权管理的租户、操作人、模块、时间及日志内容分页查阅。 */
    public Page<UnSysLogOpt> queryPageList(String token, Long systemId, SystemLogSearchBO search) {
        var context = systems.requireAdminContext(token, systemId, search.getTenantId());
        if (search.getFromTime() != null && search.getToTime() != null
                && !search.getFromTime().isBefore(search.getToTime()))
            throw new IllegalArgumentException("结束时间须晚于开始时间");
        var where = new QueryWrapper<UnSysLogOpt>().eq("system_id", systemId);
        if (search.getTenantId() != null || !context.owner()) where.eq("tenant_id", context.tenantId());
        if (search.getAccountId() != null) where.eq("account_id", search.getAccountId());
        if (search.getFromTime() != null) where.ge("create_time", search.getFromTime());
        if (search.getToTime() != null) where.lt("create_time", search.getToTime());
        if (search.getKeyword() != null && !search.getKeyword().isBlank()) {
            String content = search.getKeyword().trim();
            where.and(q -> q.like("detail_json", content).or().like("action_code", content)
                    .or().like("object_type", content));
        }
        if (search.getModuleId() != null) {
            Long moduleId = search.getModuleId();
            // 历史日志未直接保存模块主键：按对象引用追溯模块、字段及菜单，补充审计详情中的模块归属。
            where.and(q -> q.nested(direct -> direct.eq("object_type", "MODULE").eq("object_id", moduleId))
                    .or().nested(field -> field.eq("object_type", "MODULE_FIELD")
                            .apply("EXISTS (SELECT 1 FROM un_module_field f WHERE f.id = un_sys_log_opt.object_id "
                                    + "AND f.system_id = un_sys_log_opt.system_id AND f.module_id = {0})", moduleId))
                    .or().nested(menu -> menu.eq("object_type", "MODULE_MENU")
                            .apply("EXISTS (SELECT 1 FROM un_sys_menu m WHERE m.id = un_sys_log_opt.object_id "
                                    + "AND m.system_id = un_sys_log_opt.system_id AND LEFT(m.code, CHAR_LENGTH(CONCAT('MODULE_', {0}, '_'))) "
                                    + "= CONCAT('MODULE_', {0}, '_'))", moduleId))
                    .or().apply("CASE WHEN JSON_VALID(detail_json) THEN "
                            + "JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.moduleId')) = {0} "
                            + "ELSE FALSE END", String.valueOf(moduleId)));
        }
        return logs.page(search.parse(), where.orderByDesc("id"));
    }
}
