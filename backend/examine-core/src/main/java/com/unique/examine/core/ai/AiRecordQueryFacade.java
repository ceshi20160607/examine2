package com.unique.examine.core.ai;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Narrow owner port for bounded, read-only AI record queries.
 *
 * <p>Implementations retain authority over tenant and row scope, field
 * visibility and sensitive-value projection. The result deliberately exposes
 * display values only and never transports raw field values.</p>
 */
public interface AiRecordQueryFacade {

    Result query(Request request);

    record Request(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode,
            String canonicalQueryJson,
            List<String> outboundFieldCodes,
            int maxRows
    ) {
        public Request {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
                throw new IllegalArgumentException("AI record query context IDs must be positive");
            }
            if (effectivePermissions == null
                    || effectivePermissions.stream().anyMatch(
                    permission -> permission == null || permission.isBlank())) {
                throw new IllegalArgumentException("AI record query permissions are invalid");
            }
            effectivePermissions = Set.copyOf(effectivePermissions);
            if (moduleCode == null
                    || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("AI record query module code is invalid");
            }
            if (canonicalQueryJson == null
                    || canonicalQueryJson.isBlank()
                    || canonicalQueryJson.getBytes(StandardCharsets.UTF_8).length > 32 * 1024) {
                throw new IllegalArgumentException("AI canonical record query JSON is invalid");
            }
            if (outboundFieldCodes == null
                    || outboundFieldCodes.stream().anyMatch(code -> code == null
                    || !code.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$"))
                    || new LinkedHashSet<>(outboundFieldCodes).size()
                    != outboundFieldCodes.size()) {
                throw new IllegalArgumentException("AI outbound field codes are invalid");
            }
            outboundFieldCodes = List.copyOf(outboundFieldCodes);
            if (maxRows < 1 || maxRows > 50) {
                throw new IllegalArgumentException("AI record query maxRows must be within 1..50");
            }
        }
    }

    record Result(long total, List<Record> records) {
        public Result {
            if (total < 0) {
                throw new IllegalArgumentException("AI record query total must not be negative");
            }
            records = List.copyOf(Objects.requireNonNull(records, "records"));
        }
    }

    record Record(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            List<DisplayValue> values
    ) {
        public Record {
            if (!positiveLong(recordId)) {
                throw new IllegalArgumentException("AI record ID is invalid");
            }
            if (version < 0) {
                throw new IllegalArgumentException("AI record version must not be negative");
            }
            Objects.requireNonNull(recordNo, "recordNo");
            Objects.requireNonNull(status, "status");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }

        private static boolean positiveLong(String value) {
            try {
                var parsed = Long.parseLong(value);
                return parsed > 0 && Long.toString(parsed).equals(value);
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    record DisplayValue(String fieldCode, String displayValue) {
        public DisplayValue {
            if (fieldCode == null
                    || !fieldCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("AI display field code is invalid");
            }
        }
    }
}
