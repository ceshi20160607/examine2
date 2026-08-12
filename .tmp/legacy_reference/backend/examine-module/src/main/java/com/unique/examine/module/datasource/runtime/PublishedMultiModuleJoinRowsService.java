package com.unique.examine.module.datasource.runtime;

import com.unique.examine.module.datasource.api.DataSourceViews;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.service.PublishedMultiModuleJoinRowsReader;
import com.unique.examine.module.datasource.service.ExactDataSourcePublicationResolver;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes published left-deep joins over the existing exact-version runtime.
 * Child reads run concurrently and retain each module's tenant, permission and
 * data-scope checks. No child publication is re-resolved through an active
 * pointer.
 */
@Service
public final class PublishedMultiModuleJoinRowsService
        implements PublishedMultiModuleJoinRowsReader {
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private static final ExecutorService JOIN_READ_EXECUTOR =
            Executors.newFixedThreadPool(16, task -> {
                var thread = new Thread(task, "data-source-join-read-"
                        + THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
    private final ExactPublishedDataSourceRowsReader runtime;
    private final ExactDataSourcePublicationResolver dataSources;

    public PublishedMultiModuleJoinRowsService(
            ExactPublishedDataSourceRowsReader runtime,
            ExactDataSourcePublicationResolver dataSources
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
    }

    @Override
    public Result read(
            RuntimeSession session,
            DataSourcePublication publication
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(publication, "publication");
        var snapshot = publication.version().snapshot();
        if (snapshot.sourceKind()
                != DataSourceDraft.SourceKind.MULTI_MODULE_JOIN
                || snapshot.multiModuleJoin() == null) {
            throw unavailable("Published multi-module join plan is unavailable");
        }
        if (session.tenantId() == null
                || session.systemId() != publication.version().systemId()
                || session.tenantId() != publication.version().tenantId()
                || session.memberId() <= 0) {
            throw unavailable("Published multi-module join is unavailable");
        }
        var plan = snapshot.multiModuleJoin();
        var actor = new DataSourceActor(
                session.systemId(), session.tenantId(), session.memberId());
        for (var input : plan.inputs()) {
            var child = dataSources.publication(actor, input.dataSourceId(),
                    input.dataSourceVersionId());
            if (child.version().snapshot().sourceKind()
                    == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
                throw unavailable("Nested multi-module join sources are unavailable");
            }
        }
        var deadline = Instant.now().plusSeconds(plan.timeoutSeconds());
        var futures = new LinkedHashMap<String,
                CompletableFuture<DataSourceViews.RuntimeRows>>();
        for (var input : plan.inputs()) {
            futures.put(input.alias(), CompletableFuture.supplyAsync(() ->
                    runtime.rows(session, input.dataSourceId(),
                            input.dataSourceVersionId(), 1,
                            plan.rowLimit()), JOIN_READ_EXECUTOR));
        }

        var loaded = new LinkedHashMap<String, List<SourceRow>>();
        var queryHashes = new LinkedHashMap<String, String>();
        var failed = new ArrayList<String>();
        var partial = false;
        var effectiveSourceBound = plan.rowLimit();
        for (var input : plan.inputs()) {
            try {
                var remaining = Duration.between(
                        Instant.now(), deadline).toMillis();
                if (remaining <= 0) {
                    throw new java.util.concurrent.TimeoutException();
                }
                var rows = futures.get(input.alias()).get(
                        remaining, TimeUnit.MILLISECONDS);
                partial = partial || rows.partial()
                        || rows.total() > rows.rows().size();
                if (rows.sourceRowLimit() > 0) {
                    effectiveSourceBound = Math.min(
                            effectiveSourceBound, rows.sourceRowLimit());
                }
                if (rows.queryHash() == null || rows.queryHash().isBlank()) {
                    throw unavailable(
                            "A joined source returned an invalid query identity");
                }
                queryHashes.put(input.alias(), rows.queryHash());
                loaded.put(input.alias(), rows.rows().stream()
                        .map(PublishedMultiModuleJoinRowsService::sourceRow)
                        .toList());
            } catch (Exception failure) {
                futures.get(input.alias()).cancel(true);
                if (!partialAllowed(plan, input.alias())) {
                    futures.values().forEach(value -> value.cancel(true));
                    throw unavailable("A joined source is unavailable or timed out");
                }
                failed.add(input.alias());
                queryHashes.put(input.alias(), "FAILED");
                loaded.put(input.alias(), List.of());
            }
        }

        var joined = loaded.get(plan.inputs().get(0).alias()).stream()
                .map(row -> JoinedRow.initial(
                        plan.inputs().get(0).alias(), row)).toList();
        partial = partial || !failed.isEmpty();
        for (var edge : plan.edges()) {
            requireCardinality(edge, loaded.get(edge.leftAlias()),
                    loaded.get(edge.rightAlias()));
            var index = index(loaded.get(edge.rightAlias()),
                    edge.rightFieldCode());
            var next = new ArrayList<JoinedRow>();
            for (var left : joined) {
                var key = left.value(edge.leftAlias(), edge.leftFieldCode());
                var matches = key == null
                        ? List.<SourceRow>of()
                        : index.getOrDefault(canonicalKey(key), List.of());
                if (matches.isEmpty()) {
                    if (edge.joinType() == DataSourceDraft.JoinType.LEFT) {
                        next.add(left.withMissing(edge.rightAlias()));
                    }
                } else {
                    for (var right : matches) {
                        if (next.size() >= plan.rowLimit()) {
                            partial = true;
                            break;
                        }
                        next.add(left.with(edge.rightAlias(), right));
                    }
                }
                if (next.size() >= plan.rowLimit()) {
                    partial = true;
                    break;
                }
                requireWithinDeadline(deadline);
            }
            joined = List.copyOf(next);
        }

        var result = new ArrayList<Row>();
        for (int index = 0; index < joined.size(); index++) {
            var row = joined.get(index);
            var values = new LinkedHashMap<String, Object>();
            for (var projection : plan.projections()) {
                values.put(projection.fieldCode(), row.value(
                        projection.sourceAlias(),
                        projection.sourceFieldCode()));
            }
            result.add(new Row(
                    "join:" + publication.version().id() + ":" + (index + 1),
                    values, row.drillThrough()));
        }
        return new Result(
                publication.root().id(), publication.version().id(),
                publication.version().versionNumber(), queryHash(
                publication, queryHashes), result, partial, failed,
                effectiveSourceBound);
    }

    private static boolean partialAllowed(
            DataSourceDraft.MultiModuleJoin plan,
            String alias
    ) {
        if (plan.failureMode()
                != DataSourceDraft.JoinFailureMode.ALLOW_PARTIAL_LEFT
                || plan.inputs().get(0).alias().equals(alias)) {
            return false;
        }
        return plan.edges().stream().anyMatch(edge ->
                edge.rightAlias().equals(alias)
                        && edge.joinType() == DataSourceDraft.JoinType.LEFT);
    }

    private static void requireCardinality(
            DataSourceDraft.JoinEdge edge,
            List<SourceRow> left,
            List<SourceRow> right
    ) {
        if (edge.cardinality() == DataSourceDraft.JoinCardinality.ONE_TO_ONE
                || edge.cardinality()
                == DataSourceDraft.JoinCardinality.ONE_TO_MANY) {
            requireUnique(left.stream().map(row -> row.values().get(
                    edge.leftFieldCode())).toList());
        }
        if (edge.cardinality() == DataSourceDraft.JoinCardinality.ONE_TO_ONE
                || edge.cardinality()
                == DataSourceDraft.JoinCardinality.MANY_TO_ONE) {
            requireUnique(right.stream().map(row ->
                    row.values().get(edge.rightFieldCode())).toList());
        }
    }

    private static void requireUnique(List<Object> values) {
        var keys = new HashSet<String>();
        for (var value : values) {
            if (value != null && !keys.add(canonicalKey(value))) {
                throw unavailable("Published join cardinality was violated");
            }
        }
    }

    private static Map<String, List<SourceRow>> index(
            List<SourceRow> rows,
            String fieldCode
    ) {
        var index = new HashMap<String, List<SourceRow>>();
        for (var row : rows) {
            var value = row.values().get(fieldCode);
            if (value != null) {
                index.computeIfAbsent(canonicalKey(value), ignored ->
                        new ArrayList<>()).add(row);
            }
        }
        return index;
    }

    private static String canonicalKey(Object value) {
        if (value instanceof Number number) {
            try {
                return "N:" + new BigDecimal(number.toString())
                        .stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                // Fall through to a type-stable textual key.
            }
        }
        return value.getClass().getName() + ':' + value;
    }

    private static String queryHash(
            DataSourcePublication publication,
            Map<String, String> childHashes
    ) {
        var value = new StringBuilder("multi-module-join-v1|")
                .append(publication.version().id()).append('|');
        childHashes.forEach((alias, hash) -> value.append(alias)
                .append(':').append(hash).append('|'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static SourceRow sourceRow(DataSourceViews.RuntimeRow row) {
        var values = new LinkedHashMap<String, Object>();
        row.values().forEach(value -> values.put(
                value.fieldCode(), value.value()));
        return new SourceRow(row.recordId(),
                java.util.Collections.unmodifiableMap(values));
    }

    private static void requireWithinDeadline(Instant deadline) {
        if (!Instant.now().isBefore(deadline)) {
            throw unavailable("Published multi-module join timed out");
        }
    }

    private static DataSourceException unavailable(String message) {
        return new DataSourceException(
                "DATA_SOURCE_JOIN_UNAVAILABLE", message);
    }

    private record SourceRow(String recordId, Map<String, Object> values) {
    }

    private record JoinedRow(
            Map<String, SourceRow> sources,
            Map<String, String> drillThrough
    ) {
        static JoinedRow initial(String alias, SourceRow row) {
            return new JoinedRow(Map.of(alias, row),
                    Map.of(alias, row.recordId()));
        }

        JoinedRow with(String alias, SourceRow row) {
            var sourceCopy = new LinkedHashMap<>(sources);
            sourceCopy.put(alias, row);
            var drillCopy = new LinkedHashMap<>(drillThrough);
            drillCopy.put(alias, row.recordId());
            return new JoinedRow(java.util.Collections.unmodifiableMap(
                    sourceCopy), java.util.Collections.unmodifiableMap(
                    drillCopy));
        }

        JoinedRow withMissing(String alias) {
            var sourceCopy = new LinkedHashMap<>(sources);
            sourceCopy.put(alias, null);
            return new JoinedRow(java.util.Collections.unmodifiableMap(
                    sourceCopy), drillThrough);
        }

        Object value(String alias, String fieldCode) {
            var source = sources.get(alias);
            return source == null ? null : source.values().get(fieldCode);
        }
    }
}
