package com.unique.unexamine.manage.module;

/** 模块记录保存后的流程触发入口；模块域只依赖这项业务能力，不依赖流程 HTTP 控制器。 */
public interface ModuleRecordFlowTrigger {
    /** 在记录保存事务内按记录版本触发启用的流程绑定。 */
    void triggerRecordEvent(long tenantId, long moduleId, long recordId,
                            int recordVersion, long actorMemberId, String eventType);
}
