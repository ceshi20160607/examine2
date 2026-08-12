package com.unique.examine.core.ai;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Narrow owner port for a bounded page of an active published report.
 *
 * <p>Authenticated identity, live permissions and policy allowlists are
 * server-owned inputs. Results contain native display strings only; raw field
 * values and record envelopes never cross this boundary.</p>
 */
public interface AiRuntimeReportReadFacade {
    int MAX_PAGE = 10_000;
    int MAX_ROWS = 50;

    Result query(Request request);

    record Request(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            Set<String> allowedModuleCodes,
            Map<String, Set<String>> outboundFields,
            int maxRows,
            String moduleCode,
            String reportCode,
            int page,
            int size
    ) {
        public Request {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
                throw invalid("AI runtime report context IDs must be positive");
            }
            effectivePermissions = immutablePermissions(effectivePermissions);
            allowedModuleCodes = immutableCodes(
                    allowedModuleCodes, "allowed module codes", false);
            outboundFields = immutableOutboundFields(
                    outboundFields, allowedModuleCodes);
            if (maxRows < 1 || maxRows > MAX_ROWS) {
                throw invalid("AI runtime report maxRows must be within 1..50");
            }
            moduleCode = requiredCode(moduleCode, "module code");
            reportCode = requiredCode(reportCode, "report code");
            if (page < 1 || page > MAX_PAGE) {
                throw invalid("AI runtime report page must be within 1..10000");
            }
            if (size < 1 || size > Math.min(MAX_ROWS, maxRows)) {
                throw invalid("AI runtime report size exceeds policy maxRows");
            }
        }
    }

    record Result(
            String reportCode,
            String reportName,
            int reportVersionNumber,
            String dataSourceCode,
            int dataSourceVersionNumber,
            String moduleCode,
            int page,
            int size,
            long total,
            int returnedRows,
            boolean hasMore,
            String route,
            List<Field> fields,
            List<Row> rows
    ) {
        public Result {
            reportCode = requiredCode(reportCode, "result report code");
            reportName = boundedText(reportName, "report name", 200);
            dataSourceCode = requiredCode(
                    dataSourceCode, "result data source code");
            moduleCode = requiredCode(moduleCode, "result module code");
            if (reportVersionNumber <= 0 || dataSourceVersionNumber <= 0
                    || page < 1 || page > MAX_PAGE
                    || size < 1 || size > MAX_ROWS || total < 0
                    || returnedRows < 0 || returnedRows > size) {
                throw invalid("AI runtime report result metadata is invalid");
            }
            route = validatedRoute(route, reportCode);
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
            if (fields.isEmpty()
                    || new LinkedHashSet<>(fields.stream()
                    .map(Field::fieldCode).toList()).size() != fields.size()
                    || returnedRows != rows.size()) {
                throw invalid("AI runtime report result shape is invalid");
            }
            var expectedHasMore = (long) page * size < total;
            if (hasMore != expectedHasMore) {
                throw invalid("AI runtime report hasMore is inconsistent");
            }
            for (var row : rows) {
                if (row.values().size() != fields.size()) {
                    throw invalid("AI runtime report row field count is invalid");
                }
                for (int index = 0; index < fields.size(); index++) {
                    if (!fields.get(index).fieldCode().equals(
                            row.values().get(index).fieldCode())) {
                        throw invalid(
                                "AI runtime report row field order is invalid");
                    }
                }
            }
        }
    }

    record Field(String fieldCode, String fieldName, String type) {
        public Field {
            fieldCode = requiredCode(fieldCode, "field code");
            fieldName = boundedText(fieldName, "field name", 200);
            if (type == null
                    || !Pattern.matches("^[A-Z][A-Z0-9_]{0,99}$", type)) {
                throw invalid("AI runtime report field type is invalid");
            }
        }
    }

    record Row(List<Value> values) {
        public Row {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    record Value(String fieldCode, String displayValue) {
        public Value {
            fieldCode = requiredCode(fieldCode, "value field code");
        }
    }

    private static Set<String> immutablePermissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw invalid("AI runtime report permissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static Set<String> immutableCodes(
            Set<String> values,
            String name,
            boolean emptyAllowed
    ) {
        if (values == null || !emptyAllowed && values.isEmpty()
                || values.stream().anyMatch(value -> !validCode(value))) {
            throw invalid("AI runtime report " + name + " are invalid");
        }
        return Set.copyOf(values);
    }

    private static Map<String, Set<String>> immutableOutboundFields(
            Map<String, Set<String>> values,
            Set<String> allowedModuleCodes
    ) {
        if (values == null || !values.keySet().equals(allowedModuleCodes)) {
            throw invalid("AI runtime report outbound fields are invalid");
        }
        var result = new LinkedHashMap<String, Set<String>>();
        values.forEach((moduleCode, fields) -> result.put(
                requiredCode(moduleCode, "outbound module code"),
                immutableCodes(fields, "outbound field codes", true)));
        return Map.copyOf(result);
    }

    private static String requiredCode(String value, String name) {
        if (!validCode(value)) {
            throw invalid("AI runtime report " + name + " is invalid");
        }
        return value;
    }

    private static boolean validCode(String value) {
        return value != null && Pattern.matches(
                "^[A-Za-z][A-Za-z0-9_]{0,63}$", value);
    }

    private static String boundedText(String value, String name, int max) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > max) {
            throw invalid("AI runtime report " + name + " is invalid");
        }
        return value.strip();
    }

    private static String validatedRoute(String value, String reportCode) {
        if (value == null || !value.matches(
                "^/systems/[1-9][0-9]*/reports/[A-Za-z][A-Za-z0-9_]{0,63}$")
                || !value.endsWith("/" + reportCode)) {
            throw invalid("AI runtime report route is invalid");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
