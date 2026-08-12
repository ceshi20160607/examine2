package com.unique.examine.plat.manage.settings;

import java.time.LocalDateTime;

public final class PlatformGlobalSettingsModels {
    private PlatformGlobalSettingsModels() {
    }

    public record Profile(String name, String description) { }
    public record StoragePolicy(String defaultMode, long maximumUploadBytes, int retentionDays) { }
    public record SecurityPolicy(int sessionIdleMinutes, int passwordMinimumLength, boolean requireMfaForAdmins) { }
    public record QuotaPolicy(int defaultMemberLimit, int defaultModuleLimit, long defaultStorageBytes) { }
    public record BackupPolicy(boolean enabled, int retentionDays, int intervalHours) { }
    public record ReleasePolicy(boolean maintenanceMode, String channel, boolean approvalRequired) { }

    public record Settings(
            Profile profile,
            StoragePolicy storage,
            SecurityPolicy security,
            QuotaPolicy quota,
            BackupPolicy backup,
            ReleasePolicy release,
            String version,
            LocalDateTime updatedAt
    ) { }

    public record Update(
            Profile profile,
            StoragePolicy storage,
            SecurityPolicy security,
            QuotaPolicy quota,
            BackupPolicy backup,
            ReleasePolicy release,
            String expectedVersion
    ) { }
}
