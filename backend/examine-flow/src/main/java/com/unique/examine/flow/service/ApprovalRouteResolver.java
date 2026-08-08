package com.unique.examine.flow.service;

import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalMode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ApprovalRouteResolver {
    private final TriggerConditionMatcher conditions;

    public ApprovalRouteResolver(TriggerConditionMatcher conditions) {
        this.conditions = Objects.requireNonNull(conditions, "conditions");
    }

    public ResolvedRoute resolve(
            ApprovalGateway gateway,
            List<Long> defaultApproverIds,
            Map<String, String> valuesJson
    ) {
        return resolve(
                gateway,
                defaultApproverIds,
                ApprovalMode.SEQUENTIAL,
                valuesJson
        );
    }

    public ResolvedRoute resolve(
            ApprovalGateway gateway,
            List<Long> defaultApproverIds,
            ApprovalMode defaultApprovalMode,
            Map<String, String> valuesJson
    ) {
        Objects.requireNonNull(defaultApproverIds, "defaultApproverIds");
        Objects.requireNonNull(valuesJson, "valuesJson");
        if (gateway == null) {
            return new ResolvedRoute(
                    null,
                    null,
                    true,
                    List.copyOf(defaultApproverIds),
                    defaultApprovalMode
            );
        }
        for (var branch : gateway.conditionalBranches()) {
            if (conditions.matches(branch.conditions(), valuesJson)) {
                return ResolvedRoute.from(branch);
            }
        }
        return ResolvedRoute.from(gateway.defaultBranch());
    }

    public record ResolvedRoute(
            String branchCode,
            String branchName,
            boolean defaultBranch,
            List<Long> approverIds,
            ApprovalMode approvalMode
    ) {
        public ResolvedRoute {
            approverIds = ApprovalDefinitionDraftAccess.requireApprovers(approverIds);
            approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        }

        public ResolvedRoute(
                String branchCode,
                String branchName,
                boolean defaultBranch,
                List<Long> approverIds
        ) {
            this(
                    branchCode,
                    branchName,
                    defaultBranch,
                    approverIds,
                    ApprovalMode.SEQUENTIAL
            );
        }

        private static ResolvedRoute from(ApprovalGateway.Branch value) {
            return new ResolvedRoute(
                    value.code(),
                    value.name(),
                    value.defaultBranch(),
                    value.approverIds(),
                    value.approvalMode()
            );
        }
    }

    /**
     * Keeps route validation in the domain package while avoiding duplication here.
     */
    private static final class ApprovalDefinitionDraftAccess {
        private static List<Long> requireApprovers(List<Long> values) {
            if (values == null
                    || values.isEmpty()
                    || values.size() > 10
                    || values.stream().anyMatch(value -> value == null || value <= 0)) {
                throw new IllegalArgumentException("Resolved approval route is invalid");
            }
            return List.copyOf(values);
        }
    }
}
