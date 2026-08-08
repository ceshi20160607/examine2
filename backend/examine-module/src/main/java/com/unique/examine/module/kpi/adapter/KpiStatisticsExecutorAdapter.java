package com.unique.examine.module.kpi.adapter;

import com.unique.examine.module.datasource.statistics.domain.StatisticsFieldPins;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.service.DataSourceStatisticsService;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.domain.KpiFieldPin;
import com.unique.examine.module.kpi.port.KpiStatisticsExecutor;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Objects;

/** Trusted KPI bridge to the exact native statistics service. */
@Component
public final class KpiStatisticsExecutorAdapter
        implements KpiStatisticsExecutor {
    private final DataSourceStatisticsService statistics;
    private final KpiAllGrantAuthorizationAdapter authorization;

    public KpiStatisticsExecutorAdapter(
            DataSourceStatisticsService statistics,
            KpiAllGrantAuthorizationAdapter authorization
    ) {
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.authorization = Objects.requireNonNull(
                authorization, "authorization");
    }

    @Override
    public Authorization requireAllAccess(
            KpiActor actor,
            String moduleCode
    ) {
        return authorization.require(actor, moduleCode);
    }

    @Override
    public Result execute(KpiActor actor, Request request) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        var source = request.source();
        authorization.requireStillAll(
                actor, source.moduleCode(), request.authorization().epoch());

        var permissions = new LinkedHashSet<String>();
        permissions.add("module." + source.moduleCode() + ".view");
        addCurrentFieldReadPermission(
                actor, source.moduleCode(), source.measureField(),
                request.authorization().epoch(), permissions);
        addCurrentFieldReadPermission(
                actor, source.moduleCode(), source.timeField(),
                request.authorization().epoch(), permissions);
        var session = new RuntimeSession(
                actor.memberId(), actor.systemId(), actor.memberId(),
                actor.tenantId(), permissions);
        var nativeRequest = new StatisticsRequest(
                request.aggregation(), source.measureField() == null
                ? null : source.measureField().code(), null,
                new StatisticsRequest.Trend(
                        source.timeField().code(), StatisticsGrain.MONTH,
                        request.period().startInclusive(),
                        request.period().endExclusive()));
        var result = statistics.statistics(
                session, source.dataSourceId(), source.dataSourceVersionId(),
                nativeRequest, pins(source), restriction(request.ownership()));

        // Detect an authorization mutation that raced the native query. A stale
        // attempt may have executed SQL, but it can never be persisted as a
        // successful KPI calculation.
        authorization.requireStillAll(
                actor, source.moduleCode(), request.authorization().epoch());
        requireExactResult(request, result.dataSourceId(),
                result.dataSourceVersionId(), result.moduleCode(),
                result.schemaVersionId());
        return new Result(
                result.queryId(), result.dataSourceId(),
                result.dataSourceVersionId(), result.moduleCode(),
                result.schemaVersionId(), result.aggregation(),
                source.measureField(), source.timeField(),
                zeroIfAbsent(result.value()), result.matchedRecordCount(),
                result.trendBuckets().stream()
                        .map(bucket -> new KpiCalculation.TrendBucket(
                                bucket.startInclusive(),
                                bucket.endExclusive(),
                                zeroIfAbsent(bucket.value()),
                                bucket.recordCount()))
                        .toList());
    }

    private void addCurrentFieldReadPermission(
            KpiActor actor,
            String moduleCode,
            KpiFieldPin field,
            long expectedEpoch,
            LinkedHashSet<String> permissions
    ) {
        if (field == null) {
            return;
        }
        var permission = "module." + moduleCode + ".field."
                + field.code() + ".read";
        if (authorization.permissionAtEpoch(
                actor, permission, expectedEpoch)) {
            permissions.add(permission);
        }
    }

    private static StatisticsFieldPins pins(
            com.unique.examine.module.kpi.domain.KpiSourcePin source
    ) {
        return new StatisticsFieldPins(
                pin(source.measureField()), null, pin(source.timeField()));
    }

    private static StatisticsFieldPins.Field pin(KpiFieldPin field) {
        return field == null ? null : new StatisticsFieldPins.Field(
                field.logicalFieldId(), field.code(), field.name(),
                field.type(), field.queryType());
    }

    private static StatisticsRecordRestriction restriction(
            OwnershipRestriction value
    ) {
        if (value.kind() == OwnershipRestriction.Kind.DEPARTMENT) {
            return StatisticsRecordRestriction.ownerDepartment(
                    value.departmentId());
        }
        return value.memberIds().size() == 1
                ? StatisticsRecordRestriction.ownerMember(
                value.memberIds().getFirst())
                : StatisticsRecordRestriction.ownerMembers(value.memberIds());
    }

    private static void requireExactResult(
            Request request,
            long dataSourceId,
            long dataSourceVersionId,
            String moduleCode,
            String schemaVersionId
    ) {
        var source = request.source();
        if (dataSourceId != source.dataSourceId()
                || dataSourceVersionId != source.dataSourceVersionId()
                || !moduleCode.equals(source.moduleCode())
                || !schemaVersionId.equals(source.schemaVersionId())) {
            throw new KpiException(
                    "KPI_SOURCE_CONFLICT",
                    "Exact KPI statistics source pins changed");
        }
    }

    private static String zeroIfAbsent(String value) {
        return value == null ? "0" : value;
    }
}
