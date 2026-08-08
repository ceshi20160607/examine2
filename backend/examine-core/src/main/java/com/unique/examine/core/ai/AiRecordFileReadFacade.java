package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** File-owner boundary for one bounded, safe record-attachment metadata page. */
public interface AiRecordFileReadFacade {
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
            List<File> items
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

    record File(
            String fileId,
            String originalName,
            String mediaType,
            long size,
            String uploaderMemberId,
            Instant createdAt,
            Instant referencedAt
    ) {
        public File {
            fileId = positiveDecimal(fileId, "fileId");
            originalName = text(originalName, "originalName", 255);
            mediaType = text(mediaType, "mediaType", 150);
            if (size < 0) throw new IllegalArgumentException("size is invalid");
            uploaderMemberId = positiveDecimal(
                    uploaderMemberId, "uploaderMemberId");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            referencedAt = Objects.requireNonNull(referencedAt, "referencedAt");
            if (referencedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("file timestamps are invalid");
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
