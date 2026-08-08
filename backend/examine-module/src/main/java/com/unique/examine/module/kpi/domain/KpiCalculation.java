package com.unique.examine.module.kpi.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

public record KpiCalculation(
        long id,
        long systemId,
        long tenantId,
        String commandKey,
        KpiDefinitionSnapshot definition,
        KpiTargetSnapshot target,
        KpiSubjectSnapshot subject,
        String actualValue,
        String attainment,
        KpiWarningStatus status,
        String statisticsQueryId,
        long matchedRecordCount,
        List<TrendBucket> trend,
        Long authorizationEpoch,
        long actorMemberId,
        Instant startedAt,
        Instant completedAt,
        String errorCode
) {
    private static final Pattern COMMAND_KEY =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern ERROR_CODE =
            Pattern.compile("^[A-Z][A-Z0-9_]{1,99}$");
    private static final Pattern QUERY_ID =
            Pattern.compile("^[0-9a-f]{64}$");

    public KpiCalculation {
        if (id <= 0 || systemId <= 0 || tenantId <= 0
                || commandKey == null || !COMMAND_KEY.matcher(commandKey).matches()
                || definition == null || target == null || subject == null
                || status == null || matchedRecordCount < 0 || trend == null
                || actorMemberId <= 0 || startedAt == null
                || completedAt == null || completedAt.isBefore(startedAt)
                || definition.kpiId() != target.kpiId()
                || definition.kpiVersionId() != target.kpiVersionId()
                || definition.kpiVersionNumber() != target.kpiVersionNumber()
                || definition.subjectType() != target.subjectType()
                || definition.periodType() != target.period().type()
                || subject.type() != target.subjectType()
                || subject.id() != target.subjectId()) {
            throw invalid("KPI calculation state is incomplete");
        }
        trend = List.copyOf(trend);
        if (status == KpiWarningStatus.CALCULATION_FAILED) {
            if (actualValue != null || attainment != null
                    || statisticsQueryId != null || matchedRecordCount != 0
                    || !trend.isEmpty() || errorCode == null
                    || !ERROR_CODE.matcher(errorCode).matches()
                    || authorizationEpoch != null && authorizationEpoch <= 0) {
                throw invalid("Failed KPI calculation metadata is invalid");
            }
        } else {
            actualValue = KpiDecimal.require(actualValue);
            attainment = KpiDecimal.requireNonNegative(attainment);
            if (statisticsQueryId == null
                    || !QUERY_ID.matcher(statisticsQueryId).matches()
                    || authorizationEpoch == null || authorizationEpoch <= 0
                    || errorCode != null
                    || trend.size() != target.period().monthCount()) {
                throw invalid("Successful KPI calculation metadata is invalid");
            }
            validateTrend(target.period(), trend, matchedRecordCount);
            statisticsQueryId = statisticsQueryId.strip();
        }
    }

    public static String commandKey(String value) {
        if (value == null || !COMMAND_KEY.matcher(value).matches()) {
            throw invalid("KPI calculation command key is invalid");
        }
        return value;
    }

    public static KpiCalculation success(
            long id,
            KpiActor actor,
            String commandKey,
            KpiVersion definition,
            KpiTarget target,
            KpiSubjectSnapshot subject,
            String actualValue,
            KpiAttainment.Result attainment,
            String statisticsQueryId,
            long matchedRecordCount,
            List<TrendBucket> trend,
            long authorizationEpoch,
            Instant startedAt,
            Instant completedAt
    ) {
        return new KpiCalculation(id, actor.systemId(), actor.tenantId(),
                commandKey, KpiDefinitionSnapshot.from(definition),
                KpiTargetSnapshot.from(target), subject, actualValue,
                attainment.value(), attainment.status(), statisticsQueryId,
                matchedRecordCount, trend, authorizationEpoch,
                actor.memberId(), startedAt, completedAt, null);
    }

    public static KpiCalculation failed(
            long id,
            KpiActor actor,
            String commandKey,
            KpiVersion definition,
            KpiTarget target,
            KpiSubjectSnapshot subject,
            Long authorizationEpoch,
            Instant startedAt,
            Instant completedAt,
            String errorCode
    ) {
        return new KpiCalculation(id, actor.systemId(), actor.tenantId(),
                commandKey, KpiDefinitionSnapshot.from(definition),
                KpiTargetSnapshot.from(target), subject, null, null,
                KpiWarningStatus.CALCULATION_FAILED, null, 0, List.of(),
                authorizationEpoch, actor.memberId(), startedAt, completedAt,
                errorCode);
    }

    public record TrendBucket(
            LocalDate startInclusive,
            LocalDate endExclusive,
            String value,
            long recordCount
    ) {
        public TrendBucket {
            if (startInclusive == null || endExclusive == null
                    || !endExclusive.equals(startInclusive.plusMonths(1))
                    || startInclusive.getDayOfMonth() != 1
                    || recordCount < 0) {
                throw invalid("KPI trend bucket is invalid");
            }
            value = KpiDecimal.require(value);
        }
    }

    private static void validateTrend(
            KpiPeriod period,
            List<TrendBucket> buckets,
            long matchedRecordCount
    ) {
        var cursor = period.startInclusive();
        long total = 0;
        try {
            for (var bucket : buckets) {
                if (!bucket.startInclusive().equals(cursor)) {
                    throw invalid("KPI trend buckets are not contiguous");
                }
                cursor = bucket.endExclusive();
                total = Math.addExact(total, bucket.recordCount());
            }
        } catch (ArithmeticException overflow) {
            throw invalid("KPI trend record count overflowed");
        }
        if (!cursor.equals(period.endExclusive())
                || total != matchedRecordCount) {
            throw invalid("KPI trend explanation is inconsistent");
        }
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_CALCULATION_INVALID", message);
    }
}
