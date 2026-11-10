package com.unique.unexamine.core.module;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** 审批节点处理人解析端口；人员、部门、角色和上级规则由具体系统实现。 */
@FunctionalInterface
public interface ExamineCandidateResolver {
    /** 返回当前节点需要建立待办的内部账号主键。 */
    Set<Long> resolve(JsonNode node, ExamineCandidateContext context);
}
