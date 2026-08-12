package com.unique.examine.module.report.ai;

import com.unique.examine.core.ai.AiRuntimeReportReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.runtime.ReportRuntimeService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Module-owned adapter for bounded AI reads of active published reports.
 *
 * <p>The native report owner selects and validates the publication, pinned
 * source, fields, row scope and query. This adapter only applies the live AI
 * policy and copies authorized display strings into the Core-safe result.</p>
 */
@Component
public class AiRuntimeReportReadAdapter implements AiRuntimeReportReadFacade {
    private static final long UNUSED_ACCOUNT_ID = 0L;

    private final ReportRuntimeService reports;

    public AiRuntimeReportReadAdapter(ReportRuntimeService reports) {
        this.reports = Objects.requireNonNull(reports, "reports");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        requirePermission(request, request.moduleCode());
        requirePolicyModule(request, request.moduleCode());

        var session = new RuntimeSession(
                UNUSED_ACCOUNT_ID,
                request.systemId(),
                request.memberId(),
                request.tenantId(),
                request.effectivePermissions());
        var metadata = reports.metadata(session, request.reportCode());
        requireMetadataIdentity(request, metadata);
        requirePermission(request, metadata.moduleCode());
        requirePolicyModule(request, metadata.moduleCode());

        var authorizedFields = authorizedFields(request, metadata);
        if (authorizedFields.isEmpty()) {
            throw forbidden(
                    "AI_POLICY_FIELD_DENIED",
                    "The report has no field authorized by the published AI policy");
        }

        var reportId = positiveId(metadata.id(), "report id");
        var reportVersionId = positiveId(
                metadata.versionId(), "report version id");
        var nativeRows = reports.rows(
                session, reportId, reportVersionId,
                request.page(), request.size());
        requireRowsMetadata(request, nativeRows);

        var rows = nativeRows.rows().stream()
                .map(row -> safeRow(row, authorizedFields))
                .toList();
        var returnedRows = rows.size();
        var hasMore = (long) request.page() * request.size()
                < nativeRows.total();
        var route = "/systems/" + request.systemId() + "/reports/"
                + UriUtils.encodePathSegment(
                metadata.code(), StandardCharsets.UTF_8);
        return new Result(
                metadata.code(), metadata.name(), metadata.versionNumber(),
                metadata.dataSourceCode(),
                metadata.dataSourceVersionNumber(), metadata.moduleCode(),
                nativeRows.page(), nativeRows.size(), nativeRows.total(),
                returnedRows, hasMore, route, authorizedFields, rows);
    }

    private static void requireMetadataIdentity(
            Request request,
            ReportRuntimeViews.Metadata metadata
    ) {
        if (metadata == null
                || !request.reportCode().equals(metadata.code())) {
            throw resultInvalid(
                    "The report owner returned an unexpected active report");
        }
        if (!request.moduleCode().equals(metadata.moduleCode())) {
            throw forbidden(
                    "AI_POLICY_MODULE_DENIED",
                    "The active report does not belong to the authorized module");
        }
    }

    private static void requireRowsMetadata(
            Request request,
            ReportRuntimeViews.Rows rows
    ) {
        if (rows == null || rows.page() != request.page()
                || rows.size() != request.size()
                || rows.total() < 0
                || rows.partial()
                || rows.rows().size() > request.size()) {
            throw resultInvalid(
                    "The report owner returned invalid page metadata");
        }
    }

    private static List<Field> authorizedFields(
            Request request,
            ReportRuntimeViews.Metadata metadata
    ) {
        var outbound = request.outboundFields().get(metadata.moduleCode());
        if (outbound == null) {
            throw forbidden(
                    "AI_POLICY_MODULE_DENIED",
                    "The report module has no published AI field policy");
        }
        var seen = new java.util.HashSet<String>();
        var fields = new ArrayList<Field>();
        for (var field : metadata.fields()) {
            if (!seen.add(field.fieldCode())) {
                throw resultInvalid(
                        "The report owner returned duplicate fields");
            }
            if (outbound.contains(field.fieldCode())) {
                fields.add(new Field(
                        field.fieldCode(), field.fieldName(), field.type()));
            }
        }
        return List.copyOf(fields);
    }

    private static Row safeRow(
            ReportRuntimeViews.Row row,
            List<Field> authorizedFields
    ) {
        var values = new LinkedHashMap<String, ReportRuntimeViews.Value>();
        for (var value : row.values()) {
            if (values.put(value.fieldCode(), value) != null) {
                throw resultInvalid(
                        "The report owner returned duplicate row fields");
            }
        }
        var safeValues = new ArrayList<Value>();
        for (var field : authorizedFields) {
            var value = values.get(field.fieldCode());
            if (value == null
                    || !field.fieldName().equals(value.fieldName())
                    || !field.type().equals(value.type())) {
                throw resultInvalid(
                        "The report owner returned a mismatched row field");
            }
            safeValues.add(new Value(
                    field.fieldCode(), value.displayValue()));
        }
        return new Row(safeValues);
    }

    private static void requirePermission(Request request, String moduleCode) {
        if (!request.effectivePermissions().contains("system.runtime.access")
                || !request.effectivePermissions().contains(
                "module." + moduleCode + ".view")) {
            throw forbidden(
                    "PERMISSION_DENIED",
                    "The current member cannot query this runtime report");
        }
    }

    private static void requirePolicyModule(
            Request request,
            String moduleCode
    ) {
        if (!request.allowedModuleCodes().contains(moduleCode)
                || !request.outboundFields().containsKey(moduleCode)) {
            throw forbidden(
                    "AI_POLICY_MODULE_DENIED",
                    "The report module is outside the published AI policy");
        }
    }

    private static long positiveId(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed > 0 && Long.toString(parsed).equals(value)) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
            // Converted to a sanitized owner result below.
        }
        throw resultInvalid("The report owner returned an invalid " + name);
    }

    private static BusinessException forbidden(String code, String message) {
        return new BusinessException(code, message, HttpStatus.FORBIDDEN);
    }

    private static ReportException resultInvalid(String message) {
        return new ReportException("REPORT_RESULT_INVALID", message);
    }
}
