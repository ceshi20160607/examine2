package com.unique.examine.flow.metrics;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFlowMetricsFacadeTest {
    private static final LocalDate FROM = LocalDate.parse("2026-08-01");
    private static final LocalDate TO = LocalDate.parse("2026-08-04");

    @Test
    void exactUtcRangeFillsZeroDaysAndBalancesStableTerminalStatuses() {
        var repository = new InMemoryFlowMetricsRepository();
        repository.add(10, 20, fact(ApprovalInstance.Status.PENDING,
                "2026-07-31T23:59:59.999999Z", null));
        repository.add(10, 20, fact(ApprovalInstance.Status.PENDING,
                "2026-08-01T00:00:00Z", null));
        repository.add(10, 20, fact(ApprovalInstance.Status.APPROVED,
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z"));
        repository.add(10, 20, fact(ApprovalInstance.Status.REJECTED,
                "2026-07-30T00:00:00Z", "2026-08-02T12:00:00Z"));
        repository.add(10, 20, fact(ApprovalInstance.Status.WITHDRAWN,
                "2026-08-02T08:00:00Z", "2026-08-03T23:59:59.999999Z"));
        repository.add(10, 20, fact(ApprovalInstance.Status.TERMINATED,
                "2026-08-04T00:00:00Z", "2026-08-04T00:00:00Z"));
        repository.add(10, 21, fact(ApprovalInstance.Status.PENDING,
                "2026-08-01T00:00:00Z", null));
        var facade = new DefaultFlowMetricsFacade(repository);

        var snapshot = facade.snapshot(readSession(20), FROM, TO);

        assertThat(snapshot.pending().count()).isEqualTo(2);
        assertThat(snapshot.terminalInRange().count()).isEqualTo(3);
        assertThat(snapshot.daily()).extracting(
                        FlowMetricsSnapshot.Daily::date,
                        FlowMetricsSnapshot.Daily::startedCount,
                        FlowMetricsSnapshot.Daily::terminalCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(FROM, 2L, 1L),
                        org.assertj.core.groups.Tuple.tuple(FROM.plusDays(1), 1L, 1L),
                        org.assertj.core.groups.Tuple.tuple(FROM.plusDays(2), 0L, 1L));
        assertThat(snapshot.terminalBreakdown()).extracting(
                        FlowMetricsSnapshot.TerminalBreakdown::status,
                        FlowMetricsSnapshot.TerminalBreakdown::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                FlowMetricsSnapshot.TerminalStatus.APPROVED, 1L),
                        org.assertj.core.groups.Tuple.tuple(
                                FlowMetricsSnapshot.TerminalStatus.REJECTED, 1L),
                        org.assertj.core.groups.Tuple.tuple(
                                FlowMetricsSnapshot.TerminalStatus.WITHDRAWN, 1L),
                        org.assertj.core.groups.Tuple.tuple(
                                FlowMetricsSnapshot.TerminalStatus.TERMINATED, 0L));
        assertThat(snapshot.pending().route())
                .isEqualTo("/systems/10/flows?taskStatus=PENDING")
                .doesNotContain("/api/v1");
        assertThat(snapshot.terminalInRange().route())
                .isEqualTo("/systems/10/flows?from=2026-08-01&to=2026-08-04");
        assertThat(snapshot.terminalBreakdown().getFirst().route())
                .isEqualTo("/systems/10/flows?status=APPROVED"
                        + "&from=2026-08-01&to=2026-08-04");
    }

    @Test
    void emptyReadablePopulationIsZeroFilledRatherThanUnavailable() {
        var snapshot = new DefaultFlowMetricsFacade(new InMemoryFlowMetricsRepository())
                .snapshot(readSession(20), FROM, TO);

        assertThat(snapshot.pending().count()).isZero();
        assertThat(snapshot.daily()).hasSize(3)
                .allSatisfy(day -> assertThat(day.startedCount() + day.terminalCount()).isZero());
        assertThat(snapshot.terminalBreakdown()).hasSize(4)
                .allSatisfy(value -> assertThat(value.count()).isZero());
    }

    @Test
    void missingNativeReadPermissionIsDeniedBeforeRepositoryAccess() {
        FlowMetricsRepository forbiddenRepository = (system, tenant, from, to) -> {
            throw new AssertionError("Repository must not be queried");
        };
        var facade = new DefaultFlowMetricsFacade(forbiddenRepository);
        var session = new FlowSession(1, 10, 20, 30, Set.of());

        assertThatThrownBy(() -> facade.snapshot(session, FROM, TO))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void rangeMustBeInclusiveExclusiveOneToThirtyOneWholeUtcDays() {
        var facade = new DefaultFlowMetricsFacade(new InMemoryFlowMetricsRepository());

        assertThatThrownBy(() -> facade.snapshot(readSession(20), FROM, FROM))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> facade.snapshot(
                readSession(20), FROM, FROM.plusDays(32)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(facade.snapshot(readSession(20), FROM, FROM.plusDays(31)).daily())
                .hasSize(31);
    }

    private static FlowSession readSession(long tenantId) {
        return new FlowSession(1, 10, tenantId, 30,
                Set.of(FlowPermissions.INSTANCE_READ));
    }

    private static FlowMetricInstance fact(
            ApprovalInstance.Status status,
            String startedAt,
            String completedAt
    ) {
        return new FlowMetricInstance(status, Instant.parse(startedAt),
                completedAt == null ? null : Instant.parse(completedAt));
    }
}
