package com.unique.unexamine.ai.manage;

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

public final class AiConfigurationModels {
    private AiConfigurationModels() {
    }

    public record ModelRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 64) String provider,
            @NotBlank @Size(max = 200) String modelName,
            @Size(max = 1000) String endpointUrl,
            @NotBlank @Size(max = 255) String credentialRef,
            @NotNull @Size(min = 1, max = 8) List<@NotBlank String> capabilities,
            @NotNull @Min(1) Long dailyTokenLimit,
            @NotNull @Min(1) @Max(100) Integer concurrencyLimit,
            @NotNull Boolean logMasking,
            @NotBlank @Pattern(regexp = "DOMESTIC_ONLY|LOCAL_ONLY|ALLOW_EXTERNAL") String dataResidency,
            Integer expectedVersion) {
    }

    public record GrantRequest(
            @NotNull @Min(1) Long dailyTokenLimit,
            @NotNull @Min(1) @Max(100) Integer concurrencyLimit,
            Integer expectedVersion) {
    }

    public record SystemOption(Long id, String code, String name) {
    }

    public record GrantView(
            Long id,
            Long modelId,
            Long systemId,
            String systemCode,
            String systemName,
            Map<String, Object> usageLimit,
            String status,
            Integer version,
            LocalDateTime grantedAt) {
    }

    public record ModelView(
            Long id,
            Long activeGrantId,
            String code,
            String name,
            String provider,
            String modelName,
            String endpointUrl,
            String credentialReferenceType,
            boolean credentialAvailable,
            List<String> capabilities,
            Map<String, Object> limitPolicy,
            String status,
            Integer version,
            List<GrantView> grants) {
    }

    public record PlatformOverview(List<ModelView> models, List<SystemOption> systems) {
    }

    public record ToolInput(
            @NotBlank @Pattern(regexp = "QUERY|WRITE|FLOW_DRAFT|REPORT|ERROR_EXPLAIN") String toolType,
            @NotBlank @Pattern(regexp = "MODULE") String resourceType,
            @NotBlank @Size(max = 100) String resourceId,
            @NotBlank @Size(max = 64) String actionCode,
            @NotNull @Size(min = 1, max = 50) List<@NotBlank @Size(max = 100) String> fieldCodes,
            @NotBlank @Pattern(regexp = "CURRENT|ALL") String requestedDataScope,
            @NotNull Boolean requiresConfirmation) {
    }

    public record AgentDraftRequest(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull Long modelGrantId,
            @NotBlank @Size(min = 20, max = 8000) String systemPrompt,
            @NotNull Map<String, Object> contextPolicy,
            @NotNull Map<String, Object> confirmationPolicy,
            @NotNull Map<String, Object> fallbackPolicy,
            @NotNull @Size(min = 1, max = 20) @Valid List<ToolInput> tools,
            Integer expectedVersion) {
    }

    public record Issue(String code, String message, Integer toolIndex) {
    }

    public record ToolView(
            Long id,
            String toolType,
            String resourceType,
            String resourceId,
            String actionCode,
            List<String> fieldCodes,
            String requestedDataScope,
            boolean requiresConfirmation,
            Integer version) {
    }

    public record ToolPreview(
            Long toolId,
            String toolType,
            String resourceId,
            String actionCode,
            Map<String, Object> fieldAuthorization,
            Map<String, Object> dataAuthorization,
            boolean requiresConfirmation,
            boolean valid,
            List<Issue> issues) {
    }

    public record VersionView(
            Long id,
            Integer versionNumber,
            Integer draftRevision,
            String snapshotHash,
            Long publishedByMemberId,
            LocalDateTime publishedAt,
            boolean current) {
    }

    public record AgentView(
            Long id,
            String code,
            String name,
            String description,
            Long modelGrantId,
            Integer draftRevision,
            String systemPrompt,
            Map<String, Object> contextPolicy,
            Map<String, Object> confirmationPolicy,
            Map<String, Object> fallbackPolicy,
            String status,
            Integer version,
            List<ToolView> tools,
            List<VersionView> versions) {
    }

    public record ModuleOption(String code, String name, List<String> actions, List<String> fields) {
    }

    public record SystemOverview(
            List<ModelView> availableModels,
            List<ModuleOption> modules,
            List<AgentView> agents) {
    }

    public record AgentPreview(
            Long agentId,
            Integer draftRevision,
            boolean valid,
            List<Issue> issues,
            ModelView finalModel,
            List<ToolPreview> tools,
            Map<String, Object> permissionSnapshot,
            Map<String, Object> confirmationPolicy) {
    }

    public record PublishRequest(@NotNull @Min(1) Integer expectedDraftRevision) {
    }

    public record PublishResult(
            Long agentId,
            Long versionId,
            Integer versionNumber,
            Integer draftRevision,
            String snapshotHash,
            AgentPreview preview) {
    }
}
