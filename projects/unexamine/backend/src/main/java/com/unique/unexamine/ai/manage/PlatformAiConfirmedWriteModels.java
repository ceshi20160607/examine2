package com.unique.unexamine.ai.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 平台 Agent 的待确认写入请求；字段只在平台任务、消息和工作日志中解释。 */
public final class PlatformAiConfirmedWriteModels {
    private PlatformAiConfirmedWriteModels() { }

    public record RecognizeRequest(@NotNull Long agentId, @NotBlank String targetType,
            @NotBlank @Size(max = 12000) String inputText) { }

    public record ConfirmRequest(@NotNull Integer expectedVersion,
            Boolean confirmed, @NotNull JsonNode fields, Boolean reviewedLowConfidence) { }

    public record CandidateView(Long pendingWriteId, Long conversationId, Long executionId,
            String targetType, String status, String outcome, String message, String errorCode,
            JsonNode fields, double confidence, boolean businessWritten, Integer version,
            Long resultId, String resultPath, LocalDateTime confirmedTime) { }

    public record MemberOption(Long memberId, Long accountId, String name) { }

    public record TemplateOption(String code, String name, List<String> requiredVariables) { }

    public record WriteOptions(Long currentAccountId, Long currentMemberId, List<MemberOption> members,
            List<TemplateOption> templates) { }
}
