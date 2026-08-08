package com.unique.examine.flow.ai;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class AiFlowInstanceHistoryReadAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-05T03:00:00Z");
    private static final Set<String> PERMISSIONS = Set.of(
            "flow.instance.read", "system.runtime.access",
            "module.purchase_order.view");

    @Test
    void projectsTheLatestBoundedEventsInOwnerChronology() throws Exception {
        var calls = new ArrayList<String>();
        var adapter = new AiFlowInstanceHistoryReadAdapter(
                (session, id) -> {
                    calls.add("instance:" + session.systemId() + ":"
                            + session.tenantId() + ":" + id);
                    return new AiFlowInstanceHistoryReadAdapter.ResolvedInstance(
                            "PENDING", null);
                },
                (session, id) -> {
                    calls.add("history:" + id);
                    return history(id);
                },
                request -> {
                    calls.add("record");
                    return access(request);
                });

        var result = adapter.query(request(PERMISSIONS, 2));

        assertThat(calls).containsExactly("instance:10:20:40", "history:40");
        assertThat(result.instanceId()).isEqualTo("40");
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.route()).isEqualTo("/systems/10/flows");
        assertThat(result.events())
                .extracting(value -> List.of(
                        value.sequence(), value.eventType(), value.toStatus()))
                .containsExactly(
                        List.of(2, "APPROVED", "APPROVED"),
                        List.of(3, "COMPLETION_EXECUTION", "SUCCEEDED"));
        assertThat(AiFlowInstanceHistoryReadAdapter.class
                .getMethod("query", com.unique.examine.core.ai
                        .AiFlowInstanceHistoryReadFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void boundInstancePassesCanonicalViewBeforeHistoryProjection() {
        var calls = new ArrayList<String>();
        var binding = new ApprovalInstance.RecordBinding("purchase_order", 901);
        var adapter = new AiFlowInstanceHistoryReadAdapter(
                (session, id) -> new AiFlowInstanceHistoryReadAdapter
                        .ResolvedInstance("PENDING", binding),
                (session, id) -> {
                    calls.add("history");
                    return history(id);
                },
                request -> {
                    calls.add("record");
                    assertThat(request).isEqualTo(
                            new RuntimeRecordAccessFacade
                                    .RuntimeRecordAccessRequest(
                                    10, 20, 30, PERMISSIONS,
                                    "purchase_order", 901));
                    return access(request);
                });

        adapter.query(request(PERMISSIONS, 3));

        assertThat(calls).containsExactly("record", "history");
    }

    @Test
    void hiddenBoundRecordIsMaskedAndHistoryIsNotRead() {
        var calls = new ArrayList<String>();
        var adapter = new AiFlowInstanceHistoryReadAdapter(
                (session, id) -> new AiFlowInstanceHistoryReadAdapter
                        .ResolvedInstance("PENDING",
                        new ApprovalInstance.RecordBinding(
                                "purchase_order", 901)),
                (session, id) -> {
                    calls.add("history");
                    return history(id);
                },
                request -> {
                    throw new BusinessException(
                            "RECORD_NOT_FOUND", "hidden", HttpStatus.NOT_FOUND);
                });

        var failure = catchThrowableOfType(
                () -> adapter.query(request(PERMISSIONS, 3)),
                BusinessException.class);

        assertThat(failure.code()).isEqualTo("FLOW_INSTANCE_NOT_FOUND");
        assertThat(failure.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(calls).isEmpty();
    }

    @Test
    void requiresLiveReadPermissionBeforeInstanceResolution() {
        var calls = new ArrayList<String>();
        var adapter = new AiFlowInstanceHistoryReadAdapter(
                (session, id) -> {
                    calls.add("instance");
                    return new AiFlowInstanceHistoryReadAdapter.ResolvedInstance(
                            "PENDING", null);
                },
                (session, id) -> history(id),
                AiFlowInstanceHistoryReadAdapterTest::access);

        var failure = catchThrowableOfType(
                () -> adapter.query(request(Set.of(), 3)),
                BusinessException.class);

        assertThat(failure.code()).isEqualTo("PERMISSION_DENIED");
        assertThat(calls).isEmpty();
    }

    @Test
    void foreignTenantInstanceUsesStableNotFoundSemantics() {
        var adapter = new AiFlowInstanceHistoryReadAdapter(
                (session, id) -> {
                    throw new ApprovalDomainException(
                            ApprovalDomainException.Code.INSTANCE_NOT_FOUND,
                            "missing");
                },
                (session, id) -> history(id),
                AiFlowInstanceHistoryReadAdapterTest::access);

        var failure = catchThrowableOfType(
                () -> adapter.query(request(PERMISSIONS, 3)),
                BusinessException.class);

        assertThat(failure.code()).isEqualTo("FLOW_INSTANCE_NOT_FOUND");
        assertThat(failure.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static com.unique.examine.core.ai
            .AiFlowInstanceHistoryReadFacade.Request request(
                    Set<String> permissions, int limit) {
        return new com.unique.examine.core.ai
                .AiFlowInstanceHistoryReadFacade.Request(
                1, 10, 20, 30, permissions, "40", limit);
    }

    private static RuntimeRecordAccessFacade.RuntimeRecordAccess access(
            RuntimeRecordAccessFacade.RuntimeRecordAccessRequest request) {
        return new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                Long.toString(request.recordId()), 1, true);
    }

    private static FlowViews.History history(long instanceId) {
        return new FlowViews.History(Long.toString(instanceId), List.of(
                event(1, "STARTED", null, "PENDING", "30", "", NOW),
                event(2, "APPROVED", "PENDING", "APPROVED", "31",
                        "Looks good", NOW.plusSeconds(1)),
                event(3, "COMPLETION_EXECUTION", null, "SUCCEEDED", null,
                        null, NOW.plusSeconds(2))));
    }

    private static FlowViews.HistoryEvent event(
            int sequence,
            String type,
            String fromStatus,
            String toStatus,
            String actorId,
            String comment,
            Instant occurredAt
    ) {
        return new FlowViews.HistoryEvent(
                sequence, type, actorId, fromStatus, toStatus, comment,
                occurredAt.toString(), null, null, null, null, null, null);
    }
}
