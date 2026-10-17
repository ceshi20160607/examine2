package com.unique.unexamine.ai.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统后台 Flow 草稿建议入口，确认保存后仍需原设计页发布检查。 */
@RestController
@RequestMapping("/api/admin/system/ai/flow-drafts")
@RequirePermission(resourceType = "FLOW", resourceCode = "SYSTEM", actionCode = "DESIGN")
public class AiFlowDraftController {
    private final AiFlowDraftService service;

    public AiFlowDraftController(AiFlowDraftService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public ApiResult<AiFlowDraftModels.Options> options(@RequestParam Long moduleId) {
        return ApiResult.ok(service.options(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/recognize")
    public ApiResult<AiFlowDraftModels.CandidateView> recognize(
            @Valid @RequestBody AiFlowDraftModels.RecognizeRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.recognize(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{pendingWriteId}")
    public ApiResult<AiFlowDraftModels.CandidateView> candidate(@PathVariable Long pendingWriteId) {
        return ApiResult.ok(service.candidate(AuthenticationContextHolder.require(), pendingWriteId));
    }

    @PostMapping("/{pendingWriteId}/confirm")
    public ApiResult<AiFlowDraftModels.CandidateView> confirm(
            @PathVariable Long pendingWriteId, @Valid @RequestBody AiFlowDraftModels.ConfirmRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.confirm(AuthenticationContextHolder.require(), pendingWriteId, body,
                TraceIdFilter.current(request)));
    }
}
