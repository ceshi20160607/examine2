package com.unique.examine.module.datasource.statistics.domain;

import java.util.List;

public record StatisticsCapabilities(
        long dataSourceId,
        String dataSourceCode,
        long dataSourceVersionId,
        int dataSourceVersionNumber,
        String moduleCode,
        String schemaVersionId,
        List<Field> fields,
        String sourceKind,
        boolean partial,
        int sourceRowLimit,
        boolean multiModule
) {
    public StatisticsCapabilities {
        if (dataSourceId <= 0 || dataSourceVersionId <= 0
                || dataSourceVersionNumber <= 0 || fields == null
                || sourceKind == null || sourceKind.isBlank()
                || sourceRowLimit < 0
                || partial != (sourceRowLimit > 0)) {
            throw new IllegalArgumentException(
                    "Statistics capabilities metadata is invalid");
        }
        dataSourceCode = text(dataSourceCode, "data source code", 64);
        moduleCode = text(moduleCode, "module code", 100);
        schemaVersionId = text(schemaVersionId, "schema version", 200);
        sourceKind = text(sourceKind, "source kind", 32);
        fields = List.copyOf(fields);
        if (fields.stream().map(Field::code).distinct().count()
                != fields.size()) {
            throw new IllegalArgumentException(
                    "Statistics capabilities fields are duplicated");
        }
    }

    public StatisticsCapabilities(
            long dataSourceId,
            String dataSourceCode,
            long dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleCode,
            String schemaVersionId,
            List<Field> fields
    ) {
        this(dataSourceId, dataSourceCode, dataSourceVersionId,
                dataSourceVersionNumber, moduleCode, schemaVersionId, fields,
                "NATIVE_MODULE", false, 0, false);
    }

    public record Field(
            String code,
            String name,
            String type,
            boolean readable,
            boolean numeric,
            boolean temporal,
            boolean groupable
    ) {
        public Field {
            code = text(code, "field code", 64);
            name = text(name, "field name", 200);
            type = text(type, "field type", 100);
        }
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(
                    "Statistics " + name + " is invalid");
        }
        return value.strip();
    }
}
