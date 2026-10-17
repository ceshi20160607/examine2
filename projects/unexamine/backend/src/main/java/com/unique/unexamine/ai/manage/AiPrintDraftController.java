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

/** 系统后台打印模板建议入口；确认仅创建草稿，发布仍走打印服务。 */
@RestController
@RequestMapping("/api/admin/system/ai/print-drafts")
@RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "MANAGE")
public class AiPrintDraftController {
    private final AiPrintDraftService service;

    public AiPrintDraftController(AiPrintDraftService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public ApiResult<AiPrintDraftModels.Options> options(@RequestParam Long moduleId,
            HttpServletRequest request) {
        return ApiResult.ok(service.options(AuthenticationContextHolder.require(), moduleId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/recognize")
    public ApiResult<AiPrintDraftModels.CandidateView> recognize(
            @Valid @RequestBody AiPrintDraftModels.RecognizeRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.recognize(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{pendingWriteId}")
    public ApiResult<AiPrintDraftModels.CandidateView> candidate(@PathVariable Long pendingWriteId) {
        return ApiResult.ok(service.candidate(AuthenticationContextHolder.require(), pendingWriteId));
    }

    @PostMapping("/{pendingWriteId}/confirm")
    public ApiResult<AiPrintDraftModels.CandidateView> confirm(@PathVariable Long pendingWriteId,
            @Valid @RequestBody AiPrintDraftModels.ConfirmRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.confirm(AuthenticationContextHolder.require(), pendingWriteId, body,
                TraceIdFilter.current(request)));
    }
}
