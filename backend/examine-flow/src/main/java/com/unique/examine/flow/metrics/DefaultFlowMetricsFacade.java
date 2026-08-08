package com.unique.examine.flow.metrics;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@Transactional(readOnly = true)
public class DefaultFlowMetricsFacade implements FlowMetricsFacade {
    private final FlowMetricsRepository repository;

    public DefaultFlowMetricsFacade(FlowMetricsRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Flow metrics repository is required");
        }
        this.repository = repository;
    }

    @Override
    public FlowMetricsSnapshot snapshot(
            FlowSession session,
            LocalDate fromInclusive,
            LocalDate toExclusive
    ) {
        requireRead(session);
        var days = requireRange(fromInclusive, toExclusive);
        var fromInstant = fromInclusive.atStartOfDay().toInstant(ZoneOffset.UTC);
        var toInstant = toExclusive.atStartOfDay().toInstant(ZoneOffset.UTC);
        var facts = repository.findCurrentFacts(session.systemId(), session.tenantId(),
                fromInstant, toInstant);

        var started = new long[days];
        var terminal = new long[days];
        var breakdown = new EnumMap<FlowMetricsSnapshot.TerminalStatus, Long>(
                FlowMetricsSnapshot.TerminalStatus.class);
        for (var status : FlowMetricsSnapshot.TerminalStatus.values()) {
            breakdown.put(status, 0L);
        }
        long pending = 0;
        long terminalInRange = 0;
        for (var fact : facts) {
            if (fact.status() == ApprovalInstance.Status.PENDING) {
                pending++;
            }
            if (within(fact.startedAt(), fromInstant, toInstant)) {
                started[dayIndex(fromInclusive, fact.startedAt())]++;
            }
            if (fact.completedAt() != null
                    && within(fact.completedAt(), fromInstant, toInstant)) {
                terminal[dayIndex(fromInclusive, fact.completedAt())]++;
                terminalInRange++;
                breakdown.compute(terminalStatus(fact.status()),
                        (ignored, count) -> count + 1);
            }
        }

        var flowRoute = "/systems/" + session.systemId() + "/flows";
        var pendingRoute = flowRoute + "?taskStatus=PENDING";
        var rangeRoute = flowRoute + "?from=" + fromInclusive + "&to=" + toExclusive;
        var daily = new ArrayList<FlowMetricsSnapshot.Daily>(days);
        for (int index = 0; index < days; index++) {
            var date = fromInclusive.plusDays(index);
            var dayRoute = flowRoute + "?from=" + date + "&to=" + date.plusDays(1);
            daily.add(new FlowMetricsSnapshot.Daily(date,
                    started[index], terminal[index], dayRoute, dayRoute));
        }
        var statuses = new ArrayList<FlowMetricsSnapshot.TerminalBreakdown>(4);
        for (var status : FlowMetricsSnapshot.TerminalStatus.values()) {
            statuses.add(new FlowMetricsSnapshot.TerminalBreakdown(
                    status, breakdown.get(status),
                    flowRoute + "?status=" + status
                            + "&from=" + fromInclusive + "&to=" + toExclusive));
        }
        return new FlowMetricsSnapshot(fromInclusive, toExclusive,
                new FlowMetricsSnapshot.Metric(pending, pendingRoute),
                new FlowMetricsSnapshot.Metric(terminalInRange, rangeRoute),
                daily, statuses);
    }

    private static int requireRange(LocalDate fromInclusive, LocalDate toExclusive) {
        if (fromInclusive == null || toExclusive == null) {
            throw new IllegalArgumentException("Flow metrics range is required");
        }
        var days = ChronoUnit.DAYS.between(fromInclusive, toExclusive);
        if (days < 1 || days > 31) {
            throw new IllegalArgumentException("Flow metrics range must contain 1 to 31 UTC days");
        }
        return Math.toIntExact(days);
    }

    private static void requireRead(FlowSession session) {
        if (session == null || !session.permissions().contains(FlowPermissions.INSTANCE_READ)) {
            throw new BusinessException("PERMISSION_DENIED",
                    "The authenticated member does not have the required flow permission",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static boolean within(Instant value, Instant fromInclusive, Instant toExclusive) {
        return !value.isBefore(fromInclusive) && value.isBefore(toExclusive);
    }

    private static int dayIndex(LocalDate fromInclusive, Instant value) {
        return Math.toIntExact(ChronoUnit.DAYS.between(fromInclusive,
                value.atZone(ZoneOffset.UTC).toLocalDate()));
    }

    private static FlowMetricsSnapshot.TerminalStatus terminalStatus(
            ApprovalInstance.Status status
    ) {
        return switch (status) {
            case APPROVED -> FlowMetricsSnapshot.TerminalStatus.APPROVED;
            case REJECTED -> FlowMetricsSnapshot.TerminalStatus.REJECTED;
            case WITHDRAWN -> FlowMetricsSnapshot.TerminalStatus.WITHDRAWN;
            case TERMINATED -> FlowMetricsSnapshot.TerminalStatus.TERMINATED;
            case PENDING -> throw new IllegalArgumentException(
                    "Pending Flow instance cannot have a terminal timestamp");
        };
    }
}
