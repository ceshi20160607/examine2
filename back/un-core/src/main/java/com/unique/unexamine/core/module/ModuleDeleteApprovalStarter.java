package com.unique.unexamine.core.module;

import java.util.Map;

/** 删除动作的审批启动端口；模块只关心是否命中审批，不接触审批定义和节点。 */
public interface ModuleDeleteApprovalStarter {
    /** 命中时在当前业务事务内建立审批实例，返回 true；未命中时由模块直接删除。 */
    boolean startDeleteApproval(ModuleRecordProcessContext context, Map<String, Object> metadata);
}
