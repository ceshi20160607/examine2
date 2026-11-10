package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.manage.bo.PlatformLogSearchBO;
import com.unique.unexamine.plat.manage.dto.PlatformLogDto.LogView;
import org.springframework.stereotype.Service;

/** 平台操作日志业务查询；平台日志的账号、对象和内容筛选集中在此处。 */
@Service
public class PlatformLogManageService {
    private final AuthManageService auth;
    private final IUnPlatLogOptService logs;

    /** 注入平台管理员校验和生成的日志基础服务。 */
    public PlatformLogManageService(AuthManageService auth, IUnPlatLogOptService logs) {
        this.auth = auth;
        this.logs = logs;
    }

    /** 按操作人、对象、时间及日志内容分页查询平台操作历史。 */
    public Page<LogView> queryPageList(String token, PlatformLogSearchBO search) {
        auth.requirePlatformAdmin(token);
        PlatformLogSearchBO criteria = search == null ? new PlatformLogSearchBO() : search;
        if (criteria.getFromTime() != null && criteria.getToTime() != null
                && !criteria.getFromTime().isBefore(criteria.getToTime())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "结束时间须晚于开始时间");
        }
        QueryWrapper<UnPlatLogOpt> where = new QueryWrapper<>();
        if (criteria.getAccountId() != null) where.eq("account_id", criteria.getAccountId());
        if (criteria.getActionCode() != null && !criteria.getActionCode().isBlank())
            where.eq("action_code", criteria.getActionCode().trim());
        if (criteria.getObjectType() != null && !criteria.getObjectType().isBlank())
            where.eq("object_type", criteria.getObjectType().trim());
        if (criteria.getFromTime() != null) where.ge("create_time", criteria.getFromTime());
        if (criteria.getToTime() != null) where.lt("create_time", criteria.getToTime());
        String keyword = criteria.getKeyword() == null ? null : criteria.getKeyword().trim();
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 200)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "日志搜索内容过长");
            where.and(q -> q.like("detail_json", keyword)
                    .or().like("action_code", keyword)
                    .or().like("object_type", keyword));
        }
        Page<UnPlatLogOpt> result = logs.page(criteria.parse(), where.orderByDesc("id"));
        return new Page<LogView>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(result.getRecords().stream().map(row -> new LogView(row.getId(), row.getAccountId(),
                        row.getActionCode(), row.getObjectType(), row.getObjectId(), row.getDetailJson(),
                        row.getTraceId(), row.getCreateTime())).toList());
    }
}
