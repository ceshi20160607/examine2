package com.unique.unexamine.operations.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class OperationsSecurityModels {
    private OperationsSecurityModels() { }

    public record CreateSecretRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9._-]{2,99}") String secretCode,
            @NotBlank @Pattern(regexp = "APPLICATION|SSO|WEBHOOK|MODEL") String secretType,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> consumerCodes,
            @NotBlank @Size(max = 200) String approvalReference,
            @NotBlank String confirmation) { }

    public record PrepareRotationRequest(
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> consumerCodes,
            @NotBlank @Size(max = 200) String approvalReference,
            @NotBlank String confirmation) { }

    public record ConsumerCheck(
            @NotBlank @Size(max = 100) String consumerCode,
            @NotBlank @Pattern(regexp = "PASSED|FAILED") String status,
            @NotBlank @Size(max = 500) String evidenceReference) { }

    public record ActivateRotationRequest(
            @NotNull Long securityRunId,
            @NotEmpty @Size(max = 20) List<@Valid ConsumerCheck> consumerChecks,
            @NotBlank String confirmation) { }

    public record SecurityRunRequest(
            @NotBlank @Size(max = 200) String approvalReference,
            @NotBlank String confirmation) { }

    public record PerformanceRunRequest(
            @Min(1) @Max(200) int pageSize,
            @Min(1024) @Max(1048576) int fileProbeBytes,
            @Min(100) @Max(30000) int timeoutMillis,
            @Min(0) @Max(60000) long listThresholdMillis,
            @Min(0) @Max(60000) long fileThresholdMillis,
            @Min(0) @Max(60000) long queueThresholdMillis,
            @Min(0) @Max(60000) long statisticsThresholdMillis,
            @NotBlank @Size(max = 200) String approvalReference) { }

    public record SecretRefView(
            Long id, String secretCode, String secretType, String provider, String referencePath,
            String currentVersion, String status, String lastVerifiedAt, String updatedAt) { }

    public record RotationView(
            Long id, Long secretRefId, String secretCode, String fromVersion, String toVersion,
            String status, boolean switched, String activeVersion, List<String> consumerCodes,
            List<Map<String, Object>> consumerChecks, Long securityRunId, String oldVersionStatus,
            String failureCode, String requestedAt, String finishedAt) { }

    public record OneTimeSecretIssue(
            SecretRefView secretRef, RotationView rotation, String oneTimeSecret,
            boolean displayOnce, String notice) { }

    public record VerificationFindingView(
            Long id, String severity, String code, String title, String detail,
            Map<String, Object> evidence, String status, String createdAt) { }

    public record VerificationRunView(
            Long id, String verificationType, String scenarioCode, String status,
            Map<String, Object> input, Map<String, Object> result, Map<String, Object> threshold,
            String startedAt, String finishedAt, List<VerificationFindingView> findings) { }

    public record Overview(
            List<SecretRefView> secretRefs, List<RotationView> rotations,
            List<VerificationRunView> securityRuns, List<VerificationRunView> performanceRuns,
            String secretPolicy, String performancePolicy) { }
}
