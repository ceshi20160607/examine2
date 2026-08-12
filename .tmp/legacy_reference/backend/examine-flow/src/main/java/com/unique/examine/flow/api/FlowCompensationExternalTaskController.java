package com.unique.examine.flow.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowCompensationExternalTaskService;
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
public final class FlowCompensationExternalTaskController {
    private final FlowCompensationExternalTaskService compensations;

    public FlowCompensationExternalTaskController(
            FlowCompensationExternalTaskService compensations
    ) {
        this.compensations = compensations;
    }

    @GetMapping("/compensation-external-tasks")
    public ApiResponse<FlowViews.CompensationExternalTaskPage> tasks(
            @PathVariable long systemId,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() ->
                compensations.externalTasks(
                        session, topic, page, size)), request);
    }

    @PostMapping("/compensation-external-tasks/{compensationExecutionId}:claim")
    public ApiResponse<FlowViews.CompensationExternalTaskClaim> claim(
            @PathVariable long systemId,
            @PathVariable long compensationExecutionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() -> compensations.claim(
                session, compensationExecutionId, idempotencyKey)), request);
    }

    @PostMapping("/compensation-external-tasks/{compensationExecutionId}:heartbeat")
    public ApiResponse<FlowViews.CompensationExecution> heartbeat(
            @PathVariable long systemId,
            @PathVariable long compensationExecutionId,
            @RequestBody FlowRequests.ExternalTaskLease body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() -> compensations.heartbeat(
                session, compensationExecutionId, body, idempotencyKey)),
                request);
    }

    @PostMapping("/compensation-external-tasks/{compensationExecutionId}:complete")
    public ApiResponse<FlowViews.CompensationExecution> complete(
            @PathVariable long systemId,
            @PathVariable long compensationExecutionId,
            @RequestBody FlowRequests.CompleteExternalTask body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() -> compensations.complete(
                session, compensationExecutionId, body, idempotencyKey)),
                request);
    }

    @PostMapping("/compensation-external-tasks/{compensationExecutionId}:fail")
    public ApiResponse<FlowViews.CompensationExecution> fail(
            @PathVariable long systemId,
            @PathVariable long compensationExecutionId,
            @RequestBody FlowRequests.FailExternalTask body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.EXTERNAL_TASK_WORK);
        return ok(FlowHttpErrors.execute(() -> compensations.fail(
                session, compensationExecutionId, body, idempotencyKey)),
                request);
    }

    @PostMapping(
            "/instances/{instanceId}/compensation-executions/"
                    + "{compensationExecutionId}:retry")
    public ApiResponse<FlowViews.CompensationExecution> retry(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable long compensationExecutionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        return ok(FlowHttpErrors.execute(() -> compensations.retry(
                session, instanceId, compensationExecutionId,
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
                        WebRequestAttributes.TRACE_ID)));
    }
}
