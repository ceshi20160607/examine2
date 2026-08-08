package com.unique.examine.module.dashboard.runtime;

import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.service.KpiService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
public final class KpiServiceDashboardTargetReader
        implements DashboardKpiTargetReader {
    private final KpiService kpis;

    public KpiServiceDashboardTargetReader(KpiService kpis) {
        this.kpis = Objects.requireNonNull(kpis, "kpis");
    }

    @Override
    public List<KpiService.ApplicableTarget> applicableTargets(
            KpiActor actor,
            LocalDate date
    ) {
        return kpis.applicableTargets(actor, date);
    }
}
