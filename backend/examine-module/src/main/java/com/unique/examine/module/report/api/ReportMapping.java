package com.unique.examine.module.report.api;

import com.unique.examine.module.report.domain.ReportCheckReport;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportVersion;

public final class ReportMapping {
    private ReportMapping() {
    }

    public static ReportDraft draft(ReportRequests.Create request) {
        if (request == null) {
            throw invalid("Report draft is required");
        }
        return draft(request.dataSourceId(), request.outputFieldCodes());
    }

    public static ReportDraft draft(ReportRequests.SaveDraft request) {
        if (request == null) {
            throw invalid("Report draft is required");
        }
        return draft(request.dataSourceId(), request.outputFieldCodes());
    }

    private static ReportDraft draft(
            String dataSourceId,
            java.util.List<String> outputFieldCodes
    ) {
        try {
            return new ReportDraft(
                    positiveId(dataSourceId, "dataSourceId"),
                    outputFieldCodes);
        } catch (ReportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("Report draft values are malformed");
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

    public static ReportViews.Definition definition(ReportDefinition value) {
        return new ReportViews.Definition(
                Long.toString(value.id()), Long.toString(value.systemId()),
                Long.toString(value.tenantId()), value.code(), value.name(),
                value.description(), value.draftVersion(),
                string(value.activeVersionId()), value.activeVersionNumber(),
                value.createdAt().toString(), value.updatedAt().toString(),
                value.version(), new ReportViews.Draft(
                Long.toString(value.draft().dataSourceId()),
                value.draft().outputFieldCodes()));
    }

    public static ReportViews.CheckResult check(ReportCheckReport value) {
        return new ReportViews.CheckResult(
                Long.toString(value.reportId()), value.draftVersion(),
                value.publishable(), value.blockerCount(),
                value.warningCount(), source(value.source()),
                value.issues().stream().map(issue ->
                        new ReportViews.CheckIssue(issue.severity().name(),
                                issue.code(), issue.path(), issue.message()))
                        .toList());
    }

    public static ReportViews.Version version(
            ReportVersion value,
            Long activeVersionId
    ) {
        var source = value.source();
        return new ReportViews.Version(
                Long.toString(value.id()), Long.toString(value.reportId()),
                value.versionNumber(), value.sourceDraftVersion(),
                value.code(), value.name(), value.description(),
                Long.toString(source.dataSourceId()), source.dataSourceCode(),
                source.dataSourceName(),
                Long.toString(source.dataSourceVersionId()),
                source.dataSourceVersionNumber(),
                Long.toString(source.moduleId()), source.moduleCode(),
                source.schemaVersionId(), source.fields().stream()
                .map(ReportMapping::field).toList(), value.fingerprint(),
                Long.toString(value.publishedByMemberId()),
                value.publishedAt().toString(),
                activeVersionId != null && activeVersionId == value.id());
    }

    private static ReportViews.SourceCapability source(
            ReportCheckReport.SourceCapability value
    ) {
        return value == null ? null : new ReportViews.SourceCapability(
                Long.toString(value.dataSourceId()), value.dataSourceCode(),
                value.dataSourceName(),
                Long.toString(value.dataSourceVersionId()),
                value.dataSourceVersionNumber(),
                Long.toString(value.moduleId()), value.moduleCode(),
                value.schemaVersionId(), value.fields().stream().map(field ->
                new ReportViews.FieldCapability(
                        field.logicalFieldId(), field.code(), field.name(),
                        field.type(), field.queryType(), field.readable()))
                .toList());
    }

    private static ReportViews.FieldPin field(ReportFieldPin value) {
        return new ReportViews.FieldPin(
                Long.toString(value.logicalFieldId()), value.code(),
                value.name(), value.type(), value.queryType());
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_REQUEST_INVALID", message);
    }
}
