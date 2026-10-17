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

/** 系统后台字段草稿建议入口，保存仍交给现有模块配置服务。 */
@RestController
@RequestMapping("/api/admin/system/ai/module-field-drafts")
@RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "MANAGE")
public class AiModuleFieldDraftController {
    private final AiModuleFieldDraftService service;

    public AiModuleFieldDraftController(AiModuleFieldDraftService service) {
        this.service = service;
    }

    @GetMapping("/options")
    public ApiResult<AiModuleFieldDraftModels.Options> options(@RequestParam Long moduleId) {
        return ApiResult.ok(service.options(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/recognize")
    public ApiResult<AiModuleFieldDraftModels.CandidateView> recognize(
            @Valid @RequestBody AiModuleFieldDraftModels.RecognizeRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.recognize(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{pendingWriteId}")
    public ApiResult<AiModuleFieldDraftModels.CandidateView> candidate(@PathVariable Long pendingWriteId) {
        return ApiResult.ok(service.candidate(AuthenticationContextHolder.require(), pendingWriteId));
    }

    @PostMapping("/{pendingWriteId}/confirm")
    public ApiResult<AiModuleFieldDraftModels.CandidateView> confirm(
            @PathVariable Long pendingWriteId,
            @Valid @RequestBody AiModuleFieldDraftModels.ConfirmRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.confirm(AuthenticationContextHolder.require(), pendingWriteId, body,
                TraceIdFilter.current(request)));
    }
}
