package com.unique.examine.module.systemfield;

import java.util.Objects;

public record SystemFieldDefinition(
        long fieldId,
        String fieldCode,
        Type type,
        String autoNumberPrefix,
        int autoNumberDigits
) {
    public SystemFieldDefinition {
        if (fieldId <= 0) {
            throw new IllegalArgumentException("fieldId must be positive");
        }
        fieldCode = Objects.requireNonNull(fieldCode, "fieldCode").trim();
        if (fieldCode.isEmpty()) {
            throw new IllegalArgumentException("fieldCode is required");
        }
        Objects.requireNonNull(type, "type");
        autoNumberPrefix = autoNumberPrefix == null ? "" : autoNumberPrefix;
        if (type == Type.AUTO_NUMBER && (autoNumberDigits < 1 || autoNumberDigits > 32)) {
            throw new IllegalArgumentException("autoNumberDigits must be within 1..32");
        }
    }

    public enum Type {
        TENANT,
        AUTO_NUMBER,
        CREATED_BY,
        CREATED_AT,
        UPDATED_BY,
        UPDATED_AT
    }
}
