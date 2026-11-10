package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.module.ExamineCompletionHandler;
import org.springframework.stereotype.Component;

/** 删除动作的审批终态回调；只在审批通过后调用模块记录的实际删除链。 */
@Component
public class ModuleDeleteAfterExamineHandler implements ExamineCompletionHandler {
    private final ModuleRecordService records;

    public ModuleDeleteAfterExamineHandler(ModuleRecordService records) {
        this.records=records;
    }

    /** 拒绝与取消保留原业务记录，审批历史由 examine 自己维护。 */
    @Override
    public void afterCompletion(ExamineCompletionContext context) {
        if ("COMPLETED".equals(context.status()) && Boolean.TRUE.equals(context.metadata().get("deleteRequest")))
            records.completeApprovedDelete(context);
    }
}
