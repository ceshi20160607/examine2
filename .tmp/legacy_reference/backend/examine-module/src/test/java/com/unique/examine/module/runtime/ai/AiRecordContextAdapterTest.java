package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiRecordContextFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordContextAdapterTest {
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access", "module.work_order.view",
            "module.work_order.field.name.read",
            "module.work_order.field.secret_note.read");

    @Test
    void delegatesToCanonicalDetailAndProjectsRequestedDisplayValuesOnly()
            throws Exception {
        var session = new AtomicReference<RuntimeSession>();
        var module = new AtomicReference<String>();
        var recordId = new AtomicLong();
        var adapter = new AiRecordContextAdapter((seenSession, seenModule, seenId) -> {
            session.set(seenSession);
            module.set(seenModule);
            recordId.set(seenId);
            return detail();
        });

        var result = adapter.summary(request(
                VIEW, List.of("secret_note", "name", "not_visible")));

        assertThat(session.get()).isEqualTo(new RuntimeSession(0L, 11L, 17L, 13L, VIEW));
        assertThat(module.get()).isEqualTo("work_order");
        assertThat(recordId).hasValue(23);
        assertThat(result.moduleCode()).isEqualTo("work_order");
        assertThat(result.record().values()).containsExactly(
                new AiRecordContextFacade.DisplayValue("secret_note", "******"),
                new AiRecordContextFacade.DisplayValue("name", "Visible name"));
        assertThat(new ObjectMapper().writeValueAsString(result))
                .contains("Visible name", "******")
                .doesNotContain("RAW-NAME", "RAW-SECRET", "RAW-HIDDEN");
        assertThat(AiRecordContextAdapter.class.getMethod(
                        "summary", AiRecordContextFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void deniesRevokedViewBeforeOwnerAndPropagatesCanonicalNotFound() {
        var calls = new AtomicInteger();
        var adapter = new AiRecordContextAdapter((session, module, id) -> {
            calls.incrementAndGet();
            return detail();
        });

        assertThatThrownBy(() -> adapter.summary(request(
                Set.of("system.runtime.access"), List.of("name"))))
                .isInstanceOfSatisfying(BusinessException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("PERMISSION_DENIED");
                    assertThat(failure.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        assertThat(calls).hasValue(0);

        var notFound = new BusinessException(
                "RECORD_NOT_FOUND", "hidden", HttpStatus.NOT_FOUND);
        var hidden = new AiRecordContextAdapter((session, module, id) -> {
            throw notFound;
        });
        assertThatThrownBy(() -> hidden.summary(request(VIEW, List.of("name"))))
                .isSameAs(notFound);
    }

    private static AiRecordContextFacade.Request request(
            Set<String> permissions, List<String> fields
    ) {
        return new AiRecordContextFacade.Request(
                11, 13, 17, permissions, "work_order", "23", fields);
    }

    private static RecordRuntimeViews.RecordDetail detail() {
        return new RecordRuntimeViews.RecordDetail(
                "23", "WO-23", 4, "ACTIVE", "Work order", "41",
                List.of(
                        value("name", "TEXT", "RAW-NAME", "Visible name"),
                        value("secret_note", "SECRET", "RAW-SECRET", "******"),
                        value("hidden", "TEXT", "RAW-HIDDEN", "Hidden")),
                List.of("update"));
    }

    private static RecordRuntimeViews.FieldValue value(
            String code, String type, String raw, String display
    ) {
        return new RecordRuntimeViews.FieldValue(
                code, code, type, raw, display, null);
    }
}
