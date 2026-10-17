package com.unique.unexamine.ai.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** 平台 Agent 的只读对话输入与结果，不接收系统业务写入载荷。 */
public final class PlatformAiRuntimeModels {
    private PlatformAiRuntimeModels() { }

    public record AgentOption(Long id, String name, List<String> capabilities) { }

    public record QueryRequest(@NotNull Long agentId,
                               @NotBlank String resourceType,
                               @NotBlank @Size(max = 4000) String question) { }

    public record QueryResult(Long conversationId, Long executionId, String outcome,
                              String answer, String errorCode, String resourceType,
                              List<Map<String, Object>> sources) { }
}
