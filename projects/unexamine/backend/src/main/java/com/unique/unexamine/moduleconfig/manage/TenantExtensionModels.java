package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.moduleconfig.base.entity.CfgTenantExtension;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 租户扩展配置契约；绑定标识由页面选择，并由业务服务验证归属。 */
public final class TenantExtensionModels {
    private TenantExtensionModels() {
    }

    public record ExtensionFieldInput(
            Long fieldId,
            @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,59}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank String fieldType,
            @NotNull Boolean required,
            @NotNull @Min(0) Integer sortOrder,
            Long dictionaryId,
            Long referenceModuleId,
            @NotNull JsonNode config) {
    }

    public record PageOverrideInput(
            @NotBlank @Pattern(regexp = "LIST|FORM|DETAIL|DASHBOARD|CUSTOM") String pageType,
            @NotEmpty List<@NotBlank String> fieldCodes) {
    }

    public record ApplicationBindingInput(
            @NotNull Long applicationId,
            @NotEmpty List<@NotNull Long> grantIds) {
    }

    /** 租户新增手动动作，参数引用有效模块的可写字段，不能覆盖主租户动作。 */
    public record ExtensionActionInput(
            Long actionId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,59}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "ROW|DETAIL_HEADER|BATCH") String location,
            @NotEmpty @Size(max = 20) List<@NotBlank String> parameterFieldCodes,
            @NotNull @Min(0) Integer sortOrder) {
    }

    public record SaveTenantExtensionRequest(
            @NotNull List<@Valid ExtensionFieldInput> fields,
            @NotNull List<@Valid PageOverrideInput> pages,
            @NotNull List<@Valid ApplicationBindingInput> applicationBindings,
            List<@Valid ExtensionActionInput> actions,
            @NotNull @Min(0) Integer expectedVersion) {
    }

    public record TenantExtensionIssue(String path, String code, String message) {
    }

    public record TenantExtensionCheck(
            boolean valid,
            int draftRevision,
            Long baseVersionId,
            List<TenantExtensionIssue> issues,
            JsonNode mergedPreview) {
    }

    public record TenantExtensionVersionView(
            Long versionId,
            Integer versionNumber,
            Long baseModuleVersionId,
            LocalDateTime publishedAt,
            boolean current) {
    }

    public record ApplicationGrantOption(
            Long applicationId,
            String applicationCode,
            String applicationName,
            Long grantId,
            String actionCode,
            List<String> fieldCodes) {
    }

    public record TenantExtensionModuleView(
            Long moduleId,
            String moduleCode,
            String moduleName,
            Long baseTenantId,
            String baseTenantName,
            Long baseVersionId,
            Integer baseVersionNumber,
            CfgTenantExtension extension,
            JsonNode draft,
            JsonNode mergedPreview,
            List<ApplicationGrantOption> availableApplicationGrants,
            List<TenantExtensionVersionView> versions) {
    }

    public record PublishTenantExtensionRequest(@NotNull @Min(1) Integer expectedDraftRevision) {
    }

    public record RollbackTenantExtensionRequest(
            @NotNull Long targetVersionId,
            @NotNull @Min(0) Integer expectedVersion) {
    }

    public record RemoveTenantExtensionRequest(@NotNull @Min(0) Integer expectedVersion) {
    }
}
