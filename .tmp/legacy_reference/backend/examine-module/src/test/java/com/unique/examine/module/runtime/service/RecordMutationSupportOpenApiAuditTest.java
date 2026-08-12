package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.runtime.history.RecordHistoryAppend;
import com.unique.examine.module.runtime.history.RecordHistoryDiffCodec;
import com.unique.examine.module.runtime.history.RecordHistoryPage;
import com.unique.examine.module.runtime.history.RecordHistoryRepository;
import com.unique.examine.module.runtime.history.RecordHistoryWriter;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecordMutationSupportOpenApiAuditTest {

    @Test
    void openApiCreateFactProducesOneHistoryOneOpenApiAuditAndOneOutboxEvent() {
        var histories = new ArrayList<RecordHistoryAppend>();
        var audits = new ArrayList<OperationAudit>();
        var events = new ArrayList<OutboxEvent>();
        var objectMapper = new ObjectMapper();
        var history = new RecordHistoryWriter(
                new RecordHistoryRepository() {
                    @Override
                    public String append(RecordHistoryAppend value) {
                        histories.add(value);
                        return Long.toString(value.historyId());
                    }

                    @Override
                    public RecordHistoryPage page(
                            long systemId,
                            long tenantId,
                            long recordId,
                            int page,
                            int size
                    ) {
                        throw new UnsupportedOperationException();
                    }
                },
                new RecordHistoryDiffCodec(objectMapper),
                new IdService());
        var support = new RecordMutationSupport(
                null,
                new CapturingAudit(audits),
                event -> {
                    events.add(event);
                    return 1;
                },
                objectMapper,
                history);

        support.changed(
                new RuntimeSession(7, 11, 17, 13L, Set.of()),
                23, 0, "RECORD_CREATED", null, Map.of("status", "ACTIVE"),
                "request-1", "trace-1", Set.of(), "OPENAPI");

        assertThat(histories).singleElement()
                .satisfies(value -> assertThat(value.action()).isEqualTo("RECORD_CREATED"));
        assertThat(audits).singleElement().satisfies(value -> {
            assertThat(value.actor().sourceType()).isEqualTo("OPENAPI");
            assertThat(value.actor().accountId()).isEqualTo(7);
            assertThat(value.action()).isEqualTo("RECORD_CREATED");
        });
        assertThat(events).singleElement()
                .satisfies(value -> assertThat(value.eventType()).isEqualTo("RUNTIME_RECORD_CHANGED"));

        support.changed(
                new RuntimeSession(0, 11, 17, 13L, Set.of()),
                24, 0, "RECORD_DRAFT_CREATED", null, Map.of("status", "DRAFT"),
                "request-ai", "trace-ai", Set.of(), "WEB");

        assertThat(audits.get(1).actor().accountId()).isNull();
        assertThat(histories.get(1).actorMemberId()).isEqualTo(17);
    }

    private record CapturingAudit(List<OperationAudit> values) implements OperationAuditFacade {
        @Override
        public void recordSuccess(OperationAudit audit) {
            values.add(audit);
        }

        @Override
        public void recordDenied(OperationAudit audit) {
            throw new AssertionError("No denied audit expected");
        }

        @Override
        public void recordFailed(OperationAudit audit) {
            throw new AssertionError("No failed audit expected");
        }
    }
}
