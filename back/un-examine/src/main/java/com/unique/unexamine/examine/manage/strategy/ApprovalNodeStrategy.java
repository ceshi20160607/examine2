package com.unique.unexamine.examine.manage.strategy;

import java.util.Locale;
import java.util.Set;

/** 普通审批和条件审批共用的人工办理策略基类。 */
abstract class ApprovalNodeStrategy implements ExamineNodeStrategy {
    private final Set<String> types;

    ApprovalNodeStrategy(Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean supports(String nodeType) {
        return nodeType != null && types.contains(nodeType.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    public boolean supportsAction(String actionCode) {
        return Set.of("APPROVE", "REJECT", "TRANSFER", "RETURN").contains(actionCode);
    }

    @Override
    public boolean complete(String approvalMode, long pendingUserCount) {
        String mode = approvalMode == null ? "ANY" : approvalMode.toUpperCase(Locale.ROOT);
        if (Set.of("ALL", "AND", "COUNTERSIGN", "PARALLEL").contains(mode)) return pendingUserCount == 0;
        return true;
    }

    @Override
    public String userStatus(String actionCode) {
        return "APPROVE".equals(actionCode) ? "APPROVED" : "REJECTED";
    }

    @Override
    public String nodeStatus(String actionCode) {
        return "APPROVE".equals(actionCode) ? "APPROVED" : "REJECTED";
    }
}
