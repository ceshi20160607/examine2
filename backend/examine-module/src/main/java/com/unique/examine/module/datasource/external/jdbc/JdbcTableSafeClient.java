package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Package-local MySQL executor. It accepts only structured identifiers and
 * emits internally constructed, read-only statements.
 */
final class JdbcTableSafeClient {
    static final int MAXIMUM_COLUMNS = 50;
    static final int MAXIMUM_ROWS = 25;
    static final int MAXIMUM_TEXT_LENGTH = 4_096;

    private static final long MAXIMUM_DURATION_MILLIS = 30_000;
    private static final Pattern IDENTIFIER =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");
    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    private static final Pattern SECRET_ALIAS =
            Pattern.compile("^[A-Z][A-Z0-9_]{0,127}$");

    private final SecretResolverFacade secrets;
    private final JdbcTableConnectionOpener opener;
    private final JdbcTableTargetPolicy targets;
    private final JdbcTableDataSourceProperties.TlsMode tlsMode;

    JdbcTableSafeClient(
            SecretResolverFacade secrets,
            JdbcTableConnectionOpener opener,
            JdbcTableDataSourceProperties properties
    ) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.targets = new JdbcTableTargetPolicy(properties);
        this.tlsMode = Objects.requireNonNull(properties, "properties")
                .tlsMode();
    }

    CheckOutcome check(
            DataSourceActor actor,
            ConnectionSpec spec
    ) {
        var started = System.nanoTime();
        withConnection(actor, spec, connection -> {
            try (var statement = connection.prepareStatement("SELECT 1")) {
                statement.setMaxRows(1);
                statement.setQueryTimeout(spec.queryTimeoutSeconds());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != 1 || rows.wasNull()
                            || rows.next()) {
                        throw failure(
                                "CHECK_INVALID",
                                "The MySQL connection check returned an invalid result");
                    }
                }
            } catch (SQLTimeoutException timeout) {
                throw failure(
                        "QUERY_TIMEOUT",
                        "The MySQL connection check timed out");
            } catch (SQLException failed) {
                throw failure(
                        "CHECK_FAILED",
                        "The MySQL connection check failed");
            }
            return null;
        });
        return new CheckOutcome(elapsedMillis(started));
    }

    List<DiscoveredColumn> discover(
            DataSourceActor actor,
            ConnectionSpec spec
    ) {
        return withConnection(actor, spec,
                connection -> discover(connection, spec));
    }

    void verifyProjections(
            DataSourceActor actor,
            ConnectionSpec spec,
            List<Projection> projections
    ) {
        var validated = projections(projections);
        var columns = discover(actor, spec);
        var byName = new HashMap<String, DiscoveredColumn>();
        columns.forEach(column -> byName.put(column.sourceColumn(), column));
        for (var projection : validated) {
            var column = byName.get(projection.sourceColumn());
            if (column == null || !column.selectable()
                    || column.sourceType() != projection.sourceType()) {
                throw failure(
                        "SCHEMA_STALE",
                        "The MySQL table schema no longer matches its projections");
            }
        }
    }

    RowsOutcome rows(
            DataSourceActor actor,
            ConnectionSpec spec,
            List<Projection> projections
    ) {
        var validated = projections(projections);
        return withConnection(actor, spec,
                connection -> rows(connection, spec, validated));
    }

    private List<DiscoveredColumn> discover(
            Connection connection,
            ConnectionSpec spec
    ) {
        try {
            var metadata = connection.getMetaData();
            requireExactTable(metadata, spec);
            var columns = new ArrayList<DiscoveredColumn>();
            try (var rows = metadata.getColumns(
                    spec.databaseName(), null, spec.tableName(), null)) {
                while (rows.next()) {
                    if (columns.size() >= MAXIMUM_COLUMNS) {
                        throw failure(
                                "SCHEMA_TOO_WIDE",
                                "The MySQL table exceeded the column limit");
                    }
                    requireExactMetadataRow(rows, spec);
                    var name = rows.getString("COLUMN_NAME");
                    if (!validIdentifier(name)) {
                        throw failure(
                                "SCHEMA_INVALID",
                                "The MySQL table metadata is invalid");
                    }
                    var sourceType = sourceType(
                            rows.getInt("DATA_TYPE"),
                            rows.getString("TYPE_NAME"),
                            rows.getInt("COLUMN_SIZE"));
                    var nullable = rows.getInt("NULLABLE")
                            != DatabaseMetaData.columnNoNulls;
                    var ordinal = rows.getInt("ORDINAL_POSITION");
                    if (ordinal <= 0) {
                        throw failure(
                                "SCHEMA_INVALID",
                                "The MySQL table metadata is invalid");
                    }
                    columns.add(new DiscoveredColumn(
                            name, sourceType, nullable,
                            sourceType != null,
                            sourceType == null
                                    ? "COLUMN_TYPE_UNSUPPORTED" : null,
                            ordinal));
                }
            }
            if (columns.isEmpty()) {
                throw failure(
                        "SCHEMA_EMPTY",
                        "The MySQL table returned no columns");
            }
            return normalizeColumns(columns);
        } catch (DataSourceException failure) {
            throw failure;
        } catch (SQLTimeoutException timeout) {
            throw failure(
                    "QUERY_TIMEOUT",
                    "The MySQL metadata read timed out");
        } catch (SQLException failed) {
            throw failure(
                    "SCHEMA_FAILED",
                    "The MySQL table metadata could not be read");
        }
    }

    private static void requireExactTable(
            DatabaseMetaData metadata,
            ConnectionSpec spec
    ) throws SQLException {
        var matches = 0;
        try (var rows = metadata.getTables(
                spec.databaseName(), null, spec.tableName(),
                new String[]{"TABLE"})) {
            while (rows.next()) {
                var tableName = rows.getString("TABLE_NAME");
                if (!spec.tableName().equals(tableName)
                        || !exactDatabase(rows, spec.databaseName())) {
                    throw failure(
                            "SCHEMA_INVALID",
                            "The MySQL table metadata is invalid");
                }
                matches++;
                if (matches > 1) {
                    throw failure(
                            "SCHEMA_INVALID",
                            "The MySQL table metadata is invalid");
                }
            }
        }
        if (matches != 1) {
            throw failure(
                    "TABLE_UNAVAILABLE",
                    "The configured MySQL table is unavailable");
        }
    }

    private static void requireExactMetadataRow(
            ResultSet row,
            ConnectionSpec spec
    ) throws SQLException {
        if (!spec.tableName().equals(row.getString("TABLE_NAME"))
                || !exactDatabase(row, spec.databaseName())) {
            throw failure(
                    "SCHEMA_INVALID",
                    "The MySQL table metadata is invalid");
        }
    }

    private static boolean exactDatabase(
            ResultSet row,
            String databaseName
    ) throws SQLException {
        var catalog = row.getString("TABLE_CAT");
        var schema = row.getString("TABLE_SCHEM");
        return databaseName.equals(catalog)
                || catalog == null && databaseName.equals(schema);
    }

    private static List<DiscoveredColumn> normalizeColumns(
            List<DiscoveredColumn> columns
    ) {
        columns.sort(Comparator
                .comparingInt(DiscoveredColumn::ordinal)
                .thenComparing(DiscoveredColumn::sourceColumn));
        var exact = new HashSet<String>();
        var collisionCounts = new HashMap<String, Integer>();
        for (var column : columns) {
            if (!exact.add(column.sourceColumn())) {
                throw failure(
                        "SCHEMA_INVALID",
                        "The MySQL table metadata is invalid");
            }
            collisionCounts.merge(
                    collisionKey(column.sourceColumn()), 1, Integer::sum);
        }
        return columns.stream().map(column -> {
            if (collisionCounts.get(collisionKey(
                    column.sourceColumn())) <= 1) {
                return column;
            }
            return new DiscoveredColumn(
                    column.sourceColumn(), column.sourceType(),
                    column.nullable(), false, "COLUMN_NAME_COLLISION",
                    column.ordinal());
        }).toList();
    }

    private RowsOutcome rows(
            Connection connection,
            ConnectionSpec spec,
            List<Projection> projections
    ) {
        var sql = selectSql(spec, projections);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(MAXIMUM_ROWS);
            statement.setFetchSize(MAXIMUM_ROWS);
            statement.setQueryTimeout(spec.queryTimeoutSeconds());
            var result = new ArrayList<Row>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (result.size() >= MAXIMUM_ROWS) {
                        throw failure(
                                "ROW_LIMIT_EXCEEDED",
                                "The MySQL table response exceeded the row limit");
                    }
                    var values = new LinkedHashMap<String, Object>();
                    for (int index = 0; index < projections.size(); index++) {
                        var projection = projections.get(index);
                        var raw = rows.getObject(index + 1);
                        var decoded = decode(raw, projection.sourceType());
                        if (!decoded.valid()) {
                            throw failure(
                                    "SCHEMA_STALE",
                                    "The MySQL table response no longer matches its projections");
                        }
                        values.put(projection.fieldCode(), decoded.value());
                    }
                    result.add(new Row(result.size() + 1, values));
                }
            }
            return new RowsOutcome(
                    projections.stream().map(projection -> new Field(
                            projection.fieldCode(), projection.sourceType()))
                            .toList(), result);
        } catch (DataSourceException failure) {
            throw failure;
        } catch (SQLTimeoutException timeout) {
            throw failure(
                    "QUERY_TIMEOUT",
                    "The MySQL table read timed out");
        } catch (SQLException failed) {
            throw failure(
                    "READ_FAILED",
                    "The MySQL table could not be read");
        }
    }

    private <T> T withConnection(
            DataSourceActor actor,
            ConnectionSpec spec,
            ConnectionOperation<T> operation
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(operation, "operation");
        validateSpec(spec);
        final JdbcTableTargetPolicy.Target target;
        try {
            target = targets.requireAllowed(spec.host(), spec.port());
        } catch (JdbcTableTargetPolicy.UnsafeTargetException rejected) {
            throw failure(
                    "SAFE_TARGET",
                    "The MySQL target is not permitted");
        }
        requireSecretReference(
                actor.systemId(), actor.tenantId(), spec.usernameSecretRef());
        requireSecretReference(
                actor.systemId(), actor.tenantId(), spec.passwordSecretRef());
        var usernameSecret = resolve(
                actor, spec.usernameSecretRef());
        try (usernameSecret) {
            var passwordSecret = resolve(actor, spec.passwordSecretRef());
            try (passwordSecret) {
                var usernameBytes = usernameSecret.copyBytes();
                var passwordBytes = passwordSecret.copyBytes();
                try {
                    var username = credential(usernameBytes, 512);
                    var password = credential(passwordBytes, 4_096);
                    var url = jdbcUrl(target, spec, tlsMode);
                    final Connection connection;
                    try {
                        connection = opener.open(url, username, password);
                    } catch (SQLTimeoutException timeout) {
                        throw failure(
                                "CONNECT_TIMEOUT",
                                "The MySQL connection timed out");
                    } catch (SQLException | RuntimeException unavailable) {
                        throw failure(
                                "CONNECTION_FAILED",
                                "The MySQL connection could not be opened");
                    }
                    if (connection == null) {
                        throw failure(
                                "CONNECTION_FAILED",
                                "The MySQL connection could not be opened");
                    }
                    try (connection) {
                        configureReadOnly(connection);
                        try {
                            return operation.apply(connection);
                        } finally {
                            rollback(connection);
                        }
                    } catch (DataSourceException failure) {
                        throw failure;
                    } catch (SQLException closeFailure) {
                        throw failure(
                                "CONNECTION_FAILED",
                                "The MySQL connection could not be completed safely");
                    }
                } finally {
                    Arrays.fill(usernameBytes, (byte) 0);
                    Arrays.fill(passwordBytes, (byte) 0);
                }
            }
        }
    }

    private SecretResolverFacade.ResolvedSecret resolve(
            DataSourceActor actor,
            String reference
    ) {
        try {
            return secrets.resolve(new SecretResolverFacade.SecretRequest(
                            actor.systemId(), actor.tenantId(), reference))
                    .orElseThrow(JdbcTableSafeClient::secretFailure);
        } catch (DataSourceException failure) {
            throw failure;
        } catch (RuntimeException unavailable) {
            throw secretFailure();
        }
    }

    private static void configureReadOnly(Connection connection) {
        try {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            if (!connection.isReadOnly() || connection.getAutoCommit()) {
                throw failure(
                        "READ_ONLY_UNAVAILABLE",
                        "The MySQL connection could not be made read-only");
            }
        } catch (DataSourceException failure) {
            throw failure;
        } catch (SQLException | RuntimeException unavailable) {
            throw failure(
                    "READ_ONLY_UNAVAILABLE",
                    "The MySQL connection could not be made read-only");
        }
    }

    private static void rollback(Connection connection) throws SQLException {
        if (!connection.isClosed()) {
            connection.rollback();
        }
    }

    private static String jdbcUrl(
            JdbcTableTargetPolicy.Target target,
            ConnectionSpec spec,
            JdbcTableDataSourceProperties.TlsMode tlsMode
    ) {
        var connectMillis = Math.multiplyExact(
                spec.connectTimeoutSeconds(), 1_000);
        var queryMillis = Math.multiplyExact(
                spec.queryTimeoutSeconds(), 1_000);
        return "jdbc:mysql://" + target.host() + ':' + target.port()
                + '/' + spec.databaseName()
                + "?sslMode=" + tlsMode.name()
                + "&allowPublicKeyRetrieval=false"
                + "&useUnicode=true&characterEncoding=UTF-8"
                + "&serverTimezone=UTC"
                + "&connectTimeout=" + connectMillis
                + "&socketTimeout=" + queryMillis;
    }

    private static String selectSql(
            ConnectionSpec spec,
            List<Projection> projections
    ) {
        var columns = projections.stream()
                .map(Projection::sourceColumn)
                .map(JdbcTableSafeClient::quote)
                .toList();
        return "SELECT " + String.join(",", columns)
                + " FROM " + quote(spec.databaseName()) + '.'
                + quote(spec.tableName()) + " LIMIT 25";
    }

    private static String quote(String identifier) {
        if (!validIdentifier(identifier)) {
            throw failure(
                    "CONFIG_INVALID",
                    "The MySQL table configuration is invalid");
        }
        return '`' + identifier + '`';
    }

    private static List<Projection> projections(
            List<Projection> projections
    ) {
        if (projections == null || projections.isEmpty()
                || projections.size() > MAXIMUM_COLUMNS) {
            throw failure(
                    "PROJECTION_INVALID",
                    "The MySQL table projections are invalid");
        }
        var sourceColumns = new HashSet<String>();
        var fieldCodes = new HashSet<String>();
        var collisionKeys = new HashSet<String>();
        for (var projection : projections) {
            if (projection == null
                    || !validIdentifier(projection.sourceColumn())
                    || !FIELD_CODE.matcher(projection.fieldCode()).matches()
                    || projection.sourceType() == null
                    || !sourceColumns.add(projection.sourceColumn())
                    || !fieldCodes.add(projection.fieldCode())
                    || !collisionKeys.add(collisionKey(
                    projection.sourceColumn()))) {
                throw failure(
                        "PROJECTION_INVALID",
                        "The MySQL table projections are invalid");
            }
        }
        return List.copyOf(projections);
    }

    private static void validateSpec(ConnectionSpec spec) {
        if (!validIdentifier(spec.databaseName())
                || !validIdentifier(spec.tableName())
                || spec.connectTimeoutSeconds() < 1
                || spec.connectTimeoutSeconds() > 10
                || spec.queryTimeoutSeconds() < 1
                || spec.queryTimeoutSeconds() > 10) {
            throw failure(
                    "CONFIG_INVALID",
                    "The MySQL table configuration is invalid");
        }
    }

    private static boolean validIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static String collisionKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static SourceType sourceType(
            int jdbcType,
            String typeName,
            int columnSize
    ) {
        var normalized = typeName == null
                ? "" : typeName.toUpperCase(Locale.ROOT);
        if (jdbcType == Types.BOOLEAN
                || jdbcType == Types.BIT && columnSize == 1
                || SetTypes.BOOLEAN_NAMES.contains(normalized)) {
            return SourceType.BOOLEAN;
        }
        if (SetTypes.STRING_TYPES.contains(jdbcType)) {
            return SourceType.STRING;
        }
        if (SetTypes.INTEGER_TYPES.contains(jdbcType)) {
            return SourceType.INTEGER;
        }
        if (SetTypes.DECIMAL_TYPES.contains(jdbcType)) {
            return SourceType.DECIMAL;
        }
        if (jdbcType == Types.DATE) {
            return SourceType.DATE;
        }
        if (jdbcType == Types.TIME
                || jdbcType == Types.TIME_WITH_TIMEZONE) {
            return SourceType.TIME;
        }
        if (jdbcType == Types.TIMESTAMP
                || jdbcType == Types.TIMESTAMP_WITH_TIMEZONE) {
            return SourceType.DATETIME;
        }
        return null;
    }

    private static Decoded decode(Object raw, SourceType sourceType) {
        if (raw == null) {
            return new Decoded(true, null);
        }
        try {
            return switch (sourceType) {
                case STRING -> raw instanceof String value
                        && value.length() <= MAXIMUM_TEXT_LENGTH
                        && validUnicode(value)
                        ? new Decoded(true, value) : Decoded.INVALID;
                case INTEGER -> integer(raw);
                case DECIMAL -> decimal(raw);
                case BOOLEAN -> raw instanceof Boolean value
                        ? new Decoded(true, value) : Decoded.INVALID;
                case DATE -> date(raw);
                case TIME -> time(raw);
                case DATETIME -> datetime(raw);
            };
        } catch (RuntimeException invalid) {
            return Decoded.INVALID;
        }
    }

    private static Decoded integer(Object raw) {
        final BigInteger value;
        if (raw instanceof BigInteger integer) {
            value = integer;
        } else if (raw instanceof BigDecimal decimal) {
            value = decimal.toBigIntegerExact();
        } else if (raw instanceof Byte || raw instanceof Short
                || raw instanceof Integer || raw instanceof Long) {
            value = BigInteger.valueOf(((Number) raw).longValue());
        } else {
            return Decoded.INVALID;
        }
        return value.abs().toString().length() <= 38
                ? new Decoded(true, value) : Decoded.INVALID;
    }

    private static Decoded decimal(Object raw) {
        final BigDecimal value;
        if (raw instanceof BigDecimal decimal) {
            value = decimal;
        } else if (raw instanceof BigInteger integer) {
            value = new BigDecimal(integer);
        } else if (raw instanceof Byte || raw instanceof Short
                || raw instanceof Integer || raw instanceof Long) {
            value = BigDecimal.valueOf(((Number) raw).longValue());
        } else if (raw instanceof Float || raw instanceof Double) {
            var number = ((Number) raw).doubleValue();
            if (!Double.isFinite(number)) {
                return Decoded.INVALID;
            }
            value = BigDecimal.valueOf(number);
        } else {
            return Decoded.INVALID;
        }
        var expandedPrecision = value.signum() == 0
                ? value.precision()
                : value.precision() - Math.min(value.scale(), 0);
        return value.precision() <= 38
                && expandedPrecision <= 38
                && value.scale() <= 10
                ? new Decoded(true, value) : Decoded.INVALID;
    }

    private static Decoded date(Object raw) {
        if (raw instanceof LocalDate value) {
            return new Decoded(true, value);
        }
        if (raw instanceof java.sql.Date value) {
            return new Decoded(true, value.toLocalDate());
        }
        return Decoded.INVALID;
    }

    private static Decoded time(Object raw) {
        if (raw instanceof LocalTime value) {
            return new Decoded(true, value);
        }
        if (raw instanceof java.sql.Time value) {
            return new Decoded(true, value.toLocalTime());
        }
        return Decoded.INVALID;
    }

    private static Decoded datetime(Object raw) {
        if (raw instanceof LocalDateTime value) {
            return new Decoded(true, value);
        }
        if (raw instanceof java.sql.Timestamp value) {
            return new Decoded(true, value.toLocalDateTime());
        }
        return Decoded.INVALID;
    }

    private static boolean validUnicode(String value) {
        for (int offset = 0; offset < value.length();) {
            var character = value.charAt(offset);
            if (Character.isHighSurrogate(character)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(offset + 1))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
            offset += Character.charCount(value.codePointAt(offset));
        }
        return true;
    }

    private static void requireSecretReference(
            long systemId,
            long tenantId,
            String reference
    ) {
        var prefix = "env://EXAMINE_DS_S" + systemId
                + "_T" + tenantId + '_';
        if (reference == null || !reference.startsWith(prefix)) {
            throw secretReferenceFailure();
        }
        var versionMarker = reference.lastIndexOf("_V");
        if (versionMarker <= prefix.length()
                || versionMarker + 2 >= reference.length()) {
            throw secretReferenceFailure();
        }
        var alias = reference.substring(prefix.length(), versionMarker);
        var version = reference.substring(versionMarker + 2);
        if (!SECRET_ALIAS.matcher(alias).matches()
                || !version.matches("^[1-9][0-9]{0,8}$")) {
            throw secretReferenceFailure();
        }
    }

    private static DataSourceException secretReferenceFailure() {
        return failure(
                "SECRET_REF_INVALID",
                "Use env://EXAMINE_DS_S{systemId}_T{tenantId}_{ALIAS}_V{version}");
    }

    private static String credential(byte[] bytes, int maximum) {
        if (bytes.length == 0 || bytes.length > maximum) {
            throw secretFailure();
        }
        final String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw secretFailure();
        }
        if (value.isEmpty() || value.indexOf('\0') >= 0) {
            throw secretFailure();
        }
        return value;
    }

    private static DataSourceException secretFailure() {
        return failure(
                "SECRET_UNAVAILABLE",
                "The MySQL credentials are unavailable");
    }

    private static DataSourceException failure(
            String suffix,
            String message
    ) {
        return new DataSourceException(
                "DATA_SOURCE_JDBC_" + suffix, message);
    }

    private static long elapsedMillis(long started) {
        return Math.min(MAXIMUM_DURATION_MILLIS, Math.max(
                0, Duration.ofNanos(
                        System.nanoTime() - started).toMillis()));
    }

    record ConnectionSpec(
            String host,
            int port,
            String databaseName,
            String tableName,
            String usernameSecretRef,
            String passwordSecretRef,
            int connectTimeoutSeconds,
            int queryTimeoutSeconds
    ) {
    }

    record Projection(
            String sourceColumn,
            String fieldCode,
            SourceType sourceType
    ) {
    }

    enum SourceType { STRING, INTEGER, DECIMAL, BOOLEAN, DATE, TIME, DATETIME }

    record CheckOutcome(long durationMillis) {
    }

    record DiscoveredColumn(
            String sourceColumn,
            SourceType sourceType,
            boolean nullable,
            boolean selectable,
            String issueCode,
            int ordinal
    ) {
    }

    record Field(String fieldCode, SourceType sourceType) {
    }

    record Row(int rowIndex, Map<String, Object> values) {
        Row {
            values = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(values));
        }

        @Override
        public String toString() {
            return "JdbcTableSafeClient.Row[rowIndex=" + rowIndex
                    + ", values=redacted]";
        }
    }

    record RowsOutcome(List<Field> fields, List<Row> rows) {
        RowsOutcome {
            fields = List.copyOf(fields);
            rows = List.copyOf(rows);
        }

        @Override
        public String toString() {
            return "JdbcTableSafeClient.RowsOutcome[fields=" + fields
                    + ", rows=redacted]";
        }
    }

    private record Decoded(boolean valid, Object value) {
        private static final Decoded INVALID = new Decoded(false, null);

        @Override
        public String toString() {
            return "JdbcTableSafeClient.Decoded[valid=" + valid
                    + ", value=redacted]";
        }
    }

    @FunctionalInterface
    private interface ConnectionOperation<T> {
        T apply(Connection connection);
    }

    private static final class SetTypes {
        private static final java.util.Set<Integer> STRING_TYPES = java.util.Set.of(
                Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR);
        private static final java.util.Set<Integer> INTEGER_TYPES = java.util.Set.of(
                Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT);
        private static final java.util.Set<Integer> DECIMAL_TYPES = java.util.Set.of(
                Types.DECIMAL, Types.NUMERIC, Types.REAL,
                Types.FLOAT, Types.DOUBLE);
        private static final java.util.Set<String> BOOLEAN_NAMES = java.util.Set.of(
                "BOOLEAN", "BOOL");

        private SetTypes() {
        }
    }
}
