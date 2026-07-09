package com.unique.unexamine.core.module;

public record ModuleField(
        String code,
        String label,
        String type,
        boolean required
) {
}