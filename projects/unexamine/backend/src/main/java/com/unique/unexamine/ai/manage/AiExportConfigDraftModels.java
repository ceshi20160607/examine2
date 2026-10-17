package com.unique.unexamine.ai.manage;

import com.unique.unexamine.dataexchange.manage.ExportModels;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 配置 Agent 的导出字段候选与人工确认输入。 */
public final class AiExportConfigDraftModels {
    private AiExportConfigDraftModels() { }

    public record AgentOption(Long id, String name) { }

    public record Options(List<AgentOption> agents, Long moduleId, String moduleCode,
                          String moduleName, Integer draftRevision,
                          List<ExportModels.ExportFieldView> availableFields,
                          Integer configVersion) { }

    public record RecognizeRequest(@NotNull @Min(1) Long agentId,
                                   @NotNull @Min(1) Long moduleId,
                                   @NotBlank @Size(max = 10000) String instruction) { }

    public record CandidateView(Long pendingWriteId, String status, String outcome,
                                String message, String errorCode, String name,
                                List<String> selectedFields, String relationDisplay,
                                double confidence, Integer configVersion,
                                boolean businessWritten, Integer version,
                                Long moduleId, String moduleCode, Long agentId, Long configId) { }

    public record ConfirmRequest(@NotNull Integer expectedVersion,
                                 @NotNull @AssertTrue Boolean confirmed,
                                 @NotBlank @Size(max = 200) String name,
                                 @NotNull @Size(min = 1, max = 50) List<@NotBlank String> selectedFields,
                                 @NotBlank @Pattern(regexp = "LABEL|ID") String relationDisplay,
                                 Integer expectedConfigVersion,
                                 @NotNull Boolean reviewedLowConfidence) { }
}
