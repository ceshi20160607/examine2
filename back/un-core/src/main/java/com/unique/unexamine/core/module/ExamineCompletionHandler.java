package com.unique.unexamine.core.module;

/** examine 结束后的业务编排端口；审批模块不直接依赖 Flow 模块。 */
@FunctionalInterface
public interface ExamineCompletionHandler {
    /** 根据审批最终状态执行后续业务线路。 */
    void afterCompletion(ExamineCompletionContext context);
}
