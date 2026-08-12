package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.id.IdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;

@Component
public class ReportScheduleDueProducer {
    private static final int CLAIM_LIMIT = 20;

    private final ReportScheduleStore store;
    private final IdService ids;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public ReportScheduleDueProducer(
            ReportScheduleStore store,
            IdService ids,
            PlatformTransactionManager transactionManager
    ) {
        this(store, ids, Clock.systemUTC(), requiresNew(transactionManager));
    }

    ReportScheduleDueProducer(
            ReportScheduleStore store,
            IdService ids,
            Clock clock
    ) {
        this(store, ids, clock, directTransactions());
    }

    ReportScheduleDueProducer(
            ReportScheduleStore store,
            IdService ids,
            Clock clock,
            TransactionOperations transactions
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = Objects.requireNonNull(transactions,
                "transactions");
    }

    @Scheduled(fixedDelayString =
            "${examine.jobs.report-schedule.due-delay-ms:500}")
    public void produce() {
        var now = clock.instant();
        var candidates = transactions.execute(ignored ->
                store.lockDueSchedules(now, CLAIM_LIMIT));
        if (candidates == null) {
            return;
        }
        for (var schedule : candidates) {
            try {
                transactions.execute(ignored -> {
                    produce(schedule, now);
                    return null;
                });
            } catch (RuntimeException isolatedFailure) {
                // One malformed/stale schedule must not block other due rows.
            }
        }
    }

    private void produce(
            ReportScheduleStore.Schedule schedule,
            java.time.Instant now
    ) {
            if (!schedule.enabled() || schedule.nextFireAt() == null
                    || schedule.nextFireAt().isAfter(now)) {
                return;
            }
            var scheduledAt = schedule.nextFireAt();
            var key = "schedule:" + schedule.id() + ":"
                    + scheduledAt.toEpochMilli();
            var requestId = "report-schedule-" + schedule.id();
            var occurrence = new ReportScheduleStore.Occurrence(
                    ids.nextId(), schedule.systemId(), schedule.tenantId(),
                    schedule.id(), schedule.version(), schedule.code(),
                    schedule.name(), schedule.reportId(), schedule.reportCode(),
                    schedule.ownerAccountId(), schedule.ownerMemberId(),
                    schedule.recipientMemberIds(), scheduledAt, key,
                    ReportScheduleStore.OccurrenceStatus.PENDING, 0,
                    ReportScheduleStore.MAX_ATTEMPTS, null, null, null, null,
                    null, 0, false, 0, null, null, now, null, requestId,
                    requestId, null, null, now, now, 0);
            store.insertOccurrenceIfAbsent(occurrence);
            var next = schedule.cadence().nextAfter(scheduledAt,
                    ReportScheduleTiming.zone(schedule.timeZone()));
            store.advanceSchedule(schedule.id(), schedule.version(),
                    scheduledAt, scheduledAt, next, now);
    }

    private static TransactionOperations requiresNew(
            PlatformTransactionManager transactionManager
    ) {
        var template = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager"));
        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static TransactionOperations directTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }
}
