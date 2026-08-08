package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReportScheduleDueProducerTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void isolatesOneBrokenScheduleAndStillProducesTheNextDueRow() {
        var broken = schedule(10, "broken", NOW.minusSeconds(60));
        var healthy = schedule(11, "healthy", NOW);
        var advanced = new ArrayList<Long>();
        var store = proxy((target, method, arguments) -> switch (method.getName()) {
            case "lockDueSchedules" -> List.of(broken, healthy);
            case "insertOccurrenceIfAbsent" -> {
                var occurrence = (ReportScheduleStore.Occurrence) arguments[0];
                if (occurrence.scheduleId() == broken.id()) {
                    throw new IllegalStateException("isolated failure");
                }
                yield occurrence;
            }
            case "advanceSchedule" -> {
                advanced.add((Long) arguments[0]);
                yield healthy;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });

        new ReportScheduleDueProducer(store, new IdService(),
                Clock.fixed(NOW, ZoneOffset.UTC)).produce();

        assertThat(advanced).containsExactly(healthy.id());
    }

    @Test
    void usesOneStableOccurrenceIdentityAcrossReplay() {
        var due = schedule(10, "daily", NOW);
        var keys = new LinkedHashSet<String>();
        var accepted = new AtomicInteger();
        var store = proxy((target, method, arguments) -> switch (method.getName()) {
            case "lockDueSchedules" -> List.of(due);
            case "insertOccurrenceIfAbsent" -> {
                var occurrence = (ReportScheduleStore.Occurrence) arguments[0];
                keys.add(occurrence.occurrenceKey());
                accepted.compareAndSet(0, 1);
                yield occurrence;
            }
            case "advanceSchedule" -> due;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var producer = new ReportScheduleDueProducer(store, new IdService(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        producer.produce();
        producer.produce();

        assertThat(keys).containsExactly(
                "schedule:10:" + NOW.toEpochMilli());
        assertThat(accepted).hasValue(1);
    }

    private static ReportScheduleStore.Schedule schedule(
            long id,
            String code,
            Instant next
    ) {
        return new ReportScheduleStore.Schedule(id, 1, 2, 3,
                "ops_report", code, code, "UTC",
                ReportScheduleTiming.Kind.DAILY, LocalTime.of(1, 0),
                List.of(), List.of(101L), true, 9, 8, next, null,
                NOW.minusSeconds(3_600), NOW.minusSeconds(60), 1);
    }

    private static ReportScheduleStore proxy(
            java.lang.reflect.InvocationHandler handler
    ) {
        return (ReportScheduleStore) Proxy.newProxyInstance(
                ReportScheduleStore.class.getClassLoader(),
                new Class<?>[]{ReportScheduleStore.class}, handler);
    }
}
