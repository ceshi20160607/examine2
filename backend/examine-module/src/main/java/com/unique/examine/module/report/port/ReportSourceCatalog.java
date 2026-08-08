package com.unique.examine.module.report.port;

import java.util.List;
import java.util.Optional;

/** Native published data-source capabilities used by report publication. */
public interface ReportSourceCatalog {
    Optional<SourceVersion> active(
            long systemId, long tenantId, long dataSourceId);

    Optional<SourceVersion> version(
            long systemId,
            long tenantId,
            long dataSourceId,
            long dataSourceVersionId);

    record SourceVersion(
            long dataSourceId,
            long systemId,
            long tenantId,
            String dataSourceCode,
            String dataSourceName,
            long dataSourceVersionId,
            int dataSourceVersionNumber,
            long moduleId,
            String moduleCode,
            String schemaVersionId,
            List<Field> fields
    ) {
        public SourceVersion {
            if (dataSourceId <= 0 || systemId <= 0 || tenantId <= 0
                    || dataSourceVersionId <= 0
                    || dataSourceVersionNumber <= 0 || moduleId <= 0
                    || fields == null
                    || fields.stream().map(Field::code).distinct().count()
                    != fields.size()
                    || fields.stream().map(Field::logicalFieldId)
                    .distinct().count() != fields.size()) {
                throw new IllegalArgumentException(
                        "Report source catalog version is invalid");
            }
            dataSourceCode = validatedCode(
                    dataSourceCode, "source code", 64);
            dataSourceName = text(dataSourceName, "source name", 200);
            moduleCode = validatedCode(moduleCode, "module code", 100);
            schemaVersionId = text(schemaVersionId, "schema version", 200);
            fields = List.copyOf(fields);
        }
    }

    record Field(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String queryType,
            boolean readable
    ) {
        public Field {
            if (logicalFieldId <= 0) {
                throw new IllegalArgumentException(
                        "Report source logical field id is invalid");
            }
            code = validatedCode(code, "field code", 64);
            name = text(name, "field name", 200);
            type = token(type, "field type");
            queryType = token(queryType, "field query type");
        }
    }

    private static String validatedCode(
            String value,
            String label,
            int max
    ) {
        if (value == null || value.length() > max
                || !value.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException(
                    "Report source " + label + " is invalid");
        }
        return value;
    }

    private static String token(String value, String label) {
        if (value == null
                || !value.matches("^[A-Z][A-Z0-9_]{0,99}$")) {
            throw new IllegalArgumentException(
                    "Report source " + label + " is invalid");
        }
        return value;
    }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(
                    "Report source " + label + " is invalid");
        }
        return value.strip();
    }
}
