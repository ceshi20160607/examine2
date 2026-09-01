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

public final class AiContextQueryModels {
    private AiContextQueryModels() {
    }

    public record AgentOption(
            Long agentId,
            String agentCode,
            String agentName,
            Long versionId,
            Integer versionNumber,
            List<String> moduleCodes) {
    }

    public record ConversationSummary(
            Long id,
            Long agentId,
            Long agentVersionId,
            String agentName,
            String title,
            String status,
            Map<String, Object> entryContext,
            LocalDateTime updatedAt) {
    }

    public record Overview(List<AgentOption> agents, List<ConversationSummary> conversations) {
    }

    public record QueryFilter(
            @NotBlank @Size(max = 100) String fieldCode,
            @NotBlank @Pattern(regexp = "EQ|NE|CONTAINS|GT|GTE|LT|LTE|EMPTY|NOT_EMPTY") String operator,
            @Size(max = 500) String value) {
    }

    public record EntryContextRequest(
            @NotBlank @Pattern(regexp = "SYSTEM_AI|RIGHT_ASSISTANT|MODULE_PAGE|RECORD_DETAIL|WORK_PAGE") String entryType,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String moduleCode,
            Long recordId,
            @Size(max = 500) String sourcePath,
            @Pattern(regexp = "ACTIVE|ARCHIVED|DELETED") String lifecycleState,
            @Pattern(regexp = "ALL|OWN|SHARED") String tenantScope,
            @Size(max = 200) String search,
            @NotNull @Size(max = 10) List<@Valid QueryFilter> filters,
            @Size(max = 100) String sortField,
            @Pattern(regexp = "ASC|DESC") String sortDirection,
            @Min(1) @Max(200) Integer pageSize) {
    }

    public record QueryRequest(
            @NotNull @Min(1) Long agentId,
            @Min(1) Long conversationId,
            @NotBlank @Size(max = 2000) String question,
            @NotNull @Size(max = 50) List<@Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String> requestedFieldCodes,
            @Min(1) Long requestedTenantId,
            @NotNull @Valid EntryContextRequest entryContext) {
    }

    public record QueryScope(
            Long systemId,
            Long tenantId,
            String moduleCode,
            String actionCode,
            String lifecycleState,
            String tenantScope,
            String search,
            List<QueryFilter> filters,
            List<String> fieldCodes,
            Map<String, Object> dataScopes,
            String sourcePath) {
    }

    public record Source(
            Long recordId,
            String recordNumber,
            String title,
            String path,
            Map<String, Object> fields) {
    }

    public record QueryResult(
            Long conversationId,
            Long executionId,
            String outcome,
            String answer,
            String errorCode,
            boolean retryable,
            QueryScope scope,
            String metricDefinition,
            List<Source> sources,
            LocalDateTime persistedAt) {
    }

    public record MessageView(
            Long id,
            String role,
            String content,
            Map<String, Object> structuredContent,
            Map<String, Object> modelUsage,
            String errorCode,
            LocalDateTime createdAt) {
    }

    public record ConversationDetail(
            ConversationSummary conversation,
            List<MessageView> messages) {
    }
}
