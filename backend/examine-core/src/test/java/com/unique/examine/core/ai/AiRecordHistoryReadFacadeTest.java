package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordHistoryReadFacadeTest {
    private static final LocalDateTime OCCURRED =
            LocalDateTime.parse("2026-08-04T09:00:00");

    @Test
    void resultCopiesItemsAndPermissionProjectedDiffs() {
        var diffs = new ArrayList<>(List.of(
                new AiRecordHistoryReadFacade.Diff(
                        "title", "\"Before\"", "\"After\"", false),
                new AiRecordHistoryReadFacade.Diff(
                        "secret", null, null, true)));
        var history = new AiRecordHistoryReadFacade.History(
                "9", 0, "CREATE", "30", OCCURRED, diffs);
        diffs.clear();
        var items = new ArrayList<>(List.of(history));
        var result = new AiRecordHistoryReadFacade.Result(
                "work_order", "40", 1,
                "/systems/10/workbench?module=work_order&mode=view&record=40",
                items);
        items.clear();

        assertThat(result.items()).containsExactly(history);
        assertThat(result.items().getFirst().diff())
                .containsExactly(
                        new AiRecordHistoryReadFacade.Diff(
                                "title", "\"Before\"", "\"After\"", false),
                        new AiRecordHistoryReadFacade.Diff(
                                "secret", null, null, true));
    }

    @Test
    void rejectsInvalidLimitsAndDiffsThatLeakOrDoNotChange() {
        assertThatThrownBy(() -> request(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> request(21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> new AiRecordHistoryReadFacade.Diff(
                "secret", "\"raw\"", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("masked");
        assertThatThrownBy(() -> new AiRecordHistoryReadFacade.Diff(
                "title", "\"same\"", "\"same\"", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unchanged");
    }

    private static AiRecordHistoryReadFacade.Request request(int limit) {
        return new AiRecordHistoryReadFacade.Request(
                1, 10, 20, 30,
                Set.of("system.runtime.access", "module.work_order.view"),
                "work_order", "40", limit);
    }
}
