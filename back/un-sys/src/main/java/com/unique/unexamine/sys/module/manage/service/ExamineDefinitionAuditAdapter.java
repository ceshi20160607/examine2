package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.core.module.ExamineDefinitionAccess;
import com.unique.unexamine.core.module.ExamineDefinitionAudit;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import org.springframework.stereotype.Component;

/** 将审批定义配置审计写入系统操作日志。 */
@Component
public class ExamineDefinitionAuditAdapter implements ExamineDefinitionAudit {
    private final IUnSysLogOptService logs;

    public ExamineDefinitionAuditAdapter(IUnSysLogOptService logs) {
        this.logs = logs;
    }

    @Override
    public void record(ExamineDefinitionAccess.Context context, String actionCode,
                       Long examineId, String detailJson) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId());
        log.setActionCode(actionCode);
        log.setObjectType("EXAMINE_DEFINITION");
        log.setObjectId(examineId);
        log.setDetailJson(detailJson);
        logs.addOrUpdate(log);
    }
}
