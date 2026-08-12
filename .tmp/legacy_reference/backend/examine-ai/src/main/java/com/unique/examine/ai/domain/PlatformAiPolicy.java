package com.unique.examine.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PlatformAiPolicy {
    private PlatformAiPolicy() { }

    public static final Set<String> SUPPORTED_OPERATIONS = Set.of(
            "AUTHORIZED_SYSTEMS_QUERY", "SYSTEM_SWITCH_GUIDANCE",
            "PLATFORM_TASK_DRAFT", "PLATFORM_OPERATIONS_QUERY");

    public enum DraftStatus { DRAFT, CHECKED, PUBLISHED }

    public enum DataResidency { PLATFORM_METADATA_ONLY }

    public enum Severity { BLOCKER, WARNING }

    public record Settings(
            Set<String> allowedOperations,
            int maxSystems,
            int dailyRequestQuota,
            int dailyTokenQuota,
            int maxConcurrency,
            boolean strictRedaction,
            DataResidency dataResidency,
            String promptVersion,
            boolean enabled
    ) {
        public Settings {
            allowedOperations = Set.copyOf(Objects.requireNonNull(
                    allowedOperations, "allowedOperations"));
            if (allowedOperations.isEmpty()
                    || !SUPPORTED_OPERATIONS.containsAll(allowedOperations)) {
                throw new IllegalArgumentException(
                        "Platform AI allowed operations are invalid");
            }
            if (maxSystems < 1 || maxSystems > 100
                    || dailyRequestQuota < 1 || dailyRequestQuota > 10_000
                    || dailyTokenQuota < 10_000
                    || dailyTokenQuota > 10_000_000
                    || maxConcurrency < 1 || maxConcurrency > 16) {
                throw new IllegalArgumentException(
                        "Platform AI quota or concurrency limit is invalid");
            }
            if (!strictRedaction) {
                throw new IllegalArgumentException(
                        "Platform AI strict redaction must remain enabled");
            }
            dataResidency = Objects.requireNonNull(dataResidency, "dataResidency");
            if (dataResidency != DataResidency.PLATFORM_METADATA_ONLY) {
                throw new IllegalArgumentException(
                        "Platform AI data residency is invalid");
            }
            promptVersion = token(promptVersion, "promptVersion", 64);
        }
    }

    public record Draft(
            long id,
            long revision,
            DraftStatus status,
            long providerId,
            long providerVersion,
            Settings settings,
            String draftHash,
            Long activeVersionId,
            Instant updatedAt,
            long updatedBy
    ) {
        public Draft {
            if (id <= 0 || revision < 1 || providerId <= 0
                    || providerVersion < 0 || updatedBy <= 0) {
                throw new IllegalArgumentException("Platform AI policy ids are invalid");
            }
            status = Objects.requireNonNull(status, "status");
            settings = Objects.requireNonNull(settings, "settings");
            hash(draftHash, "draftHash");
            if (activeVersionId != null && activeVersionId <= 0) {
                throw new IllegalArgumentException(
                        "Platform AI active version id is invalid");
            }
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record Issue(
            String code, String path, String message, Severity severity) {
        public Issue {
            code = token(code, "issue code", 64);
            path = text(path, "issue path", 256);
            message = text(message, "issue message", 500);
            severity = Objects.requireNonNull(severity, "severity");
        }
    }

    public record Check(
            long id,
            long policyId,
            long draftRevision,
            String draftHash,
            List<Issue> issues,
            Instant checkedAt,
            long checkedBy
    ) {
        public Check {
            if (id <= 0 || policyId <= 0 || draftRevision < 1 || checkedBy <= 0) {
                throw new IllegalArgumentException(
                        "Platform AI policy check ids are invalid");
            }
            hash(draftHash, "draftHash");
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
            Objects.requireNonNull(checkedAt, "checkedAt");
        }

        public boolean valid() {
            return issues.stream().noneMatch(
                    issue -> issue.severity() == Severity.BLOCKER);
        }
    }

    public record Version(
            long id,
            long policyId,
            int versionNumber,
            long providerId,
            long providerVersion,
            String model,
            Settings settings,
            String snapshotHash,
            Instant publishedAt,
            long publishedBy
    ) {
        public Version {
            if (id <= 0 || policyId <= 0 || versionNumber < 1
                    || providerId <= 0 || providerVersion < 0 || publishedBy <= 0) {
                throw new IllegalArgumentException(
                        "Platform AI policy version ids are invalid");
            }
            model = text(model, "model", 128);
            settings = Objects.requireNonNull(settings, "settings");
            hash(snapshotHash, "snapshotHash");
            Objects.requireNonNull(publishedAt, "publishedAt");
        }
    }

    public record PublishReplay(
            long id,
            long policyId,
            String requestKey,
            String requestHash,
            long versionId,
            Instant createdAt
    ) {
        public PublishReplay {
            if (id <= 0 || policyId <= 0 || versionId <= 0) {
                throw new IllegalArgumentException(
                        "Platform AI policy replay ids are invalid");
            }
            requestKey = token(requestKey, "requestKey", 128);
            hash(requestHash, "requestHash");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    private static void hash(String value, String field) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Platform AI " + field + " is invalid");
        }
    }

    private static String token(String value, String field, int maximum) {
        value = text(value, field, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException("Platform AI " + field + " is invalid");
        }
        return value;
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Platform AI " + field + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException("Platform AI " + field + " is too long");
        }
        return value;
    }
}
