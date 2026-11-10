package com.unique.unexamine.examine.manage.strategy;

import java.util.Map;

/** 需要进入节点时自动执行的审批节点策略，例如抄送和外部 API 节点。 */
public interface ExamineAutoNodeStrategy extends ExamineNodeStrategy {
    /** 执行节点并返回可审计的结果摘要。 */
    Map<String, Object> execute(ExamineAutoNodeContext context);
}
