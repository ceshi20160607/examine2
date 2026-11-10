package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysLogLogin;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysLogLoginService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.manage.bo.SystemLogSearchBO;
import com.unique.unexamine.sys.manage.dto.SystemLogDto.LogView;
import com.unique.unexamine.sys.manage.dto.SystemLogDto.LoginView;
import org.springframework.stereotype.Service;

/** 系统操作日志的统一业务筛选；基础日志 Service 只处理主表 CRUD。 */
@Service
public class SystemLogService {
    private final SystemManageService systems;
    private final IUnSysLogOptService logs;
    private final IUnSysLogLoginService loginLogs;

    /** 复用系统管理授权与生成的主表服务。 */
    public SystemLogService(SystemManageService systems, IUnSysLogOptService logs,
            IUnSysLogLoginService loginLogs) {
        this.systems = systems;
        this.logs = logs;
        this.loginLogs = loginLogs;
    }

    /** 显式系统进入记录按管理员当前租户权限分页读取。 */
    public Page<LoginView> logins(String token, Long systemId, SystemLogSearchBO search) {
        SystemLogSearchBO criteria = search == null ? new SystemLogSearchBO() : search;
        var context = systems.requireAdminContext(token, systemId, criteria.getTenantId());
        if (criteria.getFromTime() != null && criteria.getToTime() != null
                && !criteria.getFromTime().isBefore(criteria.getToTime()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "结束时间须晚于开始时间");
        var where = new QueryWrapper<UnSysLogLogin>().eq("system_id", systemId);
        if (criteria.getTenantId() != null || !context.owner()) where.eq("tenant_id", context.tenantId());
        if (criteria.getAccountId() != null) where.eq("account_id", criteria.getAccountId());
        if (criteria.getFromTime() != null) where.ge("create_time", criteria.getFromTime());
        if (criteria.getToTime() != null) where.lt("create_time", criteria.getToTime());
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String keyword = criteria.getKeyword().trim();
            if (keyword.length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "登录日志搜索内容过长");
            where.and(w -> w.like("result_code", keyword).or().like("failure_reason", keyword));
        }
        Page<UnSysLogLogin> page = loginLogs.page(criteria.parse(), where.orderByDesc("id"));
        return new Page<LoginView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(row -> new LoginView(row.getId(), row.getSystemId(),
                        row.getTenantId(), row.getAccountId(), row.getResultCode(), row.getFailureReason(),
                        row.getIpAddress(), row.getTraceId(), row.getCreateTime())).toList());
    }

    /** 按有权管理的租户、操作人、模块、时间及日志内容分页查阅。 */
    public Page<LogView> queryPageList(String token, Long systemId, SystemLogSearchBO search) {
        // 日志查询也会被后台任务直接调用，缺省条件沿用公共 SearchBO，不能依赖 ModelAttribute 一定创建对象。
        SystemLogSearchBO criteria = search == null ? new SystemLogSearchBO() : search;
        var context = systems.requireAdminContext(token, systemId, criteria.getTenantId());
        if (criteria.getFromTime() != null && criteria.getToTime() != null
                && !criteria.getFromTime().isBefore(criteria.getToTime()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"结束时间须晚于开始时间");
        var where = new QueryWrapper<UnSysLogOpt>().eq("system_id", systemId);
        if (criteria.getTenantId() != null || !context.owner()) where.eq("tenant_id", context.tenantId());
        if (criteria.getAccountId() != null) where.eq("account_id", criteria.getAccountId());
        if (criteria.getFromTime() != null) where.ge("create_time", criteria.getFromTime());
        if (criteria.getToTime() != null) where.lt("create_time", criteria.getToTime());
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String content = criteria.getKeyword().trim();
            if (content.length() > 200)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"日志搜索内容过长");
            where.and(q -> q.like("detail_json", content).or().like("action_code", content)
                    .or().like("object_type", content));
        }
        if (criteria.getModuleId() != null) {
            Long moduleId = criteria.getModuleId();
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
        Page<UnSysLogOpt> result = logs.page(criteria.parse(), where.orderByDesc("id"));
        return new Page<LogView>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(result.getRecords().stream().map(row -> new LogView(row.getId(), row.getSystemId(),
                        row.getTenantId(), row.getAccountId(), row.getActionCode(), row.getObjectType(),
                        row.getObjectId(), row.getDetailJson(), row.getTraceId(), row.getCreateTime())).toList());
    }
}
