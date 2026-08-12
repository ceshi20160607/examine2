package com.unique.examine.module.datasource.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Published module capabilities used by check/publish without runtime coupling. */
public interface DataSourceModuleCatalog {
    /** Published modules available to the current tenant's admin selector. */
    default List<PublishedModule> publishedModules(
            long systemId,
            long tenantId
    ) {
        return List.of();
    }

    Optional<PublishedModule> publishedModule(
            long systemId,
            long tenantId,
            long moduleId);

    /** Adapters override this to reuse native typed canonical normalization. */
    default CanonicalFilterValue canonicalizeFilterValue(
            long systemId,
            long tenantId,
            long moduleId,
            FieldCapability field,
            String operator,
            String canonicalValue
    ) {
        return CanonicalFilterValue.accepted(canonicalValue);
    }

    record PublishedModule(
            long moduleId,
            String moduleCode,
            String moduleName,
            String schemaVersionId,
            List<FieldCapability> fields
    ) {
        public PublishedModule {
            if (moduleId <= 0 || moduleCode == null || moduleCode.isBlank()
                    || moduleName == null || moduleName.isBlank()
                    || schemaVersionId == null || schemaVersionId.isBlank()
                    || fields == null || fields.stream()
                    .map(FieldCapability::code).distinct().count()
                    != fields.size()) {
                throw new IllegalArgumentException(
                        "Published module catalog is invalid");
            }
            moduleCode = moduleCode.strip();
            moduleName = moduleName.strip();
            schemaVersionId = schemaVersionId.strip();
            fields = List.copyOf(fields);
        }
    }

    record FieldCapability(
            long logicalFieldId,
            String code,
            String fieldName,
            String type,
            String queryType,
            Set<String> operators,
            boolean sortable,
            boolean temporal,
        boolean available
    ) {
        public FieldCapability {
            if (logicalFieldId == 0 || available && logicalFieldId < 0
                    || code == null || code.isBlank()
                    || fieldName == null || fieldName.isBlank() || type == null
                    || type.isBlank() || queryType == null
                    || queryType.isBlank() || operators == null) {
                throw new IllegalArgumentException(
                        "Data source field capability is invalid");
            }
            code = code.strip();
            fieldName = fieldName.strip();
            type = type.strip();
            queryType = queryType.strip();
            operators = Set.copyOf(operators);
        }

        public FieldCapability(
                long logicalFieldId,
                String code,
                String fieldName,
                String type,
                Set<String> operators,
                boolean sortable,
                boolean temporal,
                boolean available
        ) {
            this(logicalFieldId, code, fieldName, type, type, operators,
                    sortable, temporal, available);
        }

        /** Compatibility constructor for pre-query-type tests and adapters. */
        public FieldCapability(
                String code,
                String fieldName,
                String type,
                Set<String> operators,
                boolean sortable,
                boolean temporal,
                boolean available
        ) {
            this(legacyLogicalFieldId(code), code, fieldName, type, type,
                    operators, sortable, temporal, available);
        }

        private static long legacyLogicalFieldId(String code) {
            if (code == null) {
                return -1;
            }
            return Integer.toUnsignedLong(code.hashCode()) + 1L;
        }
    }

    record CanonicalFilterValue(
            boolean valid,
            String canonicalValue,
            String errorMessage
    ) {
        public CanonicalFilterValue {
            if (valid && errorMessage != null
                    || !valid && (errorMessage == null
                    || errorMessage.isBlank())) {
                throw new IllegalArgumentException(
                        "Canonical filter result is invalid");
            }
            if (errorMessage != null) {
                errorMessage = errorMessage.strip();
            }
        }

        public static CanonicalFilterValue accepted(String value) {
            return new CanonicalFilterValue(true, value, null);
        }

        public static CanonicalFilterValue rejected(String message) {
            return new CanonicalFilterValue(false, null, message);
        }
    }
}
