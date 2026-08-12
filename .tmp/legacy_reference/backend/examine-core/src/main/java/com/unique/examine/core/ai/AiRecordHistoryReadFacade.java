package com.unique.examine.core.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Module-owner boundary for one bounded, permission-projected record history page. */
public interface AiRecordHistoryReadFacade {
    int MAX_LIMIT = 20;

    Result query(Request request);

    record Request(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode,
            String recordId,
            int limit
    ) {
        public Request {
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = validateModuleCode(moduleCode);
            recordId = positiveDecimal(recordId, "recordId");
            bounded(limit);
        }
    }

    record Result(
            String moduleCode,
            String recordId,
            long total,
            String route,
            List<History> items
    ) {
        public Result {
            moduleCode = validateModuleCode(moduleCode);
            recordId = positiveDecimal(recordId, "recordId");
            if (total < 0) throw new IllegalArgumentException("total is invalid");
            route = text(route, "route", 1_000);
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (items.size() > MAX_LIMIT || items.size() > total) {
                throw new IllegalArgumentException("items are invalid");
            }
        }
    }

    record History(
            String historyId,
            long recordVersion,
            String action,
            String actorMemberId,
            LocalDateTime occurredAt,
            List<Diff> diff
    ) {
        public History {
            historyId = positiveDecimal(historyId, "historyId");
            if (recordVersion < 0) {
                throw new IllegalArgumentException("recordVersion is invalid");
            }
            action = text(action, "action", 64);
            if (actorMemberId != null) {
                actorMemberId = positiveDecimal(actorMemberId, "actorMemberId");
            }
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
            diff = List.copyOf(Objects.requireNonNull(diff, "diff"));
            if (diff.size() > 500) {
                throw new IllegalArgumentException("diff is invalid");
            }
        }
    }

    record Diff(
            String fieldCode,
            String beforeValueJson,
            String afterValueJson,
            boolean masked
    ) {
        public Diff {
            fieldCode = text(fieldCode, "fieldCode", 128);
            if (masked && (beforeValueJson != null || afterValueJson != null)) {
                throw new IllegalArgumentException("masked history values must be absent");
            }
            if (!masked && Objects.equals(beforeValueJson, afterValueJson)) {
                throw new IllegalArgumentException("history values are unchanged");
            }
            beforeValueJson = json(beforeValueJson, "beforeValueJson");
            afterValueJson = json(afterValueJson, "afterValueJson");
        }
    }

    private static String json(String value, String name) {
        if (value != null && value.codePointCount(0, value.length()) > 20_000) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String validateModuleCode(String value) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("moduleCode is invalid");
        }
        return value;
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

    private static String text(String value, String name, int maximum) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " is invalid");
    }

    private static void bounded(int value) {
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit is invalid");
        }
    }
}
