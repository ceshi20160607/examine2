package com.unique.unexamine.examine.manage.strategy;

import java.util.Set;
import org.springframework.stereotype.Component;

/** 条件审批节点策略；分支条件由发布快照连线条件判断。 */
@Component
public class ConditionApprovalNodeStrategy extends ApprovalNodeStrategy {
    public ConditionApprovalNodeStrategy() {
        super(Set.of("CONDITION_APPROVAL", "CONDITIONAL_APPROVAL"));
    }
}
