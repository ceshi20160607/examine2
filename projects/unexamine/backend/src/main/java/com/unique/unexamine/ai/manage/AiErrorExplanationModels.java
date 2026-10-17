package com.unique.unexamine.ai.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 已发生业务错误的解释输入和独立降级结果；原 API 错误仍由调用页面保存。 */
public final class AiErrorExplanationModels {
    private AiErrorExplanationModels() { }

    public record ExplainRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,99}") String errorCode,
            @Size(max = 500) String errorMessage,
            @NotBlank @Size(max = 60) String operation,
            @Size(max = 64) String requestId) { }

    public record Explanation(String errorCode, String originalMessage, String status,
                              String explanation, List<String> suggestions, boolean modelUsed) { }
}
