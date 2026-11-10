package com.unique.unexamine.examine.manage.strategy;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 抄送节点策略；抄送人只接收通知，不提交审批动作，也不阻塞审批线路。 */
@Component
public class CopyNodeStrategy implements ExamineAutoNodeStrategy {
    @Override
    public boolean supports(String nodeType) {
        if (nodeType == null) return false;
        return Set.of("CC", "COPY", "COPY_SEND", "NOTIFY").contains(nodeType.toUpperCase(Locale.ROOT));
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
        return "COPIED";
    }

    @Override
    public String nodeStatus(String actionCode) {
        return "COPIED";
    }

    @Override
    public java.util.Map<String, Object> execute(ExamineAutoNodeContext context) {
        return java.util.Map.of("copied", true);
    }
}
