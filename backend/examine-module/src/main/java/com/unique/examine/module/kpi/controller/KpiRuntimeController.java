package com.unique.examine.module.kpi.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.kpi.api.KpiMapping;
import com.unique.examine.module.kpi.api.KpiViews;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.service.KpiService;
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
@RequestMapping("/api/v1/systems/{systemId}/kpis")
public class KpiRuntimeController {
    private final KpiService service;

    public KpiRuntimeController(KpiService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<KpiViews.Target>> list(
            @PathVariable long systemId,
            @RequestParam String periodType,
            @RequestParam String periodStart,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = RuntimeSession.require(value, systemId);
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new KpiException("KPI_TENANT_REQUIRED",
                    "Select an active tenant before viewing KPI targets");
        }
        var requestedType = KpiMapping.periodType(periodType);
        var requestedStart = KpiMapping.periodStart(periodStart);
        var actor = new KpiActor(
                session.systemId(), session.tenantId(), session.memberId());
        var result = service.applicableTargets(actor, requestedStart).stream()
                .filter(item -> item.target().period().type() == requestedType
                        && item.target().period().startInclusive()
                        .equals(requestedStart))
                .map(item -> KpiMapping.target(
                        item.target(), item.definition(),
                        item.latestCalculation()))
                .toList();
        return ApiResponse.success(
                result,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
