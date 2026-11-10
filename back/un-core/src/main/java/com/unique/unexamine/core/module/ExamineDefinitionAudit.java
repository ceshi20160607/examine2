package com.unique.unexamine.core.module;

/** 审批定义配置审计端口；具体日志表由系统模块实现，避免 examine 反向依赖 un-sys。 */
@FunctionalInterface
public interface ExamineDefinitionAudit {
    /** 在审批定义保存或停用的同一事务中记录配置前后快照。 */
    void record(ExamineDefinitionAccess.Context context, String actionCode,
                Long examineId, String detailJson);
}
