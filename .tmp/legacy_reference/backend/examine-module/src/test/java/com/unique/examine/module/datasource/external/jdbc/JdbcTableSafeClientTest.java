package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTableSafeClientTest {
    private static final DataSourceActor ACTOR = new DataSourceActor(7, 9, 11);
    private static final String USER_REF =
            "env://EXAMINE_DS_S7_T9_REPORT_USER_V1";
    private static final String PASSWORD_REF =
            "env://EXAMINE_DS_S7_T9_REPORT_PASSWORD_V2";

    @Test
    void connectionCheckUsesOneSafeReadOnlyQueryAndClosesResources() {
        var jdbc = new JdbcHarness();
        jdbc.queryRows = List.of(row(1, 1));
        var secrets = new SecretHarness();
        var client = client(jdbc, secrets, null);

        var outcome = client.check(ACTOR, spec());

        assertThat(outcome.durationMillis()).isBetween(0L, 30_000L);
        assertThat(jdbc.openCount).isEqualTo(1);
        assertThat(jdbc.preparedSql).containsExactly("SELECT 1");
        assertThat(jdbc.maximumRows).isEqualTo(1);
        assertThat(jdbc.queryTimeout).isEqualTo(4);
        assertThat(jdbc.readOnly).isTrue();
        assertThat(jdbc.autoCommit).isFalse();
        assertThat(jdbc.rollbackCount).isEqualTo(1);
        assertThat(jdbc.connectionClosed).isTrue();
        assertThat(jdbc.lastUrl)
                .startsWith("jdbc:mysql://db.example.com:3306/reports?")
                .contains("sslMode=VERIFY_IDENTITY")
                .contains("connectTimeout=3000")
                .contains("socketTimeout=4000")
                .doesNotContain("reader", "password", USER_REF, PASSWORD_REF,
                        "orders");
        assertThat(jdbc.lastUsername).isEqualTo("reader");
        assertThat(jdbc.lastPassword).isEqualTo("password");
        assertThat(secrets.requests).extracting(
                        SecretResolverFacade.SecretRequest::reference)
                .containsExactly(USER_REF, PASSWORD_REF);
        assertThat(secrets.resolved).allSatisfy(secret ->
                assertThatThrownBy(secret::copyBytes)
                        .isInstanceOf(IllegalStateException.class));
    }

    @Test
    void deploymentMayExplicitlyDisableTlsWithoutDraftControl() {
        var jdbc = new JdbcHarness();
        jdbc.queryRows = List.of(row(1, 1));
        var client = client(jdbc, new SecretHarness(),
                JdbcTableDataSourceProperties.TlsMode.DISABLED);

        client.check(ACTOR, spec());

        assertThat(jdbc.lastUrl).contains("sslMode=DISABLED");
    }

    @Test
    void allowlistAndScopedSecretFormatFailBeforeAnySensitiveIo() {
        var jdbc = new JdbcHarness();
        var secrets = new SecretHarness();
        var denied = new JdbcTableSafeClient(secrets, jdbc,
                new JdbcTableDataSourceProperties(
                        List.of("db.example.com:3307"), null));

        assertThatThrownBy(() -> denied.check(ACTOR, spec()))
                .isInstanceOfSatisfying(DataSourceException.class, failure ->
                        assertThat(failure.code())
                                .isEqualTo("DATA_SOURCE_JDBC_SAFE_TARGET"));
        assertThat(jdbc.openCount).isZero();
        assertThat(secrets.requests).isEmpty();

        var badRef = new JdbcTableSafeClient(secrets, jdbc,
                new JdbcTableDataSourceProperties(
                        List.of("db.example.com:3306"), null));
        var crossTenant = new JdbcTableSafeClient.ConnectionSpec(
                "db.example.com", 3306, "reports", "orders",
                USER_REF, "env://EXAMINE_DS_S7_T8_PASSWORD_V1", 3, 4);

        assertThatThrownBy(() -> badRef.check(ACTOR, crossTenant))
                .isInstanceOfSatisfying(DataSourceException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(
                            "DATA_SOURCE_JDBC_SECRET_REF_INVALID");
                    assertThat(failure.getMessage()).isEqualTo(
                            "Use env://EXAMINE_DS_S{systemId}_T{tenantId}_{ALIAS}_V{version}");
                    assertThat(failure.getMessage()).doesNotContain(
                            crossTenant.passwordSecretRef());
                });
        assertThat(jdbc.openCount).isZero();
        assertThat(secrets.requests).isEmpty();
    }

    @Test
    void discoversOnlyExactTableMetadataAndMarksUnsupportedOrCollidingColumns() {
        var jdbc = new JdbcHarness();
        jdbc.tableRows = List.of(metadataTable("reports", "orders"));
        jdbc.columnRows = List.of(
                metadataColumn("reports", "orders", "id",
                        Types.BIGINT, "BIGINT", 20, false, 1),
                metadataColumn("reports", "orders", "Name",
                        Types.VARCHAR, "VARCHAR", 255, true, 2),
                metadataColumn("reports", "orders", "name",
                        Types.VARCHAR, "VARCHAR", 255, true, 3),
                metadataColumn("reports", "orders", "payload",
                        Types.BLOB, "BLOB", 4096, true, 4));
        var client = client(jdbc, new SecretHarness(), null);

        var columns = client.discover(ACTOR, spec());

        assertThat(jdbc.tableLookup).containsExactly(
                "reports", null, "orders");
        assertThat(jdbc.columnLookup).containsExactly(
                "reports", null, "orders", null);
        assertThat(jdbc.preparedSql).isEmpty();
        assertThat(columns).extracting(
                        JdbcTableSafeClient.DiscoveredColumn::sourceColumn)
                .containsExactly("id", "Name", "name", "payload");
        assertThat(columns.get(0).sourceType())
                .isEqualTo(JdbcTableSafeClient.SourceType.INTEGER);
        assertThat(columns.get(0).nullable()).isFalse();
        assertThat(columns.get(1).selectable()).isFalse();
        assertThat(columns.get(1).issueCode())
                .isEqualTo("COLUMN_NAME_COLLISION");
        assertThat(columns.get(2).selectable()).isFalse();
        assertThat(columns.get(3).sourceType()).isNull();
        assertThat(columns.get(3).issueCode())
                .isEqualTo("COLUMN_TYPE_UNSUPPORTED");
    }

    @Test
    void exactMetadataAndProjectionDriftFailClosedWithFixedErrors() {
        var missing = new JdbcHarness();
        var missingClient = client(missing, new SecretHarness(), null);
        assertCode(() -> missingClient.discover(ACTOR, spec()),
                "DATA_SOURCE_JDBC_TABLE_UNAVAILABLE");

        var drift = new JdbcHarness();
        drift.tableRows = List.of(metadataTable("reports", "orders"));
        drift.columnRows = List.of(metadataColumn(
                "reports", "orders", "id", Types.BIGINT,
                "BIGINT", 20, false, 1));
        var driftClient = client(drift, new SecretHarness(), null);
        assertCode(() -> driftClient.verifyProjections(ACTOR, spec(), List.of(
                        new JdbcTableSafeClient.Projection(
                                "id", "recordId",
                                JdbcTableSafeClient.SourceType.STRING))),
                "DATA_SOURCE_JDBC_SCHEMA_STALE");
        assertThat(drift.openCount).isEqualTo(1);
    }

    @Test
    void readsOnlyMappedColumnsWithBoundedTypedRowsAndRedactedResults() {
        var jdbc = new JdbcHarness();
        jdbc.queryRows = List.of(row(
                1, "alice",
                2, 42L,
                3, new BigDecimal("17.25"),
                4, true,
                5, java.sql.Date.valueOf("2026-08-05"),
                6, java.sql.Time.valueOf("10:11:12"),
                7, java.sql.Timestamp.valueOf("2026-08-05 10:11:12")));
        var client = client(jdbc, new SecretHarness(), null);
        var projections = List.of(
                projection("user_name", "userName",
                        JdbcTableSafeClient.SourceType.STRING),
                projection("total_count", "totalCount",
                        JdbcTableSafeClient.SourceType.INTEGER),
                projection("amount", "amount",
                        JdbcTableSafeClient.SourceType.DECIMAL),
                projection("enabled", "enabled",
                        JdbcTableSafeClient.SourceType.BOOLEAN),
                projection("business_date", "businessDate",
                        JdbcTableSafeClient.SourceType.DATE),
                projection("business_time", "businessTime",
                        JdbcTableSafeClient.SourceType.TIME),
                projection("created_at", "createdAt",
                        JdbcTableSafeClient.SourceType.DATETIME));

        var outcome = client.rows(ACTOR, spec(), projections);

        assertThat(jdbc.preparedSql).containsExactly(
                "SELECT `user_name`,`total_count`,`amount`,`enabled`,"
                        + "`business_date`,`business_time`,`created_at` "
                        + "FROM `reports`.`orders` LIMIT 25");
        assertThat(jdbc.maximumRows).isEqualTo(25);
        assertThat(jdbc.fetchSize).isEqualTo(25);
        assertThat(outcome.rows()).hasSize(1);
        assertThat(outcome.rows().get(0).values())
                .containsEntry("userName", "alice")
                .containsEntry("totalCount", BigInteger.valueOf(42))
                .containsEntry("amount", new BigDecimal("17.25"))
                .containsEntry("enabled", true)
                .containsEntry("businessDate", LocalDate.of(2026, 8, 5))
                .containsEntry("businessTime", LocalTime.of(10, 11, 12))
                .containsEntry("createdAt",
                        LocalDateTime.of(2026, 8, 5, 10, 11, 12));
        assertThat(outcome.toString()).contains("rows=redacted")
                .doesNotContain("alice", "17.25");
        assertThat(outcome.rows().get(0).toString())
                .contains("values=redacted").doesNotContain("alice");
    }

    @Test
    void enforcesHardRowLimitAndDoesNotRetryOrLeakDriverFailures() {
        var overflow = new JdbcHarness();
        var rows = new ArrayList<Map<Object, Object>>();
        for (int index = 0; index < 26; index++) {
            rows.add(row(1, "row-" + index));
        }
        overflow.queryRows = rows;
        var overflowClient = client(overflow, new SecretHarness(), null);
        assertCode(() -> overflowClient.rows(ACTOR, spec(), List.of(
                        projection("user_name", "userName",
                                JdbcTableSafeClient.SourceType.STRING))),
                "DATA_SOURCE_JDBC_ROW_LIMIT_EXCEEDED");
        assertThat(overflow.openCount).isEqualTo(1);
        assertThat(overflow.connectionClosed).isTrue();

        var failed = new JdbcHarness();
        failed.queryFailure = new SQLException(
                "driver leaked password=very-secret");
        var failedSecrets = new SecretHarness();
        var failedClient = client(failed, failedSecrets, null);
        assertThatThrownBy(() -> failedClient.check(ACTOR, spec()))
                .isInstanceOfSatisfying(DataSourceException.class, failure -> {
                    assertThat(failure.code())
                            .isEqualTo("DATA_SOURCE_JDBC_CHECK_FAILED");
                    assertThat(failure.getMessage())
                            .doesNotContain("very-secret", "password=");
                });
        assertThat(failed.openCount).isEqualTo(1);
        assertThat(failed.connectionClosed).isTrue();
        assertThat(failed.rollbackCount).isEqualTo(1);
        assertThat(failedSecrets.resolved).allSatisfy(secret ->
                assertThatThrownBy(secret::copyBytes)
                        .isInstanceOf(IllegalStateException.class));
    }

    private static JdbcTableSafeClient client(
            JdbcHarness jdbc,
            SecretHarness secrets,
            JdbcTableDataSourceProperties.TlsMode tlsMode
    ) {
        return new JdbcTableSafeClient(secrets, jdbc,
                new JdbcTableDataSourceProperties(
                        List.of("db.example.com:3306"), tlsMode));
    }

    private static JdbcTableSafeClient.ConnectionSpec spec() {
        return new JdbcTableSafeClient.ConnectionSpec(
                "db.example.com", 3306, "reports", "orders",
                USER_REF, PASSWORD_REF, 3, 4);
    }

    private static JdbcTableSafeClient.Projection projection(
            String column,
            String field,
            JdbcTableSafeClient.SourceType type
    ) {
        return new JdbcTableSafeClient.Projection(column, field, type);
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static Map<Object, Object> metadataTable(
            String database,
            String table
    ) {
        return row("TABLE_CAT", database, "TABLE_SCHEM", null,
                "TABLE_NAME", table);
    }

    private static Map<Object, Object> metadataColumn(
            String database,
            String table,
            String column,
            int type,
            String typeName,
            int size,
            boolean nullable,
            int ordinal
    ) {
        return row("TABLE_CAT", database, "TABLE_SCHEM", null,
                "TABLE_NAME", table, "COLUMN_NAME", column,
                "DATA_TYPE", type, "TYPE_NAME", typeName,
                "COLUMN_SIZE", size,
                "NULLABLE", nullable
                        ? DatabaseMetaData.columnNullable
                        : DatabaseMetaData.columnNoNulls,
                "ORDINAL_POSITION", ordinal);
    }

    private static Map<Object, Object> row(Object... cells) {
        var row = new LinkedHashMap<Object, Object>();
        for (int index = 0; index < cells.length; index += 2) {
            row.put(cells[index], cells[index + 1]);
        }
        return row;
    }

    private static final class SecretHarness implements SecretResolverFacade {
        private final List<SecretRequest> requests = new ArrayList<>();
        private final List<ResolvedSecret> resolved = new ArrayList<>();

        @Override
        public Optional<ResolvedSecret> resolve(SecretRequest request) {
            requests.add(request);
            var secret = ResolvedSecret.utf8(
                    request.reference().contains("USER")
                            ? "reader" : "password");
            resolved.add(secret);
            return Optional.of(secret);
        }
    }

    private static final class JdbcHarness implements JdbcTableConnectionOpener {
        private int openCount;
        private String lastUrl;
        private String lastUsername;
        private String lastPassword;
        private boolean readOnly;
        private boolean autoCommit = true;
        private boolean connectionClosed;
        private int rollbackCount;
        private final List<String> preparedSql = new ArrayList<>();
        private int maximumRows;
        private int fetchSize;
        private int queryTimeout;
        private List<Map<Object, Object>> queryRows = List.of();
        private List<Map<Object, Object>> tableRows = List.of();
        private List<Map<Object, Object>> columnRows = List.of();
        private SQLException queryFailure;
        private List<Object> tableLookup = List.of();
        private List<Object> columnLookup = List.of();

        @Override
        public Connection open(String url, String username, String password) {
            openCount++;
            lastUrl = url;
            lastUsername = username;
            lastPassword = password;
            return proxy(Connection.class, (ignored, method, arguments) ->
                    connectionCall(method, arguments));
        }

        private Object connectionCall(Method method, Object[] arguments)
                throws Throwable {
            return switch (method.getName()) {
                case "setReadOnly" -> {
                    readOnly = (boolean) arguments[0];
                    yield null;
                }
                case "isReadOnly" -> readOnly;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) arguments[0];
                    yield null;
                }
                case "getAutoCommit" -> autoCommit;
                case "prepareStatement" -> {
                    preparedSql.add((String) arguments[0]);
                    yield proxy(PreparedStatement.class,
                            (ignored, called, values) ->
                                    statementCall(called, values));
                }
                case "getMetaData" -> proxy(DatabaseMetaData.class,
                        (ignored, called, values) ->
                                metadataCall(called, values));
                case "rollback" -> {
                    rollbackCount++;
                    yield null;
                }
                case "close" -> {
                    connectionClosed = true;
                    yield null;
                }
                case "isClosed" -> connectionClosed;
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object statementCall(Method method, Object[] arguments)
                throws Throwable {
            return switch (method.getName()) {
                case "setMaxRows" -> {
                    maximumRows = (int) arguments[0];
                    yield null;
                }
                case "setFetchSize" -> {
                    fetchSize = (int) arguments[0];
                    yield null;
                }
                case "setQueryTimeout" -> {
                    queryTimeout = (int) arguments[0];
                    yield null;
                }
                case "executeQuery" -> {
                    if (queryFailure != null) {
                        throw queryFailure;
                    }
                    yield resultSet(queryRows);
                }
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object metadataCall(Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getTables" -> {
                    tableLookup = java.util.Arrays.asList(
                            arguments[0], arguments[1], arguments[2]);
                    yield resultSet(tableRows);
                }
                case "getColumns" -> {
                    columnLookup = java.util.Arrays.asList(
                            arguments[0], arguments[1],
                            arguments[2], arguments[3]);
                    yield resultSet(columnRows);
                }
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    private static ResultSet resultSet(List<Map<Object, Object>> rows) {
        return proxy(ResultSet.class, new InvocationHandler() {
            private int index = -1;
            private Object last;

            @Override
            public Object invoke(Object proxy, Method method, Object[] arguments) {
                return switch (method.getName()) {
                    case "next" -> ++index < rows.size();
                    case "getString" -> {
                        last = rows.get(index).get(arguments[0]);
                        yield last == null ? null : last.toString();
                    }
                    case "getInt" -> {
                        last = rows.get(index).get(arguments[0]);
                        yield last == null ? 0 : ((Number) last).intValue();
                    }
                    case "getObject" -> {
                        last = rows.get(index).get(arguments[0]);
                        yield last;
                    }
                    case "wasNull" -> last == null;
                    default -> defaultValue(method.getReturnType());
                };
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
