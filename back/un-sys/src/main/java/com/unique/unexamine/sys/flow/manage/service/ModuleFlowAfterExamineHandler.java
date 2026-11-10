package com.unique.unexamine.sys.flow.manage.service;

import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.module.ExamineCompletionHandler;
import org.springframework.stereotype.Component;

/** 将审批最终结果作为事件交回模块 Flow，继续执行发布快照中的后续线路。 */
@Component
public class ModuleFlowAfterExamineHandler implements ExamineCompletionHandler {
    private final FlowRuntimeService flows;

    public ModuleFlowAfterExamineHandler(FlowRuntimeService flows) {
        this.flows = flows;
    }

    @Override
    public void afterCompletion(ExamineCompletionContext context) {
        if (context.metadata().containsKey("flowExampleId")
                && context.metadata().containsKey("flowTaskId")) {
            flows.afterExamine(context);
        }
    }
}
