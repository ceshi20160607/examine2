package com.unique.unexamine.ai.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 系统配置 Agent 的模块字段候选和人工确认接口。 */
public final class AiModuleFieldDraftModels {
    private AiModuleFieldDraftModels() { }

    public record AgentOption(Long id, String name) { }

    public record Options(List<AgentOption> agents, Long moduleId, String moduleCode,
                          String moduleName, Integer draftRevision) { }

    public record RecognizeRequest(@NotNull @Min(1) Long agentId,
                                   @NotNull @Min(1) Long moduleId,
                                   @NotBlank @Size(max = 12000) String instruction) { }

    /** 候选在确认前只存在 AI 待确认区，字段配置由人核对后提交。 */
    public record FieldCandidate(String code, String name, String fieldType,
                                 boolean required, boolean uniqueValue, boolean searchable,
                                 JsonNode config, double confidence) { }

    public record CandidateView(Long pendingWriteId, String status, String outcome,
                                String message, String errorCode, List<FieldCandidate> fields,
                                double confidence, boolean businessWritten, Integer version,
                                List<Long> resultFieldIds, Long moduleId, String moduleCode,
                                Long agentId) { }

    public record ConfirmField(@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}") String code,
                               @NotBlank @Size(max = 200) String name,
                               @NotBlank String fieldType, @NotNull Boolean required,
                               @NotNull Boolean uniqueValue, @NotNull Boolean searchable,
                               @NotNull JsonNode config) { }

    public record ConfirmRequest(@NotNull Integer expectedVersion,
                                 @NotNull @AssertTrue Boolean confirmed,
                                 @NotNull @Size(min = 1, max = 5) List<@Valid ConfirmField> fields,
                                 @NotNull List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,49}") String> reviewedFieldCodes) { }
}
