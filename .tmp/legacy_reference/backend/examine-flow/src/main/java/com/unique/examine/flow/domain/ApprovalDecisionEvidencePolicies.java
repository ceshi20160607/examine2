package com.unique.examine.flow.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Nullable route policy plus sparse gateway-branch policies.
 */
public record ApprovalDecisionEvidencePolicies(
        ApprovalDecisionEvidencePolicy primary,
        Map<String, ApprovalDecisionEvidencePolicy> branches
) {
    public ApprovalDecisionEvidencePolicies {
        branches = canonicalBranches(branches);
    }

    public static ApprovalDecisionEvidencePolicies none() {
        return new ApprovalDecisionEvidencePolicies(null, Map.of());
    }

    public boolean isEmpty() {
        return primary == null && branches.isEmpty();
    }

    public ApprovalDecisionEvidencePolicy branch(String code) {
        return branches.get(code);
    }

    public ApprovalDecisionEvidencePolicies requireShape(
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        var expectedCodes = branchCodes(
                gateway, parallelGateway, inclusiveGateway);
        if (!expectedCodes.containsAll(branches.keySet())) {
            throw new IllegalArgumentException(
                    "Decision evidence policies contain an unknown gateway branch");
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
                        "Ordinary routes cannot contain branch evidence policies");
            }
        } else if (!Objects.equals(primary, branches.get(mirroredCode))) {
            throw new IllegalArgumentException(
                    "Primary evidence policy must mirror the primary gateway branch");
        }
        return this;
    }

    private static Map<String, ApprovalDecisionEvidencePolicy> canonicalBranches(
            Map<String, ApprovalDecisionEvidencePolicy> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, ApprovalDecisionEvidencePolicy>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    var code = entry.getKey();
                    if (code == null
                            || !code.matches("^[a-z][a-z0-9_]{0,63}$")
                            || entry.getValue() == null) {
                        throw new IllegalArgumentException(
                                "Branch evidence policies require canonical codes and values");
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
