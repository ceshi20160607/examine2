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
@RequestMapping("/api/ai")
public class AiContextQueryController {
    private final AiContextQueryService service;

    public AiContextQueryController(AiContextQueryService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "VIEW")
    public ApiResult<AiContextQueryModels.Overview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/queries")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "VIEW")
    public ApiResult<AiContextQueryModels.QueryResult> query(
            @Valid @RequestBody AiContextQueryModels.QueryRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.query(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/conversations/{conversationId}")
    @RequirePermission(resourceType = "AI", resourceCode = "SYSTEM", actionCode = "VIEW")
    public ApiResult<AiContextQueryModels.ConversationDetail> conversation(@PathVariable Long conversationId) {
        return ApiResult.ok(service.conversation(AuthenticationContextHolder.require(), conversationId));
    }
}
