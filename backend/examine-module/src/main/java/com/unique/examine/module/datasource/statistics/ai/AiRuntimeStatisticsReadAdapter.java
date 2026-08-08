package com.unique.examine.module.datasource.statistics.ai;

import com.unique.examine.core.ai.AiRuntimeStatisticsReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.PublishedDataSource;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.domain.StatisticsResult;
import com.unique.examine.module.datasource.statistics.service.DataSourceStatisticsService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Module-owned adapter for bounded AI runtime statistics.
 *
 * <p>The adapter resolves only the current tenant's active data-source
 * publication and delegates aggregate execution to the native statistics
 * owner. It never accepts or builds SQL, fixed filters, record restrictions or
 * internal publication identifiers from an AI plan.</p>
 */
@Component
public class AiRuntimeStatisticsReadAdapter
        implements AiRuntimeStatisticsReadFacade {
    private static final long UNUSED_ACCOUNT_ID = 0L;

    private final ActiveDataSourceReader dataSources;
    private final StatisticsReader statistics;

    @Autowired
    public AiRuntimeStatisticsReadAdapter(
            DataSourceService dataSources,
            DataSourceStatisticsService statistics
    ) {
        this(dataSources::active, statistics::statistics);
    }

    AiRuntimeStatisticsReadAdapter(
            ActiveDataSourceReader dataSources,
            StatisticsReader statistics
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        requirePermissions(request, request.moduleCode());
        requirePolicy(request, request.moduleCode());

        var session = new RuntimeSession(
                UNUSED_ACCOUNT_ID,
                request.systemId(),
                request.memberId(),
                request.tenantId(),
                request.effectivePermissions());
        var actor = new DataSourceActor(
                request.systemId(), request.tenantId(), request.memberId());
        var active = Objects.requireNonNull(
                dataSources.active(actor, request.dataSourceCode()),
                "active data source");
        requireActiveIdentity(request, active);

        var realModuleCode = active.version().moduleCode();
        if (!realModuleCode.equals(request.moduleCode())) {
            throw forbidden(
                    "AI_POLICY_MODULE_DENIED",
                    "The active data source does not belong to the authorized module");
        }
        requirePermissions(request, realModuleCode);
        requirePolicy(request, realModuleCode);

        var nativeRequest = nativeRequest(request);
        var nativeResult = Objects.requireNonNull(statistics.query(
                        session,
                        active.root().id(),
                        active.version().id(),
                        nativeRequest),
                "statistics result");
        requireNativeIdentity(request, active, nativeResult);
        return safeResult(request, nativeResult);
    }

    private static void requireActiveIdentity(
            Request request,
            PublishedDataSource active
    ) {
        var root = active.root();
        var version = active.version();
        if (root.systemId() != request.systemId()
                || root.tenantId() != request.tenantId()
                || version.systemId() != request.systemId()
                || version.tenantId() != request.tenantId()
                || !root.code().equals(request.dataSourceCode())
                || !version.code().equals(request.dataSourceCode())) {
            throw new DataSourceException(
                    "DATA_SOURCE_NOT_FOUND", "Data source does not exist");
        }
    }

    private static void requirePermissions(Request request, String moduleCode) {
        if (!request.effectivePermissions().contains("system.runtime.access")
                || !request.effectivePermissions().contains(
                "module." + moduleCode + ".view")) {
            throw forbidden(
                    "PERMISSION_DENIED",
                    "The current member cannot query runtime statistics");
        }
    }

    private static void requirePolicy(Request request, String moduleCode) {
        if (!request.allowedModuleCodes().contains(moduleCode)) {
            throw forbidden(
                    "AI_POLICY_MODULE_DENIED",
                    "The module is outside the published AI policy");
        }
        var allowedFields = request.outboundFields().get(moduleCode);
        if (allowedFields == null
                || !allowedFields.containsAll(requestedFields(request))) {
            throw forbidden(
                    "AI_POLICY_FIELD_DENIED",
                    "A statistics field is outside the published AI policy");
        }
    }

    private static Set<String> requestedFields(Request request) {
        var fields = new LinkedHashSet<String>();
        if (request.measureFieldCode() != null) {
            fields.add(request.measureFieldCode());
        }
        if (request.grouping() != null) {
            fields.add(request.grouping().fieldCode());
        }
        if (request.trend() != null) {
            fields.add(request.trend().fieldCode());
        }
        return Set.copyOf(fields);
    }

    private static StatisticsRequest nativeRequest(Request request) {
        StatisticsRequest.Grouping grouping = request.grouping() == null
                ? null
                : new StatisticsRequest.Grouping(
                request.grouping().fieldCode(),
                request.grouping().bucketLimit());
        StatisticsRequest.Trend trend = request.trend() == null
                ? null
                : new StatisticsRequest.Trend(
                request.trend().fieldCode(),
                StatisticsGrain.valueOf(request.trend().grain().name()),
                request.trend().startInclusive(),
                request.trend().endExclusive());
        return new StatisticsRequest(
                StatisticsAggregation.valueOf(request.aggregation().name()),
                request.measureFieldCode(), grouping, trend);
    }

    private static void requireNativeIdentity(
            Request request,
            PublishedDataSource active,
            StatisticsResult result
    ) {
        var version = active.version();
        if (result.dataSourceId() != version.dataSourceId()
                || result.dataSourceVersionId() != version.id()
                || result.dataSourceVersionNumber() != version.versionNumber()
                || !result.dataSourceCode().equals(request.dataSourceCode())
                || !result.moduleCode().equals(request.moduleCode())
                || !result.aggregation().name().equals(
                request.aggregation().name())
                || !Objects.equals(
                result.measureFieldCode(), request.measureFieldCode())) {
            throw new IllegalStateException(
                    "Native statistics owner returned a mismatched publication");
        }
        if (request.grouping() == null && !result.groupBuckets().isEmpty()
                || request.grouping() != null
                && result.groupBuckets().size()
                > request.grouping().bucketLimit()
                || request.trend() == null && !result.trendBuckets().isEmpty()
                || request.trend() != null
                && result.trendBuckets().size()
                != request.trend().bucketCount()) {
            throw new IllegalStateException(
                    "Native statistics owner returned an invalid bucket shape");
        }
    }

    private static Result safeResult(
            Request request,
            StatisticsResult result
    ) {
        GroupingResult grouping = null;
        if (request.grouping() != null) {
            grouping = new GroupingResult(
                    request.grouping().fieldCode(),
                    result.groupBuckets().stream()
                            .map(bucket -> new GroupBucket(
                                    bucket.label(), bucket.nullBucket(),
                                    bucket.value(), bucket.recordCount()))
                            .toList());
        }
        TrendResult trend = null;
        if (request.trend() != null) {
            trend = new TrendResult(
                    request.trend().fieldCode(),
                    request.trend().grain(),
                    request.trend().startInclusive(),
                    request.trend().endExclusive(),
                    result.trendBuckets().stream()
                            .map(bucket -> new TrendBucket(
                                    bucket.startInclusive(),
                                    bucket.endExclusive(),
                                    bucket.value(), bucket.recordCount(),
                                    bucket.empty()))
                            .toList());
        }
        return new Result(
                result.dataSourceCode(), result.moduleCode(),
                result.dataSourceVersionNumber(), request.aggregation(),
                result.measureFieldCode(), result.value(),
                result.matchedRecordCount(), result.bucketCount(),
                result.totalBucketCount(), result.truncated(),
                grouping, trend);
    }

    private static BusinessException forbidden(String code, String message) {
        return new BusinessException(code, message, HttpStatus.FORBIDDEN);
    }

    @FunctionalInterface
    interface ActiveDataSourceReader {
        PublishedDataSource active(DataSourceActor actor, String code);
    }

    @FunctionalInterface
    interface StatisticsReader {
        StatisticsResult query(
                RuntimeSession session,
                long dataSourceId,
                long exactVersionId,
                StatisticsRequest request);
    }
}
