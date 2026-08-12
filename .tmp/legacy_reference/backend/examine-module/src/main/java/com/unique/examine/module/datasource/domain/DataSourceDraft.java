package com.unique.examine.module.datasource.domain;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record DataSourceDraft(
        List<OutputField> outputFields,
        List<FixedFilter> fixedFilters,
        DefaultSort defaultSort,
        String defaultTimeFieldCode,
        SourceKind sourceKind,
        HttpJsonConnection httpConnection,
        List<HttpJsonFieldProjection> httpFieldProjections,
        JdbcTableConnection jdbcTableConnection,
        List<JdbcTableFieldProjection> jdbcFieldProjections,
        MultiModuleJoin multiModuleJoin
) {
    public static final int MAX_OUTPUT_FIELDS = 50;
    public static final int MAX_FIXED_FILTERS = 20;
    public static final int MAX_HTTP_FIELD_PROJECTIONS = 50;
    public static final int MAX_JDBC_FIELD_PROJECTIONS = 50;
    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Pattern OPERATOR =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern MYSQL_IDENTIFIER =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    public DataSourceDraft {
        outputFields = outputFields == null
                ? List.of() : List.copyOf(outputFields);
        fixedFilters = fixedFilters == null
                ? List.of() : List.copyOf(fixedFilters);
        httpFieldProjections = httpFieldProjections == null
                ? List.of() : List.copyOf(httpFieldProjections);
        jdbcFieldProjections = jdbcFieldProjections == null
                ? List.of() : List.copyOf(jdbcFieldProjections);
        if (outputFields.size() > MAX_OUTPUT_FIELDS
                || fixedFilters.size() > MAX_FIXED_FILTERS
                || httpFieldProjections.size()
                > MAX_HTTP_FIELD_PROJECTIONS
                || jdbcFieldProjections.size()
                > MAX_JDBC_FIELD_PROJECTIONS) {
            throw invalid(
                    "Data source draft exceeds its field, filter, or external "
                            + "projection bound");
        }
        defaultTimeFieldCode = optionalFieldCode(defaultTimeFieldCode);
        sourceKind = sourceKind == null
                ? SourceKind.NATIVE_MODULE : sourceKind;
        if (sourceKind == SourceKind.NATIVE_MODULE
                && httpConnection != null) {
            throw invalid(
                    "Native data source must not contain an HTTP connection");
        }
        if (sourceKind == SourceKind.NATIVE_MODULE
                && !httpFieldProjections.isEmpty()) {
            throw invalid(
                    "Native data source must not contain HTTP projections");
        }
        if (sourceKind == SourceKind.HTTP_JSON
                && httpConnection == null) {
            throw invalid(
                    "HTTP JSON data source requires an HTTP connection");
        }
        if (sourceKind != SourceKind.JDBC_TABLE
                && (jdbcTableConnection != null
                || !jdbcFieldProjections.isEmpty())) {
            throw invalid(
                    "Only JDBC table data sources may contain JDBC settings");
        }
        if (sourceKind == SourceKind.JDBC_TABLE
                && jdbcTableConnection == null) {
            throw invalid(
                    "JDBC table data source requires a JDBC table connection");
        }
        if (sourceKind == SourceKind.JDBC_TABLE
                && (httpConnection != null
                || !httpFieldProjections.isEmpty())) {
            throw invalid(
                    "JDBC table data source must not contain HTTP settings");
        }
        if (sourceKind == SourceKind.MULTI_MODULE_JOIN) {
            if (multiModuleJoin == null) {
                throw invalid("Multi-module join data source requires a join plan");
            }
            if (!outputFields.isEmpty() || !fixedFilters.isEmpty()
                    || httpConnection != null || !httpFieldProjections.isEmpty()
                    || jdbcTableConnection != null
                    || !jdbcFieldProjections.isEmpty()) {
                throw invalid("Multi-module join data source contains incompatible settings");
            }
        } else if (multiModuleJoin != null) {
            throw invalid("Only multi-module join sources may contain a join plan");
        }
        var sourceFields = new HashSet<String>();
        var fieldCodes = new HashSet<String>();
        for (var projection : httpFieldProjections) {
            if (!sourceFields.add(projection.sourceField())) {
                throw invalid("HTTP projection source field is duplicated");
            }
            if (!fieldCodes.add(projection.fieldCode())) {
                throw invalid("HTTP projection anchor field is duplicated");
            }
        }
        var sourceColumns = new HashSet<String>();
        var jdbcFieldCodes = new HashSet<String>();
        for (var projection : jdbcFieldProjections) {
            if (!sourceColumns.add(normalizedIdentifier(
                    projection.sourceColumn()))) {
                throw invalid("JDBC projection source column is duplicated");
            }
            if (!jdbcFieldCodes.add(projection.fieldCode()
                    .toLowerCase(Locale.ROOT))) {
                throw invalid("JDBC projection anchor field is duplicated");
            }
        }
    }

    /** Compatibility constructor for B105 Java callers and stored JSON. */
    public DataSourceDraft(
            List<OutputField> outputFields,
            List<FixedFilter> fixedFilters,
            DefaultSort defaultSort,
            String defaultTimeFieldCode,
            SourceKind sourceKind,
            HttpJsonConnection httpConnection,
            List<HttpJsonFieldProjection> httpFieldProjections
    ) {
        this(outputFields, fixedFilters, defaultSort, defaultTimeFieldCode,
                sourceKind, httpConnection, httpFieldProjections,
                null, List.of(), null);
    }

    /** Compatibility constructor for Native/HTTP/JDBC Java callers. */
    public DataSourceDraft(
            List<OutputField> outputFields,
            List<FixedFilter> fixedFilters,
            DefaultSort defaultSort,
            String defaultTimeFieldCode,
            SourceKind sourceKind,
            HttpJsonConnection httpConnection,
            List<HttpJsonFieldProjection> httpFieldProjections,
            JdbcTableConnection jdbcTableConnection,
            List<JdbcTableFieldProjection> jdbcFieldProjections
    ) {
        this(outputFields, fixedFilters, defaultSort, defaultTimeFieldCode,
                sourceKind, httpConnection, httpFieldProjections,
                jdbcTableConnection, jdbcFieldProjections, null);
    }

    /** Compatibility constructor for existing Java callers and fixtures. */
    public DataSourceDraft(
            List<OutputField> outputFields,
            List<FixedFilter> fixedFilters,
            DefaultSort defaultSort,
            String defaultTimeFieldCode
    ) {
        this(outputFields, fixedFilters, defaultSort, defaultTimeFieldCode,
                SourceKind.NATIVE_MODULE, null, List.of(), null, List.of(),
                null);
    }

    /** Compatibility constructor for B103 Java callers and fixtures. */
    public DataSourceDraft(
            List<OutputField> outputFields,
            List<FixedFilter> fixedFilters,
            DefaultSort defaultSort,
            String defaultTimeFieldCode,
            SourceKind sourceKind,
            HttpJsonConnection httpConnection
    ) {
        this(outputFields, fixedFilters, defaultSort, defaultTimeFieldCode,
                sourceKind, httpConnection, List.of(), null, List.of(), null);
    }

    public static DataSourceDraft empty() {
        return new DataSourceDraft(List.of(), List.of(), null, null,
                SourceKind.NATIVE_MODULE, null, List.of(), null, List.of(),
                null);
    }

    /** Stable length-prefixed representation used only for publish identity. */
    public String canonicalForm() {
        var value = new StringBuilder();
        value.append("outputs:").append(outputFields.size());
        outputFields.forEach(field -> append(value, field.fieldCode()));
        value.append("|filters:").append(fixedFilters.size());
        fixedFilters.forEach(filter -> {
            append(value, filter.fieldCode());
            append(value, filter.operator());
            append(value, filter.canonicalValue());
        });
        value.append("|sort:");
        if (defaultSort == null) {
            value.append('-');
        } else {
            append(value, defaultSort.fieldCode());
            append(value, defaultSort.direction().name());
        }
        value.append("|time:");
        append(value, defaultTimeFieldCode);
        // Preserve the exact Native publication identity from V8.50. HTTP is
        // explicitly identified and cannot collide with that legacy form.
        if (sourceKind == SourceKind.HTTP_JSON) {
            value.append("|source:");
            append(value, sourceKind.name());
            value.append("http:+|");
            append(value, httpConnection.endpoint());
            append(value, httpConnection.authSecretRef());
            append(value, Integer.toString(
                    httpConnection.timeoutSeconds()));
            if (!httpFieldProjections.isEmpty()) {
                value.append("projections:")
                        .append(httpFieldProjections.size()).append('|');
                httpFieldProjections.forEach(projection -> {
                    append(value, projection.sourceField());
                    append(value, projection.fieldCode());
                    append(value, projection.sourceType().name());
                });
            }
        }
        if (sourceKind == SourceKind.JDBC_TABLE) {
            value.append("|source:");
            append(value, sourceKind.name());
            value.append("jdbc:+|");
            append(value, jdbcTableConnection.host());
            append(value, Integer.toString(jdbcTableConnection.port()));
            append(value, jdbcTableConnection.databaseName());
            append(value, jdbcTableConnection.tableName());
            append(value, jdbcTableConnection.usernameSecretRef());
            append(value, jdbcTableConnection.passwordSecretRef());
            append(value, Integer.toString(
                    jdbcTableConnection.connectTimeoutSeconds()));
            append(value, Integer.toString(
                    jdbcTableConnection.queryTimeoutSeconds()));
            if (!jdbcFieldProjections.isEmpty()) {
                value.append("projections:")
                        .append(jdbcFieldProjections.size()).append('|');
                jdbcFieldProjections.forEach(projection -> {
                    append(value, projection.sourceColumn());
                    append(value, projection.fieldCode());
                    append(value, projection.sourceType().name());
                });
            }
        }
        if (sourceKind == SourceKind.MULTI_MODULE_JOIN) {
            value.append("|source:");
            append(value, sourceKind.name());
            append(value, multiModuleJoin.failureMode().name());
            append(value, Integer.toString(multiModuleJoin.timeoutSeconds()));
            append(value, Integer.toString(multiModuleJoin.rowLimit()));
            value.append("inputs:").append(multiModuleJoin.inputs().size());
            multiModuleJoin.inputs().forEach(input -> {
                append(value, input.alias());
                append(value, Long.toString(input.dataSourceId()));
                append(value, Long.toString(input.dataSourceVersionId()));
            });
            value.append("edges:").append(multiModuleJoin.edges().size());
            multiModuleJoin.edges().forEach(edge -> {
                append(value, edge.leftAlias());
                append(value, edge.leftFieldCode());
                append(value, edge.rightAlias());
                append(value, edge.rightFieldCode());
                append(value, edge.joinType().name());
                append(value, edge.cardinality().name());
            });
            value.append("projections:")
                    .append(multiModuleJoin.projections().size());
            multiModuleJoin.projections().forEach(projection -> {
                append(value, projection.sourceAlias());
                append(value, projection.sourceFieldCode());
                append(value, projection.fieldCode());
                append(value, Long.toString(projection.logicalFieldId()));
                append(value, projection.fieldName());
                append(value, projection.type());
                append(value, projection.queryType());
            });
        }
        return value.toString();
    }

    public enum SourceKind {
        NATIVE_MODULE, HTTP_JSON, JDBC_TABLE, MULTI_MODULE_JOIN
    }

    /**
     * Immutable, bounded left-deep join plan. Each input pins one exact data
     * source publication; each input after the first is introduced by exactly
     * one edge, avoiding cycles and accidental Cartesian products.
     */
    public record MultiModuleJoin(
            List<JoinInput> inputs,
            List<JoinEdge> edges,
            List<JoinProjection> projections,
            JoinFailureMode failureMode,
            int timeoutSeconds,
            int rowLimit
    ) {
        public MultiModuleJoin {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            edges = edges == null ? List.of() : List.copyOf(edges);
            projections = projections == null
                    ? List.of() : List.copyOf(projections);
            if (inputs.size() < 2 || inputs.size() > 8
                    || edges.size() != inputs.size() - 1
                    || projections.isEmpty()
                    || projections.size() > MAX_OUTPUT_FIELDS
                    || failureMode == null || timeoutSeconds < 1
                    || timeoutSeconds > 10 || rowLimit < 1
                    || rowLimit > 100) {
                throw invalid("Multi-module join plan bounds are invalid");
            }
            var aliases = new HashSet<String>();
            for (var input : inputs) {
                if (input == null || !aliases.add(input.alias())) {
                    throw invalid("Multi-module join input aliases are duplicated");
                }
            }
            var introduced = new HashSet<String>();
            introduced.add(inputs.get(0).alias());
            for (int index = 0; index < edges.size(); index++) {
                var edge = edges.get(index);
                var expectedRight = inputs.get(index + 1).alias();
                if (edge == null || !introduced.contains(edge.leftAlias())
                        || !expectedRight.equals(edge.rightAlias())
                        || introduced.contains(edge.rightAlias())) {
                    throw invalid("Multi-module join edges must form a left-deep acyclic plan");
                }
                introduced.add(edge.rightAlias());
            }
            var outputCodes = new HashSet<String>();
            for (var projection : projections) {
                if (projection == null
                        || !aliases.contains(projection.sourceAlias())
                        || !projection.fieldCode().startsWith(
                        projection.sourceAlias() + "__")
                        || !outputCodes.add(projection.fieldCode())) {
                    throw invalid("Join projection namespace or output collision is invalid");
                }
            }
        }
    }

    public record JoinInput(
            String alias,
            long dataSourceId,
            long dataSourceVersionId
    ) {
        public JoinInput {
            if (alias == null || !alias.matches("^[A-Za-z][A-Za-z0-9_]{0,19}$")
                    || dataSourceId <= 0 || dataSourceVersionId <= 0) {
                throw invalid("Multi-module join input is invalid");
            }
        }
    }

    public record JoinEdge(
            String leftAlias,
            String leftFieldCode,
            String rightAlias,
            String rightFieldCode,
            JoinType joinType,
            JoinCardinality cardinality
    ) {
        public JoinEdge {
            if (leftAlias == null || rightAlias == null
                    || !leftAlias.matches("^[A-Za-z][A-Za-z0-9_]{0,19}$")
                    || !rightAlias.matches("^[A-Za-z][A-Za-z0-9_]{0,19}$")) {
                throw invalid("Multi-module join edge alias is invalid");
            }
            leftFieldCode = requiredFieldCode(leftFieldCode);
            rightFieldCode = requiredFieldCode(rightFieldCode);
            if (joinType == null || cardinality == null) {
                throw invalid("Multi-module join edge contract is incomplete");
            }
        }
    }

    public record JoinProjection(
            String sourceAlias,
            String sourceFieldCode,
            String fieldCode,
            long logicalFieldId,
            String fieldName,
            String type,
            String queryType,
            boolean numeric,
            boolean temporal,
            boolean groupable
    ) {
        public JoinProjection {
            if (sourceAlias == null
                    || !sourceAlias.matches("^[A-Za-z][A-Za-z0-9_]{0,19}$")) {
                throw invalid("Join projection source alias is invalid");
            }
            sourceFieldCode = requiredFieldCode(sourceFieldCode);
            fieldCode = requiredFieldCode(fieldCode);
            var pinned = logicalFieldId > 0;
            if (pinned != (fieldName != null && type != null
                    && queryType != null)) {
                throw invalid("Join projection capability pin is incomplete");
            }
            if (pinned && (fieldName.isBlank() || type.isBlank()
                    || queryType.isBlank())) {
                throw invalid("Join projection capability pin is invalid");
            }
            if (fieldName != null) {
                fieldName = fieldName.strip();
                type = type.strip();
                queryType = queryType.strip();
            }
        }

        public JoinProjection(
                String sourceAlias,
                String sourceFieldCode,
                String fieldCode
        ) {
            this(sourceAlias, sourceFieldCode, fieldCode, 0,
                    null, null, null, false, false, false);
        }
    }

    public enum JoinType { INNER, LEFT }

    public enum JoinCardinality { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE }

    public enum JoinFailureMode { FAIL_FAST, ALLOW_PARTIAL_LEFT }

    public record HttpJsonConnection(
            String endpoint,
            String authSecretRef,
            int timeoutSeconds
    ) {
        public HttpJsonConnection {
            endpoint = requiredEndpoint(endpoint);
            authSecretRef = optionalSecretReference(authSecretRef);
            if (timeoutSeconds < 1 || timeoutSeconds > 10) {
                throw invalid(
                        "HTTP JSON timeout must be between 1 and 10 seconds");
            }
        }
    }

    public record HttpJsonFieldProjection(
            String sourceField,
            String fieldCode,
            HttpJsonSourceType sourceType
    ) {
        public HttpJsonFieldProjection {
            sourceField = requiredSourceField(sourceField);
            fieldCode = requiredFieldCode(fieldCode);
            if (sourceType == null) {
                throw invalid("HTTP projection source type is required");
            }
        }
    }

    public enum HttpJsonSourceType { STRING, INTEGER, DECIMAL, BOOLEAN }

    public record JdbcTableConnection(
            String host,
            int port,
            String databaseName,
            String tableName,
            String usernameSecretRef,
            String passwordSecretRef,
            int connectTimeoutSeconds,
            int queryTimeoutSeconds
    ) {
        public JdbcTableConnection {
            host = requiredHost(host);
            if (port < 1 || port > 65_535) {
                throw invalid("JDBC table port is invalid");
            }
            databaseName = requiredMysqlIdentifier(
                    databaseName, "database name");
            tableName = requiredMysqlIdentifier(tableName, "table name");
            usernameSecretRef = requiredSecretReference(
                    usernameSecretRef, "username");
            passwordSecretRef = requiredSecretReference(
                    passwordSecretRef, "password");
            if (connectTimeoutSeconds < 1 || connectTimeoutSeconds > 10
                    || queryTimeoutSeconds < 1
                    || queryTimeoutSeconds > 10) {
                throw invalid(
                        "JDBC table timeouts must be between 1 and 10 seconds");
            }
        }

        @Override
        public String toString() {
            return "DataSourceDraft.JdbcTableConnection[host=" + host
                    + ", port=" + port + ", databaseName=" + databaseName
                    + ", tableName=" + tableName
                    + ", usernameSecretRef=redacted"
                    + ", passwordSecretRef=redacted"
                    + ", connectTimeoutSeconds=" + connectTimeoutSeconds
                    + ", queryTimeoutSeconds=" + queryTimeoutSeconds + "]";
        }
    }

    public record JdbcTableFieldProjection(
            String sourceColumn,
            String fieldCode,
            JdbcTableSourceType sourceType
    ) {
        public JdbcTableFieldProjection {
            sourceColumn = requiredMysqlIdentifier(
                    sourceColumn, "projection source column");
            fieldCode = requiredFieldCode(fieldCode);
            if (sourceType == null) {
                throw invalid("JDBC projection source type is required");
            }
        }
    }

    public enum JdbcTableSourceType {
        STRING, INTEGER, DECIMAL, BOOLEAN, DATE, TIME, DATETIME
    }

    public record OutputField(String fieldCode) {
        public OutputField {
            fieldCode = requiredFieldCode(fieldCode);
        }
    }

    public record FixedFilter(
            String fieldCode,
            String operator,
            String canonicalValue
    ) {
        public FixedFilter {
            fieldCode = requiredFieldCode(fieldCode);
            if (operator == null || !OPERATOR.matcher(operator).matches()) {
                throw invalid("Data source filter operator is invalid");
            }
            if ("EMPTY".equals(operator)) {
                if (canonicalValue != null) {
                    throw invalid("EMPTY filter must not contain a value");
                }
            } else {
                if (canonicalValue == null || canonicalValue.isBlank()
                        || canonicalValue.length() > 10_000) {
                    throw invalid(
                            "Data source filter value is invalid");
                }
                canonicalValue = canonicalValue.strip();
            }
        }
    }

    public record DefaultSort(String fieldCode, Direction direction) {
        public DefaultSort {
            fieldCode = requiredFieldCode(fieldCode);
            if (direction == null) {
                throw invalid("Data source sort direction is required");
            }
        }
    }

    public enum Direction { ASC, DESC }

    public static String requiredFieldCode(String value) {
        if (value == null || !FIELD_CODE.matcher(value).matches()) {
            throw invalid("Data source field code is invalid");
        }
        return value;
    }

    private static String optionalFieldCode(String value) {
        if (value == null) {
            return null;
        }
        return requiredFieldCode(value);
    }

    private static String requiredEndpoint(String value) {
        if (value == null || value.isBlank() || value.length() > 1024) {
            throw invalid("HTTP JSON endpoint is invalid");
        }
        var normalized = value.strip();
        final URI endpoint;
        try {
            endpoint = URI.create(normalized);
        } catch (RuntimeException invalidEndpoint) {
            throw invalid("HTTP JSON endpoint is invalid");
        }
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getHost().isBlank()
                || endpoint.getRawUserInfo() != null
                || endpoint.getRawFragment() != null
                || endpoint.getRawQuery() != null
                || endpoint.getPort() == 0
                || endpoint.getPort() < -1
                || endpoint.getPort() > 65_535) {
            throw invalid(
                    "HTTP JSON endpoint must be an HTTPS URL without "
                            + "credentials, query parameters, or fragments");
        }
        return normalized;
    }

    private static String optionalSecretReference(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > 512) {
            throw invalid("HTTP JSON secret reference is invalid");
        }
        return value.strip();
    }

    private static String requiredSourceField(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.strip())
                || value.codePointCount(0, value.length()) > 128) {
            throw invalid("HTTP projection source field is invalid");
        }
        for (int index = 0; index < value.length();) {
            var character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(index + 1))) {
                    throw invalid("HTTP projection source field is invalid");
                }
            } else if (Character.isLowSurrogate(character)) {
                throw invalid("HTTP projection source field is invalid");
            }
            var codePoint = value.codePointAt(index);
            var type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.SURROGATE) {
                throw invalid("HTTP projection source field is invalid");
            }
            index += Character.charCount(codePoint);
        }
        return value;
    }

    private static String requiredHost(String value) {
        if (value == null || value.isBlank() || value.length() > 253
                || !value.equals(value.strip())) {
            throw invalid("JDBC table host is invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isWhitespace(character)
                    || Character.isISOControl(character)) {
                throw invalid("JDBC table host is invalid");
            }
        }
        return value;
    }

    private static String requiredMysqlIdentifier(
            String value,
            String name
    ) {
        if (value == null || !MYSQL_IDENTIFIER.matcher(value).matches()) {
            throw invalid("JDBC table " + name + " is invalid");
        }
        return value;
    }

    private static String requiredSecretReference(
            String value,
            String name
    ) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw invalid("JDBC table " + name
                    + " secret reference is invalid");
        }
        return value.strip();
    }

    private static String normalizedIdentifier(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append('|');
    }

    private static DataSourceException invalid(String message) {
        return new DataSourceException("DATA_SOURCE_DRAFT_INVALID", message);
    }
}
