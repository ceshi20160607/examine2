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
            @NotBlank @Pattern(regexp = "SERVICE|WEBHOOK") String applicationType,
            @Pattern(regexp = "api|link|workflow|database|robot|message") String iconCode,
            @Min(1) @Max(3650) Integer credentialValidityDays) {
    }

    public record SaveDraftRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "SERVICE|WEBHOOK") String applicationType,
            @Pattern(regexp = "api|link|workflow|database|robot|message") String iconCode,
            @Valid List<CallbackInput> callbacks,
            @NotNull @Size(min = 1) @Valid List<GrantInput> grants,
            @Valid List<IdentityMappingInput> identityMappings) {
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

    /** 跨系统调用身份只提交结构化成员选项，系统、租户和显示名称均由服务端反查。 */
    public record IdentityMappingInput(
            Long id,
            @NotNull Long sourceTenantMemberId,
            @NotNull Long targetTenantMemberId,
            @NotNull Boolean enabled) {
    }

    public record PublishRequest(@NotNull Integer expectedDraftRevision, @NotBlank @Size(max = 500) String changeSummary) {
    }

    public record DisableRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record CopyRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @Min(1) @Max(3650) Integer credentialValidityDays) {
    }

    /** 新签发凭证的有效期；为空时使用平台默认值。 */
    public record CredentialIssueRequest(@Min(1) @Max(3650) Integer validityDays) {
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
            String iconCode,
            List<CallbackInput> callbacks,
            List<GrantInput> grants,
            LocalDateTime exportedAt) {
    }

    public record ImportRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "SERVICE|WEBHOOK") String applicationType,
            @Pattern(regexp = "api|link|workflow|database|robot|message") String iconCode,
            @Min(1) @Max(3650) Integer credentialValidityDays,
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
            LocalDateTime validFrom,
            LocalDateTime expiresAt,
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

    /** 流程设计器只读取可选择的发布摘要，不暴露地址和密钥引用。 */
    public record FlowCallbackOption(
            Long applicationId,
            String applicationCode,
            String applicationName,
            Long applicationVersionId,
            Integer applicationVersionNumber,
            Long callbackId,
            String callbackType,
            List<String> eventCodes,
            Integer timeoutMillis,
            Integer maxAttempts) {
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

    /** 应用页面使用的成员选项；页面展示业务名称，主键仅作为选择值传回。 */
    public record IdentityMemberOption(
            Long systemId,
            String systemName,
            Long tenantId,
            String tenantName,
            Long tenantMemberId,
            String memberName,
            String employeeNumber) {
    }

    public record IdentityCatalog(
            List<IdentityMemberOption> sourceMembers,
            List<IdentityMemberOption> targetMembers) {
    }

    public record IdentityMappingView(
            Long id,
            Long sourceSystemId,
            String sourceSystemName,
            Long sourceTenantId,
            String sourceTenantName,
            Long sourceTenantMemberId,
            String sourceMemberName,
            Long targetSystemId,
            String targetSystemName,
            Long targetTenantId,
            String targetTenantName,
            Long targetTenantMemberId,
            String targetMemberName,
            String status,
            Integer version) {
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
            Long resourceVersionId,
            Integer resourceVersionNumber,
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
            String iconCode,
            Integer draftRevision,
            String status,
            Integer version,
            Long currentVersionId,
            Integer publicationVersion,
            List<CallbackView> callbacks,
            List<GrantView> grants,
            List<GrantView> publishedGrants,
            List<IdentityMappingView> identityMappings,
            List<IdentityMappingView> publishedIdentityMappings,
            List<CredentialView> credentials,
            List<VersionView> versions,
            List<StatusEvent> statusHistory,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
