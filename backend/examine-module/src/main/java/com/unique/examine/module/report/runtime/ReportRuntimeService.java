package com.unique.examine.module.report.runtime;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.report.domain.PublishedReport;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.service.ReportService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public final class ReportRuntimeService {
    private static final int MAX_CATALOG_SIZE = 200;

    private final ReportService reports;
    private final ReportDataSourceRuntime sources;

    public ReportRuntimeService(
            ReportService reports,
            ReportDataSourceRuntime sources
    ) {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    public List<ReportRuntimeViews.Metadata> list(RuntimeSession session) {
        var actor = actor(session);
        var result = new ArrayList<ReportRuntimeViews.Metadata>();
        for (var root : reports.list(actor).stream()
                .filter(value -> value.activeVersionId() != null)
                .sorted(Comparator.comparing(value -> value.code()))
                .limit(MAX_CATALOG_SIZE).toList()) {
            try {
                result.add(resolve(session, reports.active(actor, root.id()))
                        .metadata());
            } catch (BusinessException hiddenOrStale) {
                // Catalog discovery must not disclose inaccessible source/report pins.
            }
        }
        return List.copyOf(result);
    }

    public ReportRuntimeViews.Metadata metadata(
            RuntimeSession session,
            String code
    ) {
        return resolve(session, active(session, code)).metadata();
    }

    /** Resolves an immutable publication instead of following the active pointer. */
    public ReportRuntimeViews.Metadata metadata(
            RuntimeSession session,
            long reportId,
            long reportVersionId
    ) {
        return resolve(session, publication(session, reportId,
                reportVersionId)).metadata();
    }

    public ReportRuntimeViews.Rows rows(
            RuntimeSession session,
            String code,
            int page,
            int size
    ) {
        return rows(session, active(session, code), page, size);
    }

    /** Pages an immutable publication for durable background consumers. */
    public ReportRuntimeViews.Rows rows(
            RuntimeSession session,
            long reportId,
            long reportVersionId,
            int page,
            int size
    ) {
        return rows(session, publication(session, reportId,
                reportVersionId), page, size);
    }

    private ReportRuntimeViews.Rows rows(
            RuntimeSession session,
            PublishedReport publication,
            int page,
            int size
    ) {
        if (page < 1 || size < 1 || size > 200) {
            throw new ReportException("REPORT_PAGE_INVALID",
                    "page must be positive and size must be between 1 and 200");
        }
        var resolved = resolve(session, publication);
        final ReportDataSourceRuntime.SourceRows sourceRows;
        try {
            var source = resolved.report().version().source();
            sourceRows = sources.rows(session, source.dataSourceId(),
                    source.dataSourceVersionId(), page, size);
        } catch (BusinessException unavailable) {
            throw sourceUnavailable();
        }
        var rows = sourceRows.rows().stream()
                .map(row -> row(row, resolved.fieldsByCode()))
                .toList();
        return new ReportRuntimeViews.Rows(
                rows, rows, sourceRows.page(), sourceRows.size(),
                sourceRows.total(), sourceRows.queryHash(),
                sourceRows.partial(), sourceRows.sourceKind(),
                sourceRows.failedSourceAliases());
    }

    private PublishedReport publication(
            RuntimeSession session,
            long reportId,
            long reportVersionId
    ) {
        try {
            return reports.publication(actor(session), reportId,
                    reportVersionId);
        } catch (ReportException known) {
            throw known;
        } catch (BusinessException hidden) {
            throw new ReportException("REPORT_VERSION_NOT_FOUND",
                    "Report publication does not exist");
        }
    }

    private PublishedReport active(RuntimeSession session, String code) {
        try {
            return reports.active(actor(session), code);
        } catch (ReportException known) {
            throw known;
        } catch (BusinessException hidden) {
            throw new ReportException("REPORT_NOT_FOUND",
                    "Report does not exist");
        }
    }

    private Resolved resolve(
            RuntimeSession session,
            PublishedReport report
    ) {
        var pin = report.version().source();
        final ReportDataSourceRuntime.SourceMetadata source;
        try {
            source = sources.metadata(session, pin.dataSourceId(),
                    pin.dataSourceVersionId());
        } catch (BusinessException unavailable) {
            throw sourceUnavailable();
        }
        if (!Long.toString(pin.dataSourceId()).equals(source.dataSourceId())
                || !pin.dataSourceCode().equals(source.dataSourceCode())
                || !Long.toString(pin.dataSourceVersionId()).equals(
                source.dataSourceVersionId())
                || pin.dataSourceVersionNumber()
                != source.dataSourceVersionNumber()
                || !pin.moduleCode().equals(source.moduleCode())
                || !pin.schemaVersionId().equals(source.schemaVersionId())) {
            throw sourceUnavailable();
        }

        Map<String, ReportDataSourceRuntime.SourceField> readable =
                new LinkedHashMap<>();
        source.fields().forEach(field -> readable.put(field.code(), field));
        var fields = new ArrayList<ReportRuntimeViews.Field>();
        var pins = new LinkedHashMap<String, ReportFieldPin>();
        for (var field : pin.fields()) {
            var current = readable.get(field.code());
            if (current == null) {
                continue;
            }
            if (!field.type().equals(current.type())) {
                throw sourceUnavailable();
            }
            fields.add(new ReportRuntimeViews.Field(
                    field.code(), field.name(), field.type()));
            pins.put(field.code(), field);
        }
        if (fields.isEmpty()) {
            throw new ReportException("REPORT_FIELDS_FORBIDDEN",
                    "No report fields are readable in the current session");
        }
        var metadata = new ReportRuntimeViews.Metadata(
                Long.toString(report.root().id()), report.version().code(),
                report.version().name(), report.version().description(),
                Long.toString(report.version().id()),
                report.version().versionNumber(),
                Long.toString(pin.dataSourceId()), pin.dataSourceCode(),
                pin.dataSourceName(), Long.toString(pin.dataSourceVersionId()),
                pin.dataSourceVersionNumber(), Long.toString(pin.moduleId()),
                pin.moduleCode(), pin.schemaVersionId(), fields);
        return new Resolved(report, metadata,
                Collections.unmodifiableMap(new LinkedHashMap<>(pins)));
    }

    private static ReportRuntimeViews.Row row(
            ReportDataSourceRuntime.SourceRow row,
            Map<String, ReportFieldPin> fields
    ) {
        Map<String, ReportDataSourceRuntime.SourceValue> values =
                new LinkedHashMap<>();
        row.values().forEach(value -> values.put(value.code(), value));
        var projected = fields.values().stream().map(pin -> {
            var value = values.get(pin.code());
            if (value == null || !pin.type().equals(value.type())) {
                return new ReportRuntimeViews.Value(
                        pin.code(), pin.name(), pin.type(), null, null);
            }
            return new ReportRuntimeViews.Value(
                    pin.code(), pin.name(), pin.type(), value.value(),
                    value.displayValue());
        }).toList();
        return new ReportRuntimeViews.Row(
                row.recordId(), row.recordNo(), row.version(), row.status(),
                row.title(), projected, row.drillThrough());
    }

    private static ReportActor actor(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new ReportException("REPORT_TENANT_REQUIRED",
                    "Select an active tenant before viewing reports");
        }
        return new ReportActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private static ReportException sourceUnavailable() {
        return new ReportException("REPORT_SOURCE_UNAVAILABLE",
                "The report source is unavailable in the current session");
    }

    private record Resolved(
            PublishedReport report,
            ReportRuntimeViews.Metadata metadata,
            Map<String, ReportFieldPin> fieldsByCode
    ) {
    }
}
