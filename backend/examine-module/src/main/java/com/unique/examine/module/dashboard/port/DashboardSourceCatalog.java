package com.unique.examine.module.dashboard.port;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped published data-source references used during dashboard check. */
public interface DashboardSourceCatalog {
    Optional<SourceRoot> source(
            long systemId,
            long tenantId,
            long dataSourceId);

    Optional<SourceVersion> version(
            long systemId,
            long tenantId,
            long dataSourceId,
            long dataSourceVersionId);

    record SourceRoot(
            long dataSourceId,
            long systemId,
            long tenantId,
            String dataSourceCode,
            Long activeVersionId,
            Integer activeVersionNumber
    ) {
        public SourceRoot {
            if (dataSourceId <= 0 || systemId <= 0 || tenantId <= 0
                    || dataSourceCode == null || dataSourceCode.isBlank()
                    || (activeVersionId == null)
                    != (activeVersionNumber == null)
                    || activeVersionId != null && activeVersionId <= 0
                    || activeVersionNumber != null
                    && activeVersionNumber <= 0) {
                throw new IllegalArgumentException(
                        "Dashboard source root is invalid");
            }
            dataSourceCode = dataSourceCode.strip();
        }
    }

    record SourceVersion(
            long versionId,
            long dataSourceId,
            long systemId,
            long tenantId,
            int versionNumber,
            String dataSourceCode,
            String moduleCode,
            String schemaVersionId,
            boolean moduleAvailable,
            int readableOutputCount,
            List<SourceField> fields
    ) {
        public SourceVersion {
            if (versionId <= 0 || dataSourceId <= 0 || systemId <= 0
                    || tenantId <= 0 || versionNumber <= 0
                    || dataSourceCode == null || dataSourceCode.isBlank()
                    || moduleCode == null || moduleCode.isBlank()
                    || schemaVersionId == null || schemaVersionId.isBlank()
                    || readableOutputCount < 0 || readableOutputCount > 50
                    || fields == null
                    || fields.stream().map(SourceField::code).distinct().count()
                    != fields.size()) {
                throw new IllegalArgumentException(
                        "Dashboard source version is invalid");
            }
            dataSourceCode = dataSourceCode.strip();
            moduleCode = moduleCode.strip();
            schemaVersionId = schemaVersionId.strip();
            fields = List.copyOf(fields);
        }

        public SourceVersion(
                long versionId,
                long dataSourceId,
                long systemId,
                long tenantId,
                int versionNumber,
                String dataSourceCode,
                String moduleCode,
                String schemaVersionId,
                boolean moduleAvailable,
                int readableOutputCount
        ) {
            this(versionId, dataSourceId, systemId, tenantId, versionNumber,
                    dataSourceCode, moduleCode, schemaVersionId,
                    moduleAvailable, readableOutputCount, List.of());
        }
    }

    record SourceField(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String queryType,
            boolean readable,
            boolean numeric,
            boolean temporal,
            boolean groupable
    ) {
        public SourceField {
            if (logicalFieldId <= 0 || code == null || code.isBlank()
                    || name == null || name.isBlank()
                    || type == null || type.isBlank()
                    || queryType == null || queryType.isBlank()) {
                throw new IllegalArgumentException(
                        "Dashboard source field is invalid");
            }
            code = code.strip();
            name = name.strip();
            type = type.strip();
            queryType = queryType.strip();
        }

        /** Compatibility constructor for existing source-catalog fixtures. */
        public SourceField(
                String code,
                String type,
                boolean readable,
                boolean numeric,
                boolean temporal,
                boolean groupable
        ) {
            this(legacyLogicalFieldId(code), code, code, type, type,
                    readable, numeric, temporal, groupable);
        }

        private static long legacyLogicalFieldId(String code) {
            if (code == null) {
                return -1;
            }
            return Integer.toUnsignedLong(code.hashCode()) + 1L;
        }
    }
}
