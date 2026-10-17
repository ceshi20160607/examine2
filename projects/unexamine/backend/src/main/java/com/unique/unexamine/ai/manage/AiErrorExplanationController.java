package com.unique.unexamine.ai.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统上下文中的可选错误说明入口；失败时页面仍展示原业务错误。 */
@RestController
@RequestMapping("/api/system/ai/error-explanations")
public class AiErrorExplanationController {
    private final AiErrorExplanationService service;

    public AiErrorExplanationController(AiErrorExplanationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResult<AiErrorExplanationModels.Explanation> explain(
            @Valid @RequestBody AiErrorExplanationModels.ExplainRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.explain(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }
}
