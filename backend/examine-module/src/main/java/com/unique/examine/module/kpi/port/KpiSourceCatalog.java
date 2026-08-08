package com.unique.examine.module.kpi.port;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped published native data-source metadata used by KPI publication. */
public interface KpiSourceCatalog {
    Optional<SourceVersion> active(
            long systemId,
            long tenantId,
            long dataSourceId);

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
            long versionId,
            int versionNumber,
            String moduleCode,
            String schemaVersionId,
            List<Field> fields
    ) {
        public SourceVersion {
            if (dataSourceId <= 0 || systemId <= 0 || tenantId <= 0
                    || versionId <= 0 || versionNumber <= 0
                    || dataSourceCode == null || dataSourceCode.isBlank()
                    || moduleCode == null || moduleCode.isBlank()
                    || schemaVersionId == null || schemaVersionId.isBlank()
                    || fields == null
                    || fields.stream().map(Field::code).distinct().count()
                    != fields.size()
                    || fields.stream().map(Field::logicalFieldId).distinct()
                    .count() != fields.size()) {
                throw new IllegalArgumentException(
                        "KPI source version is invalid");
            }
            dataSourceCode = dataSourceCode.strip();
            moduleCode = moduleCode.strip();
            schemaVersionId = schemaVersionId.strip();
            fields = List.copyOf(fields);
        }
    }

    record Field(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String queryType,
            boolean readable,
            boolean numeric,
            boolean temporal
    ) {
        public Field {
            if (logicalFieldId <= 0 || code == null || code.isBlank()
                    || name == null || name.isBlank()
                    || type == null || type.isBlank()
                    || queryType == null || queryType.isBlank()) {
                throw new IllegalArgumentException("KPI source field is invalid");
            }
            code = code.strip();
            name = name.strip();
            type = type.strip();
            queryType = queryType.strip();
        }

        public boolean money() {
            return "MONEY".equals(type) || "MONEY".equals(queryType);
        }
    }
}
