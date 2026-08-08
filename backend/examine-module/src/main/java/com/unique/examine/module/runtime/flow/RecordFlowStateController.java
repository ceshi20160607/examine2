package com.unique.examine.module.runtime.flow;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleCode}/records/{recordId}/flow-state")
public class RecordFlowStateController {
    private final RecordFlowStateService service;

    public RecordFlowStateController(RecordFlowStateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<RecordFlowViews.RecordFlowState> find(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.find(RuntimeSession.require(value, systemId), moduleCode, recordId),
                requestId(request),
                traceId(request));
    }

    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }

    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }
}
