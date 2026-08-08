package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordDraftExpiryWorker;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import com.unique.examine.module.runtime.service.ReferenceRecalculationWorker;
import com.unique.examine.module.runtime.service.SavedViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Vs3DraftJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "vs3_root";
    private static final String ROOT_PASSWORD = "Vs3-Root-Test-Password-84!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_vs3_test")
            .withUsername("examine_vs3_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.locations", () -> "filesystem:"
                + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.bootstrap.root.username", () -> ROOT_USERNAME);
        registry.add("examine.bootstrap.root.password", () -> ROOT_PASSWORD);
        registry.add("examine.bootstrap.root.display-name", () -> "VS3 Test Root");
        registry.add("examine.runtime.draft-expiry.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.runtime.draft-expiry.batch-size", () -> 10);
        registry.add("examine.runtime.reference-recalculation.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.jobs.print.poll-delay-ms", () -> 25);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordDraftExpiryWorker draftExpiryWorker;

    @Autowired
    private RecordRuntimeService recordRuntimeService;

    @Autowired
    private ReferenceRecalculationWorker referenceRecalculationWorker;

    @Autowired
    private SavedViewRepository savedViewRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Test
    void publishesTypedDerivedSnapshotsAndBlocksDependencyCycles() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));
        var system = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "p4_c4_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "P4 C4 Contract", "description", "derived publication contract", "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        assertOk(client.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";

        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "derived", "name", "Derived", "description", "", "iconKey", "function",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", "0"
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var module = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", text(group.body(), "/data/id"), "code", "derived_order", "name", "Derived order",
                "description", "", "iconKey", "calculator", "sortOrder", 0, "status", "ENABLED",
                "allowComments", false, "allowTeam", false, "draftRevision", "1"
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");
        var customerModule = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", text(group.body(), "/data/id"), "code", "customer", "name", "Customer",
                "description", "", "iconKey", "user", "sortOrder", 1, "status", "ENABLED",
                "allowComments", false, "allowTeam", false, "draftRevision", "2"
        )), Map.of("Idempotency-Key", key()));
        assertOk(customerModule);
        var customerModuleId = text(customerModule.body(), "/data/id");
        var lineModule = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", text(group.body(), "/data/id"), "code", "order_line", "name", "Order line",
                "description", "", "iconKey", "list", "sortOrder", 2, "status", "ENABLED",
                "allowComments", false, "allowTeam", false, "draftRevision", "3"
        )), Map.of("Idempotency-Key", key()));
        assertOk(lineModule);
        var lineModuleId = text(lineModule.body(), "/data/id");

        var amount = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                derivedContractField("amount", "Amount", "NUMBER", false, Map.of(), "4"),
                Map.of("Idempotency-Key", key()));
        assertOk(amount);
        var amountId = text(amount.body(), "/data/id");
        var customerName = client.postWithCsrf(configRoot + "/modules/" + customerModuleId + "/fields",
                derivedContractField("name", "Name", "TEXT", false, Map.of(), "5"),
                Map.of("Idempotency-Key", key()));
        assertOk(customerName);
        var customerNameId = text(customerName.body(), "/data/id");
        var lineAmount = client.postWithCsrf(configRoot + "/modules/" + lineModuleId + "/fields",
                derivedContractField("line_amount", "Line amount", "NUMBER", false, Map.of(), "6"),
                Map.of("Idempotency-Key", key()));
        assertOk(lineAmount);
        var lineAmountId = text(lineAmount.body(), "/data/id");

        var relation = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields", json(Map.ofEntries(
                Map.entry("dictionaryId", ""), Map.entry("targetModuleId", customerModuleId),
                Map.entry("code", "customer"), Map.entry("name", "Customer"), Map.entry("type", "RELATION"),
                Map.entry("sortOrder", 1), Map.entry("required", false), Map.entry("hidden", false),
                Map.entry("readonly", false), Map.entry("searchable", false), Map.entry("filterable", false),
                Map.entry("showInList", true), Map.entry("showInDetail", true), Map.entry("indexMode", "NONE"),
                Map.entry("status", "ENABLED"), Map.entry("properties", Map.of("multiple", false)),
                Map.entry("draftRevision", "7")
        )), Map.of("Idempotency-Key", key()));
        assertOk(relation);
        var relationId = text(relation.body(), "/data/id");
        var subtable = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields", json(Map.ofEntries(
                Map.entry("dictionaryId", ""), Map.entry("targetModuleId", lineModuleId),
                Map.entry("code", "lines"), Map.entry("name", "Lines"), Map.entry("type", "SUBTABLE"),
                Map.entry("sortOrder", 2), Map.entry("required", false), Map.entry("hidden", false),
                Map.entry("readonly", false), Map.entry("searchable", false), Map.entry("filterable", false),
                Map.entry("showInList", false), Map.entry("showInDetail", true), Map.entry("indexMode", "NONE"),
                Map.entry("status", "ENABLED"), Map.entry("properties", Map.ofEntries(
                        Map.entry("columnFieldIds", java.util.List.of(lineAmountId)),
                        Map.entry("allowRowCreate", true), Map.entry("allowRowUpdate", true),
                        Map.entry("allowRowDelete", true), Map.entry("allowRowReorder", true),
                        Map.entry("minRows", 0), Map.entry("maxRows", 100),
                        Map.entry("aggregates", java.util.List.of(Map.of(
                                "id", "lineSum", "function", "SUM", "columnFieldId", lineAmountId))))),
                Map.entry("draftRevision", "8")
        )), Map.of("Idempotency-Key", key()));
        assertOk(subtable);
        var subtableId = text(subtable.body(), "/data/id");
        var taxed = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                derivedContractField("taxed", "Taxed", "FORMULA", true, Map.of(
                        "resultSchema", "DECIMAL", "astVersion", 1,
                        "expressionAst", Map.of("fieldId", amountId)), "9"),
                Map.of("Idempotency-Key", key()));
        assertOk(taxed);
        var taxedId = text(taxed.body(), "/data/id");
        var total = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                derivedContractField("total", "Total", "CALCULATED", true, Map.of(
                        "resultSchema", "DECIMAL", "astVersion", 1,
                        "expressionAst", Map.of("op", "ADD", "args", java.util.List.of(
                                Map.of("fieldId", taxedId), Map.of("literalType", "INTEGER", "value", 1)))), "10"),
                Map.of("Idempotency-Key", key()));
        assertOk(total);
        var totalId = text(total.body(), "/data/id");
        var summary = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                derivedContractField("customer_count", "Customer count", "SUMMARY", true, Map.of(
                        "resultSchema", "INTEGER", "relationFieldId", relationId, "reduction", "COUNT"), "11"),
                Map.of("Idempotency-Key", key()));
        assertOk(summary);
        var lookup = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                derivedContractField("customer_name", "Customer name", "LOOKUP", true, Map.of(
                        "resultSchema", "STRING", "relationFieldId", relationId,
                        "targetFieldId", customerNameId, "distinct", true), "12"),
                Map.of("Idempotency-Key", key()));
        assertOk(lookup);
        var aggregate = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                derivedContractField("line_total", "Line total", "AGGREGATE", true, Map.of(
                        "resultSchema", "DECIMAL", "subtableFieldId", subtableId,
                        "aggregateId", "lineSum"), "13"),
                Map.of("Idempotency-Key", key()));
        assertOk(aggregate);
        var aggregateId = text(aggregate.body(), "/data/id");

        var invalidEditable = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                derivedContractField("editable_formula", "Editable formula", "FORMULA", false, Map.of(
                        "resultSchema", "DECIMAL", "astVersion", 1,
                        "expressionAst", Map.of("fieldId", amountId)), "14"));
        assertError(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                objectMapper.writeValueAsString(invalidEditable), Map.of("Idempotency-Key", key())),
                400, "VALIDATION_ERROR");

        var checked = client.postWithCsrf(configRoot + "/checks", json(Map.of("draftRevision", "14")), Map.of());
        assertOk(checked);
        assertThat(text(checked.body(), "/data/status")).isEqualTo("PASSED");
        var checkedRoot = client.get(configRoot);
        assertOk(checkedRoot);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"), "draftRevision", "14",
                "configRootVersion", text(checkedRoot.body(), "/data/version"),
                "reason", "P4-C4 typed dependency publication"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        var versionId = Long.parseLong(text(published.body(), "/data/version/id"));
        // Publishing changes the system authorization/configuration epoch. Re-enter the
        // authenticated context before exercising the separately protected print APIs.
        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var printTemplateRoot = "/api/v1/systems/" + systemId
                + "/admin/config/modules/" + moduleId + "/print-templates";
        var compositionTemplate = client.postWithCsrf(printTemplateRoot, json(Map.of(
                "code", "order_composition", "name", "Order composition", "status", "ENABLED",
                "paperSize", "A4", "orientation", "PORTRAIT", "header", "Order composition",
                "title", "{title}", "fieldCodes", java.util.List.of("amount", "customer", "lines"),
                "footer", "Composition snapshot")), Map.of("Idempotency-Key", key()));
        assertOk(compositionTemplate);
        assertOk(client.postWithCsrf(printTemplateRoot + "/"
                        + text(compositionTemplate.body(), "/data/templateId") + ":publish",
                json(Map.of("expectedVersion", compositionTemplate.body().at("/data/version").asLong())),
                Map.of("Idempotency-Key", key())));
        var numericSystemId = Long.parseLong(systemId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_runtime_schema_field WHERE system_id=? AND schema_version_id=? "
                        + "AND field_type IN ('FORMULA','SUMMARY','CALCULATED','LOOKUP','AGGREGATE') "
                        + "AND evaluator_version=1 AND expression_checksum REGEXP '^[a-f0-9]{64}$'",
                Long.class, numericSystemId, versionId)).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT result_schema) FROM un_module_runtime_schema_field "
                        + "WHERE system_id=? AND schema_version_id=? "
                        + "AND field_type IN ('FORMULA','SUMMARY','CALCULATED','LOOKUP','AGGREGATE')",
                Long.class, numericSystemId, versionId)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_runtime_schema_field WHERE system_id=? AND schema_version_id=? "
                        + "AND field_snapshot_id=? AND topological_rank=1 "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(dependency_json,'$[0].fieldId'))=?",
                Long.class, numericSystemId, versionId, Long.parseLong(totalId), taxedId)).isEqualTo(1L);

        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var customerRuntimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/customer";
        var customerCreated = client.postWithCsrf(customerRuntimeRoot + "/records", json(Map.of(
                "schemaVersionId", Long.toString(versionId), "title", "Acme",
                "values", Map.of("name", "Acme")
        )), Map.of("Idempotency-Key", key()));
        assertCreated(customerCreated);
        var customerRecordId = text(customerCreated.body(), "/data/recordId");
        var customerActivated = client.postWithCsrf(customerRuntimeRoot + "/records/" + customerRecordId
                        + ":activate", json(Map.of("expectedVersion", 0)), Map.of("Idempotency-Key", key()));
        assertOk(customerActivated);

        var derivedRuntimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/derived_order";
        var orderCreated = client.postWithCsrf(derivedRuntimeRoot + "/records", json(Map.ofEntries(
                Map.entry("schemaVersionId", Long.toString(versionId)), Map.entry("title", "Derived order"),
                Map.entry("values", Map.of("amount", 41.5)),
                Map.entry("relations", java.util.List.of(Map.of(
                        "fieldCode", "customer", "targets", java.util.List.of(Map.of(
                                "targetRecordId", customerRecordId, "targetExpectedVersion", 1,
                                "ordinal", 0))))),
                Map.entry("subtables", java.util.List.of(Map.of(
                        "fieldCode", "lines", "rows", java.util.List.of(Map.of(
                                "clientRowKey", "line-1", "ordinal", 0,
                                "values", Map.of("line_amount", 8.25))))))
        )), Map.of("Idempotency-Key", key()));
        assertCreated(orderCreated);
        var orderRecordId = text(orderCreated.body(), "/data/recordId");
        assertThat(item(orderCreated.body().at("/data/values"), "fieldCode", "taxed")
                .at("/value/result").decimalValue()).isEqualByComparingTo("41.5");
        assertThat(text(item(orderCreated.body().at("/data/values"), "fieldCode", "taxed"),
                "/value/recalculationState")).isEqualTo("READY");
        assertThat(text(item(orderCreated.body().at("/data/values"), "fieldCode", "customer_name"),
                "/value/recalculationState")).isEqualTo("PENDING");

        assertThat(referenceRecalculationWorker.process()).isGreaterThanOrEqualTo(1);
        var readyOrder = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(readyOrder);
        assertThat(item(readyOrder.body().at("/data/values"), "fieldCode", "total")
                .at("/value/result").decimalValue()).isEqualByComparingTo("42.5");
        assertThat(item(readyOrder.body().at("/data/values"), "fieldCode", "customer_count")
                .at("/value/result").decimalValue()).isEqualByComparingTo("1");
        assertThat(item(readyOrder.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo("Acme");
        assertThat(item(readyOrder.body().at("/data/values"), "fieldCode", "line_total")
                .at("/value/result").decimalValue()).isEqualByComparingTo("8.25");
        assertThat(text(item(readyOrder.body().at("/data/values"), "fieldCode", "line_total"),
                "/value/recalculationState")).isEqualTo("READY");

        var readyVersion = readyOrder.body().at("/data/version").asLong();
        var orderActivated = client.postWithCsrf(derivedRuntimeRoot + "/records/" + orderRecordId
                        + ":activate", json(Map.of("expectedVersion", readyVersion)),
                Map.of("Idempotency-Key", key()));
        assertOk(orderActivated);
        assertThat(referenceRecalculationWorker.process()).isGreaterThanOrEqualTo(1);
        var activeReadyOrder = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(activeReadyOrder);
        var activeReadyVersion = activeReadyOrder.body().at("/data/version").asLong();

        var compositionPreview = client.postWithCsrf(derivedRuntimeRoot + "/records/" + orderRecordId
                        + "/print-preview", json(Map.of("templateCode", "order_composition")), Map.of());
        assertOk(compositionPreview);
        assertThat(text(compositionPreview.body(), "/data/html"))
                .contains("Order composition", "print-table", "Customer", "Acme", "Lines", "8.25");
        var compositionPrint = client.postWithCsrf(derivedRuntimeRoot + "/records/" + orderRecordId + "/prints",
                json(Map.of("templateCode", "order_composition", "expectedRecordVersion", activeReadyVersion)),
                Map.of("Idempotency-Key", key()));
        assertThat(compositionPrint.status()).isEqualTo(202);
        var compositionPrintId = text(compositionPrint.body(), "/data/printId");
        TestResponse compositionTask = compositionPrint;
        for (var attempt = 0; attempt < 200; attempt++) {
            compositionTask = client.get(derivedRuntimeRoot + "/records/" + orderRecordId
                    + "/prints/" + compositionPrintId);
            if ("SUCCEEDED".equals(text(compositionTask.body(), "/data/status"))) break;
            Thread.sleep(25);
        }
        assertThat(text(compositionTask.body(), "/data/status")).isEqualTo("SUCCEEDED");
        var compositionPdf = client.download(derivedRuntimeRoot + "/records/" + orderRecordId
                + "/prints/" + compositionPrintId + "/result.pdf");
        assertThat(compositionPdf.status()).isEqualTo(200);
        assertThat(new String(compositionPdf.content(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(jdbcTemplate.queryForObject("SELECT snapshot_json FROM un_module_print_task WHERE id=?",
                String.class, compositionPrintId)).contains("Acme", "8.25", "tables");

        var customerUpdated = client.putWithCsrf(customerRuntimeRoot + "/records/" + customerRecordId,
                json(Map.of("schemaVersionId", Long.toString(versionId), "title", "Acme Prime",
                        "expectedVersion", 1, "values", Map.of("name", "Acme Prime"))),
                Map.of("Idempotency-Key", key()));
        assertOk(customerUpdated);
        var pendingOrder = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(pendingOrder);
        assertThat(pendingOrder.body().at("/data/version").asLong()).isEqualTo(activeReadyVersion);
        assertThat(item(pendingOrder.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo("Acme");
        assertThat(text(item(pendingOrder.body().at("/data/values"), "fieldCode", "customer_name"),
                "/value/recalculationState")).isEqualTo("PENDING");
        assertThat(referenceRecalculationWorker.process()).isGreaterThanOrEqualTo(1);
        var updatedOrder = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(updatedOrder);
        assertThat(updatedOrder.body().at("/data/version").asLong()).isEqualTo(activeReadyVersion + 1);
        assertThat(item(updatedOrder.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo("Acme Prime");
        assertThat(referenceRecalculationWorker.process()).isZero();
        assertThat(client.get(derivedRuntimeRoot + "/records/" + orderRecordId).body()
                .at("/data/version").asLong()).isEqualTo(activeReadyVersion + 1);

        var failedCorrelation = "forced-derived-failure-" + UUID.randomUUID();
        var failedVersion = updatedOrder.body().at("/data/version").asLong();
        var forcedTaskId = 8_100_000_000_000_000_000L + Math.floorMod(System.nanoTime(), 100_000_000L);
        jdbcTemplate.update("UPDATE un_module_record_value SET recalculation_state='FAILED',"
                        + "failure_correlation_id=? WHERE record_id=? AND field_snapshot_id=?",
                failedCorrelation, Long.parseLong(orderRecordId), Long.parseLong(aggregateId));
        jdbcTemplate.update("INSERT INTO un_module_reference_recalc_task "
                        + "(id,system_id,tenant_id,source_record_id,source_record_version,status,attempt_count,"
                        + "available_at,correlation_id,created_at,updated_at,version) "
                        + "SELECT ?,system_id,tenant_id,record_id,version,'FAILED',3,NOW(3),?,NOW(3),NOW(3),0 "
                        + "FROM un_module_record WHERE record_id=?",
                forcedTaskId, failedCorrelation, Long.parseLong(orderRecordId));
        var retried = client.postWithCsrf(derivedRuntimeRoot + "/records/" + orderRecordId
                        + "/references/line_total:retry", json(Map.of("expectedVersion", failedVersion)),
                Map.of("Idempotency-Key", key()));
        assertOk(retried);
        assertThat(text(retried.body(), "/data/recalculationState")).isEqualTo("PENDING");
        var retryPending = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(retryPending);
        assertThat(item(retryPending.body().at("/data/values"), "fieldCode", "line_total")
                .at("/value/result").decimalValue()).isEqualByComparingTo("8.25");
        assertThat(referenceRecalculationWorker.process()).isGreaterThanOrEqualTo(1);
        var retryReady = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(retryReady);
        assertThat(text(item(retryReady.body().at("/data/values"), "fieldCode", "line_total"),
                "/value/recalculationState")).isEqualTo("READY");

        var derivedQuery = new LinkedHashMap<String, Object>();
        derivedQuery.put("schemaVersionId", Long.toString(versionId));
        derivedQuery.put("page", 1);
        derivedQuery.put("size", 10);
        derivedQuery.put("recordScope", "active");
        derivedQuery.put("q", objectMapper.nullNode());
        derivedQuery.put("filter", Map.of("kind", "AND", "children", java.util.List.of(
                Map.of("kind", "PREDICATE", "fieldCode", "taxed", "operator", "EQ", "value", 41.5),
                Map.of("kind", "PREDICATE", "fieldCode", "customer_name", "operator", "EQ",
                        "value", "Acme Prime"))));
        derivedQuery.put("sort", java.util.List.of(
                Map.of("fieldCode", "total", "direction", "DESC", "nulls", "LAST")));
        derivedQuery.put("columns", java.util.List.of(
                "taxed", "total", "customer_count", "customer_name", "line_total"));
        derivedQuery.put("viewId", null);
        var queried = client.post(derivedRuntimeRoot + "/records:query", json(derivedQuery), Map.of());
        assertOk(queried);
        assertThat(queried.body().at("/data/total").asLong()).isEqualTo(1L);
        assertThat(text(queried.body(), "/data/rows/0/recordId")).isEqualTo(orderRecordId);

        var customerTaskCountBeforeFailure = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_reference_recalc_task WHERE source_record_id=?",
                Long.class, Long.parseLong(customerRecordId));
        var orderBeforeFailure = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(orderBeforeFailure);
        var orderVersionBeforeFailure = orderBeforeFailure.body().at("/data/version").asLong();
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS p4_c4_fail_runtime_outbox");
        jdbcTemplate.execute("CREATE TRIGGER p4_c4_fail_runtime_outbox BEFORE INSERT ON un_sys_outbox_event "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P4-C4 forced outbox failure'");
        TestResponse outboxFailure;
        try {
            outboxFailure = client.putWithCsrf(customerRuntimeRoot + "/records/" + customerRecordId,
                    json(Map.of("schemaVersionId", Long.toString(versionId), "title", "Must rollback",
                            "expectedVersion", 2, "values", Map.of("name", "Must rollback"))),
                    Map.of("Idempotency-Key", key()));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS p4_c4_fail_runtime_outbox");
        }
        assertError(outboxFailure, 500, "INTERNAL_ERROR");
        var customerAfterFailure = client.get(customerRuntimeRoot + "/records/" + customerRecordId);
        assertOk(customerAfterFailure);
        assertThat(customerAfterFailure.body().at("/data/version").asLong()).isEqualTo(2L);
        assertThat(text(customerAfterFailure.body(), "/data/title")).isEqualTo("Acme Prime");
        assertThat(text(item(customerAfterFailure.body().at("/data/values"), "fieldCode", "name"), "/value"))
                .isEqualTo("Acme Prime");
        var orderAfterFailure = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(orderAfterFailure);
        assertThat(orderAfterFailure.body().at("/data/version").asLong()).isEqualTo(orderVersionBeforeFailure);
        assertThat(text(item(orderAfterFailure.body().at("/data/values"), "fieldCode", "customer_name"),
                "/value/recalculationState")).isEqualTo("READY");
        assertThat(item(orderAfterFailure.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo("Acme Prime");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_reference_recalc_task WHERE source_record_id=?",
                Long.class, Long.parseLong(customerRecordId))).isEqualTo(customerTaskCountBeforeFailure);

        var concurrentGate = new CountDownLatch(1);
        var alphaBody = json(Map.of("schemaVersionId", Long.toString(versionId), "title", "Concurrent Alpha",
                "expectedVersion", 2, "values", Map.of("name", "Concurrent Alpha")));
        var betaBody = json(Map.of("schemaVersionId", Long.toString(versionId), "title", "Concurrent Beta",
                "expectedVersion", 2, "values", Map.of("name", "Concurrent Beta")));
        var alpha = CompletableFuture.supplyAsync(() -> concurrentPut(client, concurrentGate,
                customerRuntimeRoot + "/records/" + customerRecordId, alphaBody));
        var beta = CompletableFuture.supplyAsync(() -> concurrentPut(client, concurrentGate,
                customerRuntimeRoot + "/records/" + customerRecordId, betaBody));
        concurrentGate.countDown();
        var alphaResult = alpha.join();
        var betaResult = beta.join();
        assertThat(java.util.List.of(alphaResult.status(), betaResult.status()))
                .containsExactlyInAnyOrder(200, 409);
        var winner = alphaResult.status() == 200 ? alphaResult : betaResult;
        var loser = alphaResult.status() == 409 ? alphaResult : betaResult;
        assertThat(text(loser.body(), "/code")).isEqualTo("RECORD_VERSION_CONFLICT");
        assertThat(winner.body().at("/data/version").asLong()).isEqualTo(3L);
        var winnerName = text(winner.body(), "/data/title");
        var customerAfterConcurrentUpdate = client.get(customerRuntimeRoot + "/records/" + customerRecordId);
        assertOk(customerAfterConcurrentUpdate);
        assertThat(customerAfterConcurrentUpdate.body().at("/data/version").asLong()).isEqualTo(3L);
        assertThat(text(customerAfterConcurrentUpdate.body(), "/data/title")).isEqualTo(winnerName);

        var concurrentPendingOrder = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(concurrentPendingOrder);
        assertThat(concurrentPendingOrder.body().at("/data/version").asLong())
                .isEqualTo(orderVersionBeforeFailure);
        assertThat(text(item(concurrentPendingOrder.body().at("/data/values"), "fieldCode", "customer_name"),
                "/value/recalculationState")).isEqualTo("PENDING");
        assertThat(item(concurrentPendingOrder.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo("Acme Prime");
        assertThat(referenceRecalculationWorker.process()).isGreaterThanOrEqualTo(1);
        var concurrentReadyOrder = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(concurrentReadyOrder);
        assertThat(concurrentReadyOrder.body().at("/data/version").asLong())
                .isEqualTo(orderVersionBeforeFailure + 1);
        assertThat(item(concurrentReadyOrder.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo(winnerName);
        var currentPermissionEpoch = jdbcTemplate.queryForObject(
                "SELECT epoch FROM un_plat_authz_epoch WHERE scope_type='SYSTEM' AND scope_key=?",
                Long.class, numericSystemId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(after_json,'$.permissionEpoch')) "
                        + "FROM un_audit_operation WHERE aggregate_type='RUNTIME_RECORD' AND aggregate_id=? "
                        + "AND operation_type='REFERENCE_RECALCULATED' ORDER BY created_at DESC,id DESC LIMIT 1",
                String.class, orderRecordId)).isEqualTo(Long.toString(currentPermissionEpoch));

        var staleTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_module_reference_recalc_task WHERE source_record_id=? "
                        + "AND source_record_version=2",
                Long.class, Long.parseLong(customerRecordId));
        jdbcTemplate.update("UPDATE un_module_reference_recalc_task SET status='PENDING',attempt_count=0,"
                        + "available_at=NOW(3),updated_at=NOW(3),version=version+1 WHERE id=?",
                staleTaskId);
        assertThat(referenceRecalculationWorker.process()).isGreaterThanOrEqualTo(1);
        var orderAfterStaleTask = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(orderAfterStaleTask);
        assertThat(orderAfterStaleTask.body().at("/data/version").asLong())
                .isEqualTo(orderVersionBeforeFailure + 1);
        assertThat(item(orderAfterStaleTask.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo(winnerName);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM un_module_reference_recalc_task WHERE id=?", String.class, staleTaskId))
                .isEqualTo("PASSED");

        var currentTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_module_reference_recalc_task WHERE source_record_id=? "
                        + "AND source_record_version=3",
                Long.class, Long.parseLong(customerRecordId));
        jdbcTemplate.update("UPDATE un_module_config_root SET active_version_id=NULL,base_version_id=NULL "
                        + "WHERE system_id=?",
                numericSystemId);
        try {
            jdbcTemplate.update("UPDATE un_module_reference_recalc_task SET status='PENDING',attempt_count=0,"
                            + "available_at=NOW(3),updated_at=NOW(3),version=version+1 WHERE id=?",
                    currentTaskId);
            assertThat(referenceRecalculationWorker.process()).isGreaterThanOrEqualTo(1);
        } finally {
            jdbcTemplate.update("UPDATE un_module_config_root SET active_version_id=?,base_version_id=? "
                            + "WHERE system_id=?",
                    versionId, versionId, numericSystemId);
        }
        var orderAfterInactiveSnapshotTask = client.get(derivedRuntimeRoot + "/records/" + orderRecordId);
        assertOk(orderAfterInactiveSnapshotTask);
        assertThat(orderAfterInactiveSnapshotTask.body().at("/data/version").asLong())
                .isEqualTo(orderVersionBeforeFailure + 1);
        assertThat(item(orderAfterInactiveSnapshotTask.body().at("/data/values"), "fieldCode", "customer_name")
                .at("/value/result/0").asText()).isEqualTo(winnerName);

        var rootAfterPublish = client.get(configRoot);
        assertOk(rootAfterPublish);
        var cycleBody = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                derivedContractField("taxed", "Taxed", "FORMULA", true, Map.of(
                        "resultSchema", "DECIMAL", "astVersion", 1,
                        "expressionAst", Map.of("fieldId", totalId)),
                        text(rootAfterPublish.body(), "/data/draftRevision")));
        cycleBody.put("version", text(taxed.body(), "/data/version"));
        var cycleUpdate = client.putWithCsrf(configRoot + "/modules/" + moduleId + "/fields/" + taxedId,
                objectMapper.writeValueAsString(cycleBody), Map.of());
        assertOk(cycleUpdate);
        var rootAfterCycle = client.get(configRoot);
        assertOk(rootAfterCycle);
        var failedCheck = client.postWithCsrf(configRoot + "/checks", json(Map.of(
                "draftRevision", text(rootAfterCycle.body(), "/data/draftRevision"))), Map.of());
        assertOk(failedCheck);
        assertThat(text(failedCheck.body(), "/data/status")).isEqualTo("FAILED");
        assertThat(failedCheck.body().at("/data/issues").toString()).contains("SCHEMA_CYCLE");
    }

    private TestResponse concurrentPut(TestClient client, CountDownLatch gate, String path, String body) {
        try {
            gate.await();
            return client.putWithCsrf(path, body, Map.of("Idempotency-Key", key()));
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private String derivedContractField(String code, String name, String type, boolean readonly,
                                        Map<String, Object> properties, String draftRevision) throws Exception {
        var derived = Set.of("FORMULA", "SUMMARY", "CALCULATED", "LOOKUP", "AGGREGATE").contains(type);
        return json(Map.ofEntries(
                Map.entry("dictionaryId", ""), Map.entry("targetModuleId", ""), Map.entry("code", code),
                Map.entry("name", name), Map.entry("type", type), Map.entry("sortOrder", 0),
                Map.entry("required", false), Map.entry("hidden", false), Map.entry("readonly", readonly),
                Map.entry("searchable", false), Map.entry("filterable", derived), Map.entry("showInList", true),
                Map.entry("showInDetail", true), Map.entry("indexMode",
                        derived ? ("LOOKUP".equals(type) ? "FILTER" : "SORT") : "NONE"),
                Map.entry("status", "ENABLED"),
                Map.entry("properties", properties), Map.entry("draftRevision", draftRevision)
        ));
    }

    @Test
    void completesDraftPageComponentJourneyWithReferenceAndRevisionGuards() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));

        var system = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "vs3_target_" + System.nanoTime(),
                "name", "VS3 Target System",
                "description", "VS3 draft integration target",
                "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        var foreignSystem = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "vs3_foreign_" + System.nanoTime(),
                "name", "VS3 Foreign System",
                "description", "cross-system reference guard",
                "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(foreignSystem);
        var foreignSystemId = Long.parseLong(text(foreignSystem.body(), "/data/id"));
        var foreignPermissionId = 8_000_000_000_000_000_004L;
        jdbcTemplate.update("INSERT INTO un_plat_permission (id,scope_type,scope_key,system_id,permission_code,name,"
                        + "resource_type,status,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,'SYSTEM',?,?,'vs3.foreign.read','Foreign read','MODULE','ACTIVE',NOW(3),1,NOW(3),1,0)",
                foreignPermissionId, foreignSystemId, foreignSystemId);
        assertOk(client.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";

        var groupBody = json(Map.of(
                "code", "operations", "name", "Operations", "description", "", "iconKey", "folder",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", "0"
        ));
        var groupKey = key();
        var group = client.postWithCsrf(configRoot + "/module-groups", groupBody,
                Map.of("Idempotency-Key", groupKey));
        assertOk(group);
        var groupId = text(group.body(), "/data/id");
        var groupReplay = client.postWithCsrf(configRoot + "/module-groups", groupBody,
                Map.of("Idempotency-Key", groupKey));
        assertOk(groupReplay);
        assertThat(text(groupReplay.body(), "/data/id")).isEqualTo(groupId);
        var conflictingGroupBody = json(Map.of(
                "code", "operations", "name", "Different request", "description", "", "iconKey", "folder",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", "0"
        ));
        assertError(client.postWithCsrf(configRoot + "/module-groups", conflictingGroupBody,
                Map.of("Idempotency-Key", groupKey)), 409, "IDEMPOTENCY_CONFLICT");

        var module = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", groupId, "code", "work_order", "name", "Work Order", "description", "",
                "iconKey", "clipboard", "sortOrder", 0, "status", "ENABLED",
                "allowComments", true, "allowTeam", true, "draftRevision", "1"
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");

        var pages = client.get(configRoot + "/modules/" + moduleId + "/pages");
        assertOk(pages);
        assertThat(pages.body().at("/data")).hasSize(3);
        var formPageId = itemId(pages.body().at("/data"), "type", "FORM");
        var listPageId = itemId(pages.body().at("/data"), "type", "LIST");

        var field = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields", fieldBody(),
                Map.of("Idempotency-Key", key()));
        assertOk(field);
        var fieldId = text(field.body(), "/data/id");
        var invalidFixedDefault = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(fieldBody());
        invalidFixedDefault.put("code", "invalid_number_default");
        invalidFixedDefault.put("name", "Invalid number default");
        invalidFixedDefault.put("type", "NUMBER");
        invalidFixedDefault.set("properties", objectMapper.valueToTree(Map.of(
                "defaultMode", "FIXED", "defaultValue", "not-a-number")));
        assertError(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                objectMapper.writeValueAsString(invalidFixedDefault), Map.of("Idempotency-Key", key())),
                400, "VALIDATION_ERROR");

        var componentRoot = configRoot + "/modules/" + moduleId + "/pages/" + formPageId + "/components";
        var section = client.postWithCsrf(componentRoot, componentBody(null, null, "main_section", "SECTION", "3"),
                Map.of("Idempotency-Key", key()));
        assertOk(section);
        var sectionId = text(section.body(), "/data/id");

        var fieldComponentBody = componentBody(sectionId, fieldId, "title_field", "FIELD", "4");
        var componentKey = key();
        var fieldComponent = client.postWithCsrf(componentRoot, fieldComponentBody,
                Map.of("Idempotency-Key", componentKey));
        assertOk(fieldComponent);
        var fieldComponentId = text(fieldComponent.body(), "/data/id");
        var componentReplay = client.postWithCsrf(componentRoot, fieldComponentBody,
                Map.of("Idempotency-Key", componentKey));
        assertOk(componentReplay);
        assertThat(text(componentReplay.body(), "/data/id")).isEqualTo(fieldComponentId);

        var components = client.get(componentRoot);
        assertOk(components);
        assertThat(components.body().at("/data")).hasSize(2);

        var cycle = client.putWithCsrf(componentRoot + "/" + sectionId, updateComponentBody(
                fieldComponentId, null, "main_section", "SECTION", "0", "5"), Map.of());
        assertError(cycle, 422, "CONFIG_REFERENCE_INVALID");

        var crossPage = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/pages/" + listPageId + "/components",
                componentBody(sectionId, null, "cross_page", "SECTION", "5"),
                Map.of("Idempotency-Key", key()));
        assertError(crossPage, 404, "RESOURCE_NOT_FOUND");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO un_module_page_component (id,system_id,page_id,parent_component_id,field_id,component_key,component_type,sort_order,grid_row,grid_column,grid_span,property_json,created_revision,updated_revision,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,NULL,'db_cross_page','SECTION',0,0,0,12,JSON_OBJECT(),5,5,NOW(3),1,NOW(3),1,0)",
                8_000_000_000_000_000_001L, Long.parseLong(systemId), Long.parseLong(listPageId),
                Long.parseLong(sectionId)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO un_module_config_reference (id,system_id,source_type,source_id,target_type,target_id,relation_type,property_path,created_revision,created_at,created_by) "
                        + "VALUES (?,?,'FIELD',?,'FIELD',?,'READS_FIELD',NULL,5,NOW(3),1)",
                8_000_000_000_000_000_002L, Long.parseLong(systemId),
                8_000_000_000_000_000_003L, Long.parseLong(fieldId)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO un_module_config_reference (id,system_id,source_type,source_id,target_type,target_id,relation_type,property_path,created_revision,created_at,created_by) "
                        + "VALUES (?,?,'FIELD',?,'PERMISSION',?,'USES_PERMISSION',NULL,5,NOW(3),1)",
                8_000_000_000_000_000_005L, Long.parseLong(systemId), Long.parseLong(fieldId),
                foreignPermissionId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertError(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields/" + fieldId + ":delete",
                json(Map.of("version", "0", "draftRevision", "5")), Map.of()),
                422, "CONFIG_REFERENCE_INVALID");
        assertError(client.postWithCsrf(componentRoot + "/" + sectionId + ":delete",
                json(Map.of("version", "0", "draftRevision", "5")), Map.of()),
                422, "CONFIG_REFERENCE_INVALID");

        var detached = client.putWithCsrf(componentRoot + "/" + fieldComponentId, updateComponentBody(
                null, fieldId, "title_field", "FIELD", "0", "5"), Map.of());
        assertOk(detached);
        assertThat(text(detached.body(), "/data/version")).isEqualTo("1");
        assertOk(client.postWithCsrf(componentRoot + "/" + sectionId + ":delete",
                json(Map.of("version", "0", "draftRevision", "6")), Map.of()));
        assertError(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields/" + fieldId + ":delete",
                json(Map.of("version", "0", "draftRevision", "7")), Map.of()),
                422, "CONFIG_REFERENCE_INVALID");
        assertOk(client.postWithCsrf(componentRoot + "/" + fieldComponentId + ":delete",
                json(Map.of("version", "1", "draftRevision", "7")), Map.of()));
        assertOk(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields/" + fieldId + ":delete",
                json(Map.of("version", "0", "draftRevision", "8")), Map.of()));

        var dictionary = client.postWithCsrf(configRoot + "/dictionaries", json(Map.of(
                "code", "priority", "name", "Priority", "type", "LIST", "category", "work",
                "description", "Rollback closure verification", "status", "ENABLED", "draftRevision", "9"
        )), Map.of("Idempotency-Key", key()));
        assertOk(dictionary);
        var dictionaryId = text(dictionary.body(), "/data/id");
        var dictionaryItem = client.postWithCsrf(configRoot + "/dictionaries/" + dictionaryId + "/items", json(Map.of(
                "parentId", "", "code", "normal", "label", "Normal", "semanticKey", "NORMAL",
                "color", "#2563a6", "iconKey", "", "sortOrder", 0, "isDefault", true,
                "status", "ENABLED", "draftRevision", "10"
        )), Map.of("Idempotency-Key", key()));
        assertOk(dictionaryItem);

        var finalRoot = client.get(configRoot);
        assertOk(finalRoot);
        assertThat(text(finalRoot.body(), "/data/draftRevision")).isEqualTo("11");
        var memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_plat_member WHERE system_id=? AND deleted_at IS NULL", Long.class,
                Long.parseLong(systemId));
        var tenantId = jdbcTemplate.queryForObject(
                "SELECT default_tenant_id FROM un_plat_member WHERE id=?", Long.class, memberId);
        var draftPreview = client.get(configRoot + "/preview?memberId=" + memberId + "&tenantId=" + tenantId);
        assertOk(draftPreview);
        assertThat(draftPreview.body().at("/data/root").asBoolean()).isTrue();
        assertThat(draftPreview.body().at("/data/active/groups")).isEmpty();
        assertThat(text(draftPreview.body(), "/data/draft/groups/0/modules/0/code"))
                .isEqualTo("work_order");
        var runtimeRoot = "/api/v1/systems/" + systemId + "/runtime";
        assertError(client.get(runtimeRoot + "/navigation"), 404, "MODULE_NOT_PUBLISHED");

        var checked = client.postWithCsrf(configRoot + "/checks",
                json(Map.of("draftRevision", "11")), Map.of());
        assertOk(checked);
        assertThat(text(checked.body(), "/data/status")).isEqualTo("PASSED");
        assertThat(checked.body().at("/data/blockerCount").asInt()).isZero();
        var checkId = text(checked.body(), "/data/id");
        var checkedRoot = client.get(configRoot);
        assertOk(checkedRoot);
        var rootVersion = text(checkedRoot.body(), "/data/version");

        var publishKey = key();
        var publishBody = json(Map.of(
                "checkId", checkId, "draftRevision", "11", "configRootVersion", rootVersion,
                "reason", "VS3 integration publication"
        ));
        var stalePublish = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", checkId, "draftRevision", "10", "configRootVersion", rootVersion,
                "reason", "VS3 stale publish must fail"
        )), Map.of("Idempotency-Key", key()));
        assertError(stalePublish, 409, "CONFIG_VERSION_CONFLICT");

        var published = client.postWithCsrf(configRoot + ":publish", publishBody,
                Map.of("Idempotency-Key", publishKey));
        assertOk(published);
        var versionId = text(published.body(), "/data/version/id");
        assertThat(text(published.body(), "/data/version/versionNo")).isEqualTo("1");
        assertThat(text(published.body(), "/data/root/status")).isEqualTo("CLEAN");
        assertThat(text(published.body(), "/data/root/activeVersionId")).isEqualTo(versionId);

        assertError(client.get(configRoot + "/versions"), 401, "AUTHZ_SNAPSHOT_STALE");
        var refreshedAfterPublish = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshedAfterPublish);
        assertThat(refreshedAfterPublish.body().at("/data/context/permissions").toString())
                .contains("module.work_order.view");
        var publishReplay = client.postWithCsrf(configRoot + ":publish", publishBody,
                Map.of("Idempotency-Key", publishKey));
        assertOk(publishReplay);
        assertThat(text(publishReplay.body(), "/data/version/id")).isEqualTo(versionId);
        assertError(client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", checkId, "draftRevision", "11", "configRootVersion", rootVersion,
                "reason", "Different publication request"
        )), Map.of("Idempotency-Key", publishKey)), 409, "IDEMPOTENCY_CONFLICT");
        var versions = client.get(configRoot + "/versions");
        assertOk(versions);
        assertThat(versions.body().at("/data")).hasSize(1);
        assertThat(versions.body().at("/data/0/active").asBoolean()).isTrue();
        var publishedPreview = client.get(configRoot + "/preview?memberId=" + memberId + "&tenantId=" + tenantId);
        assertOk(publishedPreview);
        assertThat(text(publishedPreview.body(), "/data/active/versionId")).isEqualTo(versionId);
        assertThat(text(publishedPreview.body(), "/data/active/groups/0/modules/0/code"))
                .isEqualTo("work_order");
        assertThat(text(publishedPreview.body(), "/data/draft/groups/0/modules/0/code"))
                .isEqualTo("work_order");
        var navigationV1 = client.get(runtimeRoot + "/navigation");
        assertOk(navigationV1);
        assertThat(text(navigationV1.body(), "/data/activeVersionId")).isEqualTo(versionId);
        assertThat(text(navigationV1.body(), "/data/groups/0/name")).isEqualTo("Operations");
        assertThat(text(navigationV1.body(), "/data/groups/0/modules/0/code")).isEqualTo("work_order");
        var definitionV1 = client.get(runtimeRoot + "/modules/work_order/definition");
        assertOk(definitionV1);
        assertThat(definitionV1.body().at("/data/recordsAvailable").asBoolean()).isTrue();
        assertThat(definitionV1.body().at("/data/pages")).hasSize(3);
        var cleanRoot = client.get(configRoot);
        assertOk(cleanRoot);
        assertError(client.putWithCsrf(configRoot + "/module-groups/" + groupId, json(Map.of(
                "code", "operations_renamed", "name", "Operations V2", "description", "",
                "iconKey", "folder", "sortOrder", 0, "status", "ENABLED", "version", "0",
                "draftRevision", "11"
        )), Map.of()), 400, "VALIDATION_ERROR");
        var groupV2 = client.putWithCsrf(configRoot + "/module-groups/" + groupId, json(Map.of(
                "code", "operations", "name", "Operations V2", "description", "",
                "iconKey", "folder", "sortOrder", 0, "status", "ENABLED", "version", "0",
                "draftRevision", "11"
        )), Map.of());
        assertOk(groupV2);
        var navigationDraftV2 = client.get(runtimeRoot + "/navigation");
        assertOk(navigationDraftV2);
        assertThat(text(navigationDraftV2.body(), "/data/activeVersionId")).isEqualTo(versionId);
        assertThat(text(navigationDraftV2.body(), "/data/groups/0/name")).isEqualTo("Operations");
        var checkV2 = client.postWithCsrf(configRoot + "/checks",
                json(Map.of("draftRevision", "12")), Map.of());
        assertOk(checkV2);
        var rootV2Ready = client.get(configRoot);
        assertOk(rootV2Ready);
        var publishV2 = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checkV2.body(), "/data/id"), "draftRevision", "12",
                "configRootVersion", text(rootV2Ready.body(), "/data/version"),
                "reason", "VS3 integration publication V2"
        )), Map.of("Idempotency-Key", key()));
        assertOk(publishV2);
        var version2Id = text(publishV2.body(), "/data/version/id");
        assertThat(text(publishV2.body(), "/data/version/versionNo")).isEqualTo("2");
        var navigationV2 = client.get(runtimeRoot + "/navigation");
        assertOk(navigationV2);
        assertThat(text(navigationV2.body(), "/data/activeVersionId")).isEqualTo(version2Id);
        assertThat(text(navigationV2.body(), "/data/groups/0/name")).isEqualTo("Operations V2");

        var diffV1V2 = client.get(configRoot + "/versions/" + versionId + ":diff/" + version2Id);
        assertOk(diffV1V2);
        assertThat(diffV1V2.body().at("/data/changes/groups/changed").toString()).contains(groupId);

        var rollback = client.postWithCsrf(configRoot + "/versions/" + versionId + ":rollback", json(Map.of(
                "configRootVersion", text(publishV2.body(), "/data/root/version"),
                "reason", "Restore the accepted V1 snapshot"
        )), Map.of("Idempotency-Key", key()));
        assertOk(rollback);
        var version3Id = text(rollback.body(), "/data/version/id");
        assertThat(text(rollback.body(), "/data/version/versionNo")).isEqualTo("3");
        assertThat(text(rollback.body(), "/data/version/sourceType")).isEqualTo("ROLLBACK");
        assertThat(text(rollback.body(), "/data/version/rollbackTargetVersionId")).isEqualTo(versionId);
        assertThat(text(rollback.body(), "/data/root/activeVersionId")).isEqualTo(version3Id);
        var navigationV3 = client.get(runtimeRoot + "/navigation");
        assertOk(navigationV3);
        assertThat(text(navigationV3.body(), "/data/activeVersionId")).isEqualTo(version3Id);
        assertThat(text(navigationV3.body(), "/data/groups/0/name")).isEqualTo("Operations");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_dictionary_item_closure WHERE system_id=? AND dictionary_id=?",
                Long.class, Long.parseLong(systemId), Long.parseLong(dictionaryId))).isEqualTo(1L);

        var diffV1V3 = client.get(configRoot + "/versions/" + versionId + ":diff/" + version3Id);
        assertOk(diffV1V3);
        assertThat(diffV1V3.body().at("/data/changes/groups/changed")).isEmpty();
        assertThat(diffV1V3.body().at("/data/changes/groups/added")).isEmpty();
        assertThat(diffV1V3.body().at("/data/changes/groups/removed")).isEmpty();
        var ordinaryUsername = "vs3_member_" + Long.toUnsignedString(System.nanoTime(), 36);
        var ordinaryPassword = "Vs3-Member-Test-Password-42!";
        var registration = new TestClient().post("/api/v1/auth/register", json(Map.of(
                "username", ordinaryUsername,
                "displayName", "VS3 Ordinary Member",
                "password", ordinaryPassword,
                "systemName", "VS3 Member Personal System",
                "systemCode", "vs3_member_home_" + Long.toUnsignedString(System.nanoTime(), 36)
        )), Map.of("Idempotency-Key", key()));
        assertOk(registration);

        var roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var dataScopes = roleAdmin.get("/api/v1/systems/" + systemId + "/admin/data-scopes?size=200");
        assertOk(dataScopes);
        var allDataScopeId = itemId(dataScopes.body().at("/data/items"), "kind", "ALL");
        var role = roleAdmin.postWithCsrf("/api/v1/systems/" + systemId + "/admin/roles", json(Map.of(
                "code", "vs3_runtime_member_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "VS3 Runtime Member",
                "description", "Runtime access with explicit dynamic module grants"
        )), Map.of("Idempotency-Key", key()));
        assertOk(role);
        var roleId = text(role.body(), "/data/id");
        var deniedDraft = roleAdmin.putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + roleId + "/draft",
                json(Map.of(
                        "name", "VS3 Runtime Member",
                        "description", "Runtime access without a module grant",
                        "permissionCodes", java.util.List.of("system.runtime.access", "system.workbench.view"),
                        "deniedPermissionCodes", java.util.List.of(),
                        "dataScopeId", allDataScopeId,
                        "version", text(role.body(), "/data/version")
                )), Map.of());
        assertOk(deniedDraft);
        var deniedChecked = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + roleId + "/draft:check",
                json(Map.of("version", text(deniedDraft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(deniedChecked);
        var deniedPublished = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + roleId + "/draft:publish",
                json(Map.of("version", text(deniedChecked.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(deniedPublished);

        var ordinary = login(ordinaryUsername, ordinaryPassword);
        var accessRequest = ordinary.postWithCsrf(
                "/api/v1/context/systems/" + systemId + "/access-requests",
                json(Map.of("targetTenantId", tenantId, "reason", "VS3 runtime authorization journey")),
                Map.of("Idempotency-Key", key()));
        assertOk(accessRequest);
        var accessRequestId = text(accessRequest.body(), "/data/id");
        var approver = login();
        assertOk(approver.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var approved = approver.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/access-requests/" + accessRequestId + ":approve",
                json(Map.of(
                        "reason", "Approve VS3 ordinary-member authorization journey",
                        "version", text(accessRequest.body(), "/data/version"),
                        "tenantIds", java.util.List.of(tenantId),
                        "roleIds", java.util.List.of(roleId)
                )), Map.of("Idempotency-Key", key()));
        assertOk(approved);
        var ordinarySwitch = ordinary.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(ordinarySwitch);
        var deniedNavigation = ordinary.get(runtimeRoot + "/navigation");
        assertOk(deniedNavigation);
        assertThat(deniedNavigation.body().at("/data/groups")).isEmpty();
        assertError(ordinary.get(runtimeRoot + "/modules/work_order/definition"), 403, "PERMISSION_DENIED");
        assertError(ordinary.get(runtimeRoot + "/modules/work_order/records"), 403, "PERMISSION_DENIED");

        roleAdmin = login();
        assertOk(roleAdmin.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var allowedDraft = roleAdmin.putWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + roleId + "/draft",
                json(Map.of(
                        "name", "VS3 Runtime Member",
                        "description", "Runtime access with work-order view",
                        "permissionCodes", java.util.List.of(
                                "system.runtime.access", "system.workbench.view", "module.work_order.view"),
                        "deniedPermissionCodes", java.util.List.of(),
                        "dataScopeId", allDataScopeId,
                        "version", text(deniedPublished.body(), "/data/version")
                )), Map.of());
        assertOk(allowedDraft);
        var allowedChecked = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + roleId + "/draft:check",
                json(Map.of("version", text(allowedDraft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(allowedChecked);
        var allowedPublished = roleAdmin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/roles/" + roleId + "/draft:publish",
                json(Map.of("version", text(allowedChecked.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(allowedPublished);
        assertError(ordinary.get(runtimeRoot + "/navigation"), 401, "AUTHZ_SNAPSHOT_STALE");
        var ordinaryRefreshed = ordinary.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(ordinaryRefreshed);
        assertThat(ordinaryRefreshed.body().at("/data/context/permissions").toString())
                .contains("module.work_order.view");
        var allowedNavigation = ordinary.get(runtimeRoot + "/navigation");
        assertOk(allowedNavigation);
        assertThat(text(allowedNavigation.body(), "/data/groups/0/modules/0/code")).isEqualTo("work_order");
        assertOk(ordinary.get(runtimeRoot + "/modules/work_order/definition"));

        var draftOwner = login();
        assertOk(draftOwner.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var permissionSchema = draftOwner.get(runtimeRoot + "/modules/work_order/record-schema");
        assertOk(permissionSchema);
        var permissionSchemaVersionId = text(permissionSchema.body(), "/data/schemaVersionId");
        var privateDraft = draftOwner.postWithCsrf(runtimeRoot + "/modules/work_order/records", json(Map.of(
                "schemaVersionId", permissionSchemaVersionId,
                "title", "P4-B1 permission-negative draft",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(privateDraft);
        var privateDraftId = text(privateDraft.body(), "/data/recordId");
        var deniedMutationBody = json(Map.of(
                "schemaVersionId", permissionSchemaVersionId,
                "title", "Denied local edit",
                "expectedVersion", 0,
                "values", Map.of()
        ));
        assertError(ordinary.get(runtimeRoot + "/modules/work_order/records/" + privateDraftId),
                404, "RECORD_NOT_FOUND");
        assertError(ordinary.putWithCsrf(runtimeRoot + "/modules/work_order/records/" + privateDraftId,
                        deniedMutationBody, Map.of("Idempotency-Key", key())),
                403, "PERMISSION_DENIED");
        assertError(ordinary.postWithCsrf(
                        runtimeRoot + "/modules/work_order/records/" + privateDraftId + ":autosave",
                        deniedMutationBody, Map.of("Idempotency-Key", key())),
                403, "PERMISSION_DENIED");


        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_module_config_reference where system_id=? and source_type='COMPONENT'",
                Integer.class, Long.parseLong(systemId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_audit_operation where system_id=? and operation_type like 'MODULE_COMPONENT_%'",
                Integer.class, Long.parseLong(systemId))).isGreaterThanOrEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_module_publish_record where system_id=? and result='SUCCEEDED'",
                Integer.class, Long.parseLong(systemId))).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_plat_permission where system_id=? and permission_code like 'module.%' and status='ACTIVE'",
                Integer.class, Long.parseLong(systemId))).isGreaterThanOrEqualTo(4);

        assertThat(jdbcTemplate.update(
                "UPDATE un_sys_idempotency SET expires_at=DATE_SUB(NOW(3), INTERVAL 1 SECOND) "
                        + "WHERE idempotency_key=?",
                groupKey)).isEqualTo(1);
        var ttlClient = login();
        assertOk(ttlClient.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var rootAfterExpiration = ttlClient.get(configRoot);
        assertOk(rootAfterExpiration);
        var reusedExpiredKey = ttlClient.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "operations_archive", "name", "Operations Archive", "description", "",
                "iconKey", "archive", "sortOrder", 1, "status", "ENABLED",
                "draftRevision", text(rootAfterExpiration.body(), "/data/draftRevision")
        )), Map.of("Idempotency-Key", groupKey));
        assertOk(reusedExpiredKey);
        assertThat(text(reusedExpiredKey.body(), "/data/id")).isNotEqualTo(groupId);
    }

    @Test
    void restoresModuleConfigAsDraftBeforeExplicitPublishAndRuntimeReadback()
            throws Exception {
        var client = login();
        assertOk(client.postWithCsrf(
                "/api/v1/context/platform:switch", "{}", Map.of()));
        var system = client.postWithCsrf("/api/v1/platform/admin/systems",
                json(Map.of(
                        "code", "config_restore_"
                                + Long.toUnsignedString(System.nanoTime(), 36),
                        "name", "Config Restore", "description",
                        "draft-only restore journey", "tenantMode", "SINGLE"
                )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        var numericSystemId = Long.parseLong(systemId);
        assertOk(client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch",
                "{}", Map.of()));
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";
        var recoveryRoot = "/api/v1/systems/" + systemId
                + "/admin/config-recovery";

        var group = client.postWithCsrf(configRoot + "/module-groups",
                json(Map.of(
                        "code", "restore", "name", "Restore",
                        "description", "", "iconKey", "history",
                        "sortOrder", 0, "status", "ENABLED",
                        "draftRevision", "0"
                )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var groupId = text(group.body(), "/data/id");
        var module = client.postWithCsrf(configRoot + "/modules",
                json(Map.ofEntries(
                        Map.entry("groupId", groupId),
                        Map.entry("code", "restore_runtime"),
                        Map.entry("name", "Runtime V1"),
                        Map.entry("description", ""),
                        Map.entry("iconKey", "database"),
                        Map.entry("sortOrder", 0),
                        Map.entry("status", "ENABLED"),
                        Map.entry("allowComments", false),
                        Map.entry("allowTeam", false),
                        Map.entry("draftRevision", "1")
                )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");
        var moduleVersion = text(module.body(), "/data/version");

        var checkV1 = client.postWithCsrf(configRoot + "/checks",
                json(Map.of("draftRevision", "2")), Map.of());
        assertOk(checkV1);
        var checkedRootV1 = client.get(configRoot);
        assertOk(checkedRootV1);
        assertOk(client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checkV1.body(), "/data/id"),
                "draftRevision", "2", "configRootVersion",
                text(checkedRootV1.body(), "/data/version"),
                "reason", "publish runtime v1"
        )), Map.of("Idempotency-Key", key())));
        // Publishing advances the system authorization epoch by contract; continue the
        // restore journey with a fresh authenticated snapshot instead of masking staleness.
        client = login();
        assertOk(client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));

        var changed = client.putWithCsrf(configRoot + "/modules/" + moduleId,
                json(Map.ofEntries(
                        Map.entry("groupId", groupId),
                        Map.entry("code", "restore_runtime"),
                        Map.entry("name", "Runtime V2"),
                        Map.entry("description", ""),
                        Map.entry("iconKey", "database"),
                        Map.entry("sortOrder", 0),
                        Map.entry("status", "ENABLED"),
                        Map.entry("allowComments", false),
                        Map.entry("allowTeam", false),
                        Map.entry("version", moduleVersion),
                        Map.entry("draftRevision", "2")
                )), Map.of());
        assertOk(changed);
        var checkV2 = client.postWithCsrf(configRoot + "/checks",
                json(Map.of("draftRevision", "3")), Map.of());
        assertOk(checkV2);
        var checkedRootV2 = client.get(configRoot);
        assertOk(checkedRootV2);
        assertOk(client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checkV2.body(), "/data/id"),
                "draftRevision", "3", "configRootVersion",
                text(checkedRootV2.body(), "/data/version"),
                "reason", "publish runtime v2"
        )), Map.of("Idempotency-Key", key())));
        client = login();
        assertOk(client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));

        var versions = client.get(configRoot + "/versions");
        assertOk(versions);
        var v2Id = text(versions.body(), "/data/0/id");
        var v1Id = text(versions.body(), "/data/1/id");
        assertThat(text(versions.body(), "/data/1/versionNo")).isEqualTo("1");
        var cleanRoot = client.get(configRoot);
        assertOk(cleanRoot);
        assertThat(text(cleanRoot.body(), "/data/activeVersionId"))
                .isEqualTo(v2Id);

        var restored = client.postWithCsrf(
                recoveryRoot + "/module-config/versions/" + v1Id
                        + "/1:restore",
                json(Map.of(
                        "expectedVersion",
                        text(cleanRoot.body(), "/data/version"),
                        "reason", "restore runtime v1 for validation"
                )), Map.of("Idempotency-Key", key()));
        assertOk(restored);
        assertThat(text(restored.body(), "/data/state"))
                .isEqualTo("DRAFT_RESTORED");
        assertThat(text(restored.body(), "/data/activeVersionReference"))
                .isEqualTo(v2Id);

        var draftRoot = client.get(configRoot);
        assertOk(draftRoot);
        assertThat(text(draftRoot.body(), "/data/status")).isEqualTo("DIRTY");
        assertThat(text(draftRoot.body(), "/data/activeVersionId"))
                .isEqualTo(v2Id);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_version WHERE system_id=?",
                Integer.class, numericSystemId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT module_name FROM un_module_runtime_schema_module "
                        + "WHERE system_id=? AND schema_version_id=?",
                String.class, numericSystemId, Long.parseLong(v2Id)))
                .isEqualTo("Runtime V2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=? "
                        + "AND operation_type="
                        + "'MODULE_CONFIG_VERSION_RESTORED_TO_DRAFT'",
                Integer.class, numericSystemId)).isEqualTo(1);

        var restoredRevision = text(draftRoot.body(), "/data/draftRevision");
        var restoredCheck = client.postWithCsrf(configRoot + "/checks",
                json(Map.of("draftRevision", restoredRevision)), Map.of());
        assertOk(restoredCheck);
        assertThat(text(restoredCheck.body(), "/data/status"))
                .isEqualTo("PASSED");
        var readyRoot = client.get(configRoot);
        assertOk(readyRoot);
        var republished = client.postWithCsrf(configRoot + ":publish",
                json(Map.of(
                        "checkId", text(restoredCheck.body(), "/data/id"),
                        "draftRevision", restoredRevision,
                        "configRootVersion",
                        text(readyRoot.body(), "/data/version"),
                        "reason", "publish validated restored draft"
                )), Map.of("Idempotency-Key", key()));
        assertOk(republished);
        var v3Id = text(republished.body(), "/data/version/id");
        assertThat(text(republished.body(), "/data/version/sourceType"))
                .isEqualTo("PUBLISH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT module_name FROM un_module_runtime_schema_module "
                        + "WHERE system_id=? AND schema_version_id=?",
                String.class, numericSystemId, Long.parseLong(v3Id)))
                .isEqualTo("Runtime V1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_version WHERE system_id=?",
                Integer.class, numericSystemId)).isEqualTo(3);
    }

    @Test
    void invalidatesStaleChecksAndRollsBackFailedOrConcurrentPublications() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));
        var system = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "vs3_atomic_" + System.nanoTime(), "name", "VS3 Atomic System",
                "description", "publication failure target", "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        var numericSystemId = Long.parseLong(systemId);
        assertOk(client.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";
        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "atomic", "name", "Atomic", "description", "", "iconKey", "shield",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", "0"
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var groupId = text(group.body(), "/data/id");
        var module = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", groupId, "code", "atomic_record", "name", "Atomic Record", "description", "",
                "iconKey", "database", "sortOrder", 0, "status", "ENABLED",
                "allowComments", false, "allowTeam", false, "draftRevision", "1"
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);

        var oldCheck = client.postWithCsrf(configRoot + "/checks", json(Map.of("draftRevision", "2")), Map.of());
        assertOk(oldCheck);
        var oldCheckId = text(oldCheck.body(), "/data/id");
        assertThat(text(oldCheck.body(), "/data/status")).isEqualTo("PASSED");
        var changedGroup = client.putWithCsrf(configRoot + "/module-groups/" + groupId, json(Map.of(
                "code", "atomic", "name", "Atomic V2", "description", "", "iconKey", "shield",
                "sortOrder", 0, "status", "ENABLED", "version", "0", "draftRevision", "2"
        )), Map.of());
        assertOk(changedGroup);
        var staleCheck = client.get(configRoot + "/checks/" + oldCheckId);
        assertOk(staleCheck);
        assertThat(text(staleCheck.body(), "/data/status")).isEqualTo("STALE");
        var dirtyRoot = client.get(configRoot);
        assertOk(dirtyRoot);
        assertError(client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", oldCheckId, "draftRevision", "3",
                "configRootVersion", text(dirtyRoot.body(), "/data/version"), "reason", "stale check"
        )), Map.of("Idempotency-Key", key())), 409, "CONFIG_CHECK_STALE");

        var freshCheck = client.postWithCsrf(configRoot + "/checks", json(Map.of("draftRevision", "3")), Map.of());
        assertOk(freshCheck);
        var readyRoot = client.get(configRoot);
        assertOk(readyRoot);
        var publishBody = json(Map.of(
                "checkId", text(freshCheck.body(), "/data/id"), "draftRevision", "3",
                "configRootVersion", text(readyRoot.body(), "/data/version"), "reason", "atomic publication"
        ));
        var permissionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_plat_permission WHERE system_id=? AND permission_code LIKE 'module.atomic_record.%'",
                Integer.class, numericSystemId);
        var versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_version WHERE system_id=?", Integer.class, numericSystemId);
        var auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=? AND operation_type='MODULE_CONFIG_PUBLISHED'", Integer.class, numericSystemId);
        var outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE aggregate_id=?", Integer.class, systemId);
        var seedVersionId = 8_000_000_000_000_000_101L;
        var seedRecordId = 8_000_000_000_000_000_102L;
        var failureKey = key();
        var freshCheckId = Long.parseLong(text(freshCheck.body(), "/data/id"));
        jdbcTemplate.update("INSERT INTO un_module_config_version (id,system_id,version_no,source_type,based_on_version_id,rollback_target_version_id,source_check_id,snapshot_json,snapshot_checksum,snapshot_size_bytes,impact_report_json,published_at,published_by,publish_reason) "
                        + "VALUES (?,?,1,'PUBLISH',NULL,NULL,?,'{}',?,2,'{}',NOW(3),1,'failure seed')",
                seedVersionId, numericSystemId, freshCheckId, "0".repeat(64));
        jdbcTemplate.update("INSERT INTO un_module_publish_record (id,system_id,operation_type,from_version_id,to_version_id,target_version_id,check_id,draft_revision,idempotency_key,request_id,trace_id,result,impact_report_json,operated_at,operated_by,reason) "
                        + "VALUES (?,?,'PUBLISH',NULL,?,NULL,?,3,?,'failure-seed','failure-seed','SUCCEEDED','{}',NOW(3),1,'failure seed')",
                seedRecordId, numericSystemId, seedVersionId, freshCheckId, failureKey);
        var failedPublish = client.postWithCsrf(configRoot + ":publish", publishBody,
                Map.of("Idempotency-Key", failureKey));
        jdbcTemplate.update("DELETE FROM un_module_publish_record WHERE id=?", seedRecordId);
        jdbcTemplate.update("DELETE FROM un_module_config_version WHERE id=?", seedVersionId);
        assertError(failedPublish, 409, "DATA_CONFLICT");
        var afterFailure = client.get(configRoot);
        assertOk(afterFailure);
        assertThat(text(afterFailure.body(), "/data/status")).isEqualTo("CHECKED");
        assertThat(text(afterFailure.body(), "/data/activeVersionId")).isBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_root WHERE system_id=? AND active_version_id IS NOT NULL",
                Integer.class, numericSystemId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_plat_permission WHERE system_id=? AND permission_code LIKE 'module.atomic_record.%'",
                Integer.class, numericSystemId)).isEqualTo(permissionCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_version WHERE system_id=?", Integer.class, numericSystemId))
                .isEqualTo(versionCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=? AND operation_type='MODULE_CONFIG_PUBLISHED'", Integer.class, numericSystemId))
                .isEqualTo(auditCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE aggregate_id=?", Integer.class, systemId))
                .isEqualTo(outboxCount);

        var secondClient = login();
        assertOk(secondClient.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        TestResponse first;
        TestResponse second;
        try (var lockConnection = java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
             var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            lockConnection.setAutoCommit(false);
            try (var statement = lockConnection.prepareStatement(
                    "SELECT id FROM un_module_config_root WHERE system_id=? FOR UPDATE")) {
                statement.setLong(1, numericSystemId);
                try (var ignored = statement.executeQuery()) {
                    assertThat(ignored.next()).isTrue();
                }
            }
            var start = new java.util.concurrent.CountDownLatch(1);
            var firstFuture = executor.submit(() -> {
                start.await();
                return client.postWithCsrf(configRoot + ":publish", publishBody,
                        Map.of("Idempotency-Key", key()));
            });
            var secondFuture = executor.submit(() -> {
                start.await();
                return secondClient.postWithCsrf(configRoot + ":publish", publishBody,
                        Map.of("Idempotency-Key", key()));
            });
            start.countDown();
            Thread.sleep(500);
            lockConnection.commit();
            first = firstFuture.get(20, java.util.concurrent.TimeUnit.SECONDS);
            second = secondFuture.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        var responses = java.util.List.of(first, second);
        assertThat(responses.stream().filter(response -> response.status() == 200).count()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.status() == 409
                && "CONFIG_VERSION_CONFLICT".equals(text(response.body(), "/code"))).count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_version WHERE system_id=?", Integer.class, numericSystemId))
                .isEqualTo(versionCount + 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_publish_record WHERE system_id=? AND result='SUCCEEDED'",
                Integer.class, numericSystemId)).isEqualTo(1);
    }
    @Test
    void copiesACompleteModuleAggregateAndReplaysIdempotently() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));
        var system = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "vs3_copy_" + System.nanoTime(), "name", "VS3 Copy System",
                "description", "module copy target", "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        assertOk(client.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";

        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "business", "name", "Business", "description", "", "iconKey", "folder",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", "0"
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var groupId = text(group.body(), "/data/id");
        var module = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", groupId, "code", "request", "name", "Request", "description", "source",
                "iconKey", "file", "sortOrder", 0, "status", "ENABLED", "allowComments", true,
                "allowTeam", true, "draftRevision", "1"
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var sourceId = text(module.body(), "/data/id");
        var sourcePages = client.get(configRoot + "/modules/" + sourceId + "/pages");
        assertOk(sourcePages);
        var sourceFormPageId = itemId(sourcePages.body().at("/data"), "type", "FORM");

        var field = client.postWithCsrf(configRoot + "/modules/" + sourceId + "/fields", fieldBody(),
                Map.of("Idempotency-Key", key()));
        assertOk(field);
        var sourceFieldId = text(field.body(), "/data/id");
        var invalidProperty = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(fieldBody());
        invalidProperty.put("code", "invalid_advanced");
        invalidProperty.put("name", "Invalid advanced property");
        invalidProperty.set("properties", objectMapper.valueToTree(Map.of("formula", "title")));
        invalidProperty.put("draftRevision", "3");
        assertError(client.postWithCsrf(configRoot + "/modules/" + sourceId + "/fields",
                objectMapper.writeValueAsString(invalidProperty), Map.of("Idempotency-Key", key())),
                400, "VALIDATION_ERROR");
        var formula = client.postWithCsrf(configRoot + "/modules/" + sourceId + "/fields", json(Map.ofEntries(
                Map.entry("dictionaryId", ""), Map.entry("targetModuleId", ""), Map.entry("code", "title_copy"),
                Map.entry("name", "Title Copy"), Map.entry("type", "FORMULA"), Map.entry("sortOrder", 1),
                Map.entry("required", false), Map.entry("hidden", false), Map.entry("readonly", true),
                Map.entry("searchable", false), Map.entry("filterable", false), Map.entry("showInList", true),
                Map.entry("showInDetail", true), Map.entry("indexMode", "NONE"), Map.entry("status", "ENABLED"),
                Map.entry("properties", Map.of(
                        "resultSchema", "STRING", "astVersion", 1,
                        "expressionAst", Map.of("fieldId", sourceFieldId))),
                Map.entry("draftRevision", "3")
        )), Map.of("Idempotency-Key", key()));
        assertOk(formula);
        var subtable = client.postWithCsrf(configRoot + "/modules/" + sourceId + "/fields", json(Map.ofEntries(
                Map.entry("dictionaryId", ""), Map.entry("targetModuleId", sourceId), Map.entry("code", "details"),
                Map.entry("name", "Details"), Map.entry("type", "SUBTABLE"), Map.entry("sortOrder", 2),
                Map.entry("required", false), Map.entry("hidden", false), Map.entry("readonly", false),
                 Map.entry("searchable", false), Map.entry("filterable", false), Map.entry("showInList", false),
                 Map.entry("showInDetail", true), Map.entry("indexMode", "NONE"), Map.entry("status", "ENABLED"),
                 Map.entry("properties", Map.ofEntries(
                         Map.entry("columnFieldIds", java.util.List.of(sourceFieldId)),
                         Map.entry("allowRowCreate", true), Map.entry("allowRowUpdate", true),
                         Map.entry("allowRowDelete", false), Map.entry("allowRowReorder", true),
                         Map.entry("minRows", 1), Map.entry("maxRows", 20))),
                 Map.entry("draftRevision", "4")
         )), Map.of("Idempotency-Key", key()));
        assertOk(subtable);
        var action = client.postWithCsrf(configRoot + "/modules/" + sourceId + "/actions", json(Map.of(
                "code", "approve", "name", "Approve", "type", "APPROVAL", "placement", "DETAIL",
                "confirmMessage", "Confirm", "sortOrder", 0, "status", "ENABLED",
                "properties", Map.of("targetPageId", sourceFormPageId), "draftRevision", "5"
        )), Map.of("Idempotency-Key", key()));
        assertOk(action);
        var component = client.postWithCsrf(configRoot + "/modules/" + sourceId + "/pages/"
                + sourceFormPageId + "/components", componentBody(null, sourceFieldId, "title", "FIELD", "6"),
                Map.of("Idempotency-Key", key()));
        assertOk(component);
        assertError(client.postWithCsrf(configRoot + "/modules/" + sourceId + "/rules", json(Map.of(
                "code", "invalid_value_type", "name", "Invalid value type", "type", "FIELD_REQUIRED", "priority", 100,
                "condition", Map.of("fieldId", sourceFieldId, "operator", "EQ", "value", 1,
                        "children", java.util.List.of()),
                "effects", java.util.List.of(Map.of("effect", "REQUIRED", "targetId", sourceFieldId,
                        "value", true)), "status", "ENABLED", "draftRevision", "7"
        )), Map.of("Idempotency-Key", key())), 422, "CONFIG_REFERENCE_INVALID");
        assertError(client.postWithCsrf(configRoot + "/modules/" + sourceId + "/rules", json(Map.of(
                "code", "invalid_operator", "name", "Invalid operator", "type", "FIELD_REQUIRED", "priority", 100,
                "condition", Map.of("fieldId", sourceFieldId, "operator", "GT", "value", 1,
                        "children", java.util.List.of()),
                "effects", java.util.List.of(Map.of("effect", "REQUIRED", "targetId", sourceFieldId,
                        "value", true)), "status", "ENABLED", "draftRevision", "7"
        )), Map.of("Idempotency-Key", key())), 422, "CONFIG_REFERENCE_INVALID");
        assertError(client.postWithCsrf(configRoot + "/modules/" + sourceId + "/rules", json(Map.of(
                "code", "invalid_effect", "name", "Invalid effect", "type", "FIELD_REQUIRED", "priority", 100,
                "condition", Map.of("fieldId", sourceFieldId, "operator", "NOT_EMPTY", "children", java.util.List.of()),
                "effects", java.util.List.of(Map.of("effect", "VISIBLE", "targetId", sourceFieldId,
                        "value", true)), "status", "ENABLED", "draftRevision", "7"
        )), Map.of("Idempotency-Key", key())), 422, "CONFIG_REFERENCE_INVALID");
        var rule = client.postWithCsrf(configRoot + "/modules/" + sourceId + "/rules", json(Map.of(
                "code", "title_required", "name", "Title required", "type", "FIELD_REQUIRED", "priority", 100,
                "condition", Map.of("fieldId", sourceFieldId, "operator", "NOT_EMPTY", "children", java.util.List.of()),
                "effects", java.util.List.of(Map.of("effect", "REQUIRED", "targetId", sourceFieldId,
                        "value", true)), "status", "ENABLED", "draftRevision", "7"
        )), Map.of("Idempotency-Key", key()));
        assertOk(rule);

        var copyBody = json(Map.of("groupId", groupId, "code", "request_copy", "name", "Request Copy",
                "sourceVersion", "0", "draftRevision", "8"));
        var copyKey = key();
        var copied = client.postWithCsrf(configRoot + "/modules/" + sourceId + ":copy", copyBody,
                Map.of("Idempotency-Key", copyKey));
        assertOk(copied);
        var copiedId = text(copied.body(), "/data/id");
        var replay = client.postWithCsrf(configRoot + "/modules/" + sourceId + ":copy", copyBody,
                Map.of("Idempotency-Key", copyKey));
        assertOk(replay);
        assertThat(text(replay.body(), "/data/id")).isEqualTo(copiedId);

        var copiedFields = client.get(configRoot + "/modules/" + copiedId + "/fields");
        var copiedActions = client.get(configRoot + "/modules/" + copiedId + "/actions");
        var copiedRules = client.get(configRoot + "/modules/" + copiedId + "/rules");
        var copiedPages = client.get(configRoot + "/modules/" + copiedId + "/pages");
        assertOk(copiedFields); assertOk(copiedActions); assertOk(copiedRules); assertOk(copiedPages);
        assertThat(copiedFields.body().at("/data")).hasSize(3);
        assertThat(copiedActions.body().at("/data")).hasSize(1);
        assertThat(copiedRules.body().at("/data")).hasSize(1);
        var copiedFieldId = itemId(copiedFields.body().at("/data"), "code", "title");
        assertThat(copiedFieldId).isNotEqualTo(sourceFieldId);
        var copiedFormula = item(copiedFields.body().at("/data"), "code", "title_copy");
        assertThat(text(copiedFormula, "/properties/expressionAst/fieldId")).isEqualTo(copiedFieldId);
        var copiedSubtable = item(copiedFields.body().at("/data"), "code", "details");
        assertThat(text(copiedSubtable, "/targetModuleId")).isEqualTo(copiedId);
        assertThat(text(copiedSubtable, "/properties/columnFieldIds/0")).isEqualTo(copiedFieldId);
        assertThat(copiedSubtable.at("/properties/allowRowReorder").asBoolean()).isTrue();
        assertThat(text(copiedRules.body(), "/data/0/condition/fieldId")).isEqualTo(copiedFieldId);
        assertThat(text(copiedRules.body(), "/data/0/effects/0/targetId")).isEqualTo(copiedFieldId);
        var copiedFormPageId = itemId(copiedPages.body().at("/data"), "type", "FORM");
        assertThat(text(copiedActions.body(), "/data/0/properties/targetPageId")).isEqualTo(copiedFormPageId);
        var copiedComponents = client.get(configRoot + "/modules/" + copiedId + "/pages/"
                + copiedFormPageId + "/components");
        assertOk(copiedComponents);
        assertThat(copiedComponents.body().at("/data")).hasSize(1);
        assertThat(text(copiedComponents.body(), "/data/0/fieldId")).isEqualTo(copiedFieldId);

        var rootAfterCopy = client.get(configRoot);
        assertOk(rootAfterCopy);
        var deletedCopy = client.postWithCsrf(configRoot + "/modules/" + copiedId + ":delete",
                json(Map.of("version", text(copied.body(), "/data/version"),
                        "draftRevision", text(rootAfterCopy.body(), "/data/draftRevision"))), Map.of());
        assertOk(deletedCopy);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_page_component c JOIN un_module_page p "
                        + "ON p.system_id=c.system_id AND p.id=c.page_id "
                        + "WHERE c.system_id=? AND p.module_id=? AND c.deleted_at IS NULL",
                Long.class, Long.parseLong(systemId), Long.parseLong(copiedId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_reference r WHERE r.system_id=? AND ("
                        + "(r.source_type='FIELD' AND r.source_id IN (SELECT id FROM un_module_field WHERE system_id=? AND module_id=?)) OR "
                        + "(r.source_type='PAGE' AND r.source_id IN (SELECT id FROM un_module_page WHERE system_id=? AND module_id=?)) OR "
                        + "(r.source_type='COMPONENT' AND r.source_id IN (SELECT c.id FROM un_module_page_component c JOIN un_module_page p ON p.system_id=c.system_id AND p.id=c.page_id WHERE c.system_id=? AND p.module_id=?)) OR "
                        + "(r.source_type='ACTION' AND r.source_id IN (SELECT id FROM un_module_action WHERE system_id=? AND module_id=?)) OR "
                        + "(r.source_type='RULE' AND r.source_id IN (SELECT id FROM un_module_rule WHERE system_id=? AND module_id=?)))",
                Long.class, Long.parseLong(systemId), Long.parseLong(systemId), Long.parseLong(copiedId),
                Long.parseLong(systemId), Long.parseLong(copiedId), Long.parseLong(systemId), Long.parseLong(copiedId),
                Long.parseLong(systemId), Long.parseLong(copiedId), Long.parseLong(systemId), Long.parseLong(copiedId))).isZero();
    }

    @Test
    void servesPublishedRecordSchemaListAndDetailWithStrictQueryGuards() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));

        var system = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "p4_runtime_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "P4 Runtime System",
                "description", "P4 read runtime integration target",
                "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        assertOk(client.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";

        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "operations", "name", "Operations", "description", "", "iconKey", "folder",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", "0"
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var module = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", text(group.body(), "/data/id"), "code", "work_order", "name", "Work Order",
                "description", "", "iconKey", "clipboard", "sortOrder", 0, "status", "ENABLED",
                "allowComments", true, "allowTeam", true, "draftRevision", "1"
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");

        var runtimeFieldBody = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(fieldBody());
        runtimeFieldBody.put("indexMode", "SORT");
        var field = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields",
                objectMapper.writeValueAsString(runtimeFieldBody), Map.of("Idempotency-Key", key()));
        assertOk(field);
        var fieldId = text(field.body(), "/data/id");

        assertOk(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/actions", json(Map.of(
                "code", "archive", "name", "Archive", "type", "CUSTOM", "placement", "DETAIL",
                "confirmMessage", "Archive this record?", "sortOrder", 10, "status", "ENABLED",
                "properties", Map.of("style", "DEFAULT", "successMessage", "Record archived"),
                "draftRevision", "3"
        )), Map.of("Idempotency-Key", key())));
        assertOk(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/actions", json(Map.of(
                "code", "unarchive", "name", "Unarchive", "type", "CUSTOM", "placement", "DETAIL",
                "confirmMessage", "", "sortOrder", 20, "status", "ENABLED",
                "properties", Map.of("style", "DEFAULT", "successMessage", "Record unarchived"),
                "draftRevision", "4"
        )), Map.of("Idempotency-Key", key())));
        assertOk(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/actions", json(Map.of(
                "code", "restore_trash", "name", "Restore", "type", "CUSTOM", "placement", "DETAIL",
                "confirmMessage", "", "sortOrder", 30, "status", "ENABLED",
                "properties", Map.of("style", "DEFAULT", "successMessage", "Record restored"),
                "draftRevision", "5"
        )), Map.of("Idempotency-Key", key())));
        assertOk(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/actions", json(Map.of(
                "code", "recover_draft", "name", "Recover draft", "type", "CUSTOM", "placement", "DETAIL",
                "confirmMessage", "", "sortOrder", 40, "status", "ENABLED",
                "properties", Map.of("style", "DEFAULT", "successMessage", "Draft recovered"),
                "draftRevision", "6"
        )), Map.of("Idempotency-Key", key())));

        var checked = client.postWithCsrf(configRoot + "/checks", json(Map.of("draftRevision", "7")), Map.of());
        assertOk(checked);
        var checkedRoot = client.get(configRoot);
        assertOk(checkedRoot);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"),
                "draftRevision", "7",
                "configRootVersion", text(checkedRoot.body(), "/data/version"),
                "reason", "P4 read runtime integration publication"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        var schemaVersionId = Long.parseLong(text(published.body(), "/data/version/id"));
        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var numericSystemId = Long.parseLong(systemId);
        var numericModuleId = Long.parseLong(moduleId);
        var numericFieldId = Long.parseLong(fieldId);
        var memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM un_plat_member WHERE system_id=? AND deleted_at IS NULL",
                Long.class, numericSystemId);
        var tenantId = jdbcTemplate.queryForObject(
                "SELECT default_tenant_id FROM un_plat_member WHERE id=?", Long.class, memberId);
        var recordBase = 7_000_000_000_000_000_000L + Math.floorMod(System.nanoTime(), 10_000_000_000L);
        insertRuntimeRecord(recordBase, numericSystemId, tenantId, schemaVersionId, numericModuleId,
                numericFieldId, memberId, "WO-001", "Replace access reader");
        insertRuntimeRecord(recordBase + 1, numericSystemId, tenantId, schemaVersionId, numericModuleId,
                numericFieldId, memberId, "WO-002", "Inspect backup power");

        var runtimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/work_order";
        var definition = client.get(runtimeRoot + "/definition");
        assertOk(definition);
        assertThat(definition.body().at("/data/recordsAvailable").asBoolean()).isTrue();

        var schema = client.get(runtimeRoot + "/record-schema");
        assertOk(schema);
        assertThat(text(schema.body(), "/data/runtimeState")).isEqualTo("READY");
        assertThat(text(schema.body(), "/data/schemaVersionId")).isEqualTo(Long.toString(schemaVersionId));
        assertThat(text(schema.body(), "/data/fields/0/fieldCode")).isEqualTo("title");
        assertThat(text(schema.body(), "/data/fields/0/type")).isEqualTo("TEXT");
        assertThat(schema.body().at("/data/fields/0/sortable").asBoolean()).isTrue();
        assertThat(schema.body().at("/data/fields/0/operators").toString())
                .contains("EQ", "CONTAINS", "PREFIX", "EMPTY");

        var firstPage = client.get(runtimeRoot + "/records?page=1&size=1&sort=recordNo:desc");
        assertOk(firstPage);
        assertThat(firstPage.body().at("/data/total").asLong()).isEqualTo(2L);
        assertThat(text(firstPage.body(), "/data/rows/0/recordNo")).isEqualTo("WO-002");
        assertThat(text(firstPage.body(), "/data/rows/0/values/0/value")).isEqualTo("Inspect backup power");

        var detail = client.get(runtimeRoot + "/records/" + recordBase);
        assertOk(detail);
        assertThat(text(detail.body(), "/data/recordNo")).isEqualTo("WO-001");
        assertThat(text(detail.body(), "/data/values/0/fieldCode")).isEqualTo("title");
        assertThat(text(detail.body(), "/data/values/0/value")).isEqualTo("Replace access reader");

        assertError(client.get(runtimeRoot + "/records?size=201"), 422, "QUERY_INVALID");
        assertError(client.get(runtimeRoot + "/records?sort=owner:asc"), 422, "QUERY_INVALID");
        assertError(client.get(runtimeRoot + "/records?filter=%7B%7D"), 422, "QUERY_INVALID");
        assertError(client.get(runtimeRoot + "/records/" + (recordBase + 999)), 404, "RECORD_NOT_FOUND");

        assertThat(schema.body().at("/data/actions/0").asText()).isEqualTo("CREATE");
        var createBody = json(Map.of(
                "schemaVersionId", Long.toString(schemaVersionId),
                "title", "P4-A2 integration record",
                "values", Map.of("title", "Created through runtime command")
        ));
        var createKey = key();
        var created = client.postWithCsrf(runtimeRoot + "/records", createBody,
                Map.of("Idempotency-Key", createKey));
        assertCreated(created);
        var createdId = text(created.body(), "/data/recordId");
        assertThat(text(created.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(created.body().at("/data/version").asLong()).isZero();
        assertThat(created.body().at("/data/actions").toString())
                .contains("UPDATE", "AUTOSAVE", "ACTIVATE");

        var createReplay = client.postWithCsrf(runtimeRoot + "/records", createBody,
                Map.of("Idempotency-Key", createKey));
        assertCreated(createReplay);
        assertThat(text(createReplay.body(), "/data/recordId")).isEqualTo(createdId);
        assertError(client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId),
                        "title", "Different request",
                        "values", Map.of("title", "Different value")
                )), Map.of("Idempotency-Key", createKey)), 409, "IDEMPOTENCY_CONFLICT");
        assertError(client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId),
                        "values", Map.of("title", 99)
                )), Map.of("Idempotency-Key", key())), 422, "RECORD_VALIDATION_FAILED");
        assertError(client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId),
                        "values", Map.of("unknown", "blocked")
                )), Map.of("Idempotency-Key", key())), 403, "RECORD_FIELD_FORBIDDEN");
        assertError(client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId + 1),
                        "values", Map.of("title", "stale")
                )), Map.of("Idempotency-Key", key())), 409, "RECORD_SCHEMA_STALE");

        var incomplete = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId), "values", Map.of()
                )), Map.of("Idempotency-Key", key()));
        assertCreated(incomplete);
        assertError(client.postWithCsrf(runtimeRoot + "/records/" + text(incomplete.body(), "/data/recordId")
                        + ":activate", json(Map.of("expectedVersion", 0)), Map.of("Idempotency-Key", key())),
                422, "RECORD_VALIDATION_FAILED");

        var editableId = text(incomplete.body(), "/data/recordId");
        var editableNumericId = Long.parseLong(editableId);
        var draftDetail = client.get(runtimeRoot + "/records/" + editableId);
        assertOk(draftDetail);
        assertThat(text(draftDetail.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(draftDetail.body().at("/data/actions").toString())
                .contains("UPDATE", "AUTOSAVE", "ACTIVATE");
        var initialExpiry = jdbcTemplate.queryForObject(
                "SELECT draft_expires_at FROM un_module_record WHERE record_id=?",
                java.sql.Timestamp.class, editableNumericId);

        var updateBody = json(Map.of(
                "schemaVersionId", Long.toString(schemaVersionId),
                "title", "P4-B1 manual edit",
                "expectedVersion", 0,
                "values", Map.of("title", "Saved manually")
        ));
        var updateKey = key();
        var updatedDraft = client.putWithCsrf(runtimeRoot + "/records/" + editableId, updateBody,
                Map.of("Idempotency-Key", updateKey));
        assertOk(updatedDraft);
        assertThat(updatedDraft.body().at("/data/version").asLong()).isEqualTo(1);
        assertThat(text(updatedDraft.body(), "/data/values/0/value")).isEqualTo("Saved manually");
        var updateReplay = client.putWithCsrf(runtimeRoot + "/records/" + editableId, updateBody,
                Map.of("Idempotency-Key", updateKey));
        assertOk(updateReplay);
        assertThat(updateReplay.body().at("/data/version").asLong()).isEqualTo(1);
        assertError(client.putWithCsrf(runtimeRoot + "/records/" + editableId, json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId),
                        "title", "Different update",
                        "expectedVersion", 0,
                        "values", Map.of("title", "Different update")
                )), Map.of("Idempotency-Key", updateKey)), 409, "IDEMPOTENCY_CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draft_expires_at FROM un_module_record WHERE record_id=?",
                java.sql.Timestamp.class, editableNumericId)).isEqualTo(initialExpiry);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_value WHERE record_id=? AND string_value='Saved manually'",
                Long.class, editableNumericId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_value WHERE record_id=?",
                Long.class, editableNumericId)).isEqualTo(1L);

        Thread.sleep(5L);
        var autosaveBody = json(Map.of(
                "schemaVersionId", Long.toString(schemaVersionId),
                "title", "P4-B1 autosaved edit",
                "expectedVersion", 1,
                "values", Map.of("title", "Saved automatically")
        ));
        var autosaveKey = key();
        var autosaved = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":autosave",
                autosaveBody, Map.of("Idempotency-Key", autosaveKey));
        assertOk(autosaved);
        assertThat(autosaved.body().at("/data/record/version").asLong()).isEqualTo(2);
        assertThat(text(autosaved.body(), "/data/record/values/0/value")).isEqualTo("Saved automatically");
        assertThat(text(autosaved.body(), "/data/lastSavedAt")).isNotBlank();
        assertThat(text(autosaved.body(), "/data/expiresAt")).isNotBlank();
        var autosaveReplay = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":autosave",
                autosaveBody, Map.of("Idempotency-Key", autosaveKey));
        assertOk(autosaveReplay);
        assertThat(autosaveReplay.body().at("/data/record/version").asLong()).isEqualTo(2);
        assertError(client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":autosave",
                        json(Map.of(
                                "schemaVersionId", Long.toString(schemaVersionId),
                                "title", "Different autosave",
                                "expectedVersion", 1,
                                "values", Map.of("title", "Different autosave")
                        )), Map.of("Idempotency-Key", autosaveKey)),
                409, "IDEMPOTENCY_CONFLICT");
        var renewedExpiry = jdbcTemplate.queryForObject(
                "SELECT draft_expires_at FROM un_module_record WHERE record_id=?",
                java.sql.Timestamp.class, editableNumericId);
        assertThat(renewedExpiry).isAfter(initialExpiry);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=? AND record_status='DRAFT' "
                        + "AND string_value='Saved automatically'",
                Long.class, editableNumericId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=?",
                Long.class, editableNumericId)).isEqualTo(1L);

        var staleUpdate = client.putWithCsrf(runtimeRoot + "/records/" + editableId, json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId),
                        "title", "Stale local edit",
                        "expectedVersion", 1,
                        "values", Map.of("title", "Stale local value")
                )), Map.of("Idempotency-Key", key()));
        assertThat(staleUpdate.status()).isEqualTo(409);
        assertThat(text(staleUpdate.body(), "/code")).isEqualTo("RECORD_VERSION_CONFLICT");
        assertThat(staleUpdate.body().at("/data/currentVersion").asLong()).isEqualTo(2);
        assertThat(text(staleUpdate.body(), "/data/currentSnapshot/values/0/value"))
                .isEqualTo("Saved automatically");
        assertThat(staleUpdate.body().at("/data/conflictFields").toString()).contains("values.title");

        var editableActivated = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":activate",
                json(Map.of("expectedVersion", 2)), Map.of("Idempotency-Key", key()));
        assertOk(editableActivated);
        assertThat(editableActivated.body().at("/data/version").asLong()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_search WHERE record_id=? AND record_status='ACTIVE'",
                Long.class, editableNumericId)).isGreaterThan(1L);
        assertError(client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":autosave",
                        json(Map.of(
                                "schemaVersionId", Long.toString(schemaVersionId),
                                "title", "Invalid active autosave",
                                "expectedVersion", 3,
                                "values", Map.of("title", "Invalid active autosave")
                        )), Map.of("Idempotency-Key", key())),
                409, "RECORD_STATE_INVALID");
        var activeUpdated = client.putWithCsrf(runtimeRoot + "/records/" + editableId, json(Map.of(
                        "schemaVersionId", Long.toString(schemaVersionId),
                        "title", "P4-B1 active edit",
                        "expectedVersion", 3,
                        "values", Map.of("title", "Edited after activation")
                )), Map.of("Idempotency-Key", key()));
        assertOk(activeUpdated);
        assertThat(activeUpdated.body().at("/data/version").asLong()).isEqualTo(4);
        assertThat(text(activeUpdated.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(text(activeUpdated.body(), "/data/values/0/value")).isEqualTo("Edited after activation");
        assertThat(activeUpdated.body().at("/data/actions").toString()).contains("ARCHIVE", "TRASH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=? AND record_status='ACTIVE' "
                        + "AND string_value='Edited after activation'",
                Long.class, editableNumericId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_search WHERE record_id=? AND record_status='ACTIVE'",
                Long.class, editableNumericId)).isGreaterThan(1L);

        var searchQuery = new LinkedHashMap<String, Object>();
        searchQuery.put("schemaVersionId", Long.toString(schemaVersionId));
        searchQuery.put("page", 1);
        searchQuery.put("size", 10);
        searchQuery.put("recordScope", "active");
        searchQuery.put("q", "edited");
        searchQuery.put("filter", Map.of(
                "kind", "AND",
                "children", java.util.List.of(
                        Map.of("kind", "PREDICATE", "fieldCode", "title", "operator", "PREFIX",
                                "value", "Edited"),
                        Map.of("kind", "NOT", "children", java.util.List.of(
                                Map.of("kind", "PREDICATE", "fieldCode", "title", "operator", "EQ",
                                        "value", "Not this record")))
                )));
        searchQuery.put("sort", java.util.List.of(
                Map.of("fieldCode", "title", "direction", "ASC", "nulls", "LAST")));
        searchQuery.put("columns", java.util.List.of("title"));
        searchQuery.put("viewId", null);
        var queried = client.post(runtimeRoot + "/records:query", json(searchQuery), Map.of());
        assertOk(queried);
        assertThat(queried.body().at("/data/total").asLong()).isEqualTo(1L);
        assertThat(text(queried.body(), "/data/rows/0/recordId")).isEqualTo(editableId);
        assertThat(text(queried.body(), "/data/rows/0/values/0/value")).isEqualTo("Edited after activation");
        assertThat(text(queried.body(), "/data/queryHash")).matches("^[a-f0-9]{64}$");
        assertThat(text(queried.body(), "/data/querySnapshotToken")).contains(".");
        assertThat(queried.body().at("/data/invalidNodes")).isEmpty();
        assertError(client.post(runtimeRoot + "/records:query",
                json(Map.of("schemaVersionId", Long.toString(schemaVersionId))), Map.of()),
                422, "QUERY_INVALID");
        assertError(client.post(runtimeRoot + "/records:query",
                "{\"schemaVersionId\":\"" + schemaVersionId + "\",\"schemaVersionId\":\""
                        + schemaVersionId + "\"}", Map.of()), 422, "QUERY_INVALID");

        var unavailableQuery = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.valueToTree(searchQuery);
        unavailableQuery.putNull("q");
        unavailableQuery.set("filter", objectMapper.valueToTree(Map.of(
                "kind", "PREDICATE", "fieldCode", "missing", "operator", "EQ", "value", "x")));
        assertError(client.post(runtimeRoot + "/records:query", json(unavailableQuery), Map.of()),
                422, "QUERY_FIELD_UNAVAILABLE");

        var savedViewRoot = "/api/v1/systems/" + systemId + "/runtime/saved-views";
        var createViewBody = objectMapper.createObjectNode();
        createViewBody.put("moduleCode", "work_order");
        createViewBody.put("name", "Edited records");
        createViewBody.set("query", objectMapper.valueToTree(searchQuery));
        createViewBody.set("columns", objectMapper.valueToTree(java.util.List.of("title")));
        var createViewKey = key();
        var createdView = client.postWithCsrf(savedViewRoot, json(createViewBody),
                Map.of("Idempotency-Key", createViewKey));
        assertCreated(createdView);
        var viewId = text(createdView.body(), "/data/viewId");
        assertThat(createdView.body().at("/data/version").asLong()).isZero();
        assertThat(text(createdView.body(), "/data/name")).isEqualTo("Edited records");
        var createViewReplay = client.postWithCsrf(savedViewRoot, json(createViewBody),
                Map.of("Idempotency-Key", createViewKey));
        assertCreated(createViewReplay);
        assertThat(text(createViewReplay.body(), "/data/viewId")).isEqualTo(viewId);
        var changedCreateBody = createViewBody.deepCopy();
        changedCreateBody.put("name", "Changed idempotency body");
        assertError(client.postWithCsrf(savedViewRoot, json(changedCreateBody),
                Map.of("Idempotency-Key", createViewKey)), 409, "IDEMPOTENCY_CONFLICT");

        var staleStoredQuery = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.valueToTree(searchQuery);
        staleStoredQuery.set("filter", objectMapper.valueToTree(Map.of(
                "kind", "PREDICATE", "fieldCode", "retired_field", "operator", "EQ", "value", "old")));
        assertThat(jdbcTemplate.update("UPDATE un_module_saved_view SET query_json=? WHERE id=?",
                json(staleStoredQuery), Long.parseLong(viewId))).isEqualTo(1);
        var applyViewQuery = objectMapper.createObjectNode();
        applyViewQuery.put("schemaVersionId", Long.toString(schemaVersionId));
        applyViewQuery.put("page", 1);
        applyViewQuery.put("size", 10);
        applyViewQuery.put("recordScope", "active");
        applyViewQuery.putNull("q");
        applyViewQuery.putNull("filter");
        applyViewQuery.set("sort", objectMapper.createArrayNode());
        applyViewQuery.set("columns", objectMapper.createArrayNode());
        applyViewQuery.put("viewId", viewId);
        var appliedView = client.post(runtimeRoot + "/records:query", json(applyViewQuery), Map.of());
        assertOk(appliedView);
        assertThat(appliedView.body().at("/data/total").asLong()).isEqualTo(1L);
        assertThat(appliedView.body().at("/data/invalidNodes").toString()).contains("/filter");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(query_json,'$.filter.fieldCode')) "
                        + "FROM un_module_saved_view WHERE id=?", String.class, Long.parseLong(viewId)))
                .isEqualTo("retired_field");

        var updateViewBody = objectMapper.createObjectNode();
        updateViewBody.put("expectedVersion", 0);
        updateViewBody.put("name", "Edited records v2");
        updateViewBody.set("query", objectMapper.valueToTree(searchQuery));
        updateViewBody.set("columns", objectMapper.valueToTree(java.util.List.of("title")));
        var updateViewKey = key();
        var updatedView = client.putWithCsrf(savedViewRoot + "/" + viewId, json(updateViewBody),
                Map.of("Idempotency-Key", updateViewKey));
        assertOk(updatedView);
        assertThat(updatedView.body().at("/data/version").asLong()).isEqualTo(1L);
        assertThat(text(updatedView.body(), "/data/name")).isEqualTo("Edited records v2");
        var updateViewReplay = client.putWithCsrf(savedViewRoot + "/" + viewId, json(updateViewBody),
                Map.of("Idempotency-Key", updateViewKey));
        assertOk(updateViewReplay);
        assertThat(updateViewReplay.body().at("/data/version").asLong()).isEqualTo(1L);
        assertError(client.putWithCsrf(savedViewRoot + "/" + viewId, json(updateViewBody),
                Map.of("Idempotency-Key", key())), 409, "SAVED_VIEW_VERSION_CONFLICT");

        var duplicateNameBody = createViewBody.deepCopy();
        duplicateNameBody.put("name", "Edited records v2");
        assertError(client.postWithCsrf(savedViewRoot, json(duplicateNameBody),
                Map.of("Idempotency-Key", key())), 409, "SAVED_VIEW_NAME_CONFLICT");
        var listedViews = client.get(savedViewRoot + "?moduleCode=work_order");
        assertOk(listedViews);
        assertThat(listedViews.body().at("/data/items")).hasSize(1);
        assertThat(text(listedViews.body(), "/data/items/0/viewId")).isEqualTo(viewId);

        for (var index = 0; index < 99; index++) {
            jdbcTemplate.update("INSERT INTO un_module_saved_view "
                            + "(id,system_id,tenant_id,member_id,logical_module_id,schema_version_id,module_snapshot_id,"
                            + "name,query_json,columns_json,created_at,created_by,updated_at,updated_by,version) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,NOW(3),?,NOW(3),?,0)",
                    recordBase + 1_000 + index, numericSystemId, tenantId, memberId, numericModuleId,
                    schemaVersionId, numericModuleId, "limit-" + index, json(searchQuery), "[\"title\"]",
                    memberId, memberId);
        }
        var overLimitBody = createViewBody.deepCopy();
        overLimitBody.put("name", "Over limit");
        assertError(client.postWithCsrf(savedViewRoot, json(overLimitBody),
                Map.of("Idempotency-Key", key())), 409, "SAVED_VIEW_LIMIT_REACHED");
        assertThat(jdbcTemplate.update("DELETE FROM un_module_saved_view WHERE system_id=? AND name LIKE 'limit-%'",
                numericSystemId)).isEqualTo(99);

        var foreignOwnerSession = new RuntimeSession(1L, numericSystemId, tenantId, memberId + 999,
                Set.of("system.runtime.access", "runtime.saved_view.manage", "module.work_order.view"));
        assertThatThrownBy(() -> savedViewRepository.require(foreignOwnerSession, numericModuleId,
                Long.parseLong(viewId))).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("SAVED_VIEW_NOT_FOUND"));

        var deleteViewKey = key();
        var deletedView = client.deleteWithCsrf(savedViewRoot + "/" + viewId,
                json(Map.of("expectedVersion", 1)), Map.of("Idempotency-Key", deleteViewKey));
        assertOk(deletedView);
        assertThat(deletedView.body().at("/data/deleted").asBoolean()).isTrue();
        assertThat(deletedView.body().at("/data/version").asLong()).isEqualTo(2L);
        var deleteViewReplay = client.deleteWithCsrf(savedViewRoot + "/" + viewId,
                json(Map.of("expectedVersion", 1)), Map.of("Idempotency-Key", deleteViewKey));
        assertOk(deleteViewReplay);
        assertThat(deleteViewReplay.body().at("/data/version").asLong()).isEqualTo(2L);
        assertThat(client.get(savedViewRoot + "?moduleCode=work_order").body().at("/data/items")).isEmpty();
        assertError(client.post(runtimeRoot + "/records:query", json(applyViewQuery), Map.of()),
                404, "SAVED_VIEW_NOT_FOUND");
        var reusedName = client.postWithCsrf(savedViewRoot, json(duplicateNameBody),
                Map.of("Idempotency-Key", key()));
        assertCreated(reusedName);
        assertThat(text(reusedName.body(), "/data/name")).isEqualTo("Edited records v2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=? "
                        + "AND aggregate_type='RUNTIME_SAVED_VIEW' AND result='SUCCESS'",
                Long.class, numericSystemId)).isGreaterThanOrEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE system_id=? "
                        + "AND aggregate_type='RUNTIME_SAVED_VIEW'",
                Long.class, numericSystemId)).isGreaterThanOrEqualTo(4L);

        var archiveBody = json(Map.of("expectedVersion", 4));
        var archiveKey = key();
        var archived = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":archive",
                archiveBody, Map.of("Idempotency-Key", archiveKey));
        assertOk(archived);
        assertThat(text(archived.body(), "/data/status")).isEqualTo("ARCHIVED");
        assertThat(archived.body().at("/data/version").asLong()).isEqualTo(5);
        assertThat(archived.body().at("/data/actions").toString()).contains("UNARCHIVE", "TRASH");
        var archivedQuery = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.valueToTree(searchQuery);
        archivedQuery.put("recordScope", "archived");
        archivedQuery.put("q", "edited");
        archivedQuery.putNull("filter");
        var archivedPage = client.post(runtimeRoot + "/records:query", json(archivedQuery), Map.of());
        assertOk(archivedPage);
        assertThat(archivedPage.body().at("/data/total").asLong()).isEqualTo(1L);
        assertThat(text(archivedPage.body(), "/data/rows/0/status")).isEqualTo("ARCHIVED");
        var archiveReplay = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":archive",
                archiveBody, Map.of("Idempotency-Key", archiveKey));
        assertOk(archiveReplay);
        assertThat(archiveReplay.body().at("/data/version").asLong()).isEqualTo(5);
        assertError(client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":archive",
                        json(Map.of("expectedVersion", 5)), Map.of("Idempotency-Key", key())),
                409, "RECORD_STATE_INVALID");

        var staleUnarchive = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":unarchive",
                json(Map.of("expectedVersion", 4)), Map.of("Idempotency-Key", key()));
        assertThat(staleUnarchive.status()).isEqualTo(409);
        assertThat(text(staleUnarchive.body(), "/code")).isEqualTo("RECORD_VERSION_CONFLICT");
        assertThat(staleUnarchive.body().at("/data/currentVersion").asLong()).isEqualTo(5);
        assertThat(text(staleUnarchive.body(), "/data/currentSnapshot/status")).isEqualTo("ARCHIVED");
        var unarchived = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":unarchive",
                json(Map.of("expectedVersion", 5)), Map.of("Idempotency-Key", key()));
        assertOk(unarchived);
        assertThat(text(unarchived.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(unarchived.body().at("/data/version").asLong()).isEqualTo(6);

        var trashed = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":trash",
                json(Map.of("expectedVersion", 6)), Map.of("Idempotency-Key", key()));
        assertOk(trashed);
        assertThat(text(trashed.body(), "/data/status")).isEqualTo("TRASHED");
        assertThat(trashed.body().at("/data/version").asLong()).isEqualTo(7);
        assertThat(trashed.body().at("/data/actions").toString()).contains("RESTORE_TRASH");
        assertThat(text(trashed.body(), "/data/values/0/value")).isEqualTo("Edited after activation");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE record_id=? AND status='TRASHED' "
                        + "AND prior_status='ACTIVE' AND deleted_at IS NOT NULL AND deleted_by=?",
                Long.class, editableNumericId, memberId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=? AND record_status='TRASHED'",
                Long.class, editableNumericId)).isEqualTo(1L);
        var trashQuery = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.valueToTree(searchQuery);
        trashQuery.put("recordScope", "trash");
        trashQuery.put("q", "edited");
        trashQuery.putNull("filter");
        var trashPage = client.post(runtimeRoot + "/records:query", json(trashQuery), Map.of());
        assertOk(trashPage);
        assertThat(trashPage.body().at("/data/total").asLong()).isEqualTo(1L);
        assertThat(text(trashPage.body(), "/data/rows/0/status")).isEqualTo("TRASHED");
        var trashedDetail = client.get(runtimeRoot + "/records/" + editableId);
        assertOk(trashedDetail);
        assertThat(text(trashedDetail.body(), "/data/status")).isEqualTo("TRASHED");

        var restored = client.postWithCsrf(runtimeRoot + "/records/" + editableId + ":restore-from-trash",
                json(Map.of("expectedVersion", 7)), Map.of("Idempotency-Key", key()));
        assertOk(restored);
        assertThat(text(restored.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(restored.body().at("/data/version").asLong()).isEqualTo(8);
        assertThat(text(restored.body(), "/data/values/0/value")).isEqualTo("Edited after activation");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE record_id=? AND status='ACTIVE' "
                        + "AND prior_status IS NULL AND deleted_at IS NULL AND deleted_by IS NULL",
                Long.class, editableNumericId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=? AND record_status='ACTIVE'",
                Long.class, editableNumericId)).isEqualTo(1L);

        var discardDraft = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", Long.toString(schemaVersionId),
                "title", "Discard lifecycle draft",
                "values", Map.of("title", "Discard lifecycle draft")
        )), Map.of("Idempotency-Key", key()));
        assertCreated(discardDraft);
        var discardId = text(discardDraft.body(), "/data/recordId");
        assertThat(discardDraft.body().at("/data/actions").toString()).contains("DISCARD");
        var discarded = client.postWithCsrf(runtimeRoot + "/records/" + discardId + ":discard",
                json(Map.of("expectedVersion", 0)), Map.of("Idempotency-Key", key()));
        assertOk(discarded);
        assertThat(text(discarded.body(), "/data/status")).isEqualTo("TRASHED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE record_id=? AND status='TRASHED' "
                        + "AND prior_status='DRAFT' AND draft_expires_at IS NULL",
                Long.class, Long.parseLong(discardId))).isEqualTo(1L);

        var expiryDraft = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", Long.toString(schemaVersionId),
                "title", "Expiry worker draft",
                "values", Map.of("title", "Expiry worker value")
        )), Map.of("Idempotency-Key", key()));
        assertCreated(expiryDraft);
        var expiryId = text(expiryDraft.body(), "/data/recordId");
        var expiryNumericId = Long.parseLong(expiryId);
        jdbcTemplate.update("UPDATE un_module_record SET draft_expires_at=DATE_SUB(NOW(3), INTERVAL 1 SECOND) "
                + "WHERE record_id=? AND status='DRAFT'", expiryNumericId);

        assertThat(draftExpiryWorker.expireDueDrafts()).isEqualTo(1);
        assertThat(draftExpiryWorker.expireDueDrafts()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE record_id=? AND status='EXPIRED' AND version=1 "
                        + "AND draft_expires_at IS NULL", Long.class, expiryNumericId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=? AND record_status='EXPIRED'",
                Long.class, expiryNumericId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE aggregate_type='RUNTIME_RECORD' "
                        + "AND aggregate_id=? AND operation_type='RECORD_DRAFT_EXPIRED' "
                        + "AND actor_account_id IS NULL AND source_type='SYSTEM' AND result='SUCCESS'",
                Long.class, expiryId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE aggregate_type='RUNTIME_RECORD' "
                        + "AND aggregate_id=? AND dedupe_key=?",
                Long.class, expiryId, "runtime-record:" + systemId + ":" + expiryId
                        + ":1:RECORD_DRAFT_EXPIRED")).isEqualTo(1L);

        var expiredDetail = client.get(runtimeRoot + "/records/" + expiryId);
        assertOk(expiredDetail);
        assertThat(text(expiredDetail.body(), "/data/status")).isEqualTo("EXPIRED");
        assertThat(expiredDetail.body().at("/data/actions").toString()).contains("RECOVER_DRAFT", "TRASH");
        var recoverBody = json(Map.of("expectedVersion", 1));
        var recoverKey = key();
        var recovered = client.postWithCsrf(runtimeRoot + "/records/" + expiryId + ":recover",
                recoverBody, Map.of("Idempotency-Key", recoverKey));
        assertOk(recovered);
        assertThat(text(recovered.body(), "/data/status")).isEqualTo("DRAFT");
        assertThat(recovered.body().at("/data/version").asLong()).isEqualTo(2);
        assertThat(text(recovered.body(), "/data/values/0/value")).isEqualTo("Expiry worker value");
        var recoverReplay = client.postWithCsrf(runtimeRoot + "/records/" + expiryId + ":recover",
                recoverBody, Map.of("Idempotency-Key", recoverKey));
        assertOk(recoverReplay);
        assertThat(recoverReplay.body().at("/data/version").asLong()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE record_id=? AND status='DRAFT' AND version=2 "
                        + "AND draft_expires_at>DATE_ADD(NOW(3), INTERVAL 29 DAY)",
                Long.class, expiryNumericId)).isEqualTo(1L);
        var recoveredUpdate = client.putWithCsrf(runtimeRoot + "/records/" + expiryId, json(Map.of(
                "schemaVersionId", Long.toString(schemaVersionId),
                "title", "Recovered editable draft",
                "expectedVersion", 2,
                "values", Map.of("title", "Edited after recovery")
        )), Map.of("Idempotency-Key", key()));
        assertOk(recoveredUpdate);
        assertThat(recoveredUpdate.body().at("/data/version").asLong()).isEqualTo(3);
        assertThat(text(recoveredUpdate.body(), "/data/values/0/value")).isEqualTo("Edited after recovery");

        assertError(client.postWithCsrf(runtimeRoot + "/records/" + createdId + ":activate",
                        json(Map.of("expectedVersion", 9)), Map.of("Idempotency-Key", key())),
                409, "RECORD_VERSION_CONFLICT");
        var activateKey = key();
        var activated = client.postWithCsrf(runtimeRoot + "/records/" + createdId + ":activate",
                json(Map.of("expectedVersion", 0)), Map.of("Idempotency-Key", activateKey));
        assertOk(activated);
        assertThat(text(activated.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(activated.body().at("/data/version").asLong()).isEqualTo(1);
        var activateReplay = client.postWithCsrf(runtimeRoot + "/records/" + createdId + ":activate",
                json(Map.of("expectedVersion", 0)), Map.of("Idempotency-Key", activateKey));
        assertOk(activateReplay);
        assertThat(activateReplay.body().at("/data/version").asLong()).isEqualTo(1);
        assertError(client.postWithCsrf(runtimeRoot + "/records/" + createdId + ":activate",
                        json(Map.of("expectedVersion", 1)), Map.of("Idempotency-Key", key())),
                409, "RECORD_STATE_INVALID");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE record_id=? AND status='ACTIVE' AND version=1 "
                        + "AND draft_expires_at IS NULL", Long.class, Long.parseLong(createdId))).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_value WHERE record_id=? AND field_type='TEXT' "
                        + "AND string_value='Created through runtime command'", Long.class, Long.parseLong(createdId)))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_index WHERE record_id=? AND record_status='ACTIVE' "
                        + "AND value_kind='STRING'", Long.class, Long.parseLong(createdId))).isEqualTo(1L);
        var createdDetail = client.get(runtimeRoot + "/records/" + createdId);
        assertOk(createdDetail);
        assertThat(text(createdDetail.body(), "/data/values/0/value"))
                .isEqualTo("Created through runtime command");

        var missingLifecyclePermissions = new RuntimeSession(
                memberId, numericSystemId, memberId, tenantId,
                Set.of("module.work_order.view", "module.work_order.update")
        );
        assertPermissionDenied(() -> recordRuntimeService.archive(
                missingLifecyclePermissions, "work_order", editableNumericId,
                new RecordRuntimeViews.VersionCommandRequest(8), key(), "permission-test", "permission-test"));
        assertPermissionDenied(() -> recordRuntimeService.unarchive(
                missingLifecyclePermissions, "work_order", editableNumericId,
                new RecordRuntimeViews.VersionCommandRequest(8), key(), "permission-test", "permission-test"));
        assertPermissionDenied(() -> recordRuntimeService.trash(
                missingLifecyclePermissions, "work_order", editableNumericId,
                new RecordRuntimeViews.VersionCommandRequest(8), key(), "permission-test", "permission-test"));
        assertPermissionDenied(() -> recordRuntimeService.restoreFromTrash(
                missingLifecyclePermissions, "work_order", Long.parseLong(discardId),
                new RecordRuntimeViews.VersionCommandRequest(1), key(), "permission-test", "permission-test"));
        assertPermissionDenied(() -> recordRuntimeService.recover(
                missingLifecyclePermissions, "work_order", expiryNumericId,
                new RecordRuntimeViews.VersionCommandRequest(3), key(), "permission-test", "permission-test"));
        assertThatThrownBy(() -> recordRuntimeService.query(missingLifecyclePermissions, "work_order",
                json(archivedQuery))).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("QUERY_SCOPE_FORBIDDEN"));
    }

    private static void assertPermissionDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.code()).isEqualTo("PERMISSION_DENIED"));
    }

    private void insertRuntimeRecord(
            long recordId,
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleId,
            long fieldId,
            long memberId,
            String recordNo,
            String title
    ) {
        jdbcTemplate.update("INSERT INTO un_module_record "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "record_no,title,status,owner_member_id,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE',?,NOW(3),?,NOW(3),?,0)",
                recordId, systemId, tenantId, recordId, schemaVersionId, moduleId, moduleId,
                recordNo, title, memberId, memberId, memberId);
        jdbcTemplate.update("INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_type,ordinal,string_value,display_value,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'TEXT',0,?,?,NOW(3),?,NOW(3),?,0)",
                recordId + 10, systemId, tenantId, recordId, schemaVersionId, moduleId, moduleId,
                fieldId, fieldId, title, title, memberId, memberId);
    }

    private TestClient login() throws Exception {
        return login(ROOT_USERNAME, ROOT_PASSWORD);
    }

    private TestClient login(String username, String password) throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of(
                "account", username, "password", password)), Map.of()));
        return client;
    }

    private String componentBody(String parentId, String fieldId, String componentKey, String type, String revision)
            throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("parentComponentId", parentId);
        body.put("fieldId", fieldId);
        body.put("key", componentKey);
        body.put("type", type);
        body.put("sortOrder", 0);
        body.put("gridRow", 0);
        body.put("gridColumn", 0);
        body.put("gridSpan", 12);
        body.put("properties", Map.of("label", componentKey));
        body.put("draftRevision", revision);
        return json(body);
    }

    private String updateComponentBody(String parentId, String fieldId, String componentKey, String type,
                                       String version, String revision) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("parentComponentId", parentId);
        body.put("fieldId", fieldId);
        body.put("key", componentKey);
        body.put("type", type);
        body.put("sortOrder", 0);
        body.put("gridRow", 0);
        body.put("gridColumn", 0);
        body.put("gridSpan", 12);
        body.put("properties", Map.of("label", componentKey));
        body.put("version", version);
        body.put("draftRevision", revision);
        return json(body);
    }

    private String fieldBody() throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("dictionaryId", null);
        body.put("targetModuleId", null);
        body.put("code", "title");
        body.put("name", "Title");
        body.put("type", "TEXT");
        body.put("sortOrder", 0);
        body.put("required", true);
        body.put("hidden", false);
        body.put("readonly", false);
        body.put("searchable", true);
        body.put("filterable", true);
        body.put("showInList", true);
        body.put("showInDetail", true);
        body.put("indexMode", "NONE");
        body.put("status", "ENABLED");
        body.put("properties", Map.of("maxLength", 200));
        body.put("draftRevision", "2");
        return json(body);
    }

    private static String itemId(JsonNode items, String field, String value) {
        return item(items, field, value).path("id").asText();
    }

    private static JsonNode item(JsonNode items, String field, String value) {
        for (var item : items) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("No item with " + field + "=" + value + " in " + items);
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status()).withFailMessage("Expected 200, got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status()).withFailMessage("Expected 201, got %s: %s", response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status()).withFailMessage("Expected %s, got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private record TestResponse(int status, JsonNode body) { }
    private record BinaryResponse(int status, byte[] content) { }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        BinaryResponse download(String path) throws Exception {
            var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                            .header("X-Request-ID", "vs3-test-download-" + key()).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(response.statusCode(), response.body());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }

        TestResponse deleteWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("DELETE", path, body, headers, true);
        }

        private TestResponse request(String method, String path, String body, Map<String, String> headers, boolean csrf)
                throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) {
                builder.header("X-CSRF-Token", csrf());
            }
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(builder.header("X-Request-ID", "vs3-test-" + key()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue).findFirst()
                    .orElseThrow(() -> new IllegalStateException("CSRF cookie is missing"));
        }
    }
}
