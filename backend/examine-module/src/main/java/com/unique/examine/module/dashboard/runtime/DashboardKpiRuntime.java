package com.unique.examine.module.dashboard.runtime;

import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.kpi.api.KpiMapping;
import com.unique.examine.module.kpi.api.KpiViews;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiPeriod;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Resolves the KPI payload of one published dashboard widget.
 *
 * <p>The published widget supplies identity only. Current period and authority
 * are deliberately derived again for every runtime read.</p>
 */
@Component
public final class DashboardKpiRuntime {
    private final DashboardKpiTargetReader kpis;
    private final Clock clock;

    @Autowired
    public DashboardKpiRuntime(DashboardKpiTargetReader kpis) {
        this(kpis, Clock.systemUTC());
    }

    DashboardKpiRuntime(DashboardKpiTargetReader kpis, Clock clock) {
        this.kpis = Objects.requireNonNull(kpis, "kpis");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<KpiViews.Target> targets(
            RuntimeSession session,
            PublishedKpiPin pin
    ) {
        var actor = actor(session);
        Objects.requireNonNull(pin, "pin");
        var period = currentPeriod(pin.periodType(), LocalDate.now(clock));
        return kpis.applicableTargets(actor, period.startInclusive()).stream()
                .filter(item -> item.target().kpiId() == pin.kpiId())
                .filter(item -> item.target().kpiVersionId()
                        == pin.kpiVersionId())
                .filter(item -> item.definition().id()
                        == pin.kpiVersionId())
                .filter(item -> item.definition().periodType()
                        == pin.periodType())
                .filter(item -> item.target().period().equals(period))
                .map(item -> KpiMapping.target(
                        item.target(), item.definition(),
                        item.latestCalculation()))
                .toList();
    }

    static KpiPeriod currentPeriod(KpiPeriodType type, LocalDate date) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(date, "date");
        var firstOfMonth = date.withDayOfMonth(1);
        var start = switch (type) {
            case MONTH -> firstOfMonth;
            case QUARTER -> firstOfMonth.withMonth(
                    ((firstOfMonth.getMonthValue() - 1) / 3) * 3 + 1);
            case YEAR -> firstOfMonth.withMonth(1);
        };
        return KpiPeriod.starting(type, start);
    }

    private static KpiActor actor(RuntimeSession session) {
        Objects.requireNonNull(session, "session");
        if (session.systemId() <= 0 || session.memberId() <= 0) {
            throw new DashboardException(
                    "DASHBOARD_RUNTIME_SESSION_INVALID",
                    "Current system member context is invalid");
        }
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new DashboardException(
                    "DASHBOARD_TENANT_REQUIRED",
                    "Select an active tenant before reading a dashboard");
        }
        return new KpiActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    public record PublishedKpiPin(
            long kpiId,
            long kpiVersionId,
            KpiPeriodType periodType
    ) {
        public PublishedKpiPin {
            if (kpiId <= 0 || kpiVersionId <= 0 || periodType == null) {
                throw new IllegalArgumentException(
                        "Published dashboard KPI pin is incomplete");
            }
        }
    }
}
