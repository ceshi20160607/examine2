package com.unique.examine.module.report.scheduling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public interface ReportScheduleStore {
    int MAX_RECIPIENTS = 50;
    int MAX_ATTEMPTS = 3;

    Optional<Schedule> findSchedule(
            long systemId, long tenantId, long reportId, long scheduleId);

    Optional<Schedule> findScheduleByCode(
            long systemId, long tenantId, long reportId, String code);

    long countSchedules(long systemId, long tenantId, long reportId);

    List<Schedule> pageSchedules(
            long systemId, long tenantId, long reportId,
            int limit, long offset);

    void insertSchedule(Schedule schedule);

    Schedule updateSchedule(Schedule replacement, long expectedVersion);

    /** Must lock/skip-lock the returned due rows until the owner transaction ends. */
    List<Schedule> lockDueSchedules(Instant now, int limit);

    Schedule advanceSchedule(
            long scheduleId,
            long expectedVersion,
            Instant expectedFireAt,
            Instant firedAt,
            Instant nextFireAt,
            Instant now);

    Occurrence insertOccurrenceIfAbsent(Occurrence occurrence);

    Optional<Occurrence> findOccurrence(
            long systemId, long tenantId, long occurrenceId);

    List<Occurrence> claimPending(
            Instant now, Instant leaseUntil, int limit);

    List<Occurrence> claimRunning(
            Instant now, Instant leaseUntil, int limit);

    Occurrence attachExport(
            long occurrenceId,
            long expectedVersion,
            long exportId,
            String exportStatus,
            Instant availableAt,
            Instant now);

    Occurrence awaitExport(
            long occurrenceId,
            long expectedVersion,
            String exportStatus,
            Instant availableAt,
            Instant now);

    Occurrence retry(
            long occurrenceId,
            long expectedVersion,
            OccurrenceStatus status,
            String failureCode,
            String failureMessage,
            Instant availableAt,
            Instant now);

    Occurrence succeed(
            long occurrenceId,
            long expectedVersion,
            String exportStatus,
            String resultFilename,
            long resultSize,
            long totalRows,
            int processedRows,
            boolean truncated,
            int deliveredRecipientCount,
            Instant now);

    Occurrence fail(
            long occurrenceId,
            long expectedVersion,
            String failureCode,
            String failureMessage,
            int deliveredRecipientCount,
            Instant now);

    Delivery recordDeliveryIfAbsent(Delivery delivery);

    long countDeliveries(
            long systemId, long tenantId, long occurrenceId);

    long countDelivered(
            long systemId, long tenantId, String reportCode,
            long recipientMemberId);

    List<Occurrence> pageDelivered(
            long systemId, long tenantId, String reportCode,
            long recipientMemberId, int limit, long offset);

    Optional<Occurrence> findDelivered(
            long systemId, long tenantId, String reportCode,
            long occurrenceId, long recipientMemberId);

    enum OccurrenceStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

    record Schedule(
            long id,
            long systemId,
            long tenantId,
            long reportId,
            String reportCode,
            String code,
            String name,
            String timeZone,
            ReportScheduleTiming.Kind cadenceKind,
            LocalTime localTime,
            List<DayOfWeek> daysOfWeek,
            List<Long> recipientMemberIds,
            boolean enabled,
            long ownerAccountId,
            long ownerMemberId,
            Instant nextFireAt,
            Instant lastScheduledAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        public Schedule {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || reportId <= 0
                    || cadenceKind == null || localTime == null
                    || ownerAccountId <= 0 || ownerMemberId <= 0
                    || createdAt == null || updatedAt == null
                    || updatedAt.isBefore(createdAt) || version <= 0
                    || enabled != (nextFireAt != null)) {
                throw new IllegalArgumentException("Report schedule state is incomplete");
            }
            reportCode = ReportScheduleStore.code(
                    reportCode, "report code", 64);
            code = ReportScheduleStore.code(code, "schedule code", 64);
            name = text(name, "schedule name", 200);
            timeZone = ReportScheduleTiming.zone(timeZone).getId();
            daysOfWeek = List.copyOf(daysOfWeek);
            recipientMemberIds = members(recipientMemberIds, false);
            new ReportScheduleTiming.Cadence(
                    cadenceKind, localTime, daysOfWeek);
        }

        public ReportScheduleTiming.Cadence cadence() {
            return new ReportScheduleTiming.Cadence(
                    cadenceKind, localTime, daysOfWeek);
        }
    }

    record Occurrence(
            long id,
            long systemId,
            long tenantId,
            long scheduleId,
            long scheduleVersion,
            String scheduleCode,
            String scheduleName,
            long reportId,
            String reportCode,
            long ownerAccountId,
            long ownerMemberId,
            List<Long> configuredRecipientMemberIds,
            Instant scheduledAt,
            String occurrenceKey,
            OccurrenceStatus status,
            int attemptCount,
            int maxAttempts,
            Long exportId,
            String exportStatus,
            String resultFilename,
            Long resultSize,
            Long totalRows,
            int processedRows,
            boolean truncated,
            int deliveredRecipientCount,
            String failureCode,
            String failureMessage,
            Instant availableAt,
            Instant leaseUntil,
            String requestId,
            String traceId,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        public Occurrence {
            if (id <= 0 || systemId <= 0 || tenantId <= 0
                    || scheduleId <= 0 || scheduleVersion <= 0
                    || reportId <= 0 || ownerAccountId <= 0
                    || ownerMemberId <= 0 || scheduledAt == null
                    || status == null || attemptCount < 0
                    || maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS
                    || attemptCount > maxAttempts || createdAt == null
                    || processedRows < 0 || processedRows > 5_000
                    || totalRows != null && totalRows < processedRows
                    || deliveredRecipientCount < 0
                    || updatedAt == null || updatedAt.isBefore(createdAt)
                    || version < 0) {
                throw new IllegalArgumentException("Report schedule occurrence is incomplete");
            }
            scheduleCode = code(scheduleCode, "schedule code", 64);
            scheduleName = text(scheduleName, "schedule name", 200);
            reportCode = code(reportCode, "report code", 64);
            occurrenceKey = text(occurrenceKey, "occurrence key", 128);
            requestId = text(requestId, "request id", 64);
            traceId = text(traceId, "trace id", 64);
            configuredRecipientMemberIds = members(
                    configuredRecipientMemberIds, false);
            if (deliveredRecipientCount > configuredRecipientMemberIds.size()) {
                throw new IllegalArgumentException("Report schedule delivery count is invalid");
            }
            validateOccurrence(status, exportId, exportStatus,
                    resultFilename, resultSize, totalRows, processedRows,
                    truncated, deliveredRecipientCount, failureCode,
                    failureMessage, availableAt, leaseUntil, finishedAt);
        }
    }

    private static void validateOccurrence(
            OccurrenceStatus status,
            Long exportId,
            String exportStatus,
            String resultFilename,
            Long resultSize,
            Long totalRows,
            int processedRows,
            boolean truncated,
            int deliveredRecipientCount,
            String failureCode,
            String failureMessage,
            Instant availableAt,
            Instant leaseUntil,
            Instant finishedAt
    ) {
        var terminal = status == OccurrenceStatus.SUCCEEDED
                || status == OccurrenceStatus.FAILED;
        if (!terminal && (availableAt == null || finishedAt != null)
                || terminal && (availableAt != null || leaseUntil != null
                || finishedAt == null)
                || status == OccurrenceStatus.PENDING
                && (exportId != null || exportStatus != null)
                || !terminal && (resultFilename != null
                || resultSize != null || totalRows != null
                || processedRows != 0 || truncated
                || deliveredRecipientCount != 0)
                || status == OccurrenceStatus.SUCCEEDED
                && (exportId == null || !"SUCCEEDED".equals(exportStatus)
                || resultFilename == null || !resultFilename.endsWith(".xlsx")
                || resultSize == null || resultSize <= 0
                || totalRows == null
                || truncated != (totalRows > processedRows)
                || failureCode != null || failureMessage != null)
                || status == OccurrenceStatus.FAILED
                && (resultFilename != null || resultSize != null
                || totalRows != null || processedRows != 0 || truncated
                || failureCode == null || failureMessage == null)) {
            throw new IllegalArgumentException("Report schedule occurrence transition is invalid");
        }
        if (exportId != null && exportId <= 0
                || exportStatus != null
                && !exportStatus.matches("^[A-Z][A-Z0-9_]{1,31}$")
                || failureCode != null
                && !failureCode.matches("^[A-Z][A-Z0-9_]{1,63}$")
                || failureMessage != null
                && (failureMessage.isBlank() || failureMessage.length() > 500)) {
            throw new IllegalArgumentException("Report schedule occurrence result is invalid");
        }
    }

    record Delivery(
            long id,
            long systemId,
            long tenantId,
            long occurrenceId,
            long recipientMemberId,
            long messageId,
            String deliveryKey,
            Instant deliveredAt
    ) {
        public Delivery {
            if (id <= 0 || systemId <= 0 || tenantId <= 0
                    || occurrenceId <= 0 || recipientMemberId <= 0
                    || messageId <= 0 || deliveredAt == null) {
                throw new IllegalArgumentException("Report schedule delivery fact is incomplete");
            }
            deliveryKey = text(deliveryKey, "delivery key", 256);
        }
    }

    private static List<Long> members(List<Long> values, boolean emptyAllowed) {
        if (values == null || values.size() > MAX_RECIPIENTS
                || !emptyAllowed && values.isEmpty()) {
            throw new IllegalArgumentException("Report schedule recipients are invalid");
        }
        var result = values.stream().sorted().toList();
        if (result.stream().anyMatch(value -> value == null || value <= 0)
                || new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException("Report schedule recipients are invalid");
        }
        return result;
    }

    private static String code(String value, String label, int max) {
        if (value == null || value.length() > max
                || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Report " + label + " is invalid");
        }
        return value;
    }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Report " + label + " is invalid");
        }
        return value.strip();
    }
}
