package com.unique.unexamine.ai.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅对当前成员有权查看的流程实例生成可选审批摘要。 */
@Validated
@RestController
@RequestMapping("/api/system/ai/approval-summaries")
public class AiApprovalSummaryController {
    private final AiApprovalSummaryService service;

    public AiApprovalSummaryController(AiApprovalSummaryService service) {
        this.service = service;
    }

    @PostMapping("/instances/{instanceId}")
    public ApiResult<AiApprovalSummaryModels.Summary> summarize(@PathVariable @Positive Long instanceId,
            HttpServletRequest request) {
        return ApiResult.ok(service.summarize(AuthenticationContextHolder.require(), instanceId,
                TraceIdFilter.current(request)));
    }
}
