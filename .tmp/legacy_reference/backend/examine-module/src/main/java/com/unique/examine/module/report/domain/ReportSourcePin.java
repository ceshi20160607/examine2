package com.unique.examine.module.report.domain;

import java.util.HashSet;
import java.util.List;

public record ReportSourcePin(
        long dataSourceId,
        String dataSourceCode,
        String dataSourceName,
        long dataSourceVersionId,
        int dataSourceVersionNumber,
        long moduleId,
        String moduleCode,
        String schemaVersionId,
        List<ReportFieldPin> fields
) {
    public ReportSourcePin {
        if (dataSourceId <= 0 || dataSourceVersionId <= 0 || moduleId <= 0
                || dataSourceVersionNumber <= 0 || fields == null
                || fields.isEmpty()
                || fields.size() > ReportDraft.MAX_OUTPUT_FIELDS) {
            throw invalid("Published report source pin is incomplete");
        }
        dataSourceCode = code(dataSourceCode);
        dataSourceName = text(dataSourceName, "data source name", 200);
        moduleCode = code(moduleCode, "module code", 100);
        schemaVersionId = text(schemaVersionId, "schema version", 200);
        fields = List.copyOf(fields);
        var codes = new HashSet<String>();
        var logicalIds = new HashSet<Long>();
        for (var field : fields) {
            if (field == null || !codes.add(field.code())
                    || !logicalIds.add(field.logicalFieldId())) {
                throw invalid("Published report field pins are duplicated");
            }
        }
    }

    private static String code(String value) {
        return code(value, "data source code", 64);
    }

    private static String code(String value, String label, int max) {
        if (value == null || value.length() > max
                || !value.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw invalid("Published report " + label + " is invalid");
        }
        return value;
    }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid("Published report " + label + " is invalid");
        }
        return value.strip();
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_SOURCE_PIN_INVALID", message);
    }
}
