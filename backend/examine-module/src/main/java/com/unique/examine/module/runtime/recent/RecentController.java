package com.unique.examine.module.runtime.recent;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime")
public class RecentController {
    private final RecentService service;

    public RecentController(RecentService service) {
        this.service = service;
    }

    @PostMapping("/recent-records:touch")
    public ApiResponse<RecentViews.RecentItem> touch(
            @PathVariable long systemId,
            @RequestBody String body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.touch(RuntimeSession.require(value, systemId), body), request);
    }

    @GetMapping("/recent-records")
    public ApiResponse<RecentViews.RecentPage> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.list(RuntimeSession.require(value, systemId), page, size), request);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestId(request), traceId(request));
    }

    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }

    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }
}
