package com.unique.examine.module.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardDraftTest {
    @Test
    void preservesWidgetOrderAndCopiesTheInputList() {
        var first = widget("count", 0);
        var second = widget("list", 4);
        var source = new ArrayList<>(List.of(first, second));

        var draft = new DashboardDraft(source);
        source.clear();

        assertThat(draft.widgets()).containsExactly(first, second);
        assertThat(draft.widgets()).isUnmodifiable();
    }

    @Test
    void boundsAndOverlapUseHalfOpenDesktopRectangles() {
        var left = new DashboardGrid(0, 0, 6, 2);

        assertThat(left.withinBounds()).isTrue();
        assertThat(new DashboardGrid(6, 0, 6, 2).withinBounds()).isTrue();
        assertThat(left.overlaps(new DashboardGrid(6, 0, 6, 2))).isFalse();
        assertThat(left.overlaps(new DashboardGrid(5, 1, 2, 2))).isTrue();
        assertThat(new DashboardGrid(11, 99, 2, 2).withinBounds()).isFalse();
        assertThat(new DashboardGrid(
                Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 1)
                .withinBounds()).isFalse();
    }

    @Test
    void rejectsMoreThanTwentyWidgets() {
        var widgets = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(index -> widget("w" + index, index))
                .toList();

        assertThatThrownBy(() -> new DashboardDraft(widgets))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(
                        ((DashboardException) error).code())
                        .isEqualTo("DASHBOARD_DRAFT_INVALID"));
    }

    @Test
    void supportsMainstreamWidgetsAndSafeRefreshDrillThroughBehavior() {
        var behavior = new DashboardWidgetBehavior(
                30, "/modules/orders/records/{recordId}", "emphasis");
        var ranking = new DashboardWidgetDraft(
                "ranking", DashboardWidgetType.RANKING, "Ranking", 100L,
                null, null,
                new com.unique.examine.module.datasource.statistics.domain
                        .StatisticsRequest(
                        com.unique.examine.module.datasource.statistics.domain
                                .StatisticsAggregation.COUNT,
                        null,
                        new com.unique.examine.module.datasource.statistics.domain
                                .StatisticsRequest.Grouping("status", 10),
                        null), behavior, new DashboardGrid(0, 0, 6, 3));

        assertThat(ranking.behavior().refreshSeconds()).isEqualTo(30);
        assertThat(ranking.behavior().styleVariant()).isEqualTo("EMPHASIS");
        assertThatThrownBy(() -> new DashboardWidgetBehavior(
                30, "https://evil.example/redirect", "STANDARD"))
                .isInstanceOf(DashboardException.class);
        assertThatThrownBy(() -> new DashboardWidgetBehavior(
                5, "/modules/orders", "STANDARD"))
                .isInstanceOf(DashboardException.class);
    }

    private static DashboardWidgetDraft widget(String code, int y) {
        return new DashboardWidgetDraft(
                code, DashboardWidgetType.STAT_COUNT, code,
                100, null, new DashboardGrid(0, y, 4, 1));
    }
}
