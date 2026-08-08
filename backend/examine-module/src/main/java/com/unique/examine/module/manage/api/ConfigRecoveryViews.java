package com.unique.examine.module.manage.api;

public final class ConfigRecoveryViews {
    private ConfigRecoveryViews() {
    }

    public record RestoreResult(
            String owner,
            String resourceId,
            int sourceVersionNumber,
            String draftVersion,
            String activeVersionReference,
            String state
    ) {
    }
}
