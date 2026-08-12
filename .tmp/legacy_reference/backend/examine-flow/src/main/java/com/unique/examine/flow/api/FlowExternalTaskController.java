package com.unique.examine.flow.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowCompletionExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/flow")
public final class FlowExternalTaskController {
    private final FlowCompletionExecutionService completions;

    public FlowExternalTaskController(
            FlowCompletionExecutionService completions
    ) {
        this.completions = completions;
    }

    @GetMapping("/external-tasks")
    public ApiResponse<FlowViews.ExternalTaskPage> externalTasks(
            @PathVariable long systemId,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() ->
                completions.externalTasks(
                        session, topic, page, size)), request);
    }

    @PostMapping("/external-tasks/{executionId}:claim")
    public ApiResponse<FlowViews.ExternalTaskClaim> claim(
            @PathVariable long systemId,
            @PathVariable long executionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() ->
                completions.claim(
                        session, executionId, idempotencyKey)), request);
    }

    @PostMapping("/external-tasks/{executionId}:heartbeat")
    public ApiResponse<FlowViews.CompletionExecution> heartbeat(
            @PathVariable long systemId,
            @PathVariable long executionId,
            @RequestBody FlowRequests.ExternalTaskLease body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() ->
                completions.heartbeat(
                        session, executionId, body, idempotencyKey)), request);
    }

    @PostMapping("/external-tasks/{executionId}:complete")
    public ApiResponse<FlowViews.CompletionExecution> complete(
            @PathVariable long systemId,
            @PathVariable long executionId,
            @RequestBody FlowRequests.CompleteExternalTask body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() ->
                completions.complete(
                        session, executionId, body, idempotencyKey)), request);
    }

    @PostMapping("/external-tasks/{executionId}:fail")
    public ApiResponse<FlowViews.CompletionExecution> fail(
            @PathVariable long systemId,
            @PathVariable long executionId,
            @RequestBody FlowRequests.FailExternalTask body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() ->
                completions.fail(
                        session, executionId, body, idempotencyKey)), request);
    }

    @PostMapping(
            "/instances/{instanceId}/completion-executions/{executionId}:retry")
    public ApiResponse<FlowViews.CompletionExecution> retry(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable long executionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        return ok(FlowHttpErrors.execute(() ->
                completions.retry(
                        session, instanceId, executionId,
                        idempotencyKey)), request);
    }

    private static <T> ApiResponse<T> ok(
            T data,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                data,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID))
        );
    }
}
