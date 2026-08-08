package com.unique.examine.flow.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decision-comment policies for the primary route and gateway branches.
 */
public record ApprovalDecisionCommentPolicies(
        ApprovalDecisionCommentPolicy primary,
        Map<String, ApprovalDecisionCommentPolicy> branches
) {
    public ApprovalDecisionCommentPolicies {
        primary = primary == null ? ApprovalDecisionCommentPolicy.defaults() : primary;
        branches = canonicalBranches(branches);
    }

    public static ApprovalDecisionCommentPolicies none() {
        return new ApprovalDecisionCommentPolicies(
                ApprovalDecisionCommentPolicy.defaults(),
                Map.of()
        );
    }

    public ApprovalDecisionCommentPolicies requireShape(
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        var expectedCodes = branchCodes(gateway, parallelGateway, inclusiveGateway);
        if (!expectedCodes.containsAll(branches.keySet())) {
            throw new IllegalArgumentException(
                    "Decision comment policies contain a branch outside the configured gateway"
            );
        }
        String mirroredCode = null;
        if (gateway != null) {
            mirroredCode = gateway.defaultBranch().code();
        } else if (parallelGateway != null) {
            mirroredCode = parallelGateway.branches().getFirst().code();
        } else if (inclusiveGateway != null) {
            mirroredCode = inclusiveGateway.branches().getFirst().code();
        }
        if (mirroredCode == null) {
            if (!branches.isEmpty()) {
                throw new IllegalArgumentException(
                        "Ordinary approval routes cannot contain branch decision comment policies"
                );
            }
        } else if (!Objects.equals(primary, branch(mirroredCode))) {
            throw new IllegalArgumentException(
                    "Definition decision comment policy must mirror its primary gateway branch"
            );
        }
        return this;
    }

    public ApprovalDecisionCommentPolicy branch(String code) {
        return branches.getOrDefault(code, ApprovalDecisionCommentPolicy.defaults());
    }

    private static Map<String, ApprovalDecisionCommentPolicy> canonicalBranches(
            Map<String, ApprovalDecisionCommentPolicy> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, ApprovalDecisionCommentPolicy>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    var code = entry.getKey();
                    if (code == null
                            || !code.matches("^[a-z][a-z0-9_]{0,63}$")
                            || entry.getValue() == null) {
                        throw new IllegalArgumentException(
                                "Decision comment branch policies require canonical codes and values"
                        );
                    }
                    result.put(code, entry.getValue());
                });
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> branchCodes(
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        if (gateway != null) {
            return gateway.branches().stream()
                    .map(ApprovalGateway.Branch::code)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (parallelGateway != null) {
            return parallelGateway.branches().stream()
                    .map(ApprovalParallelGateway.Branch::code)
                    .collect(Collectors.toUnmodifiableSet());
        }
        if (inclusiveGateway != null) {
            return inclusiveGateway.branches().stream()
                    .map(ApprovalInclusiveGateway.Branch::code)
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }
}
