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

/** 系统后台页面建议入口；确认只保存模块草稿，发布仍走模块原检查。 */
@RestController
@RequestMapping("/api/admin/system/ai/page-drafts")
@RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "MANAGE")
public class AiPageDraftController {
    private final AiPageDraftService service;

    public AiPageDraftController(AiPageDraftService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public ApiResult<AiPageDraftModels.Options> options(@RequestParam Long moduleId) {
        return ApiResult.ok(service.options(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/recognize")
    public ApiResult<AiPageDraftModels.CandidateView> recognize(
            @Valid @RequestBody AiPageDraftModels.RecognizeRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.recognize(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{pendingWriteId}")
    public ApiResult<AiPageDraftModels.CandidateView> candidate(@PathVariable Long pendingWriteId) {
        return ApiResult.ok(service.candidate(AuthenticationContextHolder.require(), pendingWriteId));
    }

    @PostMapping("/{pendingWriteId}/confirm")
    public ApiResult<AiPageDraftModels.CandidateView> confirm(
            @PathVariable Long pendingWriteId, @Valid @RequestBody AiPageDraftModels.ConfirmRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.confirm(AuthenticationContextHolder.require(), pendingWriteId, body,
                TraceIdFilter.current(request)));
    }
}
