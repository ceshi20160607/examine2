package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordCommentReadFacadeTest {
    private static final Instant CREATED =
            Instant.parse("2026-08-04T09:00:00Z");

    @Test
    void requestAndResultAreBoundedImmutableSafeSnapshots() {
        var request = request(20);
        var mentions = new ArrayList<>(List.of("31", "32"));
        var comment = new AiRecordCommentReadFacade.Comment(
                "1", null, "30", "Visible comment", false, 2,
                CREATED, CREATED.plusSeconds(1), mentions);
        mentions.clear();
        var items = new ArrayList<>(List.of(comment));
        var result = new AiRecordCommentReadFacade.Result(
                request.moduleCode(), request.recordId(), 1,
                "/systems/10/workbench?module=work_order&mode=view&record=40",
                items);
        items.clear();

        assertThat(request.effectivePermissions())
                .containsExactlyInAnyOrder("system.runtime.access",
                        "module.work_order.view");
        assertThat(result.items()).containsExactly(comment);
        assertThat(result.items().getFirst().mentionedMemberIds())
                .containsExactly("31", "32");
    }

    @Test
    void rejectsInvalidLimitsScopesAndUnsafeCommentShapes() {
        assertThatThrownBy(() -> request(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> request(21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> new AiRecordCommentReadFacade.Request(
                1, 10, 20, 30, Set.of(), "bad-code", "40", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("moduleCode");
        assertThatThrownBy(() -> new AiRecordCommentReadFacade.Comment(
                "1", null, "30", "deleted body", true, 1,
                CREATED, CREATED, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
        assertThatThrownBy(() -> new AiRecordCommentReadFacade.Comment(
                "1", null, "30", "body", false, 1,
                CREATED, CREATED, List.of("31", "31")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mentionedMemberIds");
    }

    private static AiRecordCommentReadFacade.Request request(int limit) {
        return new AiRecordCommentReadFacade.Request(
                1, 10, 20, 30,
                Set.of("system.runtime.access", "module.work_order.view"),
                "work_order", "40", limit);
    }
}
