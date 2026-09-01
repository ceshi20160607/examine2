package com.unique.unexamine.operations.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

public final class OperationsContinuityModels {
    private OperationsContinuityModels() { }

    public record BackupArtifactRequest(
            @NotBlank String itemType,
            @NotBlank String storageUri,
            @NotNull @Min(1) Long sizeBytes,
            @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sha256,
            @NotBlank String verificationStatus) { }

    public record CreateBackupRequest(
            @NotNull Long sourceReleaseId,
            @NotBlank String backupType,
            @NotBlank String consistencyPoint,
            @NotBlank String encryptionKeyReference,
            @NotBlank String confirmation,
            @Min(1) @Max(3650) int retentionDays,
            @Min(1) long requiredBytes,
            @Min(0) long availableBytes,
            @NotEmpty List<@Valid BackupArtifactRequest> items) { }

    public record RestoreArtifactEvidence(
            @NotBlank String itemType,
            @NotNull @Min(1) Long sizeBytes,
            @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$") String sha256,
            @NotBlank String verificationStatus) { }

    public record RestoreVerificationEvidence(
            @NotNull Long backupId,
            @NotBlank String environment,
            @NotBlank String status,
            @NotBlank String databaseRestore,
            @NotBlank String fileObjects,
            @NotBlank String configurationVersion,
            @NotBlank String secretReferences,
            @NotBlank String verifiedAt,
            @NotEmpty List<@Valid RestoreArtifactEvidence> items) { }

    public record RestoreDrillRequest(
            @NotBlank String environmentCode,
            @NotBlank String confirmation,
            @NotNull @Valid RestoreVerificationEvidence verification) { }

    public record UpgradeRequest(
            @NotNull Long releaseId,
            @NotNull Long backupId,
            @NotBlank String sourceDatabaseVersion,
            @NotBlank String targetDatabaseVersion,
            @NotBlank String sourceConfigVersion,
            @NotBlank String targetConfigVersion,
            @NotBlank String approvalReference,
            @NotBlank String confirmation,
            @NotNull Map<String, String> configurationMapping) { }

    public record BackupItemView(Long id, String itemType, String storageUri, long sizeBytes,
                                 String sha256, String status) { }

    public record BackupView(Long id, Long sourceReleaseId, Long systemId, String backupType, String status,
                             String consistencyPoint, String manifestHash, String encryptionKeyReference,
                             String secretMaterialStrategy, String retentionUntil, Long requestedByAccountId,
                             String startedAt, String finishedAt, String createdAt, List<BackupItemView> items) { }

    public record RestoreDrillView(Long id, Long backupId, String environmentCode, String status,
                                   Map<String, Object> verification, Long requestedByAccountId,
                                   String startedAt, String finishedAt, String createdAt) { }

    public record GateCheck(String code, String name, String status, String evidence) { }

    public record UpgradePreflight(boolean ready, String sourceDatabaseVersion, String targetDatabaseVersion,
                                   String sourceConfigVersion, String targetConfigVersion,
                                   List<GateCheck> checks) { }

    public record UpgradeStepView(int stepNumber, String stepType, String sourceVersion, String targetVersion,
                                  Map<String, Object> mapping, String status, String startedAt,
                                  String finishedAt, String errorMessage) { }

    public record UpgradeView(Long id, Long releaseId, Long backupId, Long systemId, String status,
                              Map<String, Object> impactReport, Map<String, Object> rollbackPoint,
                              Long requestedByAccountId, String startedAt, String finishedAt,
                              String createdAt, List<UpgradeStepView> steps) { }

    public record BackupManifest(BackupView backup, List<RestoreDrillView> restoreDrills,
                                 String downloadNotice) { }

    public record Overview(String runtimeDatabaseVersion, String runtimeConfigVersion,
                           String backupConfirmation, String restoreConfirmation,
                           String upgradeConfirmation, List<BackupView> backups,
                           List<RestoreDrillView> restoreDrills, List<UpgradeView> upgrades) { }
}
