package com.unique.examine.flow.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable quorum rules for the definition route and each gateway branch.
 * Only routes using {@link ApprovalMode#QUORUM} own a rule.
 */
public record ApprovalQuorumRules(
        ApprovalQuorumRule primary,
        Map<String, ApprovalQuorumRule> branches
) {
    public ApprovalQuorumRules {
        branches = canonicalBranches(branches);
    }

    public static ApprovalQuorumRules none() {
        return new ApprovalQuorumRules(null, Map.of());
    }

    public ApprovalQuorumRules requireShape(
            ApprovalMode primaryMode,
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        requireRule(primaryMode, primary, "definition route");
        var expected = new LinkedHashMap<String, ApprovalQuorumRule>();
        if (gateway != null) {
            gateway.branches().forEach(branch ->
                    collect(expected, branch.code(), branch.approvalMode()));
            requirePrimaryMirror(gateway.defaultBranch().code());
        } else if (parallelGateway != null) {
            parallelGateway.branches().forEach(branch ->
                    collect(expected, branch.code(), branch.approvalMode()));
            requirePrimaryMirror(parallelGateway.branches().getFirst().code());
        } else if (inclusiveGateway != null) {
            inclusiveGateway.branches().forEach(branch ->
                    collect(expected, branch.code(), branch.approvalMode()));
            requirePrimaryMirror(inclusiveGateway.branches().getFirst().code());
        } else if (!branches.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ordinary approval routes cannot contain branch quorum rules"
            );
        }
        if (!expected.keySet().equals(branches.keySet())) {
            throw new IllegalArgumentException(
                    "Quorum branch rules must exactly match QUORUM gateway routes"
            );
        }
        return this;
    }

    public ApprovalQuorumRule branch(String code) {
        return branches.get(code);
    }

    public int requiredApprovals(ApprovalMode mode, int memberCount) {
        return requiredApprovals(mode, primary, memberCount);
    }

    public int requiredApprovals(String code, ApprovalMode mode, int memberCount) {
        return requiredApprovals(mode, branches.get(code), memberCount);
    }

    public static int requiredApprovals(
            ApprovalMode mode,
            ApprovalQuorumRule rule,
            int memberCount
    ) {
        Objects.requireNonNull(mode, "mode");
        if (memberCount < 1) {
            throw new IllegalArgumentException("Approval route must contain members");
        }
        return switch (mode) {
            case SEQUENTIAL, ALL -> memberCount;
            case ANY -> 1;
            case QUORUM -> Objects.requireNonNull(
                    rule,
                    "QUORUM approval requires a rule"
            ).requiredApprovals(memberCount);
        };
    }

    private void collect(
            Map<String, ApprovalQuorumRule> expected,
            String code,
            ApprovalMode mode
    ) {
        var rule = branches.get(code);
        requireRule(mode, rule, "gateway branch " + code);
        if (mode == ApprovalMode.QUORUM) {
            expected.put(code, rule);
        }
    }

    private void requirePrimaryMirror(String code) {
        var branchRule = branches.get(code);
        if (!Objects.equals(primary, branchRule)) {
            throw new IllegalArgumentException(
                    "Definition quorum rule must mirror the primary gateway route"
            );
        }
    }

    private static void requireRule(
            ApprovalMode mode,
            ApprovalQuorumRule rule,
            String route
    ) {
        if ((mode == ApprovalMode.QUORUM) != (rule != null)) {
            throw new IllegalArgumentException(
                    route + " must own a quorum rule exactly when its mode is QUORUM"
            );
        }
    }

    private static Map<String, ApprovalQuorumRule> canonicalBranches(
            Map<String, ApprovalQuorumRule> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, ApprovalQuorumRule>();
        var codes = new LinkedHashSet<String>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    var code = entry.getKey();
                    if (code == null
                            || !code.matches("^[a-z][a-z0-9_]{0,63}$")
                            || entry.getValue() == null
                            || !codes.add(code)) {
                        throw new IllegalArgumentException(
                                "Quorum branch rules require unique canonical branch codes"
                        );
                    }
                    result.put(code, entry.getValue());
                });
        return Collections.unmodifiableMap(result);
    }
}
