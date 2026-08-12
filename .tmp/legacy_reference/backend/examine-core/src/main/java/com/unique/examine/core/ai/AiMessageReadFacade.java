package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Narrow Event-owner port for one bounded current-member inbox projection. */
public interface AiMessageReadFacade {
    int MAX_LIMIT = 20;

    Result query(Request request);

    enum Status { ALL, UNREAD, READ, ARCHIVED }

    record Request(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            Status status,
            int limit
    ) {
        public Request {
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            status = Objects.requireNonNull(status, "status");
            bounded(limit, "limit");
        }
    }

    record Result(
            Status status,
            long unreadCount,
            long total,
            List<Message> items
    ) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            nonNegative(unreadCount, "unreadCount");
            nonNegative(total, "total");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (items.size() > MAX_LIMIT || items.size() > total) {
                throw new IllegalArgumentException("items are invalid");
            }
        }
    }

    record Message(
            String id,
            String templateCode,
            String title,
            String body,
            Target target,
            String targetPath,
            String status,
            Instant createdAt,
            Instant readAt,
            Instant archivedAt,
            long version
    ) {
        public Message {
            id = positiveDecimal(id, "id");
            templateCode = text(templateCode, "templateCode", 100);
            title = text(title, "title", 200);
            body = text(body, "body", 4_000);
            if (targetPath != null && (targetPath.isBlank()
                    || targetPath.codePointCount(0, targetPath.length()) > 500)) {
                throw new IllegalArgumentException("targetPath is invalid");
            }
            if (!Set.of("UNREAD", "READ", "ARCHIVED").contains(status)) {
                throw new IllegalArgumentException("status is invalid");
            }
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            if (version <= 0) {
                throw new IllegalArgumentException("version is invalid");
            }
            if ("UNREAD".equals(status) && (readAt != null || archivedAt != null)
                    || "READ".equals(status) && (readAt == null || archivedAt != null)
                    || "ARCHIVED".equals(status) && archivedAt == null) {
                throw new IllegalArgumentException("message timestamps are invalid");
            }
        }
    }

    record Target(String type, String id) {
        public Target {
            type = text(type, "target.type", 100);
            id = text(id, "target.id", 200);
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "effectivePermissions are invalid");
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

    private static void nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " is invalid");
    }

    private static void bounded(int value, String name) {
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
