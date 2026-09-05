package com.unique.unexamine.application.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ApplicationModels {
    private ApplicationModels() {
    }

    public record CreateRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "SERVICE|WEBHOOK") String applicationType) {
    }

    public record SaveDraftRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "SERVICE|WEBHOOK") String applicationType,
            @Valid List<CallbackInput> callbacks,
            @NotNull @Size(min = 1) @Valid List<GrantInput> grants) {
    }

    public record CallbackInput(
            @NotBlank @Pattern(regexp = "EVENT|RESULT") String callbackType,
            @NotBlank @Size(max = 1000) String url,
            @NotNull @Size(min = 1) List<@NotBlank @Size(max = 100) String> eventCodes,
            @NotBlank @Size(max = 500) String signingSecretRef,
            @NotNull @Min(100) @Max(60000) Integer timeoutMillis,
            @NotNull @Min(1) @Max(10) Integer maxAttempts) {
    }

    public record GrantInput(
            @NotBlank @Pattern(regexp = "PLATFORM|SYSTEM") String targetType,
            Long targetSystemId,
            Long targetTenantId,
            @NotBlank @Size(max = 64) String resourceType,
            @NotBlank @Size(max = 100) String resourceId,
            @NotBlank @Size(max = 64) String actionCode,
            @NotNull Map<String, Object> dataScope,
            @NotNull Map<String, Object> rateLimit,
            @Valid List<FieldInput> fields) {
    }

    public record FieldInput(
            @NotBlank @Size(max = 100) String fieldCode,
            @NotNull Boolean readable,
            @NotNull Boolean writable,
            @Size(max = 64) String maskStrategy) {
    }

    public record PublishRequest(@NotNull Integer expectedDraftRevision, @NotBlank @Size(max = 500) String changeSummary) {
    }

    public record DisableRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record CopyRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name) {
    }

    public record RollbackRequest(
            @NotNull Long targetVersionId,
            @NotNull Integer expectedPublicationVersion,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record DeleteRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ConfigurationExport(
            int schemaVersion,
            String sourceCode,
            String sourceName,
            String description,
            String applicationType,
            List<CallbackInput> callbacks,
            List<GrantInput> grants,
            LocalDateTime exportedAt) {
    }

    public record ImportRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "SERVICE|WEBHOOK") String applicationType,
            @Valid List<CallbackInput> callbacks,
            @NotNull @Size(min = 1) @Valid List<GrantInput> grants) {
    }

    public record Issue(String code, String message) {
    }

    public record PublicationCheck(Long applicationId, Integer draftRevision, boolean valid, List<Issue> issues) {
    }

    public record CredentialSecret(
            Long credentialId,
            Integer credentialVersion,
            String clientId,
            String clientSecret,
            String secretReference,
            String secretHint,
            boolean shownOnce) {
    }

    public record CreateResult(ApplicationView application, CredentialSecret issuedCredential) {
    }

    public record RotateResult(ApplicationView application, CredentialSecret issuedCredential) {
    }

    public record PublishResult(
            ApplicationView application,
            Long versionId,
            Integer versionNumber,
            String snapshotHash,
            Long previousVersionId) {
    }

    public record CallbackView(
            Long id,
            String callbackType,
            String url,
            List<String> eventCodes,
            String signingSecretRef,
            Integer timeoutMillis,
            Integer maxAttempts,
            String status,
            Integer version) {
    }

    public record FieldView(Long id, String fieldCode, boolean readable, boolean writable, String maskStrategy) {
    }

    public record ResourceField(String code, String name, String fieldType, boolean required) {
    }

    public record ResourceAction(String code, String name) {
    }

    public record ResourceOption(
            String resourceType,
            String resourceId,
            String name,
            String description,
            Long publishedVersionId,
            Integer publishedVersionNumber,
            List<ResourceAction> actions,
            List<ResourceField> fields) {
    }

    public record GrantView(
            Long id,
            String targetType,
            Long targetSystemId,
            Long targetTenantId,
            String resourceType,
            String resourceId,
            String actionCode,
            Map<String, Object> dataScope,
            Map<String, Object> rateLimit,
            String status,
            Integer version,
            List<FieldView> fields) {
    }

    public record CredentialView(
            Long id,
            Integer credentialVersion,
            String clientId,
            String secretReference,
            String secretHint,
            LocalDateTime validFrom,
            LocalDateTime expiresAt,
            String status,
            LocalDateTime revokedAt,
            LocalDateTime createdAt) {
    }

    public record VersionView(
            Long id,
            Integer versionNumber,
            Integer draftRevision,
            String snapshotHash,
            Long publishedByAccountId,
            LocalDateTime publishedAt,
            boolean current) {
    }

    public record StatusEvent(
            Long id,
            String eventCode,
            String resultCode,
            Long actorAccountId,
            LocalDateTime occurredAt,
            Map<String, Object> detail) {
    }

    public record ApplicationView(
            Long id,
            String contextType,
            Long platformId,
            Long ownerSystemId,
            Long ownerTenantId,
            String code,
            String name,
            String description,
            String applicationType,
            Integer draftRevision,
            String status,
            Integer version,
            Long currentVersionId,
            Integer publicationVersion,
            List<CallbackView> callbacks,
            List<GrantView> grants,
            List<GrantView> publishedGrants,
            List<CredentialView> credentials,
            List<VersionView> versions,
            List<StatusEvent> statusHistory,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
