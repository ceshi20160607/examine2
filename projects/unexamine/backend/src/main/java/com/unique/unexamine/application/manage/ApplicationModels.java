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

    /** 导出时间随配置包返回，用于区分多次导出的同一草稿。 */
    public record ConfigurationExport(
            int schemaVersion,
            String sourceCode,
            String sourceName,
            String description,
            String applicationType,
            String iconCode,
            List<CallbackInput> callbacks,
            List<GrantInput> grants,
            LocalDateTime exportTime) {
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

    /** 密钥明文只在签发响应中出现；有效起止时间对应凭证的持久化字段。 */
    public record CredentialSecret(
            Long credentialId,
            Integer credentialVersion,
            String clientId,
            String clientSecret,
            String secretReference,
            String secretHint,
            LocalDateTime validStartTime,
            LocalDateTime expireTime,
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

    /** 应用负责人范围候选项；系统应用使用系统成员，平台应用使用平台成员。 */
    public record ScopeMemberOption(Long id, String name, String status) {
    }

    /** 应用部门范围候选项；保存时传真实部门主键。 */
    public record ScopeDepartmentOption(Long id, String name, String status) {
    }

    public record ScopeCatalog(List<ScopeMemberOption> members, List<ScopeDepartmentOption> departments) {
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

    /** 凭证列表仅展示引用和状态，不返回密钥明文。 */
    public record CredentialView(
            Long id,
            Integer credentialVersion,
            String clientId,
            String secretReference,
            String secretHint,
            LocalDateTime validStartTime,
            LocalDateTime expireTime,
            String status,
            LocalDateTime revokeTime,
            LocalDateTime createTime) {
    }

    /** 已发布版本的时间用于回滚选择和追溯。 */
    public record VersionView(
            Long id,
            Integer versionNumber,
            Integer draftRevision,
            String snapshotHash,
            Long publishedByAccountId,
            LocalDateTime publishTime,
            boolean current) {
    }

    /** 应用状态历史读取真实审计发生时间。 */
    public record StatusEvent(
            Long id,
            String eventCode,
            String resultCode,
            Long actorAccountId,
            LocalDateTime occurTime,
            Map<String, Object> detail) {
    }

    /** 应用详情同时呈现当前草稿、已发布授权和凭证摘要。 */
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
            List<CredentialView> credentials,
            List<VersionView> versions,
            List<StatusEvent> statusHistory,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
    }
}
