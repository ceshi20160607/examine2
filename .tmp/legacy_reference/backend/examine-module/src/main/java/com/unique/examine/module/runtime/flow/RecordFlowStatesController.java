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

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleCode}/records/{recordId}/flow-states")
public class RecordFlowStatesController {
    private final RecordFlowStateService service;

    public RecordFlowStatesController(RecordFlowStateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RecordFlowViews.RecordFlowState>> findAll(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                service.findAll(RuntimeSession.require(value, systemId), moduleCode, recordId),
                String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID)));
    }
}
