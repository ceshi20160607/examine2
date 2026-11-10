package com.unique.unexamine.core.module;

import java.util.Set;

/** 审批定义管理的跨模块权限端口；具体系统和租户鉴权由 un-sys 实现。 */
@FunctionalInterface
public interface ExamineDefinitionAccess {
    /** 校验当前账号有权管理指定系统租户的审批定义。 */
    Context requireAdmin(String token, Long systemId, Long tenantId);

    /** 审批定义绑定业务模块时校验模块属于当前系统租户；不绑定模块的系统级审批允许为空。 */
    default void validateModule(Context context, Long moduleId) {
        // 具体业务域按自身模块目录实现；默认允许系统级审批定义。
    }

    /**
     * 校验流程节点引用的业务字段属于绑定模块；未绑定模块的系统审批不能引用业务字段。
     */
    default void validateFieldKeys(Context context, Long moduleId, Set<String> fieldKeys) {
        if (moduleId == null && fieldKeys != null && !fieldKeys.isEmpty())
            throw new IllegalArgumentException("未绑定业务模块的审批不能引用业务字段");
    }

    /** 按模块字段定义校验审批触发条件值；系统域实现具体字段配置和租户边界。 */
    default void validateConditionValues(Context context, Long moduleId, String conditionJson) {
    }

    /** 校验模块审批绑定引用的动作菜单属于当前模块；未接入菜单域的实现可保持默认放行。 */
    default void validateActionMenu(Context context, Long moduleId, Long actionMenuId) {
        if (actionMenuId != null && moduleId == null)
            throw new IllegalArgumentException("审批动作菜单必须绑定业务模块");
    }

    /** 已通过系统和租户管理权限检查的上下文。 */
    record Context(Long systemId, Long tenantId, Long accountId, boolean owner, boolean tenantAdmin) {}
}
