package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventListener;
import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.module.ExamineCompletionHandler;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 审批实例的当前状态同步到业务主表；审批历史和页面状态组仍由审批域维护。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ModuleRecordApprovalStatusHandler implements BusinessEventListener,ExamineCompletionHandler {
    /** 0 未提交、1 审批中、2 通过、3 拒绝、4 取消。 */
    private static final int RUNNING=1,COMPLETED=2,REJECTED=3,CANCELLED=4;
    private final IUnModuleRecordService records;

    public ModuleRecordApprovalStatusHandler(IUnModuleRecordService records) { this.records=records; }

    /** 启动事件与审批实例同一事务，失败时实例和记录状态一同回滚。 */
    @Override
    public void onEvent(BusinessEventContext context) {
        if ("EXAMINE_STARTED".equals(context.eventCode()))
            update(context.systemId(),context.tenantId(),context.moduleId(),context.recordId(),RUNNING);
    }

    /** 先同步终态，再执行可能逻辑删除该记录的后续回调。 */
    @Override
    public void afterCompletion(ExamineCompletionContext context) {
        int status=switch (context.status()) {
            case "COMPLETED" -> COMPLETED;
            case "REJECTED" -> REJECTED;
            case "CANCELLED" -> CANCELLED;
            default -> 0;
        };
        if (status!=0) update(context.systemId(),context.tenantId(),context.moduleId(),context.recordId(),status);
    }

    private void update(Long systemId,Long tenantId,Long moduleId,Long recordId,int status) {
        if (systemId==null || tenantId==null || moduleId==null || recordId==null) return;
        records.update(new LambdaUpdateWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId,recordId).eq(UnModuleRecord::getSystemId,systemId)
                .eq(UnModuleRecord::getTenantId,tenantId).eq(UnModuleRecord::getModuleId,moduleId)
                .set(UnModuleRecord::getCheckStatus,status));
    }
}
