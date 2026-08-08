package com.unique.examine.flow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.extension.FlowExtensionGraph;
import com.unique.examine.flow.extension.FlowExtensionService;
import com.unique.examine.flow.security.FlowSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/flow")
public class FlowExtensionController {
    private final FlowExtensionService extensions;

    public FlowExtensionController(FlowExtensionService extensions) {
        this.extensions = extensions;
    }

    @GetMapping("/node-catalog")
    public ApiResponse<?> catalog(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        FlowSession.require(value, systemId, FlowPermissions.DEFINITION_MANAGE);
        return ok(extensions.catalog(), request);
    }

    @PutMapping("/definitions/{definitionId}/extension-draft")
    public ApiResponse<?> saveDraft(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestBody SaveDraft body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        require(body != null && body.expectedRevision() > 0 && body.graph() != null,
                "Flow extension draft body is invalid");
        return ok(FlowHttpErrors.execute(() -> extensions.saveDraft(
                session, definitionId, body.expectedRevision(), body.graph())), request);
    }

    @GetMapping("/definitions/{definitionId}/extension-draft")
    public ApiResponse<?> draft(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        return ok(FlowHttpErrors.execute(
                () -> extensions.draft(session, definitionId)), request);
    }

    @GetMapping("/definitions/{definitionId}/publish-impact")
    public ApiResponse<?> impact(
            @PathVariable long systemId,
            @PathVariable long definitionId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.DEFINITION_MANAGE);
        return ok(FlowHttpErrors.execute(
                () -> extensions.impact(session, definitionId)), request);
    }

    @GetMapping("/instances/{instanceId}/nodes/{nodeCode}/form")
    public ApiResponse<?> form(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String nodeCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.requireAny(
                value, systemId, FlowPermissions.INSTANCE_DECIDE,
                FlowPermissions.INSTANCE_READ);
        return ok(FlowHttpErrors.execute(
                () -> extensions.form(session, instanceId, nodeCode)), request);
    }

    @PutMapping("/instances/{instanceId}/nodes/{nodeCode}/form")
    public ApiResponse<?> writeForm(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String nodeCode,
            @RequestBody WriteForm body,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.INSTANCE_DECIDE);
        require(body != null && body.expectedSnapshotVersion() >= 0
                        && body.expectedRecordVersion() >= 0,
                "Flow form write body is invalid");
        requireKey(idempotencyKey);
        return ok(FlowHttpErrors.execute(() -> extensions.writeForm(
                session, instanceId, nodeCode, body.expectedSnapshotVersion(),
                body.expectedRecordVersion(), body.values(), idempotencyKey,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID))), request);
    }

    @GetMapping("/instances/{instanceId}/nodes/{nodeCode}/form/history")
    public ApiResponse<?> formHistory(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String nodeCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.requireAny(
                value, systemId, FlowPermissions.INSTANCE_DECIDE,
                FlowPermissions.INSTANCE_READ);
        return ok(FlowHttpErrors.execute(() -> extensions.formHistory(
                session, instanceId, nodeCode)), request);
    }

    @PostMapping("/instances/{instanceId}/nodes/{nodeCode}:execute")
    public ApiResponse<?> executeNode(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String nodeCode,
            @RequestBody NodeCommand body,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.INSTANCE_DECIDE);
        require(body != null, "Flow node execute body is required");
        requireKey(idempotencyKey);
        return ok(FlowHttpErrors.execute(() -> extensions.executeNode(
                session, instanceId, nodeCode, body.expectedVersion(), body.input(),
                idempotencyKey,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID))), request);
    }

    @PostMapping("/instances/{instanceId}/nodes/{nodeCode}:resume")
    public ApiResponse<?> resumeNode(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String nodeCode,
            @RequestBody NodeCommand body,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.require(
                value, systemId, FlowPermissions.INSTANCE_DECIDE);
        require(body != null && body.expectedVersion() != null
                        && body.expectedVersion() >= 0,
                "Flow node resume requires expectedVersion");
        requireKey(idempotencyKey);
        return ok(FlowHttpErrors.execute(() -> extensions.resumeNode(
                session, instanceId, nodeCode, body.expectedVersion(), body.input(),
                idempotencyKey,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID))),
                request);
    }

    @GetMapping("/instances/{instanceId}/nodes/{nodeCode}/execution-history")
    public ApiResponse<?> nodeHistory(
            @PathVariable long systemId,
            @PathVariable long instanceId,
            @PathVariable String nodeCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false)
                    Object value,
            HttpServletRequest request) {
        var session = FlowSession.requireAny(
                value, systemId, FlowPermissions.INSTANCE_DECIDE,
                FlowPermissions.INSTANCE_READ);
        return ok(FlowHttpErrors.execute(() -> extensions.nodeHistory(
                session, instanceId, nodeCode)), request);
    }

    public record SaveDraft(int expectedRevision, FlowExtensionGraph.Graph graph) {
    }

    public record WriteForm(
            long expectedSnapshotVersion,
            long expectedRecordVersion,
            Map<String, JsonNode> values) {
        public WriteForm {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record NodeCommand(Long expectedVersion, JsonNode input) {
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new BusinessException(
                    "FLOW_REQUEST_INVALID", message, HttpStatus.BAD_REQUEST);
        }
    }

    private static void requireKey(String value) {
        require(value != null && value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$"),
                "A valid Idempotency-Key is required");
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
