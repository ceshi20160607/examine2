package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Flow-owner boundary for one bounded, permission-projected instance history. */
public interface AiFlowInstanceHistoryReadFacade {
    int MAX_LIMIT = 20;
    int MAX_COMMENT_CHARACTERS = 2_000;

    Result query(Request request);

    record Request(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String instanceId,
            int limit
    ) {
        public Request {
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            instanceId = positiveDecimal(instanceId, "instanceId");
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException("limit is invalid");
            }
        }
    }

    record Result(
            String instanceId,
            String status,
            long total,
            String route,
            List<Event> events
    ) {
        public Result {
            instanceId = positiveDecimal(instanceId, "instanceId");
            status = state(status, "status");
            if (total < 0) throw new IllegalArgumentException("total is invalid");
            route = text(route, "route", 1_000, false);
            events = List.copyOf(Objects.requireNonNull(events, "events"));
            if (events.size() > MAX_LIMIT || events.size() > total) {
                throw new IllegalArgumentException("events are invalid");
            }
            Event previous = null;
            for (var event : events) {
                if (previous != null && (event.sequence() <= previous.sequence()
                        || event.occurredAt().isBefore(previous.occurredAt()))) {
                    throw new IllegalArgumentException(
                            "events must preserve owner chronology");
                }
                previous = event;
            }
        }
    }

    record Event(
            int sequence,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorMemberId,
            String comment,
            Instant occurredAt
    ) {
        public Event {
            if (sequence < 1) {
                throw new IllegalArgumentException("sequence is invalid");
            }
            eventType = state(eventType, "eventType");
            fromStatus = nullableState(fromStatus, "fromStatus");
            toStatus = nullableState(toStatus, "toStatus");
            if (toStatus == null) {
                throw new IllegalArgumentException("toStatus is invalid");
            }
            if (actorMemberId != null) {
                actorMemberId = positiveDecimal(actorMemberId, "actorMemberId");
            }
            comment = nullableText(
                    comment, "comment", MAX_COMMENT_CHARACTERS, true);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    private static String nullableState(String value, String name) {
        return value == null ? null : state(value, name);
    }

    private static String state(String value, String name) {
        if (value == null || !value.matches("^[A-Z][A-Z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String nullableText(
            String value, String name, int maximum, boolean allowEmpty) {
        return value == null ? null : text(value, name, maximum, allowEmpty);
    }

    private static String text(
            String value, String name, int maximum, boolean allowEmpty) {
        if (value == null || !allowEmpty && value.isBlank()
                || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " is invalid");
    }
}
