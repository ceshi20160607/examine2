package com.unique.unexamine.operations.manage;

import java.util.List;
import java.util.Map;

public final class OperationsHealthModels {
    private OperationsHealthModels() { }

    public record HealthItem(
            Long id,
            String code,
            String name,
            String category,
            String status,
            String message,
            Map<String, Object> metric,
            String checkedAt) { }

    public record JobOverview(
            long queued,
            long running,
            long retryWait,
            long failed,
            long succeeded,
            long total) { }

    public record ErrorSummary(
            String requestId,
            String source,
            String code,
            String message,
            String status,
            String occurredAt,
            String targetType,
            String targetId) { }

    public record HealthView(
            Long id,
            String status,
            boolean ready,
            String releaseVersion,
            String migrationVersion,
            String requestId,
            String startedAt,
            String finishedAt,
            List<HealthItem> items,
            JobOverview jobs,
            List<ErrorSummary> recentErrors) { }

    public record ObservationEntry(
            String source,
            String kind,
            String status,
            String summary,
            String occurredAt,
            Map<String, Object> detail) { }

    public record ObservationStage(
            String code,
            String name,
            boolean observed,
            String evidence) { }

    public record ObservationView(
            String requestId,
            String traceId,
            Long systemId,
            Long tenantId,
            Long accountId,
            boolean observable,
            List<ObservationStage> stages,
            List<ObservationEntry> logs,
            Map<String, Object> metrics,
            List<Map<String, Object>> jobs,
            List<Map<String, Object>> audits,
            List<Map<String, Object>> applicationCalls,
            List<Map<String, Object>> flowEvents) { }
}
