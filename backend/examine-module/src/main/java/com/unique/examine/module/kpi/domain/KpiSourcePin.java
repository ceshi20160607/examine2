package com.unique.examine.module.kpi.domain;

public record KpiSourcePin(
        long dataSourceId,
        String dataSourceCode,
        long dataSourceVersionId,
        int dataSourceVersionNumber,
        String moduleCode,
        String schemaVersionId,
        KpiFieldPin measureField,
        KpiFieldPin timeField
) {
    public KpiSourcePin {
        if (dataSourceId <= 0 || dataSourceVersionId <= 0
                || dataSourceVersionNumber <= 0 || timeField == null) {
            throw invalid("KPI source pin is incomplete");
        }
        dataSourceCode = text(dataSourceCode, "data source code", 64);
        moduleCode = text(moduleCode, "module code", 100);
        schemaVersionId = text(schemaVersionId, "schema version", 200);
        if (measureField != null
                && measureField.logicalFieldId() == timeField.logicalFieldId()) {
            // A numeric temporal field can legitimately be both measure and time,
            // but the two published pins must still carry identical metadata.
            if (!measureField.equals(timeField)) {
                throw invalid("KPI source pins disagree for one logical field");
            }
        }
    }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid("KPI " + label + " is invalid");
        }
        return value.strip();
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_SOURCE_INVALID", message);
    }
}
