package com.unique.examine.module.kpi.api;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.kpi.domain.KpiAttainmentDirection;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiCheckReport;
import com.unique.examine.module.kpi.domain.KpiDefinition;
import com.unique.examine.module.kpi.domain.KpiDraft;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.domain.KpiFieldPin;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import com.unique.examine.module.kpi.domain.KpiTarget;
import com.unique.examine.module.kpi.domain.KpiVersion;

import java.time.LocalDate;

public final class KpiMapping {
    private KpiMapping() {
    }

    public static KpiDraft draft(KpiRequests.Create request) {
        if (request == null) {
            throw invalid("KPI draft is required");
        }
        return draft(request.dataSourceId(), request.subjectType(),
                request.periodType(), request.aggregation(),
                request.measureFieldCode(), request.timeFieldCode(),
                request.direction(), request.warningThreshold());
    }

    public static KpiDraft draft(KpiRequests.SaveDraft request) {
        if (request == null) {
            throw invalid("KPI draft is required");
        }
        return draft(request.dataSourceId(), request.subjectType(),
                request.periodType(), request.aggregation(),
                request.measureFieldCode(), request.timeFieldCode(),
                request.direction(), request.warningThreshold());
    }

    private static KpiDraft draft(
            String dataSourceId,
            String subjectType,
            String periodType,
            String aggregation,
            String measureFieldCode,
            String timeFieldCode,
            String direction,
            String warningThreshold
    ) {
        try {
            return new KpiDraft(
                    positiveId(dataSourceId, "dataSourceId"),
                    KpiSubjectType.valueOf(subjectType),
                    KpiPeriodType.valueOf(periodType),
                    StatisticsAggregation.valueOf(aggregation),
                    measureFieldCode, timeFieldCode,
                    KpiAttainmentDirection.valueOf(direction),
                    warningThreshold);
        } catch (KpiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("KPI draft enum or id value is unsupported");
        }
    }

    public static KpiPeriodType periodType(String value) {
        try {
            return KpiPeriodType.valueOf(value);
        } catch (RuntimeException exception) {
            throw invalid("periodType must be MONTH, QUARTER or YEAR");
        }
    }

    public static LocalDate periodStart(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw invalid("periodStart must be an ISO local date");
        }
    }

    public static long positiveId(String value, String field) {
        if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
            throw invalid(field + " must be a positive decimal id string");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid(field + " is outside the supported id range");
        }
    }

    public static String commandKey(String value) {
        if (value == null
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")) {
            throw invalid("Idempotency-Key is required and malformed");
        }
        return value;
    }

    public static KpiViews.Definition definition(KpiDefinition value) {
        return new KpiViews.Definition(
                Long.toString(value.id()), Long.toString(value.systemId()),
                Long.toString(value.tenantId()), value.code(), value.name(),
                value.description(), value.draftVersion(),
                string(value.activeVersionId()), value.activeVersionNumber(),
                value.createdAt().toString(), value.updatedAt().toString(),
                value.version(), draft(value.draft()));
    }

    public static KpiViews.CheckResult check(KpiCheckReport report) {
        return new KpiViews.CheckResult(
                Long.toString(report.kpiId()), report.draftVersion(),
                report.publishable(), report.blockerCount(),
                report.warningCount(), report.issues().stream()
                .map(issue -> new KpiViews.CheckIssue(
                        issue.severity().name(), issue.code(), issue.path(),
                        issue.message())).toList());
    }

    public static KpiViews.Version version(
            KpiVersion value,
            Long activeVersionId
    ) {
        var source = value.source();
        return new KpiViews.Version(
                Long.toString(value.id()), Long.toString(value.kpiId()),
                value.versionNumber(), value.sourceDraftVersion(),
                value.code(), value.name(), value.description(),
                value.subjectType().name(), value.periodType().name(),
                value.aggregation().name(), value.direction().name(),
                value.warningThreshold(), Long.toString(source.dataSourceId()),
                Long.toString(source.dataSourceVersionId()),
                source.dataSourceVersionNumber(), source.dataSourceCode(),
                source.moduleCode(), source.schemaVersionId(),
                field(source.measureField()), field(source.timeField()),
                value.fingerprint(),
                Long.toString(value.publishedByMemberId()),
                value.publishedAt().toString(),
                activeVersionId != null && activeVersionId == value.id());
    }

    public static KpiViews.Target target(
            KpiTarget value,
            KpiVersion definition,
            KpiCalculation latest
    ) {
        return new KpiViews.Target(
                Long.toString(value.id()), Long.toString(value.kpiId()),
                Long.toString(value.kpiVersionId()),
                value.kpiVersionNumber(), definition.code(),
                definition.name(), value.subjectType().name(),
                Long.toString(value.subjectId()), value.subjectDisplayName(),
                value.period().type().name(),
                value.period().startInclusive().toString(),
                value.period().endExclusive().toString(), value.targetValue(),
                value.version(), value.createdAt().toString(),
                value.updatedAt().toString(),
                latest == null ? null : calculation(latest));
    }

    public static KpiViews.Calculation calculation(KpiCalculation value) {
        var definition = value.definition();
        var source = definition.source();
        return new KpiViews.Calculation(
                Long.toString(value.id()),
                Long.toString(value.target().targetId()),
                value.status().name(), value.errorCode(),
                value.target().targetValue(), value.actualValue(),
                value.attainment(), value.completedAt().toString(),
                Long.toString(value.actorMemberId()),
                new KpiViews.CalculationExplanation(
                        value.statisticsQueryId(),
                        Long.toString(value.matchedRecordCount()),
                        definition.aggregation().name(),
                        source.dataSourceCode(),
                        Long.toString(source.dataSourceVersionId()),
                        source.dataSourceVersionNumber(),
                        source.schemaVersionId(), field(source.measureField()),
                        field(source.timeField()),
                        value.authorizationEpoch() == null ? null
                                : Long.toString(value.authorizationEpoch()),
                        value.subject().roleMemberIds().stream()
                                .map(String::valueOf).toList(),
                        value.trend().stream().map(bucket ->
                                new KpiViews.TrendBucket(
                                        bucket.startInclusive().toString(),
                                        bucket.endExclusive().toString(),
                                        bucket.value(),
                                        Long.toString(bucket.recordCount())))
                                .toList()));
    }

    private static KpiViews.Draft draft(KpiDraft value) {
        return new KpiViews.Draft(
                Long.toString(value.dataSourceId()),
                value.subjectType().name(), value.periodType().name(),
                value.aggregation().name(), value.measureFieldCode(),
                value.timeFieldCode(), value.direction().name(),
                value.warningThreshold());
    }

    private static KpiViews.FieldPin field(KpiFieldPin value) {
        return value == null ? null : new KpiViews.FieldPin(
                Long.toString(value.logicalFieldId()), value.code(),
                value.name(), value.type(), value.queryType());
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_REQUEST_INVALID", message);
    }
}
