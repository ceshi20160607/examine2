package com.unique.examine.flow.domain;

import java.util.Objects;
import java.util.List;

/**
 * One task projection for the actual actor and the immutable participant slot.
 */
public record ApprovalTaskAssignment(
        ApprovalInstance instance,
        long actorMemberId,
        List<RepresentedAuthority> representedAuthorities
) {
    public ApprovalTaskAssignment {
        Objects.requireNonNull(instance, "instance");
        if (actorMemberId <= 0) {
            throw new IllegalArgumentException("Task actor member id must be positive");
        }
        representedAuthorities = representedAuthorities == null
                ? List.of()
                : List.copyOf(representedAuthorities);
        if (representedAuthorities.isEmpty()
                || representedAuthorities.stream()
                        .map(RepresentedAuthority::representedMemberId)
                        .distinct()
                        .count() != representedAuthorities.size()) {
            throw new IllegalArgumentException(
                    "Task projection requires unique represented authorities"
            );
        }
        if (representedAuthorities.stream().anyMatch(authority ->
                (authority.representedMemberId() == actorMemberId)
                        != (authority.delegationRuleId() == null))) {
            throw new IllegalArgumentException(
                    "Direct and delegated task authorities are inconsistent"
            );
        }
    }

    public static ApprovalTaskAssignment direct(
            ApprovalInstance instance,
            long memberId
    ) {
        return new ApprovalTaskAssignment(
                instance,
                memberId,
                List.of(new RepresentedAuthority(memberId, null))
        );
    }

    public record RepresentedAuthority(
            long representedMemberId,
            Long delegationRuleId
    ) {
        public RepresentedAuthority {
            if (representedMemberId <= 0) {
                throw new IllegalArgumentException(
                        "Represented member id must be positive"
                );
            }
            if (delegationRuleId != null && delegationRuleId <= 0) {
                throw new IllegalArgumentException(
                        "Delegation rule id must be positive"
                );
            }
        }
    }
}
