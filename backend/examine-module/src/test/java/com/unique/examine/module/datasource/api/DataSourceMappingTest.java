package com.unique.examine.module.datasource.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.service.DataSourceConnectionCheckUseCase;
import com.unique.examine.module.datasource.service.DataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.DataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.JdbcTableDataSourceSchemaDiscoveryUseCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceMappingTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void mapsHttpCreateAndViewWithoutResolvingTheSecret() throws Exception {
        var request = JSON.readValue("""
                {"code":"external_orders","moduleId":"91",
                 "name":"External orders","description":null,
                 "sourceKind":"HTTP_JSON",
                 "httpJsonConnection":{
                   "endpoint":"https://datasource.example.test/query",
                   "authSecretRef":"env://EXAMINE_DS_S10_T20_ORDERS_V1",
                   "timeoutSeconds":5}}
                """, DataSourceRequests.Create.class);
        var draft = DataSourceMapping.draft(request);
        var root = ModuleDataSource.create(
                1, 10, 20, request.code(), 91, request.name(), null,
                draft, Instant.parse("2026-08-05T00:00:00Z"));

        assertThat(draft.sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.HTTP_JSON);
        assertThat(draft.httpConnection().authSecretRef())
                .isEqualTo("env://EXAMINE_DS_S10_T20_ORDERS_V1");
        var view = DataSourceMapping.source(root, null, JSON).draft();
        assertThat(view.sourceKind()).isEqualTo("HTTP_JSON");
        assertThat(view.httpJsonConnection().endpoint())
                .isEqualTo("https://datasource.example.test/query");
        assertThat(view.httpJsonConnection().authSecretRef())
                .isEqualTo("env://EXAMINE_DS_S10_T20_ORDERS_V1");
        assertThat(view.httpFieldProjections()).isEmpty();
    }

    @Test
    void mapsLegacySavePayloadToNative() throws Exception {
        var request = JSON.readValue("""
                {"expectedVersion":1,"name":"Orders","description":null,
                 "outputFields":[{"fieldCode":"title"}],
                 "fixedFilters":[],"defaultSort":null,
                 "defaultTimeFieldCode":null}
                """, DataSourceRequests.SaveDraft.class);

        var draft = DataSourceMapping.draft(request, JSON);

        assertThat(draft.sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.NATIVE_MODULE);
        assertThat(draft.httpConnection()).isNull();
        assertThat(draft.httpFieldProjections()).isEmpty();
    }

    @Test
    void mapsJdbcRequestMergesBlankSecretsAndReturnsOnlySafeState()
            throws Exception {
        var current = jdbcDraft(
                "secret://systems/10/tenants/20/mysql-user-v1",
                "secret://systems/10/tenants/20/mysql-password-v1");
        var request = JSON.readValue("""
                {"expectedVersion":2,"name":"External orders",
                 "description":null,"outputFields":[],"fixedFilters":[],
                 "defaultSort":null,"defaultTimeFieldCode":null,
                 "sourceKind":"JDBC_TABLE",
                 "jdbcTableConnection":{"host":"mysql.internal",
                   "port":3306,"databaseName":"operations",
                   "tableName":"orders","usernameSecretRef":"",
                   "passwordSecretRef":"secret://password-v2",
                   "connectTimeoutSeconds":3,"queryTimeoutSeconds":5},
                 "jdbcFieldProjections":[
                   {"sourceColumn":"external_title","fieldCode":"title",
                    "sourceType":"STRING"}]}
                """, DataSourceRequests.SaveDraft.class);

        var mapped = DataSourceMapping.draft(request, JSON, current);
        var root = ModuleDataSource.create(
                1, 10, 20, "jdbc_orders", 91, "External orders", null,
                mapped, Instant.parse("2026-08-05T00:00:00Z"));
        var view = DataSourceMapping.source(root, null, JSON).draft();
        var serialized = JSON.writeValueAsString(view);

        assertThat(mapped.jdbcTableConnection().usernameSecretRef())
                .isEqualTo(current.jdbcTableConnection().usernameSecretRef());
        assertThat(mapped.jdbcTableConnection().passwordSecretRef())
                .isEqualTo("secret://password-v2");
        assertThat(mapped.jdbcFieldProjections()).singleElement()
                .satisfies(value -> assertThat(value.sourceColumn())
                        .isEqualTo("external_title"));
        assertThat(view.jdbcTableConnection().usernameConfigured()).isTrue();
        assertThat(view.jdbcTableConnection().passwordConfigured()).isTrue();
        assertThat(serialized)
                .doesNotContain("usernameSecretRef", "passwordSecretRef",
                        "mysql-user-v1", "password-v2");
    }

    @Test
    void mapsHttpSavePayloadToThePersistedDomainShape() throws Exception {
        var request = JSON.readValue("""
                {"expectedVersion":2,"name":"External orders",
                 "description":null,
                 "outputFields":[],
                 "fixedFilters":[],"defaultSort":null,
                 "defaultTimeFieldCode":null,"sourceKind":"HTTP_JSON",
                 "httpJsonConnection":{
                   "endpoint":"https://datasource.example.test/query",
                   "authSecretRef":null,"timeoutSeconds":7},
                 "httpFieldProjections":[
                   {"sourceField":"external_title","fieldCode":"title",
                    "sourceType":"STRING"}]}
                """, DataSourceRequests.SaveDraft.class);

        var draft = DataSourceMapping.draft(request, JSON);

        assertThat(draft.sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.HTTP_JSON);
        assertThat(draft.httpConnection())
                .isEqualTo(new DataSourceDraft.HttpJsonConnection(
                        "https://datasource.example.test/query", null, 7));
        assertThat(draft.httpFieldProjections()).containsExactly(
                new DataSourceDraft.HttpJsonFieldProjection(
                        "external_title", "title",
                        DataSourceDraft.HttpJsonSourceType.STRING));
    }

    @Test
    void mapsOnlyTheSafeConnectionCheckSummary() {
        var result = new DataSourceConnectionCheckUseCase.Result(
                true, true, 200, 12, "SUCCESS", "Connection succeeded");

        var view = DataSourceMapping.connectionCheck(result);

        assertThat(view).isEqualTo(new DataSourceViews.ConnectionCheckResult(
                true, true, 200, 12, "SUCCESS",
                "Connection succeeded"));
    }

    @Test
    void mapsOnlyBoundedSchemaDiscoveryMetadata() {
        var result = new DataSourceSchemaDiscoveryUseCase.Result(
                true, true, 200, 14, "SUCCESS", "Schema discovered", 3,
                List.of(new DataSourceSchemaDiscoveryUseCase.Field(
                        "external_title", "external_title", "STRING",
                        true, true, null)));

        var view = DataSourceMapping.schemaDiscovery(result);

        assertThat(view.checkedDraftVersion()).isEqualTo(3);
        assertThat(view.fields()).containsExactly(
                new DataSourceViews.SchemaDiscoveryField(
                        "external_title", "external_title", "STRING",
                        true, true, null));
    }

    @Test
    void mapsJdbcSchemaUsingSourceColumnWithoutAnHttpStatus() {
        var result = new JdbcTableDataSourceSchemaDiscoveryUseCase.Result(
                true, true, 8, "SUCCESS", "Schema discovered", 4,
                List.of(new JdbcTableDataSourceSchemaDiscoveryUseCase.Field(
                        "occurred_at", "occurredAt", "DATETIME",
                        false, true, null)));

        var view = DataSourceMapping.schemaDiscovery(result);

        assertThat(view.httpStatus()).isNull();
        assertThat(view.fields()).containsExactly(
                new DataSourceViews.SchemaDiscoveryField(
                        null, "occurred_at", "occurredAt", "DATETIME",
                        false, true, null));
    }

    @Test
    void mapsOnlyOrderedSafeDraftPreviewFieldsAndTypedValues()
            throws Exception {
        var values = new LinkedHashMap<String, Object>();
        values.put("title", "External order");
        values.put("count", new BigInteger("7"));
        values.put("amount", new BigDecimal("12.50"));
        values.put("enabled", true);
        values.put("note", null);
        var row = new DataSourceDraftRowsPreviewUseCase.Row(1, values);
        var result = new DataSourceDraftRowsPreviewUseCase.Result(
                true, true, 200, 17, "SUCCESS", "Preview succeeded", 4,
                List.of(
                        new DataSourceDraftRowsPreviewUseCase.Field(
                                "title", "STRING"),
                        new DataSourceDraftRowsPreviewUseCase.Field(
                                "count", "INTEGER"),
                        new DataSourceDraftRowsPreviewUseCase.Field(
                                "amount", "DECIMAL"),
                        new DataSourceDraftRowsPreviewUseCase.Field(
                                "enabled", "BOOLEAN"),
                        new DataSourceDraftRowsPreviewUseCase.Field(
                                "note", "STRING")),
                List.of(row));

        values.put("later", "must not appear");
        var view = DataSourceMapping.draftRowsPreview(result);
        var json = JSON.writeValueAsString(view);

        assertThat(view.checkedDraftVersion()).isEqualTo(4);
        assertThat(view.fields())
                .extracting(DataSourceViews.DraftRowsPreviewField::fieldCode)
                .containsExactly(
                        "title", "count", "amount", "enabled", "note");
        assertThat(view.rows()).singleElement().satisfies(mapped -> {
            assertThat(mapped.rowIndex()).isEqualTo(1);
            assertThat(mapped.values().keySet()).containsExactly(
                    "title", "count", "amount", "enabled", "note");
            assertThat(mapped.values())
                    .containsEntry("title", "External order")
                    .containsEntry("count", new BigInteger("7"))
                    .containsEntry("amount", new BigDecimal("12.50"))
                    .containsEntry("enabled", true)
                    .containsEntry("note", null);
            assertThat(mapped.values()).doesNotContainKey("later");
        });
        assertThat(json)
                .doesNotContain(
                        "\"sourceField\"", "\"raw\"", "\"endpoint\"",
                        "\"secret\"", "\"recordId\"", "\"total\""
                );
        assertThat(result.toString()).doesNotContain("External order");
        assertThat(row.toString()).doesNotContain("External order");
        assertThat(view.toString()).doesNotContain("External order");
        assertThat(view.rows().getFirst().toString())
                .doesNotContain("External order");
    }

    private static DataSourceDraft jdbcDraft(
            String usernameSecretRef,
            String passwordSecretRef
    ) {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.JDBC_TABLE, null, List.of(),
                new DataSourceDraft.JdbcTableConnection(
                        "mysql.internal", 3306, "operations", "orders",
                        usernameSecretRef, passwordSecretRef, 3, 5),
                List.of(new DataSourceDraft.JdbcTableFieldProjection(
                        "external_title", "title",
                        DataSourceDraft.JdbcTableSourceType.STRING)));
    }
}
