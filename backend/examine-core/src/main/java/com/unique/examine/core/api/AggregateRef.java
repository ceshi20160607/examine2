package com.unique.examine.core.api;

/**
 * Stable aggregate identity shared by audit and outbox contracts.
 * The id may be absent for audit records that intentionally do not disclose a target.
 */
public record AggregateRef(String type, String id) {
    public AggregateRef {
        type = required(type, "aggregate type", 64);
        id = optional(id, "aggregate id", 64);
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    private static String optional(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }
}
