package com.unique.unexamine.examine.manage.strategy;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 审批开始节点策略；开始节点只负责进入下一条线路，不产生人工待办。 */
@Component
public class StartNodeStrategy implements ExamineAutoNodeStrategy {
    private static final Set<String> TYPES = Set.of("START", "BEGIN", "START_EVENT");

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
        return "STARTED";
    }

    @Override
    public String nodeStatus(String actionCode) {
        return "STARTED";
    }

    @Override
    public Map<String, Object> execute(ExamineAutoNodeContext context) {
        return Map.of("started", true);
    }
}
