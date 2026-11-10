package com.unique.unexamine.core.module;

/** Flow 向 examine 发起审批的跨模块端口。 */
public interface ExamineStarter {
    /** 校验 Flow 引用的审批定义属于当前系统租户，并可用于指定模块。 */
    void validateReference(Long systemId, Long tenantId, Long moduleId, Long examineId);

    /** 按已发布的审批定义创建审批实例。 */
    void start(ExamineStartContext context);
}
