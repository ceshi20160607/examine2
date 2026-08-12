package com.unique.examine.module.runtime.history;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}"
                + "/records/{recordId}/history")
public class RecordHistoryController {
    private final RecordHistoryService service;

    public RecordHistoryController(RecordHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<RecordHistoryApi.PageResponse> page(
            @PathVariable long systemId,
            @PathVariable String moduleCode,
            @PathVariable long recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false
            ) Object sessionValue,
            HttpServletRequest request
    ) {
        var result = service.page(
                RuntimeSession.require(sessionValue, systemId),
                moduleCode,
                recordId,
                page,
                size);
        return ApiResponse.success(
                RecordHistoryApi.PageResponse.from(result),
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
