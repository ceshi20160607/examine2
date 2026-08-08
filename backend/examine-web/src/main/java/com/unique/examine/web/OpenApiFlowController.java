package com.unique.examine.web;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.flow.api.FlowHttpErrors;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.FlowMutationService;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import com.unique.examine.openapi.security.OpenApiSecurityErrors;
import com.unique.examine.openapi.service.OpenApiCallbackPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Map;

@RestController
@RequestMapping("/openapi/v1/flow")
public class OpenApiFlowController {
    private final FlowMutationService mutations;
    private final OpenApiCallbackPublisher callbacks;

    public OpenApiFlowController(FlowMutationService mutations) {
        this(mutations, OpenApiCallbackPublisher.noop());
    }

    @Autowired
    public OpenApiFlowController(FlowMutationService mutations, OpenApiCallbackPublisher callbacks) {
        this.mutations = mutations;
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    @PostMapping("/definitions/{definitionId}/instances")
    public ResponseEntity<ApiResponse<FlowViews.Instance>> startInstance(
            @PathVariable long definitionId,
            @RequestBody FlowRequests.StartInstance body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(
                    value = OpenApiAuthentication.REQUEST_ATTRIBUTE,
                    required = false
            ) Object authenticationValue,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var authentication = authentication(authenticationValue, sessionValue);
        var application = authentication.application();
        var session = FlowSession.require(
                authentication.session(),
                application.systemId(),
                FlowPermissions.INSTANCE_START
        );
        var response = FlowHttpErrors.execute(() -> mutations.startOpenApi(
                session,
                application.id(),
                definitionId,
                body,
                idempotencyKey,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)
        ));
        callbacks.publish(new OpenApiCallbackPublisher.Event(application.systemId(),
                application.tenantId(), application.id(), application.serviceMemberId(),
                OpenApiCallbackPublisher.deterministicEventId("FLOW_STARTED",
                        Long.toString(application.id()), response.instanceId()),
                "FLOW_STARTED", "FLOW_INSTANCE", response.instanceId(),
                Map.of("definitionId", Long.toString(definitionId), "status", response.status()),
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                response,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID)
        ));
    }

    private static OpenApiAuthentication authentication(
            Object authenticationValue,
            Object sessionValue
    ) {
        if (!(authenticationValue instanceof OpenApiAuthentication authentication)
                || !(sessionValue instanceof OpenApiMachineSession machineSession)
                || !machineSession.equals(authentication.session())
                || !Objects.equals(machineSession.systemId(), authentication.application().systemId())
                || !Objects.equals(machineSession.tenantId(), authentication.application().tenantId())
                || !Objects.equals(
                        machineSession.memberId(),
                        authentication.application().serviceMemberId()
                )) {
            throw OpenApiSecurityErrors.authenticationRequired();
        }
        return authentication;
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
