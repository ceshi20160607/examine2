package com.unique.examine.flow.domain;

import java.util.List;
import java.util.Objects;

public record TriggerBinding(
        String moduleCode,
        Event event,
        int priority,
        boolean exclusive,
        List<TriggerCondition> conditions,
        PeriodicSchedule periodicSchedule
) {
    public TriggerBinding {
        Objects.requireNonNull(event, "event");
        if (priority < -1000 || priority > 1000) {
            throw new IllegalArgumentException("Trigger binding priority must be between -1000 and 1000");
        }
        if (conditions == null
                || conditions.size() > 10
                || conditions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Trigger binding accepts at most 10 conditions");
        }
        conditions = List.copyOf(conditions);
        if (event == Event.PERIODIC) {
            if (moduleCode != null
                    || priority != 0
                    || !exclusive
                    || !conditions.isEmpty()
                    || periodicSchedule == null) {
                throw new IllegalArgumentException(
                        "Periodic triggers accept only a schedule and fixed execution semantics");
            }
        } else {
            if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Trigger binding module code is invalid");
            }
            if (periodicSchedule != null) {
                throw new IllegalArgumentException(
                        "Record and import triggers cannot contain a periodic schedule");
            }
        }
    }

    public TriggerBinding(
            String moduleCode,
            Event event,
            int priority,
            boolean exclusive,
            List<TriggerCondition> conditions
    ) {
        this(moduleCode, event, priority, exclusive, conditions, null);
    }

    public TriggerBinding(
            String moduleCode,
            Event event,
            int priority,
            boolean exclusive
    ) {
        this(moduleCode, event, priority, exclusive, List.of(), null);
    }

    public static TriggerBinding periodic(PeriodicSchedule schedule) {
        return new TriggerBinding(null, Event.PERIODIC, 0, true, List.of(), schedule);
    }

    public TriggerBinding forDraftRequester(long requesterMemberId) {
        if (event != Event.PERIODIC) {
            return this;
        }
        return periodic(new PeriodicSchedule(
                periodicSchedule.startAt(),
                periodicSchedule.intervalMinutes(),
                requesterMemberId
        ));
    }

    public enum Event {
        RECORD_ACTIVATED,
        RECORD_CREATED,
        RECORD_UPDATED,
        RECORD_DELETED,
        RECORD_STATUS_CHANGED,
        IMPORT_COMPLETED,
        PERIODIC
    }
}
