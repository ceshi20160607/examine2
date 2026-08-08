package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.plat.manage.service.AuthzEpochService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "import_job_root";
    private static final String ROOT_PASSWORD = "Import-Job-Root-Password-84!";
    private static final String MODULE_CODE = "import_item";
    private static volatile String restartSystemId;
    private static volatile String restartRecordRoot;
    private static volatile String restartPrintId;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_import_job_test")
            .withUsername("examine_import_job_test")
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
        registry.add("examine.bootstrap.root.display-name", () -> "Import Job Test Root");
        registry.add("examine.jobs.import.poll-delay-ms", () -> 25);
        registry.add("examine.jobs.export.poll-delay-ms", () -> 25);
        registry.add("examine.jobs.print.poll-delay-ms", () -> 25);
    }

    @LocalServerPort private int port;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ResultNotificationFacade notifications;
    @Autowired private AuthzEpochService authzEpochService;

    @Test
    @Order(1)
    void previewsCommitsAndConditionallyRollsBackThroughRealMysqlAndRedis() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));
        var createdSystem = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "import_job_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Import Job Journey", "description", "Durable module import",
                "tenantMode", "MULTI")), Map.of("Idempotency-Key", key()));
        assertOk(createdSystem);
        var systemId = text(createdSystem.body(), "/data/id");
        var switched = client.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        var tenantId = text(switched.body(), "/data/context/tenantId");
        var memberId = text(switched.body(), "/data/context/memberId");

        var schemaVersionId = publishModule(client, systemId);
        var refreshed = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshed);
        assertThat(refreshed.body().at("/data/context/permissions")).extracting(JsonNode::asText)
                .contains("module." + MODULE_CODE + ".import", "module." + MODULE_CODE + ".export",
                        "module." + MODULE_CODE + ".print",
                        "event.message.access", "event.template.manage",
                        "module." + MODULE_CODE + ".create",
                        "module." + MODULE_CODE + ".update", "module." + MODULE_CODE + ".delete");

        var messageTemplateRoot = "/api/v1/systems/" + systemId + "/admin/event/message-templates";
        var messageTemplates = client.get(messageTemplateRoot);
        assertOk(messageTemplates);
        assertThat(messageTemplates.body().at("/data").findValuesAsText("templateCode"))
                .containsExactlyInAnyOrder("FLOW_APPROVAL_DEADLINE_REMINDER",
                        "MODULE_IMPORT_SUCCEEDED", "MODULE_IMPORT_FAILED",
                        "MODULE_EXPORT_SUCCEEDED", "MODULE_EXPORT_FAILED",
                        "MODULE_PRINT_SUCCEEDED", "MODULE_PRINT_FAILED",
                        "RECORD_COMMENT_MENTIONED");
        var exportMessageTemplate = messageTemplates.body().at("/data").findParents("templateCode").stream()
                .filter(item -> "MODULE_EXPORT_SUCCEEDED".equals(item.path("templateCode").asText()))
                .findFirst().orElseThrow();
        assertError(client.putWithCsrf(messageTemplateRoot + "/MODULE_EXPORT_SUCCEEDED", json(Map.of(
                "expectedVersion", exportMessageTemplate.path("version").asLong(),
                "name", "Export result", "enabled", true,
                "titleTemplate", "Export {unknown}", "bodyTemplate", "{moduleCode} {rows}",
                "channels", List.of("INBOX")))), 422, "MESSAGE_TEMPLATE_INVALID");
        var updatedExportMessage = client.putWithCsrf(messageTemplateRoot + "/MODULE_EXPORT_SUCCEEDED",
                json(Map.of("expectedVersion", exportMessageTemplate.path("version").asLong(),
                        "name", "Export result", "enabled", true,
                        "titleTemplate", "Export ready", "bodyTemplate", "{moduleCode} exported {rows} rows.",
                        "channels", List.of("INBOX"))));
        assertOk(updatedExportMessage);
        var publishedExportMessage = client.postWithCsrf(
                messageTemplateRoot + "/MODULE_EXPORT_SUCCEEDED:publish",
                json(Map.of("expectedVersion", updatedExportMessage.body().at("/data/version").asLong())), Map.of());
        assertOk(publishedExportMessage);
        assertThat(publishedExportMessage.body().at("/data/publishedVersion").asLong()).isEqualTo(2);

        var preferenceRoot = "/api/v1/systems/" + systemId
                + "/event/delivery-preferences";
        var beforePreferenceReads = eventReadState();
        var initialPreferences = client.get(preferenceRoot);
        assertOk(initialPreferences);
        assertThat(initialPreferences.body().at("/data").findValuesAsText("templateCode"))
                .containsExactly(
                        "FLOW_APPROVAL_DEADLINE_REMINDER",
                        "MODULE_EXPORT_FAILED",
                        "MODULE_EXPORT_SUCCEEDED",
                        "MODULE_IMPORT_FAILED",
                        "MODULE_IMPORT_SUCCEEDED",
                        "MODULE_PRINT_FAILED",
                        "MODULE_PRINT_SUCCEEDED",
                        "RECORD_COMMENT_MENTIONED"
                );
        assertPreference(
                findPreference(initialPreferences, "MODULE_EXPORT_SUCCEEDED"),
                "MODULE_EXPORT_SUCCEEDED",
                "MODULE_EXPORT_SUCCEEDED",
                "Export result",
                true,
                0,
                false
        );
        var repeatedPreferences = client.get(preferenceRoot);
        assertOk(repeatedPreferences);
        assertThat(repeatedPreferences.body().at("/data"))
                .isEqualTo(initialPreferences.body().at("/data"));
        assertThat(eventReadState()).isEqualTo(beforePreferenceReads);

        var moduleId = jdbc.queryForObject("SELECT id FROM un_module_definition WHERE system_id=? AND module_code=?",
                String.class, systemId, MODULE_CODE);
        var printTemplateRoot = "/api/v1/systems/" + systemId
                + "/admin/config/modules/" + moduleId + "/print-templates";
        var createdPrintTemplate = client.postWithCsrf(printTemplateRoot, json(Map.ofEntries(
                Map.entry("code", "item_sheet"), Map.entry("name", "Item sheet"), Map.entry("status", "ENABLED"),
                Map.entry("paperSize", "A4"), Map.entry("orientation", "PORTRAIT"),
                Map.entry("header", "Controlled copy · {recordNo}"), Map.entry("title", "Item {recordNo}"),
                Map.entry("fieldCodes", List.of("external_key", "item_name", "signature")),
                Map.entry("footer", "Import journey snapshot"),
                Map.entry("positionedElements", List.of(Map.of(
                        "kind", "SIGNATURE", "label", "Applicant signature", "fieldCode", "signature",
                        "xMm", 20, "yMm", 220, "widthMm", 50, "heightMm", 24))),
                Map.entry("codeBlocks", List.of(Map.of(
                        "kind", "QR", "label", "Record QR", "fieldCode", "{recordNo}"))))),
                Map.of("Idempotency-Key", key()));
        assertOk(createdPrintTemplate);
        var printTemplateId = text(createdPrintTemplate.body(), "/data/templateId");
        var publishedPrintTemplate = client.postWithCsrf(printTemplateRoot + "/" + printTemplateId + ":publish",
                json(Map.of("expectedVersion", createdPrintTemplate.body().at("/data/version").asLong())),
                Map.of("Idempotency-Key", key()));
        assertOk(publishedPrintTemplate);
        assertThat(text(publishedPrintTemplate.body(), "/data/publishedSchemaVersionId"))
                .isEqualTo(schemaVersionId);

        var flowRoot = "/api/v1/systems/" + systemId + "/flow";
        var importedFlow = client.postWithCsrf(flowRoot + "/definitions", json(Map.of(
                "name", "Imported item approval",
                "approverId", memberId,
                "triggerBinding", Map.of(
                        "moduleCode", MODULE_CODE,
                        "event", "IMPORT_COMPLETED",
                        "priority", 100,
                        "exclusive", true,
                        "conditions", List.of(Map.of(
                                "fieldCode", "item_name",
                                "operator", "EQ",
                                "value", "Original name"))))), Map.of());
        assertCreated(importedFlow);
        var importFlowDefinitionId = text(importedFlow.body(), "/data/definitionId");
        assertOk(client.postWithCsrf(flowRoot + "/definitions/" + importFlowDefinitionId + ":publish",
                "{}", Map.of()));

        var root = "/api/v1/systems/" + systemId + "/runtime/modules/" + MODULE_CODE;
        var anonymous = new TestClient();
        assertError(anonymous.get(root + "/imports/template"), 401, "AUTH_REQUIRED");
        assertError(anonymous.get(root + "/imports?page=1&size=20"), 401, "AUTH_REQUIRED");
        assertThat(anonymous.download(root + "/imports/template.xlsx").status()).isEqualTo(401);
        var template = client.get(root + "/imports/template");
        assertOk(template);
        assertThat(text(template.body(), "/data/schemaVersionId")).isEqualTo(schemaVersionId);
        assertThat(template.body().at("/data/fields").findValuesAsText("fieldCode"))
                .containsExactly("external_key", "item_name");
        assertThat(template.body().at("/data/fields/0/unique").asBoolean()).isTrue();
        assertThat(template.body().at("/data/excludedFields").findValuesAsText("fieldCode"))
                .contains("created_at", "signature");
        assertThat(template.body().at("/data/excludedFields").findValuesAsText("reason"))
                .contains("FILE_OWNED");

        var duplicate = preview(client, root, "UPSERT", "external_key", List.of(
                row("https://example.test/duplicate", "Duplicate A"),
                row("https://example.test/duplicate", "Duplicate B")));
        var duplicateBatchId = text(duplicate.body(), "/data/batchId");
        var invalid = await(client, root, duplicateBatchId, "INVALID");
        assertThat(invalid.body().at("/data/failedRows").asInt()).isEqualTo(2);
        assertThat(invalid.body().at("/data/rows").findValuesAsText("errorCode"))
                .containsOnly("IMPORT_DUPLICATE_IN_REQUEST");
        assertThat(recordCount(systemId, tenantId)).isZero();
        assertThat(flowInstanceCount(systemId, tenantId, importFlowDefinitionId)).isZero();

        var errors = client.download(root + "/imports/" + duplicateBatchId + "/errors.xlsx");
        assertThat(errors.status()).isEqualTo(200);
        assertThat(errors.header("Content-Type")).contains("spreadsheetml.sheet");
        assertThat(errors.header("Cache-Control")).contains("no-store");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(errors.body()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("_error_code");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("IMPORT_DUPLICATE_IN_REQUEST");
        }

        var duplicateId = Long.parseLong(duplicateBatchId);
        var ownerMemberId = jdbc.queryForObject(
                "SELECT requested_by_member_id FROM un_module_import_batch WHERE id=?", Long.class, duplicateId);
        try {
            assertThat(jdbc.update("UPDATE un_module_import_batch SET requested_by_member_id=? WHERE id=?",
                    ownerMemberId + 1L, duplicateId)).isOne();
            var isolatedHistory = client.get(root + "/imports?page=1&size=20");
            assertOk(isolatedHistory);
            assertThat(isolatedHistory.body().at("/data/items").findValuesAsText("batchId"))
                    .doesNotContain(duplicateBatchId);
            assertError(client.get(root + "/imports/" + duplicateBatchId),
                    404, "IMPORT_BATCH_NOT_FOUND");
            assertThat(client.download(root + "/imports/" + duplicateBatchId + "/errors.xlsx").status())
                    .isEqualTo(404);
        } finally {
            jdbc.update("UPDATE un_module_import_batch SET requested_by_member_id=? WHERE id=?",
                    ownerMemberId, duplicateId);
        }
        var otherModuleRoot = "/api/v1/systems/" + systemId + "/runtime/modules/other_import_item";
        assertError(client.get(otherModuleRoot + "/imports/" + duplicateBatchId),
                404, "IMPORT_BATCH_NOT_FOUND");
        assertThat(client.download(otherModuleRoot + "/imports/" + duplicateBatchId + "/errors.xlsx").status())
                .isEqualTo(404);

        var liveWorkbook = client.download(root + "/imports/template.xlsx");
        assertThat(liveWorkbook.status()).isEqualTo(200);
        assertThat(liveWorkbook.header("Content-Disposition")).contains("import_item-import-template.xlsx");
        var initial = client.postMultipart(root + "/imports:preview-xlsx", "items.xlsx",
                addWorkbookRow(liveWorkbook.body(), "https://example.test/existing", "Original name"),
                Map.of("mode", "NEW"));
        assertAccepted(initial);
        var initialBatchId = text(initial.body(), "/data/batchId");
        var readyInitial = await(client, root, initialBatchId, "READY");
        assertThat(readyInitial.body().at("/data/newRows").asInt()).isOne();
        assertThat(redis.opsForStream().size(RedisJobSignalPublisher.STREAM_KEY)).isGreaterThanOrEqualTo(2);
        assertAccepted(client.postWithCsrf(root + "/imports/" + initialBatchId + ":commit", "{}", Map.of()));
        var committedInitial = await(client, root, initialBatchId, "COMMITTED");
        var existingRecordId = text(committedInitial.body(), "/data/rows/0/targetRecordId");
        assertThat(recordCount(systemId, tenantId)).isOne();
        assertThat(flowInstanceCount(systemId, tenantId, importFlowDefinitionId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_flow_trigger_dispatch WHERE system_id=? "
                        + "AND tenant_id=? AND event_key=?", Long.class, systemId, tenantId,
                "record:" + systemId + ":" + tenantId + ":" + MODULE_CODE + ":" + existingRecordId
                        + ":1:IMPORT_COMPLETED")).isOne();

        var recordRoot = root + "/records/" + existingRecordId;
        var signatureUpload = client.postMultipart("/api/v1/systems/" + systemId + "/files",
                "signature.png", "image/png", java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="),
                Map.of());
        assertCreated(signatureUpload);
        var signatureFileId = text(signatureUpload.body(), "/data/id");
        var importedRecord = client.get(recordRoot);
        assertOk(importedRecord);
        var boundSignature = client.putWithCsrf(recordRoot, json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Original name",
                "expectedVersion", importedRecord.body().at("/data/version").asLong(),
                "values", Map.of("external_key", "https://example.test/existing",
                        "item_name", "Original name", "signature", List.of(signatureFileId)))),
                Map.of("Idempotency-Key", key()));
        assertOk(boundSignature);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_file_reference WHERE system_id=? AND tenant_id=? "
                        + "AND file_id=? AND target_type='MODULE_RECORD_FIELD' AND target_id LIKE CONCAT(?,':%')",
                Long.class, systemId, tenantId, signatureFileId, existingRecordId)).isOne();
        assertError(anonymous.get(recordRoot + "/print-templates"), 401, "AUTH_REQUIRED");
        var runtimePrintTemplates = client.get(recordRoot + "/print-templates");
        assertOk(runtimePrintTemplates);
        assertThat(runtimePrintTemplates.body().at("/data").findValuesAsText("code")).contains("item_sheet");
        var printPreview = client.postWithCsrf(recordRoot + "/print-preview",
                json(Map.of("templateCode", "item_sheet")), Map.of());
        assertOk(printPreview);
        assertThat(text(printPreview.body(), "/data/html"))
                .contains("Controlled copy").contains("Item ").contains("Original name")
                .contains("https://example.test/existing").contains("data:image/png;base64,")
                .contains("data-kind=\"SIGNATURE\"");
        var printKey = key();
        var printRequest = json(Map.of("templateCode", "item_sheet",
                "expectedRecordVersion", boundSignature.body().at("/data/version").asLong()));
        var createdPrint = client.postWithCsrf(recordRoot + "/prints", printRequest,
                Map.of("Idempotency-Key", printKey));
        assertAccepted(createdPrint);
        var printId = text(createdPrint.body(), "/data/printId");
        var completedPrint = awaitPrint(client, recordRoot, printId, "SUCCEEDED");
        assertThat(completedPrint.body().at("/data/resultSize").asLong()).isGreaterThan(1000);
        var printPdf = client.download(recordRoot + "/prints/" + printId + "/result.pdf");
        assertThat(printPdf.status()).isEqualTo(200);
        assertThat(printPdf.header("Content-Type")).contains("application/pdf");
        assertThat(printPdf.header("Cache-Control")).contains("no-store");
        assertThat(new String(printPdf.body(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        var exactPrintReplay = client.postWithCsrf(recordRoot + "/prints", printRequest,
                Map.of("Idempotency-Key", printKey));
        assertAccepted(exactPrintReplay);
        assertThat(text(exactPrintReplay.body(), "/data/printId")).isEqualTo(printId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_module_print_task WHERE id=?", Long.class, printId))
                .isOne();
        assertThat(deliveryCount(systemId, tenantId, memberId,
                "job-result:import:" + initialBatchId + ":SUCCEEDED")).isOne();
        assertThat(deliveryCount(systemId, tenantId, memberId,
                "job-result:print:" + printId + ":SUCCEEDED")).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_event_message WHERE system_id=? AND tenant_id=? "
                + "AND recipient_member_id=? AND template_code IN ('MODULE_IMPORT_SUCCEEDED',"
                + "'MODULE_PRINT_SUCCEEDED')", Long.class, systemId, tenantId, memberId)).isEqualTo(2);
        var printHistory = client.get(recordRoot + "/prints?page=1&size=20");
        assertOk(printHistory);
        assertThat(printHistory.body().at("/data/items").findValuesAsText("printId")).contains(printId);
        var printOwner = jdbc.queryForObject("SELECT requested_by_member_id FROM un_module_print_task WHERE id=?",
                Long.class, printId);
        try {
            assertThat(jdbc.update("UPDATE un_module_print_task SET requested_by_member_id=? WHERE id=?",
                    printOwner + 1L, printId)).isOne();
            var isolatedPrints = client.get(recordRoot + "/prints?page=1&size=20");
            assertOk(isolatedPrints);
            assertThat(isolatedPrints.body().at("/data/items").findValuesAsText("printId"))
                    .doesNotContain(printId);
            assertError(client.get(recordRoot + "/prints/" + printId), 404, "PRINT_NOT_FOUND");
            assertThat(client.download(recordRoot + "/prints/" + printId + "/result.pdf").status())
                    .isEqualTo(404);
        } finally {
            jdbc.update("UPDATE un_module_print_task SET requested_by_member_id=? WHERE id=?", printOwner, printId);
        }
        var otherPrintRoot = "/api/v1/systems/" + systemId
                + "/runtime/modules/other_import_item/records/" + existingRecordId;
        assertError(client.get(otherPrintRoot + "/prints/" + printId), 404, "PRINT_NOT_FOUND");
        assertThat(client.download(otherPrintRoot + "/prints/" + printId + "/result.pdf").status())
                .isEqualTo(404);
        assertError(client.get(root + "/records/1/prints/" + printId), 404, "PRINT_NOT_FOUND");
        assertAccepted(client.postWithCsrf(root + "/imports/" + initialBatchId + ":commit", "{}", Map.of()));
        assertThat(flowInstanceCount(systemId, tenantId, importFlowDefinitionId)).isOne();
        assertThat(deliveryCount(systemId, tenantId, memberId,
                "job-result:import:" + initialBatchId + ":SUCCEEDED")).isOne();
        var history = client.get(root + "/imports?page=1&size=20");
        assertOk(history);
        assertThat(history.body().at("/data/total").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(history.body().at("/data/items").findValuesAsText("batchId"))
                .contains(initialBatchId, duplicateBatchId);

        var mixed = preview(client, root, "UPSERT", "external_key", List.of(
                row("https://example.test/existing", "Imported update"),
                row("https://example.test/new", "Imported new")));
        var mixedBatchId = text(mixed.body(), "/data/batchId");
        var readyMixed = await(client, root, mixedBatchId, "READY");
        assertThat(readyMixed.body().at("/data/newRows").asInt()).isOne();
        assertThat(readyMixed.body().at("/data/updateRows").asInt()).isOne();
        assertAccepted(client.postWithCsrf(root + "/imports/" + mixedBatchId + ":commit", "{}", Map.of()));
        var committedMixed = await(client, root, mixedBatchId, "COMMITTED");
        var commitJobId = text(committedMixed.body(), "/data/commitJobId");
        var exactReplay = client.postWithCsrf(root + "/imports/" + mixedBatchId + ":commit", "{}", Map.of());
        assertAccepted(exactReplay);
        assertThat(text(exactReplay.body(), "/data/commitJobId")).isEqualTo(commitJobId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_sys_job WHERE owner_id=? AND job_type='MODULE_IMPORT_COMMIT'",
                Long.class, mixedBatchId)).isOne();
        assertThat(recordCount(systemId, tenantId)).isEqualTo(2);
        assertThat(fieldDisplay(client.get(root + "/records/" + existingRecordId).body(), "item_name"))
                .isEqualTo("Imported update");
        assertThat(jdbc.queryForObject("SELECT snapshot_json FROM un_module_print_task WHERE id=?", String.class,
                printId)).contains("Original name").doesNotContain("Imported update");
        assertThat(client.download(recordRoot + "/prints/" + printId + "/result.pdf").status()).isEqualTo(200);

        var currentVersion = client.get(root + "/records/" + existingRecordId).body().at("/data/version").asLong();
        var manualEdit = client.postWithCsrf(root + "/records:batch-edit", json(Map.of(
                "items", List.of(Map.of("recordId", existingRecordId, "expectedVersion", currentVersion)),
                "changes", List.of(Map.of("fieldCode", "item_name", "operation", "SET", "value", "Manual edit"))
        )), Map.of("Idempotency-Key", key()));
        assertOk(manualEdit);
        assertAccepted(client.postWithCsrf(root + "/imports/" + mixedBatchId + ":rollback", "{}", Map.of()));
        await(client, root, mixedBatchId, "FAILED");
        assertThat(recordCount(systemId, tenantId)).isEqualTo(2);
        assertThat(fieldDisplay(client.get(root + "/records/" + existingRecordId).body(), "item_name"))
                .isEqualTo("Manual edit");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_module_import_row WHERE batch_id=? "
                + "AND row_status='ROLLED_BACK'", Long.class, mixedBatchId)).isZero();

        var rollbackable = preview(client, root, "NEW", null,
                List.of(row("https://example.test/rollback", "Rollback me")));
        var rollbackBatchId = text(rollbackable.body(), "/data/batchId");
        await(client, root, rollbackBatchId, "READY");
        assertAccepted(client.postWithCsrf(root + "/imports/" + rollbackBatchId + ":commit", "{}", Map.of()));
        var committedRollbackable = await(client, root, rollbackBatchId, "COMMITTED");
        var rollbackRecordId = text(committedRollbackable.body(), "/data/rows/0/targetRecordId");
        assertAccepted(client.postWithCsrf(root + "/imports/" + rollbackBatchId + ":rollback", "{}", Map.of()));
        await(client, root, rollbackBatchId, "ROLLED_BACK");
        assertThat(jdbc.queryForObject("SELECT status FROM un_module_record WHERE system_id=? AND tenant_id=? "
                + "AND record_id=?", String.class, systemId, tenantId, rollbackRecordId)).isEqualTo("TRASHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_audit_operation WHERE system_id=? AND tenant_id=? "
                + "AND aggregate_type='MODULE_IMPORT_BATCH' AND result='SUCCESS'", Long.class,
                systemId, tenantId)).isGreaterThanOrEqualTo(7);

        var exportQuery = objectMapper.createObjectNode();
        exportQuery.put("schemaVersionId", schemaVersionId);
        exportQuery.put("page", 1);
        exportQuery.put("size", 20);
        exportQuery.put("recordScope", "active");
        exportQuery.putNull("q");
        var filter = exportQuery.putObject("filter");
        filter.put("kind", "PREDICATE");
        filter.put("fieldCode", "item_name");
        filter.put("operator", "EQ");
        filter.put("value", "Manual edit");
        exportQuery.putArray("sort");
        exportQuery.putArray("columns").add("external_key").add("item_name");
        exportQuery.putNull("viewId");
        var exportRoot = root + "/exports";
        assertError(new TestClient().get(exportRoot + "?page=1&size=20"), 401, "AUTH_REQUIRED");

        var disabledPreference = client.putWithCsrf(
                preferenceRoot + "/MODULE_EXPORT_SUCCEEDED",
                json(Map.of("enabled", false, "expectedVersion", 0))
        );
        assertOk(disabledPreference);
        assertPreference(
                disabledPreference.body().at("/data"),
                "MODULE_EXPORT_SUCCEEDED",
                "MODULE_EXPORT_SUCCEEDED",
                "Export result",
                false,
                1,
                true
        );
        assertThat(preferenceCount(
                systemId, tenantId, memberId,
                "MODULE_EXPORT_SUCCEEDED", false, 1)).isOne();

        var exportRequest = json(Map.of(
                "query", exportQuery,
                "fieldCodes", List.of("external_key", "item_name")));
        var skippedExport = client.postWithCsrf(exportRoot, exportRequest, Map.of());
        assertAccepted(skippedExport);
        var skippedExportId = text(skippedExport.body(), "/data/exportId");
        var completedSkippedExport = awaitExport(
                client, exportRoot, skippedExportId, "SUCCEEDED");
        assertThat(completedSkippedExport.body().at("/data/totalRows").asInt()).isOne();
        assertThat(completedSkippedExport.body().at("/data/processedRows").asInt()).isOne();
        var exportWorkbook = client.download(
                exportRoot + "/" + skippedExportId + "/result.xlsx");
        assertThat(exportWorkbook.status()).isEqualTo(200);
        assertThat(exportWorkbook.header("Cache-Control")).contains("no-store");
        assertThat(exportWorkbook.header("Content-Disposition")).contains(MODULE_CODE + "-export-");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exportWorkbook.body()))) {
            var sheet = workbook.getSheet("export");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("external_key");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("item_name");
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Manual edit");
        }
        var skippedDedupeKey = "job-result:export:"
                + skippedExportId + ":SUCCEEDED";
        var skippedDelivery = awaitDelivery(
                systemId, tenantId, memberId, skippedDedupeKey, "SKIPPED");
        assertThat(skippedDelivery.attemptCount()).isOne();
        assertThat(skippedDelivery.messageId()).isNull();
        assertThat(skippedDelivery.failureCode())
                .isEqualTo("MESSAGE_DELIVERY_DISABLED_BY_RECIPIENT");
        assertThat(skippedDelivery.failureMessage())
                .isEqualTo("Recipient disabled this notification");
        assertThat(deliveryRowCount(
                systemId, tenantId, memberId, skippedDedupeKey)).isOne();
        assertThat(exportMessageCount(
                systemId, tenantId, memberId, skippedExportId)).isZero();

        var enabledPreference = client.putWithCsrf(
                preferenceRoot + "/MODULE_EXPORT_SUCCEEDED",
                json(Map.of("enabled", true, "expectedVersion", 1))
        );
        assertOk(enabledPreference);
        assertPreference(
                enabledPreference.body().at("/data"),
                "MODULE_EXPORT_SUCCEEDED",
                "MODULE_EXPORT_SUCCEEDED",
                "Export result",
                true,
                2,
                true
        );
        var skippedReplay = notifications.dispatch(new ResultNotificationFacade.Command(
                Long.parseLong(systemId),
                Long.parseLong(tenantId),
                Long.parseLong(memberId),
                Long.parseLong(memberId),
                "MODULE_EXPORT_SUCCEEDED",
                Map.of("moduleCode", MODULE_CODE, "rows", "1"),
                new AggregateRef("MODULE_EXPORT_TASK", skippedExportId),
                "/systems/" + systemId + "/workbench?module=" + MODULE_CODE
                        + "&panel=export&task=" + skippedExportId,
                skippedDedupeKey
        ));
        assertThat(skippedReplay.deliveryLogId()).isEqualTo(skippedDelivery.id());
        assertThat(skippedReplay.messageId()).isNull();
        assertThat(skippedReplay.status()).isEqualTo("SKIPPED");
        assertThat(skippedReplay.replay()).isTrue();
        assertThat(deliveryRowCount(
                systemId, tenantId, memberId, skippedDedupeKey)).isOne();
        assertThat(exportMessageCount(
                systemId, tenantId, memberId, skippedExportId)).isZero();
        assertThat(deliveryFact(
                systemId, tenantId, memberId, skippedDedupeKey))
                .isEqualTo(skippedDelivery);

        // A non-transactional one-shot gate makes the first inbox INSERT fail
        // while allowing the durable compensation attempt to recover on the
        // same delivery/message id. The trigger is scoped to this exact target.
        var retryExportId = "batch102-" + Long.toUnsignedString(System.nanoTime(), 36);
        var retryDedupeKey = "job-result:export:" + retryExportId + ":SUCCEEDED";
        jdbc.execute("DROP TRIGGER IF EXISTS batch102_event_retry_once");
        jdbc.execute("DROP TABLE IF EXISTS batch102_event_retry_gate");
        try {
            jdbc.execute("CREATE TABLE batch102_event_retry_gate (target_id VARCHAR(64) PRIMARY KEY, "
                    + "remaining INT NOT NULL) ENGINE=MyISAM");
            jdbc.update("INSERT INTO batch102_event_retry_gate (target_id,remaining) VALUES (?,1)",
                    retryExportId);
            jdbc.execute("CREATE TRIGGER batch102_event_retry_once BEFORE INSERT ON un_event_message "
                    + "FOR EACH ROW BEGIN IF NEW.template_code='MODULE_EXPORT_SUCCEEDED' "
                    + "AND EXISTS (SELECT 1 FROM batch102_event_retry_gate WHERE target_id=NEW.target_id "
                    + "AND remaining>0) THEN UPDATE batch102_event_retry_gate SET remaining=remaining-1 "
                    + "WHERE target_id=NEW.target_id AND remaining>0; SIGNAL SQLSTATE '45000' "
                    + "SET MESSAGE_TEXT='batch102 temporary inbox failure'; END IF; END");

            var firstRetryReceipt = notifications.dispatch(new ResultNotificationFacade.Command(
                    Long.parseLong(systemId), Long.parseLong(tenantId), Long.parseLong(memberId),
                    Long.parseLong(memberId), "MODULE_EXPORT_SUCCEEDED",
                    Map.of("moduleCode", MODULE_CODE, "rows", "1"),
                    new AggregateRef("MODULE_EXPORT_TASK", retryExportId),
                    "/systems/" + systemId + "/workbench?module=" + MODULE_CODE
                            + "&panel=export&task=" + retryExportId,
                    retryDedupeKey));
            assertThat(firstRetryReceipt.status()).isEqualTo("FAILED");
            assertThat(firstRetryReceipt.messageId()).isNull();

            var recoveredDelivery = awaitDelivery(
                    systemId, tenantId, memberId, retryDedupeKey, "DELIVERED");
            assertThat(recoveredDelivery.id()).isEqualTo(firstRetryReceipt.deliveryLogId());
            assertThat(recoveredDelivery.messageId()).isEqualTo(firstRetryReceipt.deliveryLogId());
            assertThat(recoveredDelivery.attemptCount()).isEqualTo(2);
            assertThat(recoveredDelivery.failureCode()).isNull();
            assertThat(exportMessageCount(systemId, tenantId, memberId, retryExportId)).isOne();
            assertThat(awaitSucceededEventRetryJob(firstRetryReceipt.deliveryLogId())).isOne();
            assertThat(jdbc.queryForObject("SELECT remaining FROM batch102_event_retry_gate "
                    + "WHERE target_id=?", Integer.class, retryExportId)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS batch102_event_retry_once");
            jdbc.execute("DROP TABLE IF EXISTS batch102_event_retry_gate");
        }

        var deliveredExport = client.postWithCsrf(exportRoot, exportRequest, Map.of());
        assertAccepted(deliveredExport);
        var exportId = text(deliveredExport.body(), "/data/exportId");
        var completedExport = awaitExport(client, exportRoot, exportId, "SUCCEEDED");
        assertThat(completedExport.body().at("/data/totalRows").asInt()).isOne();
        assertThat(completedExport.body().at("/data/processedRows").asInt()).isOne();
        var deliveredDedupeKey = "job-result:export:" + exportId + ":SUCCEEDED";
        var delivered = awaitDelivery(
                systemId, tenantId, memberId, deliveredDedupeKey, "DELIVERED");
        assertThat(delivered.attemptCount()).isOne();
        assertThat(delivered.messageId()).isNotNull();
        assertThat(delivered.failureCode()).isNull();
        assertThat(deliveryRowCount(
                systemId, tenantId, memberId, deliveredDedupeKey)).isOne();
        assertThat(exportMessageCount(systemId, tenantId, memberId, exportId)).isOne();

        var exportHistory = client.get(exportRoot + "?page=1&size=20");
        assertOk(exportHistory);
        assertThat(exportHistory.body().at("/data/items").findValuesAsText("exportId"))
                .contains(skippedExportId, exportId);
        assertThat(deliveryCount(systemId, tenantId, memberId,
                "job-result:export:" + exportId + ":SUCCEEDED")).isOne();
        var inbox = client.get("/api/v1/systems/" + systemId + "/event/messages?status=ALL&page=1&size=100");
        assertOk(inbox);
        assertThat(inbox.body().at("/data/items").findValuesAsText("templateCode"))
                .contains("MODULE_IMPORT_SUCCEEDED", "MODULE_PRINT_SUCCEEDED", "MODULE_EXPORT_SUCCEEDED");
        assertThat(inbox.body().at("/data/items").findValuesAsText("targetPath"))
                .anyMatch(path -> path.contains("module=" + MODULE_CODE + "&panel=import"))
                .anyMatch(path -> path.contains("module=" + MODULE_CODE + "&panel=export&task=" + exportId))
                .anyMatch(path -> path.contains("module=" + MODULE_CODE + "&panel=print&task=" + printId));
        var exportMessageId = jdbc.queryForObject("SELECT id FROM un_event_message WHERE system_id=? "
                + "AND tenant_id=? AND recipient_member_id=? AND template_code='MODULE_EXPORT_SUCCEEDED' "
                + "AND target_id=?", Long.class, systemId, tenantId, memberId, exportId);
        try {
            assertThat(jdbc.update("UPDATE un_event_message SET recipient_member_id=? WHERE id=?",
                    Long.parseLong(memberId) + 1L, exportMessageId)).isOne();
            var isolatedInbox = client.get("/api/v1/systems/" + systemId
                    + "/event/messages?status=ALL&page=1&size=100");
            assertOk(isolatedInbox);
            assertThat(isolatedInbox.body().at("/data/items").findValuesAsText("id"))
                    .doesNotContain(Long.toString(exportMessageId));
        } finally {
            jdbc.update("UPDATE un_event_message SET recipient_member_id=? WHERE id=?", memberId, exportMessageId);
        }

        var exportNumericId = Long.parseLong(exportId);
        var exportOwner = jdbc.queryForObject(
                "SELECT requested_by_member_id FROM un_module_export_task WHERE id=?", Long.class, exportNumericId);
        try {
            assertThat(jdbc.update("UPDATE un_module_export_task SET requested_by_member_id=? WHERE id=?",
                    exportOwner + 1L, exportNumericId)).isOne();
            var isolatedExports = client.get(exportRoot + "?page=1&size=20");
            assertOk(isolatedExports);
            assertThat(isolatedExports.body().at("/data/items").findValuesAsText("exportId"))
                    .doesNotContain(exportId);
            assertError(client.get(exportRoot + "/" + exportId), 404, "EXPORT_NOT_FOUND");
            assertThat(client.download(exportRoot + "/" + exportId + "/result.xlsx").status()).isEqualTo(404);
        } finally {
            jdbc.update("UPDATE un_module_export_task SET requested_by_member_id=? WHERE id=?",
                    exportOwner, exportNumericId);
        }
        var otherExportRoot = "/api/v1/systems/" + systemId
                + "/runtime/modules/other_import_item/exports";
        assertError(client.get(otherExportRoot + "/" + exportId), 404, "EXPORT_NOT_FOUND");
        assertThat(client.download(otherExportRoot + "/" + exportId + "/result.xlsx").status()).isEqualTo(404);

        var isolatedTenant = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/tenants",
                json(Map.of(
                        "code", "event_preference_isolated_"
                                + Long.toUnsignedString(System.nanoTime(), 36),
                        "name", "Event preference isolated tenant"
                )),
                Map.of("Idempotency-Key", key())
        );
        assertOk(isolatedTenant);
        var isolatedTenantId = text(isolatedTenant.body(), "/data/id");
        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var isolatedSwitch = client.postWithCsrf(
                "/api/v1/context/tenants/" + isolatedTenantId + ":switch",
                "{}",
                Map.of()
        );
        assertOk(isolatedSwitch);
        assertThat(text(isolatedSwitch.body(), "/data/context/memberId"))
                .isEqualTo(memberId);
        var isolatedPreferences = client.get(preferenceRoot);
        assertOk(isolatedPreferences);
        assertPreference(
                findPreference(isolatedPreferences, "MODULE_EXPORT_SUCCEEDED"),
                "MODULE_EXPORT_SUCCEEDED",
                "MODULE_EXPORT_SUCCEEDED",
                "Export result",
                true,
                0,
                false
        );
        assertThat(preferenceRows(
                systemId, isolatedTenantId, memberId,
                "MODULE_EXPORT_SUCCEEDED")).isZero();
        var isolatedDisabled = client.putWithCsrf(
                preferenceRoot + "/MODULE_EXPORT_SUCCEEDED",
                json(Map.of("enabled", false, "expectedVersion", 0))
        );
        assertOk(isolatedDisabled);
        assertPreference(
                isolatedDisabled.body().at("/data"),
                "MODULE_EXPORT_SUCCEEDED",
                "MODULE_EXPORT_SUCCEEDED",
                "Export result",
                false,
                1,
                true
        );

        var switchedBack = client.postWithCsrf(
                "/api/v1/context/tenants/" + tenantId + ":switch",
                "{}",
                Map.of()
        );
        assertOk(switchedBack);
        var ownerPreferences = client.get(preferenceRoot);
        assertOk(ownerPreferences);
        assertPreference(
                findPreference(ownerPreferences, "MODULE_EXPORT_SUCCEEDED"),
                "MODULE_EXPORT_SUCCEEDED",
                "MODULE_EXPORT_SUCCEEDED",
                "Export result",
                true,
                2,
                true
        );
        assertThat(preferenceCount(
                systemId, tenantId, memberId,
                "MODULE_EXPORT_SUCCEEDED", true, 2)).isOne();
        assertThat(preferenceCount(
                systemId, isolatedTenantId, memberId,
                "MODULE_EXPORT_SUCCEEDED", false, 1)).isOne();

        var rootAccountId = jdbc.queryForObject(
                "SELECT id FROM un_plat_account WHERE username=?",
                Long.class,
                ROOT_USERNAME
        );
        var beforeDeniedPreferenceCalls = eventReadState();
        assertThat(jdbc.update(
                "UPDATE un_plat_permission SET status='DISABLED',"
                        + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                        + "WHERE system_id=? AND permission_code='event.message.access'",
                Long.parseLong(systemId)
        )).isOne();
        authzEpochService.bumpSystem(Long.parseLong(systemId), rootAccountId);
        try {
            var permissionRevoked = client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of());
            assertOk(permissionRevoked);
            assertThat(permissionRevoked.body().at("/data/context/permissions"))
                    .extracting(JsonNode::asText)
                    .doesNotContain("event.message.access");
            assertError(client.get(preferenceRoot), 403, "PERMISSION_DENIED");
            assertError(client.putWithCsrf(
                            preferenceRoot + "/MODULE_EXPORT_SUCCEEDED",
                            json(Map.of("enabled", false, "expectedVersion", 2))),
                    403,
                    "PERMISSION_DENIED");
        } finally {
            assertThat(jdbc.update(
                    "UPDATE un_plat_permission SET status='ACTIVE',"
                            + "updated_at=UTC_TIMESTAMP(3),version=version+1 "
                            + "WHERE system_id=? AND permission_code='event.message.access'",
                    Long.parseLong(systemId)
            )).isOne();
            authzEpochService.bumpSystem(Long.parseLong(systemId), rootAccountId);
            assertOk(client.postWithCsrf(
                    "/api/v1/auth/refresh", "{}", Map.of()));
        }
        assertThat(eventReadState()).isEqualTo(beforeDeniedPreferenceCalls);

        restartSystemId = systemId;
        restartRecordRoot = recordRoot;
        restartPrintId = printId;
    }

    @Test
    @Order(2)
    void readsImmutablePrintHistoryAndPdfAfterSpringRestart() throws Exception {
        assertThat(restartSystemId).as("ordered print journey system id").isNotBlank();
        assertThat(restartRecordRoot).as("ordered print journey record root").isNotBlank();
        assertThat(restartPrintId).as("ordered print journey print id").isNotBlank();

        var client = login();
        assertOk(client.postWithCsrf(
                "/api/v1/context/systems/" + restartSystemId + ":switch", "{}", Map.of()));
        var history = client.get(restartRecordRoot + "/prints?page=1&size=20");
        assertOk(history);
        assertThat(history.body().at("/data/items").findValuesAsText("printId"))
                .contains(restartPrintId);
        var stored = client.get(restartRecordRoot + "/prints/" + restartPrintId);
        assertOk(stored);
        assertThat(text(stored.body(), "/data/printId")).isEqualTo(restartPrintId);
        assertThat(text(stored.body(), "/data/status")).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject(
                "SELECT snapshot_json FROM un_module_print_task WHERE id=?",
                String.class, restartPrintId))
                .contains("Original name")
                .doesNotContain("Imported update");
        var pdf = client.download(restartRecordRoot + "/prints/" + restartPrintId + "/result.pdf");
        assertThat(pdf.status()).isEqualTo(200);
        assertThat(new String(pdf.body(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    private String publishModule(TestClient client, String systemId) throws Exception {
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";
        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "import_data", "name", "Import data", "description", "", "iconKey", "folder",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", "0")),
                Map.of("Idempotency-Key", key()));
        assertOk(group);
        var module = client.postWithCsrf(configRoot + "/modules", json(Map.ofEntries(
                Map.entry("groupId", text(group.body(), "/data/id")), Map.entry("code", MODULE_CODE),
                Map.entry("name", "Import item"), Map.entry("description", ""), Map.entry("iconKey", "table"),
                Map.entry("sortOrder", 0), Map.entry("status", "ENABLED"), Map.entry("allowComments", false),
                Map.entry("allowTeam", false), Map.entry("draftRevision", "1"))),
                Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");
        createField(client, configRoot, moduleId, "external_key", "External key", "URL", 0,
                true, false, "UNIQUE", Map.of(), "2");
        createField(client, configRoot, moduleId, "item_name", "Item name", "TEXT", 1,
                true, false, "FILTER", Map.of("maxLength", 100), "3");
        createField(client, configRoot, moduleId, "signature", "Signature", "SIGNATURE", 2,
                false, false, "NONE", Map.of("maxFiles", 1, "imageOnly", true,
                        "allowedExtensions", List.of("png")), "4");
        createField(client, configRoot, moduleId, "created_at", "Created at", "CREATED_AT", 3,
                false, true, "NONE", Map.of(), "5");
        var checked = client.postWithCsrf(configRoot + "/checks", json(Map.of("draftRevision", "6")), Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/blockerCount").asLong()).isZero();
        var config = client.get(configRoot);
        assertOk(config);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"), "draftRevision", "6",
                "configRootVersion", text(config.body(), "/data/version"), "reason", "Import journey")),
                Map.of("Idempotency-Key", key()));
        assertOk(published);
        return text(published.body(), "/data/version/id");
    }

    private void createField(TestClient client, String configRoot, String moduleId, String code, String name,
                             String type, int sortOrder, boolean required, boolean readonly, String indexMode,
                             Map<String, ?> properties, String revision) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("dictionaryId", null); body.put("targetModuleId", null); body.put("code", code);
        body.put("name", name); body.put("type", type); body.put("sortOrder", sortOrder);
        body.put("required", required); body.put("hidden", false); body.put("readonly", readonly);
        body.put("searchable", false); body.put("filterable", !"NONE".equals(indexMode));
        body.put("showInList", true); body.put("showInDetail", true); body.put("indexMode", indexMode);
        body.put("status", "ENABLED"); body.put("properties", properties); body.put("draftRevision", revision);
        assertOk(client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields", json(body),
                Map.of("Idempotency-Key", key())));
    }

    private TestResponse preview(TestClient client, String root, String mode, String match,
                                 List<Map<String, Object>> rows) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("mode", mode); body.put("matchFieldCode", match); body.put("rows", rows);
        var response = client.postWithCsrf(root + "/imports:preview", json(body), Map.of());
        assertAccepted(response);
        return response;
    }

    private TestResponse await(TestClient client, String root, String batchId, String target) throws Exception {
        TestResponse last = null;
        for (var attempt = 0; attempt < 200; attempt++) {
            last = client.get(root + "/imports/" + batchId);
            assertOk(last);
            if (target.equals(text(last.body(), "/data/status"))) return last;
            Thread.sleep(25);
        }
        throw new AssertionError("Import batch did not reach " + target + ": " + (last == null ? "none" : last.body()));
    }

    private TestResponse awaitExport(TestClient client, String root, String exportId, String target) throws Exception {
        TestResponse last = null;
        for (var attempt = 0; attempt < 200; attempt++) {
            last = client.get(root + "/" + exportId);
            assertOk(last);
            if (target.equals(text(last.body(), "/data/status"))) return last;
            Thread.sleep(25);
        }
        throw new AssertionError("Export did not reach " + target + ": "
                + (last == null ? "none" : last.body()));
    }

    private TestResponse awaitPrint(TestClient client, String recordRoot, String printId, String target)
            throws Exception {
        TestResponse last = null;
        for (var attempt = 0; attempt < 200; attempt++) {
            last = client.get(recordRoot + "/prints/" + printId);
            assertOk(last);
            if (target.equals(text(last.body(), "/data/status"))) return last;
            Thread.sleep(25);
        }
        throw new AssertionError("Print did not reach " + target + ": "
                + (last == null ? "none" : last.body()));
    }

    private long recordCount(String systemId, String tenantId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                + "AND logical_module_id=(SELECT id FROM un_module_definition WHERE system_id=? AND module_code=?) "
                + "AND status='ACTIVE'", Long.class, systemId, tenantId, systemId, MODULE_CODE);
    }

    private long deliveryCount(String systemId, String tenantId, String memberId, String dedupeKey) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM un_event_message_delivery_log WHERE system_id=? "
                        + "AND tenant_id=? AND recipient_member_id=? AND channel='INBOX' AND dedupe_key=? "
                        + "AND status='DELIVERED'", Long.class, systemId, tenantId, memberId, dedupeKey);
    }

    private JsonNode findPreference(TestResponse response, String templateCode) {
        var items = response.body().at("/data");
        assertThat(items.isArray()).isTrue();
        for (var item : items) {
            if (templateCode.equals(item.path("templateCode").asText())) return item;
        }
        throw new AssertionError(
                "Missing delivery preference " + templateCode + " in " + items);
    }

    private static void assertPreference(
            JsonNode item,
            String templateCode,
            String eventType,
            String name,
            boolean enabled,
            long version,
            boolean explicitlyStored
    ) {
        assertThat(fieldNames(item)).containsExactlyInAnyOrder(
                "templateCode", "eventType", "name", "channel",
                "enabled", "version", "updatedAt");
        assertThat(item.path("templateCode").asText()).isEqualTo(templateCode);
        assertThat(item.path("eventType").asText()).isEqualTo(eventType);
        assertThat(item.path("name").asText()).isEqualTo(name);
        assertThat(item.path("channel").asText()).isEqualTo("INBOX");
        assertThat(item.path("enabled").asBoolean()).isEqualTo(enabled);
        assertThat(item.path("version").asLong()).isEqualTo(version);
        if (explicitlyStored) {
            assertThat(item.path("updatedAt").isTextual()).isTrue();
        } else {
            assertThat(item.path("updatedAt").isNull()).isTrue();
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        var names = new java.util.LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private EventReadState eventReadState() {
        return jdbc.queryForObject(
                "SELECT "
                        + "(SELECT COUNT(*) FROM un_event_delivery_preference) "
                        + "AS preference_count,"
                        + "(SELECT COALESCE(SUM(version),0) "
                        + "FROM un_event_delivery_preference) AS preference_version_sum,"
                        + "(SELECT COUNT(*) FROM un_event_message_template) "
                        + "AS template_count,"
                        + "(SELECT COALESCE(SUM(version),0) "
                        + "FROM un_event_message_template) AS template_version_sum,"
                        + "(SELECT COUNT(*) FROM un_event_message_template_version) "
                        + "AS template_revision_count,"
                        + "(SELECT COALESCE(SUM(version_no),0) "
                        + "FROM un_event_message_template_version) AS template_revision_sum,"
                        + "(SELECT COUNT(*) FROM un_event_message) AS message_count,"
                        + "(SELECT COALESCE(SUM(version),0) FROM un_event_message) "
                        + "AS message_version_sum,"
                        + "(SELECT COUNT(*) FROM un_event_message_delivery_log) "
                        + "AS delivery_count,"
                        + "(SELECT COALESCE(SUM(attempt_count),0) "
                        + "FROM un_event_message_delivery_log) AS delivery_attempt_sum",
                (result, row) -> new EventReadState(
                        result.getLong("preference_count"),
                        result.getLong("preference_version_sum"),
                        result.getLong("template_count"),
                        result.getLong("template_version_sum"),
                        result.getLong("template_revision_count"),
                        result.getLong("template_revision_sum"),
                        result.getLong("message_count"),
                        result.getLong("message_version_sum"),
                        result.getLong("delivery_count"),
                        result.getLong("delivery_attempt_sum")
                )
        );
    }

    private long preferenceRows(
            String systemId,
            String tenantId,
            String memberId,
            String templateCode
    ) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_event_delivery_preference "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=? "
                        + "AND template_code=? AND channel='INBOX'",
                Long.class,
                systemId,
                tenantId,
                memberId,
                templateCode
        );
    }

    private long preferenceCount(
            String systemId,
            String tenantId,
            String memberId,
            String templateCode,
            boolean enabled,
            long version
    ) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_event_delivery_preference "
                        + "WHERE system_id=? AND tenant_id=? AND member_id=? "
                        + "AND template_code=? AND channel='INBOX' "
                        + "AND enabled=? AND version=?",
                Long.class,
                systemId,
                tenantId,
                memberId,
                templateCode,
                enabled,
                version
        );
    }

    private long deliveryRowCount(
            String systemId,
            String tenantId,
            String memberId,
            String dedupeKey
    ) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_event_message_delivery_log "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=? "
                        + "AND channel='INBOX' AND dedupe_key=?",
                Long.class,
                systemId,
                tenantId,
                memberId,
                dedupeKey
        );
    }

    private long exportMessageCount(
            String systemId,
            String tenantId,
            String memberId,
            String exportId
    ) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_event_message "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=? "
                        + "AND template_code='MODULE_EXPORT_SUCCEEDED' "
                        + "AND target_type='MODULE_EXPORT_TASK' AND target_id=?",
                Long.class,
                systemId,
                tenantId,
                memberId,
                exportId
        );
    }

    private DeliveryFact awaitDelivery(
            String systemId,
            String tenantId,
            String memberId,
            String dedupeKey,
            String status
    ) throws InterruptedException {
        for (var attempt = 0; attempt < 200; attempt++) {
            var values = deliveryFacts(systemId, tenantId, memberId, dedupeKey);
            if (values.size() == 1 && status.equals(values.getFirst().status())) {
                return values.getFirst();
            }
            Thread.sleep(25);
        }
        throw new AssertionError(
                "Event delivery did not reach " + status + " for " + dedupeKey);
    }

    private long awaitSucceededEventRetryJob(long deliveryLogId) throws InterruptedException {
        long count = 0;
        for (var attempt = 0; attempt < 200; attempt++) {
            count = jdbc.queryForObject("SELECT COUNT(*) FROM un_sys_job WHERE "
                            + "job_type='EVENT_DELIVERY_RETRY' AND owner_type='EVENT_DELIVERY' "
                            + "AND owner_id=? AND status='SUCCEEDED' AND attempt_count=1 AND max_attempts=2",
                    Long.class, Long.toString(deliveryLogId));
            if (count == 1) return count;
            Thread.sleep(25);
        }
        return count;
    }

    private DeliveryFact deliveryFact(
            String systemId,
            String tenantId,
            String memberId,
            String dedupeKey
    ) {
        var values = deliveryFacts(systemId, tenantId, memberId, dedupeKey);
        if (values.size() != 1) {
            throw new AssertionError(
                    "Expected one delivery for " + dedupeKey + " but got " + values.size());
        }
        return values.getFirst();
    }

    private List<DeliveryFact> deliveryFacts(
            String systemId,
            String tenantId,
            String memberId,
            String dedupeKey
    ) {
        return jdbc.query(
                "SELECT id,status,attempt_count,message_id,failure_code,failure_message "
                        + "FROM un_event_message_delivery_log "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=? "
                        + "AND channel='INBOX' AND dedupe_key=?",
                (result, row) -> new DeliveryFact(
                        result.getLong("id"),
                        result.getString("status"),
                        result.getInt("attempt_count"),
                        result.getObject("message_id", Long.class),
                        result.getString("failure_code"),
                        result.getString("failure_message")
                ),
                systemId,
                tenantId,
                memberId,
                dedupeKey
        );
    }

    private long flowInstanceCount(String systemId, String tenantId, String definitionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM un_flow_instance WHERE system_id=? AND tenant_id=? "
                        + "AND definition_id=?", Long.class,
                Long.parseLong(systemId), Long.parseLong(tenantId), Long.parseLong(definitionId));
    }

    private static Map<String, Object> row(String key, String name) {
        return Map.of("external_key", key, "item_name", name);
    }

    private static byte[] addWorkbookRow(byte[] template, String key, String name) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             var output = new ByteArrayOutputStream()) {
            var row = workbook.getSheetAt(0).createRow(2);
            row.createCell(0).setCellValue(key);
            row.createCell(1).setCellValue(name);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String fieldDisplay(JsonNode response, String code) {
        for (var value : response.at("/data/values")) {
            if (code.equals(value.path("fieldCode").asText())) return value.path("displayValue").asText();
        }
        throw new AssertionError("Missing field " + code);
    }

    private TestClient login() throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of("account", ROOT_USERNAME,
                "password", ROOT_PASSWORD)), Map.of()));
        return client;
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private static String text(JsonNode node, String pointer) { return node.at(pointer).asText(); }
    private static String key() { return UUID.randomUUID().toString(); }
    private static void assertOk(TestResponse response) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
        assertThat(response.body().path("code").asText()).isEqualTo("OK");
    }
    private static void assertAccepted(TestResponse response) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(202);
        assertThat(response.body().path("code").asText()).isEqualTo("OK");
    }
    private static void assertCreated(TestResponse response) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
        assertThat(response.body().path("code").asText()).isEqualTo("OK");
    }
    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(status);
        assertThat(response.body().path("code").asText()).isEqualTo(code);
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private record EventReadState(
            long preferenceCount,
            long preferenceVersionSum,
            long templateCount,
            long templateVersionSum,
            long templateRevisionCount,
            long templateRevisionSum,
            long messageCount,
            long messageVersionSum,
            long deliveryCount,
            long deliveryAttemptSum
    ) { }

    private record DeliveryFact(
            long id,
            String status,
            int attemptCount,
            Long messageId,
            String failureCode,
            String failureMessage
    ) { }

    private record TestResponse(int status, JsonNode body) { }
    private record BinaryResponse(int status, java.net.http.HttpHeaders headers, byte[] body) {
        String header(String name) { return headers.firstValue(name).orElse(""); }
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();

        TestResponse get(String path) throws Exception { return send(HttpRequest.newBuilder(uri(path)).GET()); }
        BinaryResponse download(String path) throws Exception {
            var response = client.send(HttpRequest.newBuilder(uri(path))
                            .header("X-Request-ID", "import-job-test-" + key()).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(response.statusCode(), response.headers(), response.body());
        }
        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }
        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }
        TestResponse putWithCsrf(String path, String body) throws Exception {
            return request("PUT", path, body, Map.of(), true);
        }
        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }
        TestResponse postMultipart(String path, String filename, byte[] content,
                                   Map<String, String> fields) throws Exception {
            return postMultipart(path, filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content, fields);
        }
        TestResponse postMultipart(String path, String filename, String mediaType, byte[] content,
                                   Map<String, String> fields) throws Exception {
            var boundary = "----examine-import-" + key();
            var body = new ByteArrayOutputStream();
            for (var field : fields.entrySet()) {
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                        + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + filename + "\"\r\nContent-Type: " + mediaType
                    + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(content);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return send(HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-CSRF-Token", csrf())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
        }
        private TestResponse request(String method, String path, String body, Map<String, String> headers,
                                     boolean csrf) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) builder.header("X-CSRF-Token", csrf());
            return send(builder);
        }
        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(builder.header("X-Request-ID", "import-job-test-" + key()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
        }
        private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName())).map(HttpCookie::getValue)
                    .findFirst().orElseThrow(() -> new IllegalStateException("CSRF cookie is missing"));
        }
    }
}
