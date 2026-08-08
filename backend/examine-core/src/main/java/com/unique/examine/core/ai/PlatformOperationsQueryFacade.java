package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Live, bounded platform operations owner boundary.
 *
 * <p>The boundary accepts no business-context identifier or free-form filter.
 * Dedicated providers compose coarse platform projections without granting the
 * platform owner direct access to another module's persistence.</p>
 */
public interface PlatformOperationsQueryFacade {
    int MAX_LIMIT = 50;

    Result query(Request request);

    enum QueryKind {
        PERSONAL_TASKS,
        AI_QUOTA,
        SERVICE_HEALTH,
        AGENT_ACTIVITY
    }

    record Request(
            long accountId,
            long authorizationEpoch,
            QueryKind queryKind,
            int limit
    ) {
        public Request {
            positive(accountId, "accountId");
            positive(authorizationEpoch, "authorizationEpoch");
            queryKind = Objects.requireNonNull(queryKind, "queryKind");
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException("limit must be within 1..50");
            }
        }
    }

    sealed interface Result permits PersonalTasksResult, AiQuotaResult,
            ServiceHealthResult, AgentActivityResult {
        QueryKind queryKind();
    }

    record PersonalTasksResult(List<PersonalTask> tasks) implements Result {
        public PersonalTasksResult {
            tasks = copyBounded(tasks, "tasks");
            descending(tasks.stream().map(PersonalTask::createdAt).toList(),
                    "tasks");
        }

        @Override
        public QueryKind queryKind() {
            return QueryKind.PERSONAL_TASKS;
        }
    }

    record PersonalTask(
            String taskId,
            String title,
            Instant dueAt,
            PlatformTaskFacade.Priority priority,
            PlatformTaskFacade.Status status,
            PlatformTaskFacade.Source source,
            Instant createdAt
    ) {
        public PersonalTask {
            taskId = positiveDecimal(taskId, "taskId");
            title = text(title, "title", 200);
            priority = Objects.requireNonNull(priority, "priority");
            status = Objects.requireNonNull(status, "status");
            source = Objects.requireNonNull(source, "source");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /** Current published daily limits and the matching UTC bucket counters. */
    record AiQuotaResult(
            Instant bucketStart,
            Instant bucketEnd,
            int requestLimit,
            int requestCount,
            int remainingRequests,
            long tokenLimit,
            long usedTokens,
            long reservedTokens,
            long remainingTokens,
            int concurrencyLimit,
            int runningCount,
            int remainingConcurrency
    ) implements Result {
        public AiQuotaResult {
            bucketStart = Objects.requireNonNull(bucketStart, "bucketStart");
            bucketEnd = Objects.requireNonNull(bucketEnd, "bucketEnd");
            if (!bucketStart.isBefore(bucketEnd)) {
                throw new IllegalArgumentException("quota bucket is invalid");
            }
            positive(requestLimit, "requestLimit");
            nonNegative(requestCount, "requestCount");
            nonNegative(tokenLimit, "tokenLimit");
            nonNegative(usedTokens, "usedTokens");
            nonNegative(reservedTokens, "reservedTokens");
            positive(concurrencyLimit, "concurrencyLimit");
            nonNegative(runningCount, "runningCount");
            if (remainingRequests != remaining(requestLimit, requestCount)
                    || remainingTokens != remaining(
                    tokenLimit, saturatedAdd(usedTokens, reservedTokens))
                    || remainingConcurrency != remaining(
                    concurrencyLimit, runningCount)) {
                throw new IllegalArgumentException(
                        "quota remaining counts are inconsistent");
            }
        }

        @Override
        public QueryKind queryKind() {
            return QueryKind.AI_QUOTA;
        }
    }

    enum ServiceName {
        DATABASE,
        REDIS,
        PLATFORM_AI_CONFIGURATION,
        PLATFORM_AI_PROVIDER
    }

    enum HealthState { UP, DEGRADED, UNKNOWN }

    record ServiceHealthResult(
            Instant observedAt,
            List<ServiceHealth> services
    ) implements Result {
        public ServiceHealthResult {
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
            services = List.copyOf(Objects.requireNonNull(services, "services"));
            if (services.size() != ServiceName.values().length
                    || !services.stream().map(ServiceHealth::service)
                    .collect(java.util.stream.Collectors.toSet())
                    .equals(EnumSet.allOf(ServiceName.class))) {
                throw new IllegalArgumentException(
                        "service health must contain each coarse service once");
            }
        }

        @Override
        public QueryKind queryKind() {
            return QueryKind.SERVICE_HEALTH;
        }
    }

    record ServiceHealth(ServiceName service, HealthState state) {
        public ServiceHealth {
            service = Objects.requireNonNull(service, "service");
            state = Objects.requireNonNull(state, "state");
        }
    }

    record AgentActivityResult(List<AgentActivity> activities)
            implements Result {
        public AgentActivityResult {
            activities = copyBounded(activities, "activities");
            descending(activities.stream().map(AgentActivity::occurredAt).toList(),
                    "activities");
        }

        @Override
        public QueryKind queryKind() {
            return QueryKind.AGENT_ACTIVITY;
        }
    }

    record AgentActivity(
            String event,
            Instant occurredAt,
            String operation,
            String resultCode,
            String requestId,
            String traceId
    ) {
        public AgentActivity {
            event = code(event, "event", 32);
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
            operation = code(operation, "operation", 64);
            resultCode = code(resultCode, "resultCode", 64);
            requestId = token(requestId, "requestId", 128);
            traceId = token(traceId, "traceId", 128);
        }
    }

    /** Implemented by the AI module; it exposes projection data, not storage. */
    @FunctionalInterface
    interface AiQuotaProvider {
        AiQuotaResult current(long accountId);
    }

    /** Implemented by the application owner using coarse readiness probes. */
    @FunctionalInterface
    interface ServiceHealthProvider {
        ServiceHealthResult current();
    }

    /** Implemented by the AI module and scoped by the supplied platform account. */
    @FunctionalInterface
    interface AgentActivityProvider {
        AgentActivityResult recent(long accountId, int limit);
    }

    private static <T> List<T> copyBounded(List<T> values, String name) {
        values = List.copyOf(Objects.requireNonNull(values, name));
        if (values.size() > MAX_LIMIT) {
            throw new IllegalArgumentException(name + " are invalid");
        }
        return values;
    }

    private static void descending(List<Instant> values, String name) {
        for (var index = 1; index < values.size(); index++) {
            if (values.get(index).isAfter(values.get(index - 1))) {
                throw new IllegalArgumentException(name + " must be newest first");
            }
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static int remaining(int limit, int used) {
        return Math.max(0, limit - Math.min(limit, used));
    }

    private static long remaining(long limit, long used) {
        return Math.max(0, limit - Math.min(limit, used));
    }

    private static String text(String value, String name, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String code(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[A-Z][A-Z0-9_]{0," + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String token(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
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

    private static void nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " is invalid");
    }
}
