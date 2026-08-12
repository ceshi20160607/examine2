package com.unique.examine.module.datasource.statistics.domain;

import java.time.LocalDate;
import java.util.regex.Pattern;

public record StatisticsRequest(
        StatisticsAggregation aggregation,
        String measureFieldCode,
        Grouping grouping,
        Trend trend
) {
    public StatisticsRequest {
        if (aggregation == null) {
            throw invalid("Statistics aggregation is required");
        }
        measureFieldCode = optionalFieldCode(measureFieldCode);
        if (aggregation == StatisticsAggregation.COUNT
                && measureFieldCode != null) {
            throw invalid("COUNT does not accept a measure field");
        }
        if (aggregation != StatisticsAggregation.COUNT
                && measureFieldCode == null) {
            throw invalid(aggregation + " requires a numeric measure field");
        }
        if (grouping != null && trend != null) {
            throw invalid(
                    "Grouping and trend cannot be combined in this version");
        }
    }

    public record Grouping(String fieldCode, int bucketLimit) {
        public static final int MAX_BUCKETS = 20;

        public Grouping {
            fieldCode = requiredFieldCode(fieldCode);
            if (bucketLimit < 1 || bucketLimit > MAX_BUCKETS) {
                throw invalid(
                        "Statistics group bucket limit must be between 1 and 20");
            }
        }
    }

    public record Trend(
            String fieldCode,
            StatisticsGrain grain,
            LocalDate startInclusive,
            LocalDate endExclusive
    ) {
        public static final int MAX_BUCKETS = 100;

        public Trend {
            fieldCode = requiredFieldCode(fieldCode);
            if (grain == null || startInclusive == null
                    || endExclusive == null
                    || !startInclusive.isBefore(endExclusive)) {
                throw invalid("Statistics trend range is invalid");
            }
            if (!grain.aligned(startInclusive)
                    || !grain.aligned(endExclusive)) {
                throw invalid(
                        "Statistics trend range must align to its grain");
            }
            var buckets = grain.buckets(startInclusive, endExclusive);
            if (buckets < 1 || buckets > MAX_BUCKETS) {
                throw invalid(
                        "Statistics trend must contain between 1 and 100 buckets");
            }
        }

        public int bucketCount() {
            return Math.toIntExact(grain.buckets(
                    startInclusive, endExclusive));
        }
    }

    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private static String requiredFieldCode(String value) {
        if (value == null || !FIELD_CODE.matcher(value).matches()) {
            throw invalid("Statistics field code is invalid");
        }
        return value;
    }

    private static String optionalFieldCode(String value) {
        return value == null ? null : requiredFieldCode(value);
    }

    private static StatisticsException invalid(String message) {
        return new StatisticsException("STATISTICS_REQUEST_INVALID", message);
    }
}
