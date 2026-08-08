package com.unique.examine.flow.domain;

import java.util.Objects;

/**
 * Definition-time quorum rule. Runtime execution stores only the computed
 * required approval count so later rule or membership changes are harmless.
 */
public record ApprovalQuorumRule(Type type, int value) {
    public ApprovalQuorumRule {
        Objects.requireNonNull(type, "type");
        if (value < 1 || (type == Type.PERCENTAGE && value > 100)) {
            throw new IllegalArgumentException(
                    "Quorum count must be positive and percentage must be within 1..100"
            );
        }
    }

    public int requiredApprovals(int memberCount) {
        if (memberCount < 1) {
            throw new IllegalArgumentException("Quorum route must contain active members");
        }
        if (type == Type.COUNT) {
            if (value > memberCount) {
                throw new IllegalArgumentException(
                        "Quorum count cannot exceed the resolved member count"
                );
            }
            return value;
        }
        return Math.toIntExact(
                Math.floorDiv(Math.multiplyExact((long) memberCount, value) + 99L, 100L)
        );
    }

    public enum Type {
        COUNT,
        PERCENTAGE
    }
}
