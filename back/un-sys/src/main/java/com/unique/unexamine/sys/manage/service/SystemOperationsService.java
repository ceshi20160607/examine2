package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.core.ops.OperationsHealthService;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeJob;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeJobService;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 系统运维体检校验系统管理员身份，并统计本系统的导入导出作业。 */
@Service
public class SystemOperationsService {
    public record Snapshot(OperationsHealthService.Snapshot infrastructure,Map<String,Long> exchangeJobs) {}
    private final SystemManageService systems;
    private final OperationsHealthService health;
    private final IUnModuleExchangeJobService jobs;

    public SystemOperationsService(SystemManageService systems,OperationsHealthService health,
                                   IUnModuleExchangeJobService jobs) {
        this.systems=systems; this.health=health; this.jobs=jobs;
    }

    public Snapshot inspect(String token,Long systemId,Long tenantId) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        Map<String,Long> counts=Map.of("PENDING",count(systemId,context.tenantId(),"PENDING"),
                "RUNNING",count(systemId,context.tenantId(),"RUNNING"),
                "FAILED",count(systemId,context.tenantId(),"FAILED"));
        return new Snapshot(health.inspect(),counts);
    }

    private long count(Long systemId,Long tenantId,String status) {
        return jobs.count(new LambdaQueryWrapper<UnModuleExchangeJob>()
                .eq(UnModuleExchangeJob::getSystemId,systemId)
                .eq(UnModuleExchangeJob::getTenantId,tenantId)
                .eq(UnModuleExchangeJob::getStatus,status));
    }
}
