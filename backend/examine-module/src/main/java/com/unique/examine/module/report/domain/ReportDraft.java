package com.unique.examine.module.report.domain;

import java.util.List;
import java.util.regex.Pattern;

public record ReportDraft(long dataSourceId, List<String> outputFieldCodes) {
    public static final int MAX_OUTPUT_FIELDS = 100;
    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    public ReportDraft {
        if (dataSourceId <= 0) {
            throw invalid("Report data source id must be positive");
        }
        outputFieldCodes = outputFieldCodes == null
                ? List.of() : List.copyOf(outputFieldCodes);
        if (outputFieldCodes.size() > MAX_OUTPUT_FIELDS) {
            throw invalid("Report draft exceeds its output field bound");
        }
        outputFieldCodes.forEach(ReportDraft::fieldCode);
    }

    /** Stable length-prefixed representation used only for publish identity. */
    public String canonicalForm() {
        var value = new StringBuilder("source:");
        append(value, Long.toString(dataSourceId));
        value.append("fields:").append(outputFieldCodes.size()).append('|');
        outputFieldCodes.forEach(field -> append(value, field));
        return value.toString();
    }

    public static String fieldCode(String value) {
        if (value == null || !FIELD_CODE.matcher(value).matches()) {
            throw invalid("Report output field code is invalid");
        }
        return value;
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('|');
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_DRAFT_INVALID", message);
    }
}
