package com.unique.examine.module.report.exporting;

import com.unique.examine.module.report.domain.ReportSourcePin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportExportStore {
    int MAX_ROWS = 5_000;

    Optional<Run> findByRequestKey(
            long systemId,
            long tenantId,
            long requestedByMemberId,
            long reportId,
            String requestKeyHash);

    Optional<Run> findById(long systemId, long tenantId, long exportId);

    long countOwned(
            long systemId,
            long tenantId,
            long reportId,
            long requestedByMemberId);

    List<Run> pageOwned(
            long systemId,
            long tenantId,
            long reportId,
            long requestedByMemberId,
            int limit,
            long offset);

    void insert(Run run);

    Run start(long exportId, long expectedVersion, LocalDateTime now);

    Run requeue(
            long exportId,
            long expectedVersion,
            LocalDateTime now);

    Run complete(
            long exportId,
            long expectedVersion,
            long totalRows,
            int processedRows,
            boolean truncated,
            String resultFilename,
            byte[] content,
            LocalDateTime now);

    Run fail(
            long exportId,
            long expectedVersion,
            String failureCode,
            String failureMessage,
            LocalDateTime now);

    byte[] result(long systemId, long tenantId, long exportId);

    enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    record Run(
            long id,
            long systemId,
            long tenantId,
            long reportId,
            String reportCode,
            String reportName,
            long reportVersionId,
            int reportVersionNumber,
            ReportSourcePin source,
            String requestKeyHash,
            long jobId,
            long requestedByAccountId,
            long requestedByMemberId,
            Status status,
            Long totalRows,
            int processedRows,
            boolean truncated,
            String resultFilename,
            Long resultSize,
            String failureCode,
            String failureMessage,
            String requestId,
            String traceId,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version
    ) {
        public Run {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || reportId <= 0
                    || reportVersionId <= 0 || reportVersionNumber <= 0
                    || source == null || jobId <= 0
                    || requestedByAccountId <= 0 || requestedByMemberId <= 0
                    || status == null || processedRows < 0
                    || processedRows > MAX_ROWS
                    || totalRows != null && totalRows < processedRows
                    || requestKeyHash == null
                    || !requestKeyHash.matches("^[0-9a-f]{64}$")
                    || requestId == null || requestId.isBlank()
                    || traceId == null || traceId.isBlank()
                    || createdAt == null || updatedAt == null
                    || updatedAt.isBefore(createdAt) || version < 0) {
                throw new IllegalArgumentException(
                        "Report export run state is incomplete");
            }
            reportCode = code(reportCode);
            reportName = text(reportName, "report name", 200);
            requestId = text(requestId, "request id", 128);
            traceId = text(traceId, "trace id", 128);
            validateState(status, totalRows, processedRows, truncated,
                    resultFilename, resultSize, failureCode, failureMessage,
                    startedAt, finishedAt);
        }

        private static void validateState(
                Status status,
                Long totalRows,
                int processedRows,
                boolean truncated,
                String filename,
                Long size,
                String failureCode,
                String failureMessage,
                LocalDateTime startedAt,
                LocalDateTime finishedAt
        ) {
            if (status == Status.QUEUED && (finishedAt != null
                    || filename != null || size != null
                    || failureCode != null || failureMessage != null)
                    || status == Status.RUNNING && (startedAt == null
                    || finishedAt != null || filename != null || size != null
                    || failureCode != null || failureMessage != null)
                    || status == Status.SUCCEEDED && (startedAt == null
                    || finishedAt == null || totalRows == null
                    || filename == null || !filename.endsWith(".xlsx")
                    || size == null || size <= 0 || failureCode != null
                    || failureMessage != null
                    || truncated != (totalRows > processedRows))
                    || status == Status.FAILED && (finishedAt == null
                    || filename != null || size != null
                    || failureCode == null || failureMessage == null)) {
                throw new IllegalArgumentException(
                        "Report export run transition state is invalid");
            }
            if (failureCode != null
                    && !failureCode.matches("^[A-Z][A-Z0-9_]{1,99}$")) {
                throw new IllegalArgumentException(
                        "Report export failure code is invalid");
            }
            if (failureMessage != null
                    && (failureMessage.isBlank()
                    || failureMessage.length() > 500)) {
                throw new IllegalArgumentException(
                        "Report export failure message is invalid");
            }
        }

        private static String code(String value) {
            if (value == null
                    || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException(
                        "Report export report code is invalid");
            }
            return value;
        }

        private static String text(String value, String label, int max) {
            if (value == null || value.isBlank() || value.length() > max) {
                throw new IllegalArgumentException(
                        "Report export " + label + " is invalid");
            }
            return value.strip();
        }
    }
}
