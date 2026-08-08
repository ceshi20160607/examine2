package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Collab-owner boundary for one bounded, read-only runtime-record comment page. */
public interface AiRecordCommentReadFacade {
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
            List<Comment> items
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

    record Comment(
            String commentId,
            String parentCommentId,
            String authorMemberId,
            String body,
            boolean deleted,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<String> mentionedMemberIds
    ) {
        public Comment {
            commentId = positiveDecimal(commentId, "commentId");
            if (parentCommentId != null) {
                parentCommentId = positiveDecimal(parentCommentId, "parentCommentId");
            }
            authorMemberId = positiveDecimal(authorMemberId, "authorMemberId");
            if (deleted && body != null || !deleted && (body == null || body.isBlank()
                    || body.codePointCount(0, body.length()) > 4_000)) {
                throw new IllegalArgumentException("body is invalid");
            }
            positive(version, "version");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("comment timestamps are invalid");
            }
            mentionedMemberIds = List.copyOf(Objects.requireNonNull(
                    mentionedMemberIds, "mentionedMemberIds"));
            if (mentionedMemberIds.size() > 100
                    || mentionedMemberIds.stream().map(value -> positiveDecimal(
                    value, "mentionedMemberId")).distinct().count()
                    != mentionedMemberIds.size()) {
                throw new IllegalArgumentException("mentionedMemberIds are invalid");
            }
        }
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
