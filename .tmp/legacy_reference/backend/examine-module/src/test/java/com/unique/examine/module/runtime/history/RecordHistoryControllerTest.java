package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordHistoryControllerTest {
    private static final Set<String> HISTORY = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.history.read");

    private RecordHistoryController controller;

    @BeforeEach
    void setUp() {
        var repository = new InMemoryRecordHistoryRepository();
        var occurredAt = LocalDateTime.parse("2026-07-25T12:00:00");
        repository.append(new RecordHistoryAppend(
                101,
                1,
                2,
                3,
                1,
                "RECORD_UPDATED",
                10L,
                occurredAt,
                List.of(new RecordHistoryDiff(
                        "summary",
                        TextNode.valueOf("old"),
                        TextNode.valueOf("new"),
                        false))));
        repository.append(new RecordHistoryAppend(
                102,
                1,
                2,
                3,
                2,
                "RECORD_DRAFT_EXPIRED",
                null,
                occurredAt,
                List.of(new RecordHistoryDiff(
                        "$status",
                        TextNode.valueOf("DRAFT"),
                        TextNode.valueOf("EXPIRED"),
                        false))));
        var service = new RecordHistoryService(
                repository,
                (session, moduleCode, recordId) -> Set.of("summary"));
        controller = new RecordHistoryController(service);
    }

    @Test
    void exposesStringIdsNullableSystemActorAndStableDescendingPage() {
        var response = controller.page(
                1,
                "work_order",
                3,
                1,
                2,
                session(HISTORY),
                request());

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data().page()).isEqualTo(1);
        assertThat(response.data().size()).isEqualTo(2);
        assertThat(response.data().total()).isEqualTo(2);
        assertThat(response.data().items()).hasSize(2);
        var first = response.data().items().getFirst();
        assertThat(first.historyId()).isEqualTo("102");
        assertThat(first.recordVersion()).isEqualTo(2);
        assertThat(first.actorMemberId()).isNull();
        assertThat(first.diff()).singleElement()
                .satisfies(diff -> assertThat(diff.fieldCode()).isEqualTo("$status"));
        var second = response.data().items().get(1);
        assertThat(second.historyId()).isEqualTo("101");
        assertThat(second.actorMemberId()).isEqualTo("10");
        assertThat(second.diff().getFirst().beforeValue().asText()).isEqualTo("old");
        assertThat(second.diff().getFirst().afterValue().asText()).isEqualTo("new");
    }

    @Test
    void rejectsMissingHistoryPermission() {
        assertThatThrownBy(() -> controller.page(
                1,
                "work_order",
                3,
                1,
                20,
                session(Set.of("system.runtime.access", "module.work_order.view")),
                request()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PERMISSION_DENIED"));
    }

    @Test
    void freezesTheHistoryGetMapping() throws Exception {
        var typeMapping = RecordHistoryController.class.getAnnotation(RequestMapping.class);
        var methodMapping = RecordHistoryController.class
                .getMethod(
                        "page",
                        long.class,
                        String.class,
                        long.class,
                        int.class,
                        int.class,
                        Object.class,
                        jakarta.servlet.http.HttpServletRequest.class)
                .getAnnotation(GetMapping.class);

        assertThat(typeMapping.value()).containsExactly(
                "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}"
                        + "/records/{recordId}/history");
        assertThat(methodMapping).isNotNull();
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-1");
        return request;
    }

    private static RequestSession session(Set<String> permissions) {
        return new TestSession(ContextType.SYSTEM, 1L, 2L, 10L, permissions);
    }

    private record TestSession(
            ContextType contextType,
            Long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) implements RequestSession {
        @Override
        public long sessionId() {
            return 100;
        }

        @Override
        public long accountId() {
            return 200;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }
    }

}
