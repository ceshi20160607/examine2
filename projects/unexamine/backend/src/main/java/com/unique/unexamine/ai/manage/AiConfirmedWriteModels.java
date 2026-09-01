package com.unique.unexamine.ai.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AiConfirmedWriteModels {
    private AiConfirmedWriteModels() {
    }

    public record WriteAgentOption(
            Long agentId,
            String agentCode,
            String agentName,
            Long agentVersionId,
            Integer versionNumber,
            List<String> moduleCodes) {
    }

    public record WriteOverview(List<WriteAgentOption> agents) {
    }

    public record RecognizeRequest(
            @NotNull @Min(1) Long agentId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String moduleCode,
            @NotBlank @Size(max = 12000) String inputText,
            @NotBlank @Pattern(regexp = "TEXT|FILE") String sourceType,
            @Size(max = 500) String sourceReference,
            @NotNull @Min(1) Long requestedTenantId,
            @NotBlank @Pattern(regexp = "SYSTEM_AI|RIGHT_ASSISTANT|MODULE_PAGE|RECORD_DETAIL|WORK_PAGE")
            String entryType) {
    }

    public record FieldCandidate(
            String code,
            String label,
            String fieldType,
            boolean required,
            boolean writable,
            JsonNode value,
            double confidence,
            boolean recognized,
            String note) {
    }

    public record CandidateView(
            Long pendingWriteId,
            Long conversationId,
            Long executionId,
            Long agentId,
            Long agentVersionId,
            String moduleCode,
            String outcome,
            String status,
            String errorCode,
            String message,
            boolean retryable,
            boolean confirmationRequired,
            boolean businessWritten,
            String sourceType,
            String sourceReference,
            String inputText,
            String proposedTitle,
            String proposedRecordNumber,
            String proposedStatus,
            List<FieldCandidate> fields,
            List<String> unknownSegments,
            Long recordId,
            String recordPath,
            LocalDateTime confirmedAt,
            Integer version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record ConfirmRequest(
            @NotNull Integer expectedVersion,
            @AssertTrue Boolean confirmed,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 100) String recordNumber,
            @Size(max = 64) String status,
            Long ownerMemberId,
            Long departmentId,
            @NotNull List<Long> participantMemberIds,
            @NotNull @Size(max = 100) Map<@Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String, JsonNode> fields) {
    }

    public record CancelRequest(@NotNull Integer expectedVersion) {
    }

    public record WriteResult(
            Long pendingWriteId,
            String status,
            String outcome,
            String errorCode,
            String message,
            Long recordId,
            String recordPath,
            Map<String, Object> record,
            boolean businessWritten,
            Integer version,
            LocalDateTime confirmedAt) {
    }
}
