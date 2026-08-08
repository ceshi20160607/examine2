package com.unique.examine.module.dashboard.runtime;

import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.service.KpiService;

import java.time.LocalDate;
import java.util.List;

@FunctionalInterface
public interface DashboardKpiTargetReader {
    List<KpiService.ApplicableTarget> applicableTargets(
            KpiActor actor,
            LocalDate date
    );
}
