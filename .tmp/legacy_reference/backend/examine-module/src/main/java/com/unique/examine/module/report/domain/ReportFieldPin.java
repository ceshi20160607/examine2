package com.unique.examine.module.report.domain;

public record ReportFieldPin(
        long logicalFieldId,
        String code,
        String name,
        String type,
        String queryType
) {
    public ReportFieldPin {
        if (logicalFieldId <= 0) {
            throw invalid("Report field logical id must be positive");
        }
        code = ReportDraft.fieldCode(code);
        name = text(name, "name", 200);
        type = token(type, "type");
        queryType = token(queryType, "query type");
    }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid("Report field " + label + " is invalid");
        }
        return value.strip();
    }

    private static String token(String value, String label) {
        if (value == null
                || !value.matches("^[A-Z][A-Z0-9_]{0,99}$")) {
            throw invalid("Report field " + label + " is invalid");
        }
        return value;
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_FIELD_PIN_INVALID", message);
    }
}
