package com.unique.unexamine.ai.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 平台 AI 写入入口使用独立路径，并拒绝任何额外的系统业务字段。 */
@RestController
@RequestMapping("/api/platform/ai/write-candidates")
public class PlatformAiConfirmedWriteController {
    private final PlatformAiConfirmedWriteService service;

    public PlatformAiConfirmedWriteController(PlatformAiConfirmedWriteService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public ApiResult<PlatformAiConfirmedWriteModels.WriteOptions> options() {
        return ApiResult.ok(service.options(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<PlatformAiConfirmedWriteModels.CandidateView> recognize(
            @RequestBody JsonNode body, HttpServletRequest request) {
        requireOnly(body, Set.of("agentId", "targetType", "inputText"));
        JsonNode id = body.path("agentId");
        JsonNode target = body.path("targetType");
        JsonNode text = body.path("inputText");
        if (!id.isIntegralNumber() || id.asLong() <= 0 || !target.isTextual()
                || !text.isTextual() || text.asText().isBlank() || text.asText().length() > 12000) {
            throw invalid("AI_PLATFORM_WRITE_REQUEST_INVALID", "平台识别请求不完整");
        }
        return ApiResult.ok(service.recognize(AuthenticationContextHolder.require(),
                new PlatformAiConfirmedWriteModels.RecognizeRequest(
                        id.asLong(), target.asText(), text.asText()), TraceIdFilter.current(request)));
    }

    @GetMapping("/{pendingWriteId}")
    public ApiResult<PlatformAiConfirmedWriteModels.CandidateView> candidate(
            @PathVariable Long pendingWriteId) {
        return ApiResult.ok(service.candidate(AuthenticationContextHolder.require(), pendingWriteId));
    }

    @PostMapping("/{pendingWriteId}/confirm")
    public ApiResult<PlatformAiConfirmedWriteModels.CandidateView> confirm(
            @PathVariable Long pendingWriteId, @RequestBody JsonNode body,
            HttpServletRequest request) {
        requireOnly(body, Set.of("expectedVersion", "confirmed", "fields", "reviewedLowConfidence"));
        JsonNode version = body.path("expectedVersion");
        JsonNode confirmed = body.path("confirmed");
        JsonNode fields = body.path("fields");
        JsonNode reviewed = body.path("reviewedLowConfidence");
        if (!version.isIntegralNumber() || version.asInt() < 0 || !confirmed.isBoolean()
                || !confirmed.asBoolean() || !fields.isObject()
                || (!reviewed.isMissingNode() && !reviewed.isBoolean())) {
            throw invalid("AI_WRITE_CONFIRMATION_REQUIRED", "请检查候选并明确确认");
        }
        return ApiResult.ok(service.confirm(AuthenticationContextHolder.require(), pendingWriteId,
                new PlatformAiConfirmedWriteModels.ConfirmRequest(
                        version.asInt(), true, fields, reviewed.asBoolean(false)), TraceIdFilter.current(request)));
    }

    private void requireOnly(JsonNode body, Set<String> keys) {
        if (body == null || !body.isObject() || body.size() > keys.size()) {
            throw invalid("AI_PLATFORM_SYSTEM_WRITE_DENIED", "平台入口不能携带系统业务写入内容");
        }
        body.fieldNames().forEachRemaining(key -> {
            if (!keys.contains(key)) {
                throw invalid("AI_PLATFORM_SYSTEM_WRITE_DENIED", "平台入口不能携带系统业务写入内容");
            }
        });
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
