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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/writes")
@RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "VIEW")
public class AiConfirmedWriteController {
    private final AiConfirmedWriteService service;

    public AiConfirmedWriteController(AiConfirmedWriteService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<AiConfirmedWriteModels.WriteOverview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/recognize")
    public ApiResult<AiConfirmedWriteModels.CandidateView> recognize(
            @Valid @RequestBody AiConfirmedWriteModels.RecognizeRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.recognize(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{pendingWriteId}")
    public ApiResult<AiConfirmedWriteModels.CandidateView> candidate(@PathVariable Long pendingWriteId) {
        return ApiResult.ok(service.candidate(AuthenticationContextHolder.require(), pendingWriteId));
    }

    @PostMapping("/{pendingWriteId}/confirm")
    public ApiResult<AiConfirmedWriteModels.WriteResult> confirm(
            @PathVariable Long pendingWriteId,
            @Valid @RequestBody AiConfirmedWriteModels.ConfirmRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.confirm(AuthenticationContextHolder.require(), pendingWriteId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{pendingWriteId}/cancel")
    public ApiResult<AiConfirmedWriteModels.WriteResult> cancel(
            @PathVariable Long pendingWriteId,
            @Valid @RequestBody AiConfirmedWriteModels.CancelRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.cancel(AuthenticationContextHolder.require(), pendingWriteId, body,
                TraceIdFilter.current(request)));
    }
}
