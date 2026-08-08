package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.examine.core.ai.AiRecordHistoryReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.history.RecordHistoryDiff;
import com.unique.examine.module.runtime.history.RecordHistoryEntry;
import com.unique.examine.module.runtime.history.RecordHistoryPage;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordHistoryReadAdapterTest {
    private static final Set<String> PERMISSIONS = Set.of(
            "system.runtime.access", "module.work_order.view",
            "module.work_order.history.read");

    @Test
    void delegatesToCanonicalHistoryAndDropsMaskedValues() throws Exception {
        var session = new AtomicReference<RuntimeSession>();
        var module = new AtomicReference<String>();
        var recordId = new AtomicLong();
        var page = new AtomicInteger();
        var size = new AtomicInteger();
        var occurredAt = LocalDateTime.of(2026, 8, 4, 12, 30);
        var adapter = new AiRecordHistoryReadAdapter((seenSession, seenModule,
                                                       seenRecordId, seenPage, seenSize) -> {
            session.set(seenSession);
            module.set(seenModule);
            recordId.set(seenRecordId);
            page.set(seenPage);
            size.set(seenSize);
            return new RecordHistoryPage(List.of(new RecordHistoryEntry(
                    "31", "23", 0, "CREATED", null, occurredAt,
                    List.of(
                            new RecordHistoryDiff(
                                    "title", TextNode.valueOf("before"),
                                    TextNode.valueOf("after"), false),
                            new RecordHistoryDiff(
                                    "secret", TextNode.valueOf("RAW-BEFORE"),
                                    TextNode.valueOf("RAW-AFTER"), true)))),
                    1, seenSize, 1);
        });

        var result = adapter.query(request());

        assertThat(session.get()).isEqualTo(new RuntimeSession(
                5, 11, 17, 13L, PERMISSIONS));
        assertThat(module.get()).isEqualTo("work_order");
        assertThat(recordId).hasValue(23);
        assertThat(page).hasValue(1);
        assertThat(size).hasValue(10);
        assertThat(result.route()).isEqualTo(
                "/systems/11/workbench?module=work_order&mode=view&record=23");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.actorMemberId()).isNull();
            assertThat(item.diff()).containsExactly(
                    new AiRecordHistoryReadFacade.Diff(
                            "title", "\"before\"", "\"after\"", false),
                    new AiRecordHistoryReadFacade.Diff(
                            "secret", null, null, true));
            assertThat(item.toString()).doesNotContain("RAW-BEFORE", "RAW-AFTER");
        });
        assertThat(AiRecordHistoryReadAdapter.class.getMethod(
                        "query", AiRecordHistoryReadFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void propagatesCanonicalOwnerDenialWithoutFallback() {
        var denied = new BusinessException(
                "PERMISSION_DENIED", "history revoked", HttpStatus.FORBIDDEN);
        var adapter = new AiRecordHistoryReadAdapter(
                (session, module, id, page, size) -> { throw denied; });

        assertThatThrownBy(() -> adapter.query(request())).isSameAs(denied);
    }

    private static AiRecordHistoryReadFacade.Request request() {
        return new AiRecordHistoryReadFacade.Request(
                5, 11, 13, 17, PERMISSIONS, "work_order", "23", 10);
    }
}
