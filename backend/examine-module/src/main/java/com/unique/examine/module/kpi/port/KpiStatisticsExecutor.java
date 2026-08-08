package com.unique.examine.module.kpi.port;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiFieldPin;
import com.unique.examine.module.kpi.domain.KpiPeriod;
import com.unique.examine.module.kpi.domain.KpiSourcePin;

import java.util.List;

/** Internal exact statistics boundary. None of these records are HTTP DTOs. */
public interface KpiStatisticsExecutor {
    Authorization requireAllAccess(KpiActor actor, String moduleCode);

    Result execute(KpiActor actor, Request request);

    record Authorization(long epoch) {
        public Authorization {
            if (epoch <= 0) {
                throw new IllegalArgumentException(
                        "KPI authorization epoch must be positive");
            }
        }
    }

    record Request(
            KpiSourcePin source,
            StatisticsAggregation aggregation,
            KpiPeriod period,
            OwnershipRestriction ownership,
            Authorization authorization
    ) {
        public Request {
            if (source == null || aggregation == null || period == null
                    || ownership == null || authorization == null) {
                throw new IllegalArgumentException(
                        "KPI exact statistics request is incomplete");
            }
        }
    }

    record OwnershipRestriction(
            Kind kind,
            Long departmentId,
            List<Long> memberIds
    ) {
        public static final int MAX_MEMBER_IDS = 1_000;

        public OwnershipRestriction {
            memberIds = memberIds == null ? List.of()
                    : memberIds.stream().sorted().distinct().toList();
            if (kind == null || memberIds.size() > MAX_MEMBER_IDS
                    || memberIds.stream().anyMatch(id -> id <= 0)
                    || kind == Kind.DEPARTMENT
                    && (departmentId == null || departmentId <= 0
                    || !memberIds.isEmpty())
                    || kind == Kind.MEMBERS
                    && (departmentId != null)) {
                throw new IllegalArgumentException(
                        "KPI ownership restriction is invalid");
            }
        }

        public static OwnershipRestriction department(long departmentId) {
            return new OwnershipRestriction(
                    Kind.DEPARTMENT, departmentId, List.of());
        }

        public static OwnershipRestriction members(List<Long> memberIds) {
            return new OwnershipRestriction(Kind.MEMBERS, null, memberIds);
        }

        public enum Kind { MEMBERS, DEPARTMENT }
    }

    record Result(
            String queryId,
            long dataSourceId,
            long dataSourceVersionId,
            String moduleCode,
            String schemaVersionId,
            StatisticsAggregation aggregation,
            KpiFieldPin measureField,
            KpiFieldPin timeField,
            String value,
            long matchedRecordCount,
            List<KpiCalculation.TrendBucket> trend
    ) {
        public Result {
            if (queryId == null || !queryId.matches("^[0-9a-f]{64}$")
                    || dataSourceId <= 0 || dataSourceVersionId <= 0
                    || moduleCode == null || moduleCode.isBlank()
                    || schemaVersionId == null || schemaVersionId.isBlank()
                    || aggregation == null || timeField == null
                    || matchedRecordCount < 0 || trend == null) {
                throw new IllegalArgumentException(
                        "KPI statistics result is invalid");
            }
            queryId = queryId.strip();
            moduleCode = moduleCode.strip();
            schemaVersionId = schemaVersionId.strip();
            trend = List.copyOf(trend);
        }
    }
}
