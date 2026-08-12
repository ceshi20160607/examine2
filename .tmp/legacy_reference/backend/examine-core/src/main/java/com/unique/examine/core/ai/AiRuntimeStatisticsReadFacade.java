package com.unique.examine.core.ai;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Narrow owner port for bounded, read-only AI runtime statistics.
 *
 * <p>The caller supplies authenticated identity, live permissions and policy
 * allowlists separately from the provider plan. Implementations retain
 * authority over active-publication resolution, tenant and record scope,
 * field visibility and aggregate execution.</p>
 */
public interface AiRuntimeStatisticsReadFacade {
    int MAX_GROUP_BUCKETS = 20;
    int MAX_TREND_BUCKETS = 31;
    int MAX_POLICY_ROWS = 50;

    Result query(Request request);

    enum Aggregation {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX
    }

    enum Grain {
        DAY,
        WEEK,
        MONTH;

        boolean aligned(LocalDate value) {
            return switch (this) {
                case DAY -> true;
                case WEEK -> value.getDayOfWeek() == DayOfWeek.MONDAY;
                case MONTH -> value.getDayOfMonth() == 1;
            };
        }

        long buckets(LocalDate startInclusive, LocalDate endExclusive) {
            return switch (this) {
                case DAY -> ChronoUnit.DAYS.between(startInclusive, endExclusive);
                case WEEK -> ChronoUnit.WEEKS.between(startInclusive, endExclusive);
                case MONTH -> ChronoUnit.MONTHS.between(startInclusive, endExclusive);
            };
        }

        LocalDate next(LocalDate value) {
            return switch (this) {
                case DAY -> value.plusDays(1);
                case WEEK -> value.plusWeeks(1);
                case MONTH -> value.plusMonths(1);
            };
        }
    }

    record Request(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            Set<String> allowedModuleCodes,
            Map<String, Set<String>> outboundFields,
            int maxRows,
            String moduleCode,
            String dataSourceCode,
            Aggregation aggregation,
            String measureFieldCode,
            Grouping grouping,
            Trend trend
    ) {
        public Request {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
                throw invalid("AI runtime statistics context IDs must be positive");
            }
            effectivePermissions = immutablePermissions(effectivePermissions);
            allowedModuleCodes = immutableCodes(
                    allowedModuleCodes, "allowed module codes", false);
            outboundFields = immutableOutboundFields(
                    outboundFields, allowedModuleCodes);
            if (maxRows < 1 || maxRows > MAX_POLICY_ROWS) {
                throw invalid("AI runtime statistics maxRows must be within 1..50");
            }
            moduleCode = requiredCode(moduleCode, "module code");
            dataSourceCode = requiredCode(dataSourceCode, "data source code");
            aggregation = Objects.requireNonNull(
                    aggregation, "aggregation");
            measureFieldCode = optionalCode(
                    measureFieldCode, "measure field code");
            if (aggregation == Aggregation.COUNT && measureFieldCode != null) {
                throw invalid("COUNT does not accept a measure field");
            }
            if (aggregation != Aggregation.COUNT && measureFieldCode == null) {
                throw invalid(aggregation + " requires a measure field");
            }
            if (grouping != null && trend != null) {
                throw invalid("Grouping and trend are mutually exclusive");
            }
            if (grouping != null && grouping.bucketLimit() > maxRows) {
                throw invalid("AI statistics group buckets exceed policy maxRows");
            }
            if (trend != null && trend.bucketCount() > maxRows) {
                throw invalid("AI statistics trend buckets exceed policy maxRows");
            }
        }
    }

    record Grouping(String fieldCode, int bucketLimit) {
        public Grouping {
            fieldCode = requiredCode(fieldCode, "grouping field code");
            if (bucketLimit < 1 || bucketLimit > MAX_GROUP_BUCKETS) {
                throw invalid("AI statistics group bucket limit must be within 1..20");
            }
        }
    }

    record Trend(
            String fieldCode,
            Grain grain,
            LocalDate startInclusive,
            LocalDate endExclusive
    ) {
        public Trend {
            fieldCode = requiredCode(fieldCode, "trend field code");
            grain = Objects.requireNonNull(grain, "grain");
            if (startInclusive == null || endExclusive == null
                    || !startInclusive.isBefore(endExclusive)
                    || !grain.aligned(startInclusive)
                    || !grain.aligned(endExclusive)) {
                throw invalid("AI statistics trend range is invalid or unaligned");
            }
            var buckets = grain.buckets(startInclusive, endExclusive);
            if (buckets < 1 || buckets > MAX_TREND_BUCKETS) {
                throw invalid("AI statistics trend must contain 1..31 buckets");
            }
        }

        public int bucketCount() {
            return Math.toIntExact(grain.buckets(startInclusive, endExclusive));
        }
    }

    record Result(
            String dataSourceCode,
            String moduleCode,
            int dataSourceVersionNumber,
            Aggregation aggregation,
            String measureFieldCode,
            String value,
            long matchedRecordCount,
            int bucketCount,
            long totalBucketCount,
            boolean truncated,
            GroupingResult grouping,
            TrendResult trend
    ) {
        public Result {
            dataSourceCode = requiredCode(dataSourceCode, "result data source code");
            moduleCode = requiredCode(moduleCode, "result module code");
            if (dataSourceVersionNumber <= 0 || matchedRecordCount < 0
                    || bucketCount < 0 || totalBucketCount < 0) {
                throw invalid("AI statistics result metadata is invalid");
            }
            aggregation = Objects.requireNonNull(aggregation, "aggregation");
            measureFieldCode = optionalCode(
                    measureFieldCode, "result measure field code");
            if (aggregation == Aggregation.COUNT && measureFieldCode != null
                    || aggregation != Aggregation.COUNT
                    && measureFieldCode == null) {
                throw invalid("AI statistics result measure does not match aggregation");
            }
            value = canonicalDecimal(value);
            if (grouping != null && trend != null) {
                throw invalid("AI statistics result branches are mutually exclusive");
            }
            var actualBuckets = grouping != null
                    ? grouping.buckets().size()
                    : trend != null ? trend.buckets().size() : 0;
            if (bucketCount != actualBuckets || totalBucketCount < bucketCount
                    || truncated != (totalBucketCount > bucketCount)) {
                throw invalid("AI statistics result bucket metadata is inconsistent");
            }
            if (grouping == null && trend == null
                    && (bucketCount != 0 || totalBucketCount != 0 || truncated)) {
                throw invalid("AI scalar statistics cannot contain buckets");
            }
            if (trend != null && (totalBucketCount != bucketCount || truncated)) {
                throw invalid("AI trend statistics must contain the complete range");
            }
        }
    }

    record GroupingResult(String fieldCode, List<GroupBucket> buckets) {
        public GroupingResult {
            fieldCode = requiredCode(fieldCode, "result grouping field code");
            buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets"));
            if (buckets.size() > MAX_GROUP_BUCKETS) {
                throw invalid("AI statistics result has too many group buckets");
            }
        }
    }

    record GroupBucket(
            String label,
            boolean nullBucket,
            String value,
            long recordCount
    ) {
        public GroupBucket {
            if (recordCount <= 0 || nullBucket != (label == null)) {
                throw invalid("AI statistics group bucket is invalid");
            }
            if (label != null && label.length() > 1_000) {
                throw invalid("AI statistics group label exceeds its bound");
            }
            label = label == null ? null : label.strip();
            value = canonicalDecimal(value);
        }
    }

    record TrendResult(
            String fieldCode,
            Grain grain,
            LocalDate startInclusive,
            LocalDate endExclusive,
            List<TrendBucket> buckets
    ) {
        public TrendResult {
            fieldCode = requiredCode(fieldCode, "result trend field code");
            grain = Objects.requireNonNull(grain, "grain");
            buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets"));
            if (startInclusive == null || endExclusive == null
                    || !startInclusive.isBefore(endExclusive)
                    || !grain.aligned(startInclusive)
                    || !grain.aligned(endExclusive)
                    || grain.buckets(startInclusive, endExclusive)
                    != buckets.size()
                    || buckets.size() > MAX_TREND_BUCKETS) {
                throw invalid("AI statistics result trend range is invalid");
            }
            var cursor = startInclusive;
            for (var bucket : buckets) {
                var next = grain.next(cursor);
                if (!bucket.startInclusive().equals(cursor)
                        || !bucket.endExclusive().equals(next)) {
                    throw invalid("AI statistics trend buckets are not contiguous");
                }
                cursor = next;
            }
            if (!cursor.equals(endExclusive)) {
                throw invalid("AI statistics trend buckets do not cover the range");
            }
        }
    }

    record TrendBucket(
            LocalDate startInclusive,
            LocalDate endExclusive,
            String value,
            long recordCount,
            boolean empty
    ) {
        public TrendBucket {
            if (startInclusive == null || endExclusive == null
                    || !startInclusive.isBefore(endExclusive)
                    || recordCount < 0 || empty != (recordCount == 0)) {
                throw invalid("AI statistics trend bucket is invalid");
            }
            value = canonicalDecimal(value);
        }
    }

    private static Set<String> immutablePermissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw invalid("AI runtime statistics permissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static Set<String> immutableCodes(
            Set<String> values,
            String name,
            boolean emptyAllowed
    ) {
        if (values == null || !emptyAllowed && values.isEmpty()
                || values.stream().anyMatch(value -> !validCode(value))) {
            throw invalid("AI runtime statistics " + name + " are invalid");
        }
        return Set.copyOf(values);
    }

    private static Map<String, Set<String>> immutableOutboundFields(
            Map<String, Set<String>> values,
            Set<String> allowedModuleCodes
    ) {
        if (values == null || !values.keySet().equals(allowedModuleCodes)) {
            throw invalid("AI runtime statistics outbound fields are invalid");
        }
        var result = new LinkedHashMap<String, Set<String>>();
        values.forEach((moduleCode, fields) -> result.put(
                requiredCode(moduleCode, "outbound module code"),
                immutableCodes(fields, "outbound field codes", true)));
        return Map.copyOf(result);
    }

    private static String requiredCode(String value, String name) {
        if (!validCode(value)) {
            throw invalid("AI statistics " + name + " is invalid");
        }
        return value;
    }

    private static String optionalCode(String value, String name) {
        return value == null ? null : requiredCode(value, name);
    }

    private static boolean validCode(String value) {
        return value != null && Pattern.matches(
                "^[A-Za-z][A-Za-z0-9_]{0,63}$", value);
    }

    private static String canonicalDecimal(String value) {
        if (value == null) {
            return null;
        }
        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalid("AI statistics decimal is not canonical");
        }
        var canonical = parsed.signum() == 0
                ? "0" : parsed.stripTrailingZeros().toPlainString();
        if (!canonical.equals(value) || value.length() > 1_000) {
            throw invalid("AI statistics decimal is not canonical");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
