package com.unique.unexamine.examine.manage.strategy;

import java.util.Set;
import org.springframework.stereotype.Component;

/** 普通审批节点策略。 */
@Component
public class OrdinaryApprovalNodeStrategy extends ApprovalNodeStrategy {
    public OrdinaryApprovalNodeStrategy() {
        super(Set.of("APPROVAL", "ORDINARY", "NORMAL", "ORDINARY_APPROVAL", "APPROVAL_NODE",
                "OR_SIGN", "AND_SIGN", "COUNTERSIGN", "PARALLEL", "ROLE_APPROVAL",
                "USER_APPROVAL", "DEPARTMENT_APPROVAL", "SUPERIOR_APPROVAL"));
    }
}
