package com.unique.examine.module.datasource.statistics.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.statistics.api.StatisticsMapping;
import com.unique.examine.module.datasource.statistics.api.StatisticsRequests;
import com.unique.examine.module.datasource.statistics.api.StatisticsViews;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.service.DataSourceStatisticsService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/data-sources")
public class DataSourceStatisticsController {
    private final DataSourceService dataSources;
    private final DataSourceStatisticsService statistics;

    public DataSourceStatisticsController(
            DataSourceService dataSources,
            DataSourceStatisticsService statistics
    ) {
        this.dataSources = dataSources;
        this.statistics = statistics;
    }

    @GetMapping("/{code}/statistics-capabilities")
    public ApiResponse<StatisticsViews.Capabilities> activeCapabilities(
            @PathVariable long systemId,
            @PathVariable String code,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = RuntimeSession.require(value, systemId);
        var publication = dataSources.active(actor(session), code);
        return ok(StatisticsMapping.capabilities(statistics.capabilities(
                session, publication.root().id(), publication.version().id())),
                request);
    }

    @PostMapping("/{code}:statistics")
    public ApiResponse<StatisticsViews.Result> active(
            @PathVariable long systemId,
            @PathVariable String code,
            @Valid @RequestBody StatisticsRequests.Query body,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = RuntimeSession.require(value, systemId);
        var publication = dataSources.active(actor(session), code);
        return ok(StatisticsMapping.result(statistics.statistics(
                session, publication.root().id(), publication.version().id(),
                StatisticsMapping.request(body))), request);
    }

    private static DataSourceActor actor(RuntimeSession session) {
        if (session == null || session.tenantId() == null
                || session.tenantId() <= 0) {
            throw new StatisticsException(
                    "STATISTICS_TENANT_REQUIRED",
                    "Select an active tenant before running statistics");
        }
        return new DataSourceActor(
                session.systemId(), session.tenantId(), session.memberId());
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
