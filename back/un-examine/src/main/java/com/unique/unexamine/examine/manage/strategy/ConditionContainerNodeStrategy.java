package com.unique.unexamine.examine.manage.strategy;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 条件审批容器策略；自身不产生人工待办，按连线条件进入嵌套审批或抄送节点。 */
@Component
public class ConditionContainerNodeStrategy implements ExamineAutoNodeStrategy {
    @Override
    public boolean supports(String nodeType) {
        if (nodeType == null) return false;
        return Set.of("CONDITION", "CONDITION_GATE", "CONDITION_CONTAINER", "CONDITIONAL")
                .contains(nodeType.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean interactive() { return false; }

    @Override
    public boolean supportsAction(String actionCode) { return false; }

    @Override
    public boolean complete(String approvalMode, long pendingUserCount) { return true; }

    @Override
    public String userStatus(String actionCode) { return "SKIPPED"; }

    @Override
    public String nodeStatus(String actionCode) { return "CONDITION_MATCHED"; }

    @Override
    public Map<String, Object> execute(ExamineAutoNodeContext context) {
        return Map.of("nested", true, "nodeType", context.node().path("nodeType")
                .asText(context.node().path("node_type").asText("CONDITION")));
    }
}
