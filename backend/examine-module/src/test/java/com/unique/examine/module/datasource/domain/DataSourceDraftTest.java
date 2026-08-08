package com.unique.examine.module.datasource.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceDraftTest {
    @Test
    void preservesExplicitOrderAndUsesItForPublishIdentity() {
        var title = new DataSourceDraft.OutputField("title");
        var createdAt = new DataSourceDraft.OutputField("createdAt");
        var source = new ArrayList<>(List.of(title, createdAt));

        var draft = new DataSourceDraft(
                source,
                List.of(new DataSourceDraft.FixedFilter(
                        "status", "EQ", "OPEN")),
                new DataSourceDraft.DefaultSort(
                        "createdAt", DataSourceDraft.Direction.DESC),
                "createdAt");
        source.clear();

        assertThat(draft.outputFields()).containsExactly(title, createdAt);
        assertThat(draft.outputFields()).isUnmodifiable();
        assertThat(draft.canonicalForm()).isNotEqualTo(
                new DataSourceDraft(
                        List.of(createdAt, title), draft.fixedFilters(),
                        draft.defaultSort(), draft.defaultTimeFieldCode())
                        .canonicalForm());
    }

    @Test
    void enforcesBoundedFieldsAndFiltersAtTheDomainEdge() {
        var fields = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> new DataSourceDraft.OutputField("f" + index))
                .toList();

        assertThatThrownBy(() -> new DataSourceDraft(
                fields, List.of(), null, null))
                .isInstanceOf(DataSourceException.class)
                .satisfies(error -> assertThat(
                        ((DataSourceException) error).code())
                        .isEqualTo("DATA_SOURCE_DRAFT_INVALID"));
    }

    @Test
    void readsLegacyJsonAsNativeAndPreservesItsPublishIdentity()
            throws Exception {
        var json = new ObjectMapper();
        var legacy = json.readValue("""
                {"outputFields":[{"fieldCode":"title"}],
                 "fixedFilters":[],"defaultSort":null,
                 "defaultTimeFieldCode":null}
                """, DataSourceDraft.class);
        var constructed = new DataSourceDraft(
                List.of(new DataSourceDraft.OutputField("title")),
                List.of(), null, null);

        assertThat(legacy.sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.NATIVE_MODULE);
        assertThat(legacy.httpConnection()).isNull();
        assertThat(legacy.httpFieldProjections()).isEmpty();
        assertThat(legacy.jdbcTableConnection()).isNull();
        assertThat(legacy.jdbcFieldProjections()).isEmpty();
        assertThat(legacy.canonicalForm())
                .isEqualTo(constructed.canonicalForm())
                .isEqualTo(
                        "outputs:15:title||filters:0|sort:-|time:-1:|")
                .doesNotContain("HTTP_JSON", "NATIVE_MODULE");
    }

    @Test
    void validatesAndIdentifiesHttpJsonConnection() {
        var first = http("https://datasource.example.test/query", 5);
        var second = http("https://datasource.example.test/query", 6);

        assertThat(first.sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.HTTP_JSON);
        assertThat(first.httpConnection().authSecretRef())
                .isEqualTo("env://EXAMINE_DS_S10_T20_ORDERS_V1");
        assertThat(first.canonicalForm())
                .contains("HTTP_JSON")
                .isNotEqualTo(second.canonicalForm());

        assertThatThrownBy(() -> new DataSourceDraft.HttpJsonConnection(
                "http://datasource.example.test/query", null, 5))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> new DataSourceDraft.HttpJsonConnection(
                "https://datasource.example.test/query?q=1", null, 5))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.NATIVE_MODULE,
                first.httpConnection()))
                .isInstanceOf(DataSourceException.class);
    }

    @Test
    void preservesBoundedOrderedHttpAnchorProjections() {
        var title = projection(
                "external_title", "title",
                DataSourceDraft.HttpJsonSourceType.STRING);
        var amount = projection(
                "external_amount", "amount",
                DataSourceDraft.HttpJsonSourceType.DECIMAL);
        var draft = http(List.of(title, amount));

        assertThat(draft.httpFieldProjections())
                .containsExactly(title, amount)
                .isUnmodifiable();
        assertThat(draft.canonicalForm()).contains(
                "external_title", "title", "STRING",
                "external_amount", "amount", "DECIMAL");
        assertThat(draft.canonicalForm()).isNotEqualTo(
                http(List.of(amount, title)).canonicalForm());

        assertThatThrownBy(() -> http(List.of(
                title, projection("external_title", "other",
                        DataSourceDraft.HttpJsonSourceType.STRING))))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> http(List.of(
                title, projection("other", "title",
                        DataSourceDraft.HttpJsonSourceType.STRING))))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> projection(
                " external_title", "title",
                DataSourceDraft.HttpJsonSourceType.STRING))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> projection(
                "external" + (char) 1 + "title", "title",
                DataSourceDraft.HttpJsonSourceType.STRING))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> projection(
                String.valueOf((char) 0xD800), "title",
                DataSourceDraft.HttpJsonSourceType.STRING))
                .isInstanceOf(DataSourceException.class);
    }

    @Test
    void nativeDraftForbidsHttpProjectionsAndB103ConstructorDefaultsEmpty() {
        var connection = new DataSourceDraft.HttpJsonConnection(
                "https://datasource.example.test/query", null, 5);
        var b103 = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON, connection);

        assertThat(b103.httpFieldProjections()).isEmpty();
        assertThatThrownBy(() -> new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.NATIVE_MODULE, null,
                List.of(projection(
                        "external_title", "title",
                        DataSourceDraft.HttpJsonSourceType.STRING))))
                .isInstanceOf(DataSourceException.class);
    }

    @Test
    void validatesCanonicalJdbcTableSnapshotAndRedactsSecrets() {
        var connection = new DataSourceDraft.JdbcTableConnection(
                "mysql.internal", 3306, "operations", "work_items",
                "secret://systems/10/tenants/20/mysql-user",
                "secret://systems/10/tenants/20/mysql-password", 3, 5);
        var title = new DataSourceDraft.JdbcTableFieldProjection(
                "external_title", "title",
                DataSourceDraft.JdbcTableSourceType.STRING);
        var draft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                connection, List.of(title));

        assertThat(draft.sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.JDBC_TABLE);
        assertThat(draft.jdbcFieldProjections()).containsExactly(title)
                .isUnmodifiable();
        assertThat(draft.canonicalForm())
                .contains("JDBC_TABLE", "mysql.internal", "external_title")
                .isNotEqualTo(new DataSourceDraft(
                        List.of(), List.of(), null, null,
                        DataSourceDraft.SourceKind.JDBC_TABLE,
                        null, List.of(),
                        new DataSourceDraft.JdbcTableConnection(
                                "mysql.internal", 3306, "operations",
                                "work_items", connection.usernameSecretRef(),
                                connection.passwordSecretRef(), 3, 6),
                        List.of(title)).canonicalForm());
        assertThat(connection.toString())
                .doesNotContain(connection.usernameSecretRef())
                .doesNotContain(connection.passwordSecretRef());
    }

    @Test
    void rejectsUnsafeOrAmbiguousJdbcTableShape() {
        var connection = new DataSourceDraft.JdbcTableConnection(
                "mysql.internal", 3306, "operations", "work_items",
                "secret://mysql-user", "secret://mysql-password", 3, 5);

        assertThatThrownBy(() -> new DataSourceDraft.JdbcTableConnection(
                "mysql.internal", 3306, "operations;drop", "work_items",
                "secret://mysql-user", "secret://mysql-password", 3, 5))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                connection, List.of(
                new DataSourceDraft.JdbcTableFieldProjection(
                        "OrderId", "title",
                        DataSourceDraft.JdbcTableSourceType.STRING),
                new DataSourceDraft.JdbcTableFieldProjection(
                        "orderid", "other",
                        DataSourceDraft.JdbcTableSourceType.STRING))))
                .isInstanceOf(DataSourceException.class);
        assertThatThrownBy(() -> new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://datasource.example.test/query", null, 5),
                List.of(), connection, List.of()))
                .isInstanceOf(DataSourceException.class);
    }

    private static DataSourceDraft http(String endpoint, int timeout) {
        return new DataSourceDraft(
                List.of(new DataSourceDraft.OutputField("title")),
                List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        endpoint,
                        "env://EXAMINE_DS_S10_T20_ORDERS_V1", timeout));
    }

    private static DataSourceDraft http(
            List<DataSourceDraft.HttpJsonFieldProjection> projections
    ) {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://datasource.example.test/query", null, 5),
                projections);
    }

    private static DataSourceDraft.HttpJsonFieldProjection projection(
            String sourceField,
            String fieldCode,
            DataSourceDraft.HttpJsonSourceType sourceType
    ) {
        return new DataSourceDraft.HttpJsonFieldProjection(
                sourceField, fieldCode, sourceType);
    }
}
