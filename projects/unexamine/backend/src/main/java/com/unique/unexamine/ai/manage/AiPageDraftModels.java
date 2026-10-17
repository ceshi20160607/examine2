package com.unique.unexamine.ai.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 配置 Agent 的模块页面布局候选及人工确认输入。 */
public final class AiPageDraftModels {
    private AiPageDraftModels() { }

    public record AgentOption(Long id, String name) { }

    public record Options(List<AgentOption> agents, Long moduleId, String moduleCode,
                          String moduleName, Integer draftRevision) { }

    public record RecognizeRequest(@NotNull @Min(1) Long agentId,
                                   @NotNull @Min(1) Long moduleId,
                                   @NotBlank @Size(max = 10000) String instruction) { }

    /** 页面版本来自生成时的模块草稿，确认时必须逐页重新核对。 */
    public record PageCandidate(String pageType, List<String> fieldCodes, String title,
                                int columns, String density, Integer pageVersion,
                                double confidence) { }

    public record CandidateView(Long pendingWriteId, String status, String outcome,
                                String message, String errorCode, List<PageCandidate> pages,
                                double confidence, boolean businessWritten, Integer version,
                                Long moduleId, String moduleCode, Long agentId,
                                List<Long> resultPageIds) { }

    public record ConfirmPage(@NotBlank @Pattern(regexp = "FORM|LIST|DETAIL") String pageType,
                              @NotNull @Size(min = 1, max = 50) List<@NotBlank String> fieldCodes,
                              @NotBlank @Size(max = 200) String title,
                              @Min(1) @Max(3) int columns,
                              @NotBlank @Pattern(regexp = "comfortable|compact") String density,
                              @NotNull @Min(0) Integer pageVersion) { }

    public record ConfirmRequest(@NotNull Integer expectedVersion,
                                 @NotNull @AssertTrue Boolean confirmed,
                                 @NotNull @Size(min = 3, max = 3) List<@Valid ConfirmPage> pages,
                                 @NotNull List<@Pattern(regexp = "FORM|LIST|DETAIL") String> reviewedPageTypes) { }
}
