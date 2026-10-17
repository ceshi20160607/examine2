package com.unique.unexamine.ai.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 平台首页的只读 Agent 入口；资源权限由业务服务逐项复查。 */
@RestController
@RequestMapping("/api/platform/ai")
public class PlatformAiRuntimeController {
    private final PlatformAiRuntimeService service;

    public PlatformAiRuntimeController(PlatformAiRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/agents")
    public ApiResult<List<PlatformAiRuntimeModels.AgentOption>> agents() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/queries")
    public ApiResult<PlatformAiRuntimeModels.QueryResult> query(
            @Valid @RequestBody PlatformAiRuntimeModels.QueryRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.query(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }
}
