package com.unique.examine.flow.service;

import com.unique.examine.flow.domain.ApprovalInclusiveGateway;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ApprovalInclusiveRouteResolver {
    private final TriggerConditionMatcher conditions;

    public ApprovalInclusiveRouteResolver(TriggerConditionMatcher conditions) {
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    public List<ApprovalInclusiveGateway.Branch> resolve(
            ApprovalInclusiveGateway gateway,
            Map<String, String> valuesJson
    ) {
        Objects.requireNonNull(gateway, "gateway");
        Objects.requireNonNull(valuesJson, "valuesJson");
        var selected = gateway.conditionalBranches().stream()
                .filter(branch -> conditions.matches(branch.conditions(), valuesJson))
                .toList();
        if (!selected.isEmpty()) {
            return selected;
        }
        return gateway.defaultBranch().map(List::of).orElseGet(List::of);
    }
}
