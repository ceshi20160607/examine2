package com.unique.unexamine.ai.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 配置 Agent 的线性审批 Flow 候选与确认输入。 */
public final class AiFlowDraftModels {
    private AiFlowDraftModels() { }

    public record AgentOption(Long id, String name) { }

    public record Options(List<AgentOption> agents, Long moduleId, String moduleCode,
                          String moduleName, Integer draftRevision) { }

    public record RecognizeRequest(@NotNull @Min(1) Long agentId,
                                   @NotNull @Min(1) Long moduleId,
                                   @NotBlank @Size(max = 10000) String instruction) { }

    /** 索引按审批步骤从零计数，开始和结束节点由服务端生成。 */
    public record StepCandidate(int index, String name, String assigneeType, double confidence) { }

    public record CandidateView(Long pendingWriteId, String status, String outcome,
                                String message, String errorCode, String flowName,
                                String description, List<StepCandidate> steps, double confidence,
                                boolean businessWritten, Integer version, Long moduleId,
                                String moduleCode, Long agentId, Long flowId, String flowPath) { }

    public record ConfirmStep(@NotBlank @Size(max = 200) String name,
                              @NotBlank String assigneeType) { }

    public record ConfirmRequest(@NotNull Integer expectedVersion,
                                 @NotNull @AssertTrue Boolean confirmed,
                                 @NotBlank @Size(max = 200) String flowName,
                                 @Size(max = 1000) String description,
                                 @NotNull @Size(min = 1, max = 3) List<@Valid ConfirmStep> steps,
                                 @NotNull List<@Min(0) Integer> reviewedStepIndexes) { }
}
