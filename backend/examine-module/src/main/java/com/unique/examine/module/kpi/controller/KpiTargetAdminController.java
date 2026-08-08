package com.unique.examine.module.kpi.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.module.kpi.api.KpiMapping;
import com.unique.examine.module.kpi.api.KpiRequests;
import com.unique.examine.module.kpi.api.KpiViews;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiTarget;
import com.unique.examine.module.kpi.domain.KpiVersion;
import com.unique.examine.module.kpi.service.KpiService;
import com.unique.examine.module.manage.security.ConfigSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin")
public class KpiTargetAdminController {
    private final KpiService service;

    public KpiTargetAdminController(KpiService service) {
        this.service = service;
    }

    @GetMapping("/kpis/{kpiId}/targets")
    public ApiResponse<List<KpiViews.Target>> targets(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = KpiAdminController.actor(value, systemId);
        return KpiAdminController.ok(service.targets(actor, kpiId).stream()
                .map(target -> view(actor, target)).toList(), request);
    }

    @PostMapping("/kpis/{kpiId}/targets")
    public ApiResponse<KpiViews.Target> createTarget(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @Valid @RequestBody KpiRequests.CreateTarget body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = KpiAdminController.actor(value, systemId);
        var target = service.createTarget(
                actor, kpiId,
                KpiMapping.positiveId(body.subjectId(), "subjectId"),
                body.periodStart(), body.targetValue());
        return KpiAdminController.ok(view(actor, target), request);
    }

    @PutMapping("/kpi-targets/{targetId}")
    public ApiResponse<KpiViews.Target> updateTarget(
            @PathVariable long systemId,
            @PathVariable long targetId,
            @Valid @RequestBody KpiRequests.UpdateTarget body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = KpiAdminController.actor(value, systemId);
        return KpiAdminController.ok(view(actor, service.updateTarget(
                actor, targetId, body.expectedVersion(), body.targetValue())),
                request);
    }

    @PostMapping("/kpi-targets/{targetId}:calculate")
    public ApiResponse<KpiViews.Calculation> calculate(
            @PathVariable long systemId,
            @PathVariable long targetId,
            @RequestHeader(
                    name = "Idempotency-Key", required = false) String commandKey,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return KpiAdminController.ok(KpiMapping.calculation(
                service.calculate(KpiAdminController.actor(value, systemId),
                        targetId, KpiMapping.commandKey(commandKey))), request);
    }

    @GetMapping("/kpi-targets/{targetId}/calculations")
    public ApiResponse<List<KpiViews.Calculation>> history(
            @PathVariable long systemId,
            @PathVariable long targetId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = KpiAdminController.actor(value, systemId);
        return KpiAdminController.ok(service.calculationHistory(
                        actor, targetId).stream()
                .map(KpiMapping::calculation).toList(), request);
    }

    private KpiViews.Target view(KpiActor actor, KpiTarget target) {
        KpiVersion definition = service.version(
                actor, target.kpiId(), target.kpiVersionNumber());
        KpiCalculation latest = service.calculationHistory(actor, target.id())
                .stream().findFirst().orElse(null);
        return KpiMapping.target(target, definition, latest);
    }
}
