package com.unique.unexamine.operations.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class OperationsDeploymentModels {
    private OperationsDeploymentModels() { }

    public record RuntimeManifest(
            String environmentCode,
            String backendVersion,
            String frontendVersion,
            String databaseVersion,
            String configVersion,
            String artifactSha256,
            String frontendSmokeUrl,
            boolean artifactDigestConfigured,
            boolean ready) { }

    public record ReleaseRequest(
            @NotBlank @Size(max = 100) String versionName,
            @NotBlank @Pattern(regexp = "[A-Fa-f0-9]{64}") String artifactSha256,
            @NotBlank @Size(max = 100) String databaseVersion,
            @NotBlank @Size(max = 100) String configVersion,
            @NotBlank @Size(max = 100) String frontendVersion,
            @NotBlank @Pattern(regexp = "FORWARD_FIX|REVERSIBLE") String databaseRollbackStrategy,
            @NotBlank @Size(max = 2000) String releaseNotes) { }

    public record DeployRequest(
            @NotNull Long releaseId,
            @NotBlank @Size(max = 100) String environmentCode,
            @NotBlank @Size(max = 100) String expectedBackendVersion,
            @NotBlank @Size(max = 100) String expectedFrontendVersion,
            @NotBlank @Pattern(regexp = "FORWARD_FIX|REVERSIBLE|UNRECOVERABLE") String databaseStrategy,
            @NotBlank @Size(max = 200) String approvalReference,
            @NotBlank @Size(max = 300) String confirmation) { }

    public record RollbackRequest(
            @NotNull Long targetReleaseId,
            @NotBlank @Pattern(regexp = "FORWARD_FIX|REVERSIBLE|UNRECOVERABLE") String databaseStrategy,
            @NotBlank @Size(max = 200) String approvalReference,
            @NotBlank @Size(max = 300) String confirmation) { }

    public record ReleaseView(
            Long id,
            String versionName,
            String artifactSha256,
            String databaseVersion,
            String configVersion,
            String frontendVersion,
            String databaseRollbackStrategy,
            String releaseNotes,
            String status,
            Long createdByAccountId,
            String createdAt) { }

    public record DeploymentStep(
            String code,
            String name,
            String version,
            long durationMillis,
            String status,
            String health,
            String realEntrySmoke,
            String evidence) { }

    public record ApprovalView(
            String reference,
            Long approvedByAccountId,
            String approvedAt,
            String confirmation) { }

    public record DeploymentView(
            Long id,
            Long releaseId,
            Long fromReleaseId,
            String releaseVersion,
            String environmentCode,
            String deploymentType,
            String status,
            String failureCode,
            String failureMessage,
            ApprovalView approval,
            List<DeploymentStep> steps,
            Map<String, Object> rollbackPoint,
            Long requestedByAccountId,
            String startedAt,
            String finishedAt,
            String createdAt) { }

    public record Overview(
            RuntimeManifest runtime,
            String deployConfirmation,
            List<ReleaseView> releases,
            List<DeploymentView> deployments) { }
}
