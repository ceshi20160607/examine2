package com.unique.examine.module.dashboard.api;

import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardGrid;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.DashboardVersion;
import com.unique.examine.module.dashboard.domain.DashboardVersionWidget;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardKpiMappingTest {
    @Test
    void kpiValueRequestStoresOnlyKpiIdentityAndLayout() {
        var request = new DashboardRequests.SaveDraft(
                1L, "Home", null, List.of(new DashboardRequests.Widget(
                "revenueKpi", "KPI_VALUE", "Revenue KPI", null, "900",
                null, null, new DashboardRequests.Grid(0, 0, 4, 2))));

        var widget = DashboardMapping.draft(request).widgets().getFirst();

        assertThat(widget.kpiId()).isEqualTo(900L);
        assertThat(widget.dataSourceId()).isNull();
        assertThat(widget.rowLimit()).isNull();
        assertThat(widget.statistics()).isNull();
    }

    @Test
    void kpiValueRequestRejectsDataSourceStatisticsAndRowLimit() {
        var mixedSource = new DashboardRequests.SaveDraft(
                1L, "Home", null, List.of(new DashboardRequests.Widget(
                "mixed", "KPI_VALUE", "Mixed", "100", "900",
                null, null, new DashboardRequests.Grid(0, 0, 4, 2))));
        var withLimit = new DashboardRequests.SaveDraft(
                1L, "Home", null, List.of(new DashboardRequests.Widget(
                "limited", "KPI_VALUE", "Limited", null, "900",
                10, null, new DashboardRequests.Grid(0, 0, 4, 2))));
        var withStatistics = new DashboardRequests.SaveDraft(
                1L, "Home", null, List.of(new DashboardRequests.Widget(
                "statistics", "KPI_VALUE", "Statistics", null, "900",
                null, new DashboardRequests.Statistic(
                "COUNT", null, null, null),
                new DashboardRequests.Grid(0, 0, 4, 2))));

        assertThatThrownBy(() -> DashboardMapping.draft(mixedSource))
                .isInstanceOf(DashboardException.class);
        assertThatThrownBy(() -> DashboardMapping.draft(withLimit))
                .isInstanceOf(DashboardException.class);
        assertThatThrownBy(() -> DashboardMapping.draft(withStatistics))
                .isInstanceOf(DashboardException.class);
    }

    @Test
    void versionViewExposesEveryImmutableKpiPin() {
        var widget = DashboardVersionWidget.kpiValue(
                30, 20, 10, 1, 2, 0, "revenueKpi", "Revenue KPI",
                900, 901, 3, "revenue", "Revenue",
                KpiSubjectType.DEPARTMENT, KpiPeriodType.QUARTER,
                new DashboardGrid(0, 0, 4, 2));
        var version = new DashboardVersion(
                20, 10, 1, 2, 4, 7, "home",
                DashboardPlacement.SYSTEM_HOME, "Home", null,
                List.of(widget), "a".repeat(64), 31,
                Instant.parse("2026-08-03T00:00:00Z"));

        var view = DashboardMapping.version(version, 20L).widgets().getFirst();

        assertThat(view.kpiId()).isEqualTo("900");
        assertThat(view.kpiVersionId()).isEqualTo("901");
        assertThat(view.kpiVersionNumber()).isEqualTo(3);
        assertThat(view.kpiCode()).isEqualTo("revenue");
        assertThat(view.kpiName()).isEqualTo("Revenue");
        assertThat(view.kpiSubjectType()).isEqualTo("DEPARTMENT");
        assertThat(view.kpiPeriodType()).isEqualTo("QUARTER");
        assertThat(view.dataSourceId()).isNull();
    }
}
