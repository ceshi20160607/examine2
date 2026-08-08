package com.unique.examine.module.datasource.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.datasource.api.DataSourceViews;
import com.unique.examine.module.datasource.runtime.DataSourceRuntimeService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/data-sources")
public class DataSourceRuntimeController {
    private final DataSourceRuntimeService service;

    public DataSourceRuntimeController(DataSourceRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/{code}")
    public ApiResponse<DataSourceViews.RuntimeMetadata> metadata(
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
    public ApiResponse<DataSourceViews.RuntimeRows> rows(
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

    @GetMapping("/{code}/http-rows")
    public ApiResponse<DataSourceViews.PublishedHttpRowsResult> httpRows(
            @PathVariable long systemId,
            @PathVariable String code,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.publishedHttpRows(
                RuntimeSession.require(value, systemId), code), request);
    }

    @GetMapping(
            "/{dataSourceId}/versions/{versionId}/http-rows")
    public ApiResponse<DataSourceViews.PublishedHttpRowsResult>
    pinnedHttpRows(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @PathVariable long versionId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.publishedHttpRows(
                RuntimeSession.require(value, systemId),
                dataSourceId, versionId), request);
    }

    @GetMapping("/{code}/jdbc-rows")
    public ApiResponse<DataSourceViews.PublishedJdbcTableRowsResult> jdbcRows(
            @PathVariable long systemId,
            @PathVariable String code,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.publishedJdbcRows(
                RuntimeSession.require(value, systemId), code), request);
    }

    @GetMapping(
            "/{dataSourceId}/versions/{versionId}/jdbc-rows")
    public ApiResponse<DataSourceViews.PublishedJdbcTableRowsResult>
    pinnedJdbcRows(
            @PathVariable long systemId,
            @PathVariable long dataSourceId,
            @PathVariable long versionId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.publishedJdbcRows(
                RuntimeSession.require(value, systemId),
                dataSourceId, versionId), request);
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
