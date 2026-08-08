package com.unique.examine.module.kpi.api;

import java.util.List;

public final class KpiViews {
    private KpiViews() {
    }

    public record Draft(
            String dataSourceId,
            String subjectType,
            String periodType,
            String aggregation,
            String measureFieldCode,
            String timeFieldCode,
            String direction,
            String warningThreshold
    ) {
    }

    public record Definition(
            String id,
            String systemId,
            String tenantId,
            String code,
            String name,
            String description,
            long draftVersion,
            String activeVersionId,
            Integer activeVersionNumber,
            String createdAt,
            String updatedAt,
            long version,
            Draft draft
    ) {
    }

    public record CheckIssue(
            String severity,
            String code,
            String path,
            String message
    ) {
    }

    public record CheckResult(
            String kpiId,
            long checkedDraftVersion,
            boolean valid,
            long blockerCount,
            long warningCount,
            List<CheckIssue> issues
    ) {
        public CheckResult {
            issues = List.copyOf(issues);
        }
    }

    public record FieldPin(
            String logicalFieldId,
            String code,
            String name,
            String type,
            String queryType
    ) {
    }

    public record Version(
            String id,
            String kpiId,
            int versionNumber,
            long sourceDraftVersion,
            String code,
            String name,
            String description,
            String subjectType,
            String periodType,
            String aggregation,
            String direction,
            String warningThreshold,
            String dataSourceId,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String dataSourceCode,
            String moduleCode,
            String schemaVersionId,
            FieldPin measureField,
            FieldPin timeField,
            String fingerprint,
            String publishedBy,
            String publishedAt,
            boolean active
    ) {
    }

    public record PublishResult(Definition kpi, Version version) {
    }

    public record TrendBucket(
            String startInclusive,
            String endExclusive,
            String value,
            String matchedCount
    ) {
    }

    public record CalculationExplanation(
            String statisticsQueryId,
            String matchedCount,
            String aggregation,
            String dataSourceCode,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String schemaVersionId,
            FieldPin measureField,
            FieldPin timeField,
            String authorizationEpoch,
            List<String> subjectMemberIds,
            List<TrendBucket> trend
    ) {
        public CalculationExplanation {
            subjectMemberIds = List.copyOf(subjectMemberIds);
            trend = List.copyOf(trend);
        }
    }

    public record Calculation(
            String id,
            String targetId,
            String status,
            String errorCode,
            String targetValue,
            String actualValue,
            String attainment,
            String calculatedAt,
            String calculatedBy,
            CalculationExplanation explanation
    ) {
    }

    public record Target(
            String id,
            String kpiId,
            String kpiVersionId,
            int kpiVersionNumber,
            String kpiCode,
            String kpiName,
            String subjectType,
            String subjectId,
            String subjectName,
            String periodType,
            String periodStart,
            String periodEndExclusive,
            String targetValue,
            long version,
            String createdAt,
            String updatedAt,
            Calculation latestCalculation
    ) {
    }
}
