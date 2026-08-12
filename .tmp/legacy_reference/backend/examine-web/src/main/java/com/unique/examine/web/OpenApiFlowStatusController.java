package com.unique.examine.web;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.api.FlowHttpErrors;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.OpenApiFlowStatusService;
import com.unique.examine.openapi.security.OpenApiAuthentication;
import com.unique.examine.openapi.security.OpenApiMachineSession;
import com.unique.examine.openapi.security.OpenApiSecurityErrors;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/openapi/v1/flow/instances")
public class OpenApiFlowStatusController {
    private final StatusOperations statuses;

    @Autowired
    public OpenApiFlowStatusController(OpenApiFlowStatusService statuses) {
        this(new ServiceStatusOperations(statuses));
    }

    OpenApiFlowStatusController(StatusOperations statuses) {
        this.statuses = Objects.requireNonNull(statuses, "statuses");
    }

    @GetMapping("/{instanceId}")
    public ApiResponse<FlowViews.OpenApiInstanceStatus> status(
            @PathVariable long instanceId,
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
        var session = FlowSession.require(
                authentication.session(),
                authentication.application().systemId(),
                FlowPermissions.INSTANCE_READ);
        var result = FlowHttpErrors.execute(() -> statuses.status(session, instanceId));
        return ApiResponse.success(
                result,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static OpenApiAuthentication authentication(
            Object authenticationValue,
            Object sessionValue
    ) {
        if (!(authenticationValue instanceof OpenApiAuthentication authentication)
                || !(sessionValue instanceof OpenApiMachineSession machineSession)
                || !machineSession.equals(authentication.session())
                || !Objects.equals(
                        machineSession.systemId(), authentication.application().systemId())
                || !Objects.equals(
                        machineSession.tenantId(), authentication.application().tenantId())
                || !Objects.equals(
                        machineSession.memberId(),
                        authentication.application().serviceMemberId())) {
            throw OpenApiSecurityErrors.authenticationRequired();
        }
        return authentication;
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    interface StatusOperations {
        FlowViews.OpenApiInstanceStatus status(FlowSession session, long instanceId);
    }

    private record ServiceStatusOperations(OpenApiFlowStatusService delegate)
            implements StatusOperations {
        private ServiceStatusOperations {
            Objects.requireNonNull(delegate, "statuses");
        }

        @Override
        public FlowViews.OpenApiInstanceStatus status(
                FlowSession session,
                long instanceId
        ) {
            return delegate.status(session, instanceId);
        }
    }
}
