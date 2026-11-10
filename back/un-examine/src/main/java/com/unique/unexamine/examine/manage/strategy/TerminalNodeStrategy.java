package com.unique.unexamine.examine.manage.strategy;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 审批结束节点策略；结束节点不产生待办，也不再继续路由。 */
@Component
public class TerminalNodeStrategy implements ExamineAutoNodeStrategy {
    private static final Set<String> TYPES = Set.of("END", "FINISH", "TERMINATE");

    @Override
    public boolean supports(String nodeType) {
        return nodeType != null && TYPES.contains(nodeType.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean interactive() {
        return false;
    }

    @Override
    public boolean supportsAction(String actionCode) {
        return false;
    }

    @Override
    public boolean complete(String approvalMode, long pendingUserCount) {
        return true;
    }

    @Override
    public String userStatus(String actionCode) {
        return "FINISHED";
    }

    @Override
    public String nodeStatus(String actionCode) {
        return "FINISHED";
    }

    @Override
    public Map<String, Object> execute(ExamineAutoNodeContext context) {
        return Map.of("finished", true);
    }
}
