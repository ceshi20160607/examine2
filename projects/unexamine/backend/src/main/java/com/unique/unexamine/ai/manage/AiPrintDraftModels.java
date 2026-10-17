package com.unique.unexamine.ai.manage;

import com.unique.unexamine.print.manage.PrintModels;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 配置 Agent 的打印模板候选与人工确认输入。 */
public final class AiPrintDraftModels {
    private AiPrintDraftModels() { }

    public record AgentOption(Long id, String name) { }

    public record FieldOption(String code, String name, String fieldType) { }

    public record Options(List<AgentOption> agents, Long moduleId, String moduleCode,
                          String moduleName, Integer draftRevision, Long publishedVersionId,
                          List<FieldOption> fields) { }

    public record RecognizeRequest(@NotNull @Min(1) Long agentId,
                                   @NotNull @Min(1) Long moduleId,
                                   @NotBlank @Size(max = 10000) String instruction) { }

    public record CandidateView(Long pendingWriteId, String status, String outcome,
                                String message, String errorCode, String code, String name,
                                String pageSize, String orientation, PrintModels.Layout layout,
                                double confidence, boolean businessWritten, Integer version,
                                Long moduleId, String moduleCode, Long agentId, Long templateId) { }

    public record ConfirmRequest(@NotNull Integer expectedVersion,
                                 @NotNull @AssertTrue Boolean confirmed,
                                 @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String code,
                                 @NotBlank @Size(max = 200) String name,
                                 @NotBlank @Pattern(regexp = "A4|A5") String pageSize,
                                 @NotBlank @Pattern(regexp = "PORTRAIT|LANDSCAPE") String orientation,
                                 @NotNull @Valid PrintModels.Layout layout,
                                 @NotNull Boolean reviewedLowConfidence) { }
}
