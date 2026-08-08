package com.unique.examine.core.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Narrow Module-owner port for one authorized runtime record context summary.
 *
 * <p>The owner remains responsible for live module permission, row scope and
 * sensitive-value projection. Only display values may cross this boundary.</p>
 */
public interface AiRecordContextFacade {
    int MAX_OUTBOUND_FIELDS = 128;

    Result summary(Request request);

    record Request(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode,
            String recordId,
            List<String> outboundFieldCodes
    ) {
        public Request {
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            outboundFieldCodes = fieldCodes(outboundFieldCodes);
        }
    }

    record Result(String moduleCode, Record record) {
        public Result {
            moduleCode = code(moduleCode, "moduleCode");
            record = Objects.requireNonNull(record, "record");
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
            recordId = positiveDecimal(recordId, "recordId");
            recordNo = requiredText(recordNo, "recordNo", 200);
            if (version < 0) {
                throw new IllegalArgumentException("version is invalid");
            }
            status = requiredText(status, "status", 32);
            if (title != null && title.codePointCount(0, title.length()) > 1_000) {
                throw new IllegalArgumentException("title is invalid");
            }
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.size() > MAX_OUTBOUND_FIELDS
                    || values.stream().map(DisplayValue::fieldCode).distinct().count()
                    != values.size()) {
                throw new IllegalArgumentException("display values are invalid");
            }
        }
    }

    record DisplayValue(String fieldCode, String displayValue) {
        public DisplayValue {
            fieldCode = code(fieldCode, "fieldCode");
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static List<String> fieldCodes(List<String> values) {
        if (values == null || values.isEmpty()
                || values.size() > MAX_OUTBOUND_FIELDS
                || values.stream().anyMatch(value -> !isCode(value))
                || new LinkedHashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("outboundFieldCodes are invalid");
        }
        return List.copyOf(values);
    }

    private static String code(String value, String name) {
        if (!isCode(value)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static boolean isCode(String value) {
        return value != null && value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static String requiredText(String value, String name, int maximum) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
