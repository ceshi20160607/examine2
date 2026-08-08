package com.unique.examine.module.kpi.domain;

import java.util.regex.Pattern;

public record KpiFieldPin(
        long logicalFieldId,
        String code,
        String name,
        String type,
        String queryType
) {
    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    public KpiFieldPin {
        if (logicalFieldId <= 0 || code == null
                || !FIELD_CODE.matcher(code).matches()) {
            throw invalid("KPI field identity is invalid");
        }
        name = text(name, "name", 200);
        type = text(type, "type", 100);
        queryType = text(queryType, "query type", 100);
    }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > max) {
            throw invalid("KPI field " + label + " is invalid");
        }
        return value.strip();
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_FIELD_INVALID", message);
    }
}
