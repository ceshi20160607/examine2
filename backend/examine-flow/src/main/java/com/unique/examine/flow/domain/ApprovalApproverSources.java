package com.unique.examine.flow.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.GATEWAY_INVALID;

/**
 * Source selectors for the ordinary route and, when present, every gateway
 * branch. Existing fixed-member definitions normalize to FIXED selectors.
 */
public record ApprovalApproverSources(
        ApprovalApproverSource route,
        Map<String, ApprovalApproverSource> branches
) {
    public ApprovalApproverSources {
        route = route == null ? ApprovalApproverSource.fixed() : route;
        branches = branches == null ? Map.of() : canonicalBranches(branches);
    }

    public static ApprovalApproverSources fixedFor(
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        var branches = new LinkedHashMap<String, ApprovalApproverSource>();
        if (gateway != null) {
            gateway.branches().forEach(branch ->
                    branches.put(branch.code(), ApprovalApproverSource.fixed()));
        } else if (parallelGateway != null) {
            parallelGateway.branches().forEach(branch ->
                    branches.put(branch.code(), ApprovalApproverSource.fixed()));
        } else if (inclusiveGateway != null) {
            inclusiveGateway.branches().forEach(branch ->
                    branches.put(branch.code(), ApprovalApproverSource.fixed()));
        }
        return new ApprovalApproverSources(ApprovalApproverSource.fixed(), branches);
    }

    public ApprovalApproverSource branch(String code) {
        var source = branches.get(code);
        if (source == null) {
            throw invalid("Approver source is missing for gateway branch " + code);
        }
        return source;
    }

    public ApprovalApproverSources requireShape(
            ApprovalGateway gateway,
            ApprovalParallelGateway parallelGateway,
            ApprovalInclusiveGateway inclusiveGateway
    ) {
        var expectedCodes = branchCodes(gateway, parallelGateway, inclusiveGateway);
        if (!branches.keySet().equals(expectedCodes)) {
            throw invalid("Approver sources must exactly match gateway branch codes");
        }
        String mirroredCode = null;
        if (gateway != null) {
            mirroredCode = gateway.defaultBranch().code();
        } else if (parallelGateway != null) {
            mirroredCode = parallelGateway.branches().getFirst().code();
        } else if (inclusiveGateway != null) {
            mirroredCode = inclusiveGateway.branches().getFirst().code();
        }
        if (mirroredCode != null && !route.equals(branches.get(mirroredCode))) {
            throw invalid("Definition approver source must mirror its primary gateway branch");
        }
        return this;
    }

    private static Map<String, ApprovalApproverSource> canonicalBranches(
            Map<String, ApprovalApproverSource> values
    ) {
        var result = new LinkedHashMap<String, ApprovalApproverSource>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    var code = entry.getKey();
                    if (code == null || code.isBlank() || entry.getValue() == null) {
                        throw new IllegalArgumentException(
                                "Approver source branch keys and values must not be blank");
                    }
                    result.put(code, entry.getValue());
                });
        return Map.copyOf(result);
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

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(GATEWAY_INVALID, message);
    }
}
