package com.unique.examine.module.report.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.report.runtime.ReportRuntimeService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/reports")
public final class ReportRuntimeController {
    private final ReportRuntimeService service;

    public ReportRuntimeController(ReportRuntimeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ReportRuntimeViews.Metadata>> list(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.list(
                RuntimeSession.require(value, systemId)), request);
    }

    @GetMapping("/{code}")
    public ApiResponse<ReportRuntimeViews.Metadata> metadata(
            @PathVariable long systemId,
            @PathVariable String code,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.metadata(
                RuntimeSession.require(value, systemId), code), request);
    }

    @GetMapping("/{code}/rows")
    public ApiResponse<ReportRuntimeViews.Rows> rows(
            @PathVariable long systemId,
            @PathVariable String code,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.rows(
                RuntimeSession.require(value, systemId), code, page, size),
                request);
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
