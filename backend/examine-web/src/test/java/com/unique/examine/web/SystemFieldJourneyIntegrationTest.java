package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SystemFieldJourneyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_system_field_test")
            .withUsername("examine_test")
            .withPassword("test-only-password");

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
        registry.add("spring.flyway.locations",
                () -> "filesystem:" + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdService idService;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Test
    void computesAndProtectsSystemFieldsAcrossPublishedHttpRecordJourney() throws Exception {
        var registration = register();
        assertOk(registration.response());
        var client = registration.client();
        var systemId = text(registration.response().body(), "/data/firstSystemId");
        var accountId = text(registration.response().body(), "/data/account/id");

        var entered = client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(entered);
        assertThat(text(entered.body(), "/data/context/type")).isEqualTo("SYSTEM");
        assertThat(text(entered.body(), "/data/context/systemId")).isEqualTo(systemId);

        var numericSystemId = Long.parseLong(systemId);
        var member = jdbcTemplate.queryForMap(
                "SELECT id,default_tenant_id FROM un_plat_member "
                        + "WHERE system_id=? AND account_id=? AND deleted_at IS NULL",
                numericSystemId, Long.parseLong(accountId));
        var memberId = ((Number) member.get("id")).longValue();
        var tenantId = ((Number) member.get("default_tenant_id")).longValue();
        var otherMemberId = createOtherMember(numericSystemId, tenantId, Long.parseLong(accountId));

        var published = publishSystemFieldModule(client, systemId);
        var schemaVersionId = text(published.body(), "/data/version/id");
        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var runtimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/system_asset";
        var schema = client.get(runtimeRoot + "/record-schema");
        assertOk(schema);
        assertThat(text(schema.body(), "/data/runtimeState")).isEqualTo("READY");
        assertThat(schema.body().at("/data/fields")).hasSize(7);
        assertThat(field(schema.body(), "/data/fields", "asset_no").path("writable").asBoolean()).isFalse();
        assertThat(field(schema.body(), "/data/fields", "tenant_id").path("writable").asBoolean()).isFalse();
        assertThat(field(schema.body(), "/data/fields", "asset_note").path("writable").asBoolean()).isTrue();
        assertThat(schema.body().at("/data/actions"))
                .extracting(JsonNode::asText)
                .contains("CREATE");

        var spoofedCreate = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Spoofed create",
                "values", Map.of(
                        "tenant_id", 999_999L,
                        "asset_no", "ATTACK-9999",
                        "created_by", 999_999L,
                        "created_at", "1999-01-01T00:00:00",
                        "updated_by", 999_999L,
                        "updated_at", "1999-01-01T00:00:00")
        )), Map.of("Idempotency-Key", key()));
        assertError(spoofedCreate, 422, "SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN");

        var firstCreated = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "First generated asset",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(firstCreated);
        var firstRecordId = text(firstCreated.body(), "/data/recordId");

        var secondCreated = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Second generated asset",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(secondCreated);
        var secondRecordId = text(secondCreated.body(), "/data/recordId");

        var myDraftsPath = runtimeRoot + "/records:my-drafts-query";
        var allMyDraftsQuery = objectMapper.createObjectNode();
        allMyDraftsQuery.put("page", 1);
        allMyDraftsQuery.put("size", 20);
        allMyDraftsQuery.putNull("q");
        var initialMyDrafts = client.post(
                myDraftsPath,
                allMyDraftsQuery.toString(),
                Map.of());
        assertOk(initialMyDrafts);
        assertThat(initialMyDrafts.body().at("/data/total").asLong()).isEqualTo(2L);
        assertThat(initialMyDrafts.body().at("/data/rows")).hasSize(2);

        jdbcTemplate.update(
                "UPDATE un_module_record SET owner_member_id=? WHERE system_id=? AND tenant_id=? AND record_id=?",
                otherMemberId, numericSystemId, tenantId, Long.parseLong(secondRecordId));
        var isolatedMyDrafts = client.post(
                myDraftsPath,
                allMyDraftsQuery.toString(),
                Map.of());
        assertOk(isolatedMyDrafts);
        assertThat(isolatedMyDrafts.body().at("/data/total").asLong()).isOne();
        assertThat(text(isolatedMyDrafts.body(), "/data/rows/0/recordId")).isEqualTo(firstRecordId);
        var searchedMyDrafts = client.post(
                myDraftsPath,
                json(Map.of("page", 1, "size", 20, "q", "first generated")),
                Map.of());
        assertOk(searchedMyDrafts);
        assertThat(searchedMyDrafts.body().at("/data/total").asLong()).isOne();
        assertThat(text(searchedMyDrafts.body(), "/data/rows/0/recordId")).isEqualTo(firstRecordId);
        var otherOwnerSearch = client.post(
                myDraftsPath,
                json(Map.of("page", 1, "size", 20, "q", "second generated")),
                Map.of());
        assertOk(otherOwnerSearch);
        assertThat(otherOwnerSearch.body().at("/data/rows")).isEmpty();
        assertThat(otherOwnerSearch.body().at("/data/total").asLong()).isZero();
        assertError(client.post(
                        myDraftsPath,
                        json(Map.of("page", 1, "size", 20, "q", "x")),
                        Map.of()),
                422,
                "MY_DRAFTS_QUERY_INVALID");

        var firstRead = client.get(runtimeRoot + "/records/" + firstRecordId);
        var secondRead = client.get(runtimeRoot + "/records/" + secondRecordId);
        assertOk(firstRead);
        assertOk(secondRead);
        assertGeneratedCreateValues(firstRead.body(), tenantId, memberId, "ASSET-0001");
        assertGeneratedCreateValues(secondRead.body(), tenantId, memberId, "ASSET-0002");
        assertThat(value(secondRead.body(), "asset_no")).isNotEqualTo(value(firstRead.body(), "asset_no"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_value FROM un_module_auto_number_sequence "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class, numericSystemId, tenantId)).isEqualTo(2L);

        var createdAt = LocalDateTime.parse(value(firstRead.body(), "created_at"));
        var originalUpdatedAt = LocalDateTime.parse(value(firstRead.body(), "updated_at"));
        var updatedByRowBefore = systemValueRowId(Long.parseLong(firstRecordId), "updated_by");

        var spoofedUpdate = client.putWithCsrf(runtimeRoot + "/records/" + firstRecordId, json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Spoofed update",
                "expectedVersion", 0,
                "values", Map.of(
                        "asset_no", "ATTACK-0001",
                        "updated_by", 999_999L,
                        "updated_at", "2099-01-01T00:00:00")
        )), Map.of("Idempotency-Key", key()));
        assertError(spoofedUpdate, 422, "SYSTEM_FIELD_CLIENT_VALUE_FORBIDDEN");
        assertThat(client.get(runtimeRoot + "/records/" + firstRecordId).body()
                .at("/data/version").asLong()).isZero();

        Thread.sleep(25L);
        var updateBody = json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "First generated asset updated",
                "expectedVersion", 0,
                "values", Map.of()
        ));
        var updateKey = key();
        var updated = client.putWithCsrf(runtimeRoot + "/records/" + firstRecordId, updateBody,
                Map.of("Idempotency-Key", updateKey));
        assertOk(updated);
        assertThat(updated.body().at("/data/version").asLong()).isEqualTo(1L);

        var updatedRead = client.get(runtimeRoot + "/records/" + firstRecordId);
        assertOk(updatedRead);
        assertThat(text(updatedRead.body(), "/data/title")).isEqualTo("First generated asset updated");
        assertThat(value(updatedRead.body(), "tenant_id")).isEqualTo(Long.toString(tenantId));
        assertThat(value(updatedRead.body(), "asset_no")).isEqualTo("ASSET-0001");
        assertThat(value(updatedRead.body(), "created_by")).isEqualTo(Long.toString(memberId));
        assertThat(value(updatedRead.body(), "created_at")).isEqualTo(value(firstRead.body(), "created_at"));
        assertThat(value(updatedRead.body(), "updated_by")).isEqualTo(Long.toString(memberId));
        assertThat(LocalDateTime.parse(value(updatedRead.body(), "updated_at")))
                .isAfter(originalUpdatedAt)
                .isAfter(createdAt);
        assertThat(systemValueRowId(Long.parseLong(firstRecordId), "updated_by"))
                .isNotEqualTo(updatedByRowBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_value "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class, numericSystemId, tenantId, Long.parseLong(firstRecordId))).isEqualTo(6L);

        var historyRoot = runtimeRoot + "/records/" + firstRecordId + "/history";
        var history = client.get(historyRoot + "?page=1&size=20");
        assertOk(history);
        assertThat(history.body().at("/data/items")).hasSize(2);
        assertThat(text(history.body(), "/data/items/0/action")).isEqualTo("RECORD_UPDATED");
        assertThat(history.body().at("/data/items/0/historyId").isTextual()).isTrue();
        assertThat(history.body().at("/data/items/0/actorMemberId").isTextual()).isTrue();
        assertThat(history.body().at("/data/items/0/recordVersion").asLong()).isEqualTo(1L);
        var titleDiff = field(history.body(), "/data/items/0/diff", "$title");
        assertThat(titleDiff.path("beforeValue").asText()).isEqualTo("First generated asset");
        assertThat(titleDiff.path("afterValue").asText()).isEqualTo("First generated asset updated");
        assertThat(titleDiff.path("masked").asBoolean()).isFalse();
        assertThat(text(history.body(), "/data/items/1/action")).isEqualTo("RECORD_DRAFT_CREATED");

        var replay = client.putWithCsrf(runtimeRoot + "/records/" + firstRecordId, updateBody,
                Map.of("Idempotency-Key", updateKey));
        assertOk(replay);
        assertThat(replay.body().at("/data/version").asLong()).isEqualTo(1L);
        assertThat(client.get(historyRoot + "?page=1&size=20").body().at("/data/total").asLong())
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class, numericSystemId, tenantId, Long.parseLong(firstRecordId))).isEqualTo(2L);

        var neighborFirst = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Neighbor first asset",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        var neighborSecond = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Neighbor second asset",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(neighborFirst);
        assertCreated(neighborSecond);
        var neighborFirstId = text(neighborFirst.body(), "/data/recordId");
        var neighborSecondId = text(neighborSecond.body(), "/data/recordId");
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + neighborFirstId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + neighborSecondId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));

        var queryBody = objectMapper.createObjectNode();
        queryBody.put("schemaVersionId", schemaVersionId);
        queryBody.put("page", 1);
        queryBody.put("size", 20);
        queryBody.put("recordScope", "active");
        queryBody.putNull("q");
        queryBody.putNull("filter");
        queryBody.putArray("sort");
        var columns = queryBody.putArray("columns");
        List.of("tenant_id", "asset_no", "created_by", "created_at", "updated_by", "updated_at")
                .forEach(columns::add);
        queryBody.putNull("viewId");

        var queried = client.postWithCsrf(
                runtimeRoot + "/records:query", queryBody.toString(), Map.of());
        assertOk(queried);
        assertThat(queried.body().at("/data/rows")).hasSize(2);
        var queryToken = text(queried.body(), "/data/querySnapshotToken");
        assertThat(queryToken).isNotBlank();
        var firstRow = queried.body().at("/data/rows/0");
        var secondRow = queried.body().at("/data/rows/1");
        assertThat(firstRow.path("sortAnchor").path("recordId").asText())
                .isEqualTo(firstRow.path("recordId").asText());
        assertThat(secondRow.path("sortAnchor").path("recordId").asText())
                .isEqualTo(secondRow.path("recordId").asText());

        var nextNeighbor = client.postWithCsrf(
                runtimeRoot + "/records/" + firstRow.path("recordId").asText() + ":neighbors",
                json(Map.of(
                        "direction", "NEXT",
                        "querySnapshotToken", queryToken,
                        "sortAnchor", firstRow.path("sortAnchor"))),
                Map.of());
        assertOk(nextNeighbor);
        assertThat(nextNeighbor.body().at("/data/boundary").asBoolean()).isFalse();
        assertThat(text(nextNeighbor.body(), "/data/neighbor/recordId"))
                .isEqualTo(secondRow.path("recordId").asText());

        var previousNeighbor = client.postWithCsrf(
                runtimeRoot + "/records/" + secondRow.path("recordId").asText() + ":neighbors",
                json(Map.of(
                        "direction", "PREVIOUS",
                        "querySnapshotToken", queryToken,
                        "sortAnchor", secondRow.path("sortAnchor"))),
                Map.of());
        assertOk(previousNeighbor);
        assertThat(text(previousNeighbor.body(), "/data/neighbor/recordId"))
                .isEqualTo(firstRow.path("recordId").asText());

        var firstBoundary = client.postWithCsrf(
                runtimeRoot + "/records/" + firstRow.path("recordId").asText() + ":neighbors",
                json(Map.of(
                        "direction", "PREVIOUS",
                        "querySnapshotToken", queryToken,
                        "sortAnchor", firstRow.path("sortAnchor"))),
                Map.of());
        assertOk(firstBoundary);
        assertThat(firstBoundary.body().at("/data/boundary").asBoolean()).isTrue();
        assertThat(firstBoundary.body().at("/data/neighbor").isNull()
                || firstBoundary.body().at("/data/neighbor").isMissingNode()).isTrue();

        var replacement = queryToken.endsWith("A") ? "B" : "A";
        var tamperedToken = queryToken.substring(0, queryToken.length() - 1) + replacement;
        assertError(client.postWithCsrf(
                        runtimeRoot + "/records/" + firstRow.path("recordId").asText() + ":neighbors",
                        json(Map.of(
                                "direction", "NEXT",
                                "querySnapshotToken", tamperedToken,
                                "sortAnchor", firstRow.path("sortAnchor"))),
                        Map.of()),
                422,
                "QUERY_SNAPSHOT_INVALID");

        var batchArchivePath = runtimeRoot + "/records:batch-archive";
        var failedBatch = client.postWithCsrf(
                batchArchivePath,
                json(Map.of("items", List.of(
                        Map.of("recordId", neighborSecondId, "expectedVersion", 1),
                        Map.of("recordId", neighborFirstId, "expectedVersion", 99)))),
                Map.of("Idempotency-Key", key()));
        assertError(failedBatch, 409, "BATCH_PRECONDITION_FAILED");
        assertThat(failedBatch.body().at("/data/allApplied").asBoolean()).isFalse();
        assertThat(text(failedBatch.body(), "/data/items/0/recordId")).isEqualTo(neighborFirstId);
        assertThat(text(failedBatch.body(), "/data/items/0/resultCode")).isEqualTo("VERSION_STALE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND status='ACTIVE'",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId), Long.parseLong(neighborSecondId))).isEqualTo(2L);

        var batchBody = json(Map.of("items", List.of(
                Map.of("recordId", neighborSecondId, "expectedVersion", 1),
                Map.of("recordId", neighborFirstId, "expectedVersion", 1))));
        var batchKey = key();
        var archivedBatch = client.postWithCsrf(
                batchArchivePath, batchBody, Map.of("Idempotency-Key", batchKey));
        assertOk(archivedBatch);
        assertThat(archivedBatch.body().at("/data/allApplied").asBoolean()).isTrue();
        assertThat(archivedBatch.body().at("/data/items")).hasSize(2);
        assertThat(text(archivedBatch.body(), "/data/items/0/recordId")).isEqualTo(neighborSecondId);
        assertThat(text(archivedBatch.body(), "/data/items/1/recordId")).isEqualTo(neighborFirstId);
        assertThat(archivedBatch.body().at("/data/items/0/newVersion").asLong()).isEqualTo(2L);
        assertThat(archivedBatch.body().at("/data/items/1/newVersion").asLong()).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND status='ARCHIVED'",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId), Long.parseLong(neighborSecondId))).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_ARCHIVED'",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId), Long.parseLong(neighborSecondId))).isEqualTo(2L);

        var replayedBatch = client.postWithCsrf(
                batchArchivePath, batchBody, Map.of("Idempotency-Key", batchKey));
        assertOk(replayedBatch);
        assertThat(replayedBatch.body().at("/data")).isEqualTo(archivedBatch.body().at("/data"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_ARCHIVED'",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId), Long.parseLong(neighborSecondId))).isEqualTo(2L);

        var batchTrashPath = runtimeRoot + "/records:batch-trash";
        var failedTrash = client.postWithCsrf(
                batchTrashPath,
                json(Map.of("items", List.of(
                        Map.of("recordId", neighborFirstId, "expectedVersion", 2),
                        Map.of("recordId", firstRecordId, "expectedVersion", 99)))),
                Map.of("Idempotency-Key", key()));
        assertError(failedTrash, 409, "BATCH_PRECONDITION_FAILED");
        assertThat(failedTrash.body().at("/data/allApplied").asBoolean()).isFalse();
        assertThat(text(failedTrash.body(), "/data/items/0/recordId")).isEqualTo(firstRecordId);
        assertThat(text(failedTrash.body(), "/data/items/0/resultCode")).isEqualTo("VERSION_STALE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND ((record_id=? AND status='DRAFT' AND version=1) "
                        + "OR (record_id=? AND status='ARCHIVED' AND version=2))",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(firstRecordId), Long.parseLong(neighborFirstId))).isEqualTo(2L);

        var trashBody = json(Map.of("items", List.of(
                Map.of("recordId", neighborFirstId, "expectedVersion", 2),
                Map.of("recordId", secondRecordId, "expectedVersion", 0),
                Map.of("recordId", firstRecordId, "expectedVersion", 1))));
        var trashKey = key();
        var trashedBatch = client.postWithCsrf(
                batchTrashPath, trashBody, Map.of("Idempotency-Key", trashKey));
        assertOk(trashedBatch);
        assertThat(trashedBatch.body().at("/data/allApplied").asBoolean()).isTrue();
        assertThat(trashedBatch.body().at("/data/items")).hasSize(3);
        assertThat(text(trashedBatch.body(), "/data/items/0/recordId")).isEqualTo(neighborFirstId);
        assertThat(text(trashedBatch.body(), "/data/items/1/recordId")).isEqualTo(secondRecordId);
        assertThat(text(trashedBatch.body(), "/data/items/2/recordId")).isEqualTo(firstRecordId);
        assertThat(trashedBatch.body().at("/data/items/0/newVersion").asLong()).isEqualTo(3L);
        assertThat(trashedBatch.body().at("/data/items/1/newVersion").asLong()).isEqualTo(1L);
        assertThat(trashedBatch.body().at("/data/items/2/newVersion").asLong()).isEqualTo(2L);
        assertThat(trashedBatch.body().at("/data/items/0/status").asText()).isEqualTo("TRASHED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?,?) AND status='TRASHED' AND deleted_by=?",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId), Long.parseLong(secondRecordId),
                Long.parseLong(firstRecordId), memberId)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND ((record_id=? AND prior_status='ARCHIVED') "
                        + "OR (record_id IN (?,?) AND prior_status='DRAFT'))",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId),
                Long.parseLong(secondRecordId), Long.parseLong(firstRecordId))).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?,?) AND action='RECORD_TRASHED'",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId), Long.parseLong(secondRecordId),
                Long.parseLong(firstRecordId))).isEqualTo(3L);

        var replayedTrash = client.postWithCsrf(
                batchTrashPath, trashBody, Map.of("Idempotency-Key", trashKey));
        assertOk(replayedTrash);
        assertThat(replayedTrash.body().at("/data")).isEqualTo(trashedBatch.body().at("/data"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?,?) AND action='RECORD_TRASHED'",
                Long.class, numericSystemId, tenantId,
                Long.parseLong(neighborFirstId), Long.parseLong(secondRecordId),
                Long.parseLong(firstRecordId))).isEqualTo(3L);

        var activationCandidate = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "My draft activation candidate",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(activationCandidate);
        var activationCandidateId = text(activationCandidate.body(), "/data/recordId");
        var activationSearchBody = json(Map.of(
                "page", 1,
                "size", 20,
                "q", "activation candidate"));
        var beforeActivation = client.post(myDraftsPath, activationSearchBody, Map.of());
        assertOk(beforeActivation);
        assertThat(beforeActivation.body().at("/data/total").asLong()).isOne();
        assertThat(text(beforeActivation.body(), "/data/rows/0/recordId"))
                .isEqualTo(activationCandidateId);
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + activationCandidateId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        var afterActivation = client.post(myDraftsPath, activationSearchBody, Map.of());
        assertOk(afterActivation);
        assertThat(afterActivation.body().at("/data/rows")).isEmpty();
        assertThat(afterActivation.body().at("/data/total").asLong()).isZero();

        var transferFirst = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Transfer first asset",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(transferFirst);
        var transferFirstId = text(transferFirst.body(), "/data/recordId");
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + transferFirstId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        var transferSecond = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Transfer second asset",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(transferSecond);
        var transferSecondId = text(transferSecond.body(), "/data/recordId");
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + transferSecondId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));

        var batchTransferPath = runtimeRoot + "/records:batch-transfer";
        var failedTransfer = client.postWithCsrf(
                batchTransferPath,
                json(Map.of(
                        "items", List.of(
                                Map.of("recordId", transferSecondId, "expectedVersion", 1),
                                Map.of("recordId", transferFirstId, "expectedVersion", 99)),
                        "targetMemberId", Long.toString(otherMemberId))),
                Map.of("Idempotency-Key", key()));
        assertError(failedTransfer, 409, "BATCH_PRECONDITION_FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND owner_member_id=? AND version=1",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId), memberId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_TRANSFERRED'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isZero();

        var transferBody = json(Map.of(
                "items", List.of(
                        Map.of("recordId", transferSecondId, "expectedVersion", 1),
                        Map.of("recordId", transferFirstId, "expectedVersion", 1)),
                "targetMemberId", Long.toString(otherMemberId)));
        var transferKey = key();
        var transferred = client.postWithCsrf(
                batchTransferPath, transferBody, Map.of("Idempotency-Key", transferKey));
        assertOk(transferred);
        assertThat(transferred.body().at("/data/allApplied").asBoolean()).isTrue();
        assertThat(transferred.body().at("/data/items")).hasSize(2);
        assertThat(text(transferred.body(), "/data/items/0/recordId")).isEqualTo(transferSecondId);
        assertThat(text(transferred.body(), "/data/items/1/recordId")).isEqualTo(transferFirstId);
        assertThat(transferred.body().at("/data/items/0/newVersion").asLong()).isEqualTo(2L);
        assertThat(transferred.body().at("/data/items/1/newVersion").asLong()).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND owner_member_id=? AND status='ACTIVE' AND version=2",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId), otherMemberId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_team_member member_row "
                        + "WHERE member_row.system_id=? AND member_row.tenant_id=? "
                        + "AND member_row.record_id IN (?,?) AND member_row.member_id=? "
                        + "AND member_row.team_role='OWNER'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId), otherMemberId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_team_member member_row "
                        + "WHERE member_row.system_id=? AND member_row.tenant_id=? "
                        + "AND member_row.record_id IN (?,?) AND member_row.member_id=? "
                        + "AND member_row.team_role='COLLABORATOR'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId), memberId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_TRANSFERRED'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isEqualTo(2L);

        var replayedTransfer = client.postWithCsrf(
                batchTransferPath, transferBody, Map.of("Idempotency-Key", transferKey));
        assertOk(replayedTransfer);
        assertThat(replayedTransfer.body().at("/data")).isEqualTo(transferred.body().at("/data"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_TRANSFERRED'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isEqualTo(2L);

        assertError(client.postWithCsrf(
                        batchTransferPath,
                        json(Map.of(
                                "items", List.of(Map.of(
                                        "recordId", transferFirstId,
                                        "expectedVersion", 2)),
                                "targetMemberId", Long.toString(otherMemberId + 1))),
                        Map.of("Idempotency-Key", key())),
                422,
                "BATCH_TRANSFER_TARGET_INVALID");

        var batchEditPath = runtimeRoot + "/records:batch-edit";
        var failedBatchEdit = client.postWithCsrf(
                batchEditPath,
                json(Map.of(
                        "items", List.of(
                                Map.of("recordId", transferSecondId, "expectedVersion", 2),
                                Map.of("recordId", transferFirstId, "expectedVersion", 99)),
                        "changes", List.of(Map.of(
                                "fieldCode", "asset_note",
                                "operation", "SET",
                                "value", "Batch twelve note")))),
                Map.of("Idempotency-Key", key()));
        assertError(failedBatchEdit, 409, "BATCH_PRECONDITION_FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND status='ACTIVE' AND version=2",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_value value_row "
                        + "JOIN un_module_field field_row "
                        + "ON field_row.system_id=value_row.system_id "
                        + "AND field_row.id=value_row.logical_field_id "
                        + "WHERE value_row.system_id=? AND value_row.tenant_id=? "
                        + "AND value_row.record_id IN (?,?) AND field_row.field_code='asset_note'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isZero();

        var batchEditBody = json(Map.of(
                "items", List.of(
                        Map.of("recordId", transferSecondId, "expectedVersion", 2),
                        Map.of("recordId", transferFirstId, "expectedVersion", 2)),
                "changes", List.of(Map.of(
                        "fieldCode", "asset_note",
                        "operation", "SET",
                        "value", "Batch twelve note"))));
        var batchEditKey = key();
        var editedBatch = client.postWithCsrf(
                batchEditPath, batchEditBody, Map.of("Idempotency-Key", batchEditKey));
        assertOk(editedBatch);
        assertThat(editedBatch.body().at("/data/allApplied").asBoolean()).isTrue();
        assertThat(editedBatch.body().at("/data/items")).hasSize(2);
        assertThat(text(editedBatch.body(), "/data/items/0/recordId")).isEqualTo(transferSecondId);
        assertThat(text(editedBatch.body(), "/data/items/1/recordId")).isEqualTo(transferFirstId);
        assertThat(editedBatch.body().at("/data/items/0/newVersion").asLong()).isEqualTo(3L);
        assertThat(editedBatch.body().at("/data/items/1/newVersion").asLong()).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_value value_row "
                        + "JOIN un_module_field field_row "
                        + "ON field_row.system_id=value_row.system_id "
                        + "AND field_row.id=value_row.logical_field_id "
                        + "WHERE value_row.system_id=? AND value_row.tenant_id=? "
                        + "AND value_row.record_id IN (?,?) AND field_row.field_code='asset_note' "
                        + "AND value_row.string_value='Batch twelve note'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_BATCH_EDITED'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isEqualTo(2L);

        var replayedEdit = client.postWithCsrf(
                batchEditPath, batchEditBody, Map.of("Idempotency-Key", batchEditKey));
        assertOk(replayedEdit);
        assertThat(replayedEdit.body().at("/data")).isEqualTo(editedBatch.body().at("/data"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_BATCH_EDITED'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isEqualTo(2L);

        var clearedBatch = client.postWithCsrf(
                batchEditPath,
                json(Map.of(
                        "items", List.of(
                                Map.of("recordId", transferFirstId, "expectedVersion", 3),
                                Map.of("recordId", transferSecondId, "expectedVersion", 3)),
                        "changes", List.of(Map.of(
                                "fieldCode", "asset_note",
                                "operation", "CLEAR")))),
                Map.of("Idempotency-Key", key()));
        assertOk(clearedBatch);
        assertThat(clearedBatch.body().at("/data/items/0/newVersion").asLong()).isEqualTo(4L);
        assertThat(clearedBatch.body().at("/data/items/1/newVersion").asLong()).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_value value_row "
                        + "JOIN un_module_field field_row "
                        + "ON field_row.system_id=value_row.system_id "
                        + "AND field_row.id=value_row.logical_field_id "
                        + "WHERE value_row.system_id=? AND value_row.tenant_id=? "
                        + "AND value_row.record_id IN (?,?) AND field_row.field_code='asset_note'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_record_history WHERE system_id=? AND tenant_id=? "
                        + "AND record_id IN (?,?) AND action='RECORD_BATCH_EDITED'",
                Long.class, numericSystemId, tenantId, Long.parseLong(transferFirstId),
                Long.parseLong(transferSecondId))).isEqualTo(4L);

        var auditBeforeRecent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=?",
                Long.class, numericSystemId);
        var outboxBeforeRecent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE system_id=?",
                Long.class, numericSystemId);
        var recentTouchPath = "/api/v1/systems/" + systemId + "/runtime/recent-records:touch";
        var firstRecent = client.postWithCsrf(
                recentTouchPath,
                json(Map.of("moduleCode", "system_asset", "recordId", transferFirstId)),
                Map.of());
        assertOk(firstRecent);
        var firstRecentId = text(firstRecent.body(), "/data/recentId");
        assertThat(firstRecent.body().at("/data/accessCount").asLong()).isOne();
        assertThat(text(firstRecent.body(), "/data/displayLabel")).contains("Transfer first asset");

        var secondRecent = client.postWithCsrf(
                recentTouchPath,
                json(Map.of("moduleCode", "system_asset", "recordId", transferSecondId)),
                Map.of());
        assertOk(secondRecent);
        var secondRecentId = text(secondRecent.body(), "/data/recentId");
        assertThat(secondRecent.body().at("/data/accessCount").asLong()).isOne();

        var firstRecentAgain = client.postWithCsrf(
                recentTouchPath,
                json(Map.of("moduleCode", "system_asset", "recordId", transferFirstId)),
                Map.of());
        assertOk(firstRecentAgain);
        assertThat(text(firstRecentAgain.body(), "/data/recentId")).isEqualTo(firstRecentId);
        assertThat(firstRecentAgain.body().at("/data/accessCount").asLong()).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_recent WHERE system_id=? AND tenant_id=? "
                        + "AND member_id=? AND record_id IN (?,?)",
                Long.class, numericSystemId, tenantId, memberId,
                Long.parseLong(transferFirstId), Long.parseLong(transferSecondId))).isEqualTo(2L);

        var recentRecords = client.get(
                "/api/v1/systems/" + systemId + "/runtime/recent-records?page=1&size=20");
        assertOk(recentRecords);
        assertThat(recentRecords.body().at("/data/total").asLong()).isEqualTo(2L);
        assertThat(recentRecords.body().at("/data/items")).hasSize(2);
        assertThat(text(recentRecords.body(), "/data/items/0/recentId")).isEqualTo(firstRecentId);
        assertThat(text(recentRecords.body(), "/data/items/1/recentId")).isEqualTo(secondRecentId);
        assertThat(text(recentRecords.body(), "/data/items/0/status")).isEqualTo("ACTIVE");
        assertThat(recentRecords.body().at("/data/items/0/accessCount").asLong()).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=?",
                Long.class, numericSystemId)).isEqualTo(auditBeforeRecent);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE system_id=?",
                Long.class, numericSystemId)).isEqualTo(outboxBeforeRecent);

        var globalSearchSeed = client.postWithCsrf(
                batchEditPath,
                json(Map.of(
                        "items", List.of(Map.of(
                                "recordId", transferFirstId,
                                "expectedVersion", 4)),
                        "changes", List.of(Map.of(
                                "fieldCode", "asset_note",
                                "operation", "SET",
                                "value", "crossmodulesearchneedle")))),
                Map.of("Idempotency-Key", key()));
        assertOk(globalSearchSeed);
        assertThat(globalSearchSeed.body().at("/data/items/0/newVersion").asLong()).isEqualTo(5L);

        var auditBeforeGlobalSearch = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=?",
                Long.class, numericSystemId);
        var outboxBeforeGlobalSearch = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE system_id=?",
                Long.class, numericSystemId);
        var globalSearchPath = "/api/v1/systems/" + systemId
                + "/runtime/global-search?q=crossmodulesearchneedle&page=1&size=20";
        var globalSearch = client.get(globalSearchPath);
        assertOk(globalSearch);
        assertThat(globalSearch.body().at("/data/total").asLong()).isOne();
        assertThat(globalSearch.body().at("/data/items")).hasSize(1);
        assertThat(text(globalSearch.body(), "/data/items/0/moduleCode")).isEqualTo("system_asset");
        assertThat(text(globalSearch.body(), "/data/items/0/moduleName")).isEqualTo("System Asset");
        assertThat(text(globalSearch.body(), "/data/items/0/recordId")).isEqualTo(transferFirstId);
        assertThat(text(globalSearch.body(), "/data/items/0/displayLabel"))
                .contains("Transfer first asset");
        assertThat(text(globalSearch.body(), "/data/items/0/status")).isEqualTo("ACTIVE");
        assertThat(globalSearch.body().at("/data/items/0/matchedFieldCodes"))
                .extracting(JsonNode::asText)
                .containsExactly("asset_note");
        var emptyGlobalPage = client.get(
                "/api/v1/systems/" + systemId
                        + "/runtime/global-search?q=crossmodulesearchneedle&page=2&size=1");
        assertOk(emptyGlobalPage);
        assertThat(emptyGlobalPage.body().at("/data/total").asLong()).isOne();
        assertThat(emptyGlobalPage.body().at("/data/items")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=?",
                Long.class, numericSystemId)).isEqualTo(auditBeforeGlobalSearch);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE system_id=?",
                Long.class, numericSystemId)).isEqualTo(outboxBeforeGlobalSearch);
        assertError(client.get(
                        "/api/v1/systems/" + systemId + "/runtime/global-search?q=x&page=1&size=20"),
                422,
                "GLOBAL_SEARCH_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=? "
                        + "AND operation_type='HTTP_REQUEST_FAILED' "
                        + "AND failure_code='GLOBAL_SEARCH_INVALID'",
                Long.class, numericSystemId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE system_id=?",
                Long.class, numericSystemId)).isEqualTo(outboxBeforeGlobalSearch);

        var auditBeforeQuickCreate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=?",
                Long.class, numericSystemId);
        var quickCreate = client.get(
                "/api/v1/systems/" + systemId + "/runtime/quick-create-modules");
        assertOk(quickCreate);
        assertThat(quickCreate.body().at("/data/items")).hasSize(1);
        assertThat(text(quickCreate.body(), "/data/items/0/moduleCode")).isEqualTo("system_asset");
        assertThat(text(quickCreate.body(), "/data/items/0/moduleName")).isEqualTo("System Asset");
        assertThat(text(quickCreate.body(), "/data/items/0/schemaVersionId"))
                .isEqualTo(schemaVersionId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_audit_operation WHERE system_id=?",
                Long.class, numericSystemId)).isEqualTo(auditBeforeQuickCreate);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_sys_outbox_event WHERE system_id=?",
                Long.class, numericSystemId)).isEqualTo(outboxBeforeGlobalSearch);

        var favoritesPath = "/api/v1/systems/" + systemId + "/runtime/favorites";
        var moduleFavorite = client.postWithCsrf(
                favoritesPath,
                json(Map.of("type", "MODULE", "moduleCode", "system_asset")),
                Map.of("Idempotency-Key", key()));
        assertCreated(moduleFavorite);
        var moduleFavoriteId = text(moduleFavorite.body(), "/data/favoriteId");
        assertThat(text(moduleFavorite.body(), "/data/type")).isEqualTo("MODULE");
        assertThat(text(moduleFavorite.body(), "/data/displayLabel")).isEqualTo("system_asset");

        var recordFavoriteBody = json(Map.of(
                "type", "RECORD",
                "moduleCode", "system_asset",
                "recordId", transferFirstId));
        var recordFavorite = client.postWithCsrf(
                favoritesPath,
                recordFavoriteBody,
                Map.of("Idempotency-Key", key()));
        assertCreated(recordFavorite);
        var recordFavoriteId = text(recordFavorite.body(), "/data/favoriteId");
        assertThat(text(recordFavorite.body(), "/data/recordId")).isEqualTo(transferFirstId);
        assertThat(text(recordFavorite.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(text(recordFavorite.body(), "/data/displayLabel"))
                .contains("Transfer first asset");

        var duplicateFavorite = client.postWithCsrf(
                favoritesPath,
                recordFavoriteBody,
                Map.of("Idempotency-Key", key()));
        assertOk(duplicateFavorite);
        assertThat(text(duplicateFavorite.body(), "/data/favoriteId")).isEqualTo(recordFavoriteId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_favorite WHERE system_id=? AND tenant_id=? "
                        + "AND member_id=? AND deleted_at IS NULL",
                Long.class, numericSystemId, tenantId, memberId)).isEqualTo(2L);

        var favorites = client.get(favoritesPath + "?page=1&size=20");
        assertOk(favorites);
        assertThat(favorites.body().at("/data/total").asLong()).isEqualTo(2L);
        assertThat(favorites.body().at("/data/items")).hasSize(2);
        assertThat(text(favorites.body(), "/data/items/0/favoriteId")).isEqualTo(recordFavoriteId);

        var deletedRecordFavorite = client.deleteWithCsrf(
                favoritesPath + "/" + recordFavoriteId,
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(deletedRecordFavorite);
        assertThat(deletedRecordFavorite.body().at("/data/deleted").asBoolean()).isTrue();
        assertThat(deletedRecordFavorite.body().at("/data/version").asLong()).isEqualTo(1L);
        var oneFavorite = client.get(favoritesPath + "?page=1&size=20");
        assertOk(oneFavorite);
        assertThat(oneFavorite.body().at("/data/total").asLong()).isOne();
        assertThat(text(oneFavorite.body(), "/data/items/0/favoriteId")).isEqualTo(moduleFavoriteId);

        var deletedModuleFavorite = client.deleteWithCsrf(
                favoritesPath + "/" + moduleFavoriteId,
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(deletedModuleFavorite);
        var noFavorites = client.get(favoritesPath + "?page=1&size=20");
        assertOk(noFavorites);
        assertThat(noFavorites.body().at("/data/total").asLong()).isZero();
        assertThat(noFavorites.body().at("/data/items")).isEmpty();
    }

    private TestResponse publishSystemFieldModule(TestClient client, String systemId) throws Exception {
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";
        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "system_assets",
                "name", "System Assets",
                "description", "",
                "iconKey", "box",
                "sortOrder", 0,
                "status", "ENABLED",
                "draftRevision", "0"
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);

        var module = client.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", text(group.body(), "/data/id"),
                "code", "system_asset",
                "name", "System Asset",
                "description", "",
                "iconKey", "database",
                "sortOrder", 0,
                "status", "ENABLED",
                "allowComments", false,
                "allowTeam", true,
                "draftRevision", "1"
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");

        createSystemField(client, configRoot, moduleId, "tenant_id", "Tenant", "TENANT", 0,
                Map.of("multiple", false, "selectionScope", "CURRENT_TENANT", "allowInactive", false),
                "NONE", "2");
        createSystemField(client, configRoot, moduleId, "asset_no", "Asset number", "AUTO_NUMBER", 1,
                Map.of("autoNumberPrefix", "ASSET-", "digits", 4), "UNIQUE", "3");
        createSystemField(client, configRoot, moduleId, "created_by", "Created by", "CREATED_BY", 2,
                Map.of(), "NONE", "4");
        createSystemField(client, configRoot, moduleId, "created_at", "Created at", "CREATED_AT", 3,
                Map.of(), "NONE", "5");
        createSystemField(client, configRoot, moduleId, "updated_by", "Updated by", "UPDATED_BY", 4,
                Map.of(), "NONE", "6");
        createSystemField(client, configRoot, moduleId, "updated_at", "Updated at", "UPDATED_AT", 5,
                Map.of(), "NONE", "7");
        createWritableTextField(
                client, configRoot, moduleId, "asset_note", "Asset note", 6, "8");

        assertOk(client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/actions",
                json(Map.of(
                        "code", "archive",
                        "name", "Archive selected records",
                        "type", "CUSTOM",
                        "placement", "BATCH",
                        "confirmMessage", "Archive all selected records?",
                        "sortOrder", 10,
                        "status", "ENABLED",
                        "properties", Map.of(
                                "style", "DEFAULT",
                                "successMessage", "Selected records archived"),
                        "draftRevision", "9"
                )),
                Map.of("Idempotency-Key", key())));

        assertOk(client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/actions",
                json(Map.of(
                        "code", "transfer",
                        "name", "Transfer selected records",
                        "type", "CUSTOM",
                        "placement", "BATCH",
                        "confirmMessage", "Transfer all selected records?",
                        "sortOrder", 20,
                        "status", "ENABLED",
                        "properties", Map.of(
                                "style", "DEFAULT",
                                "successMessage", "Selected records transferred"),
                        "draftRevision", "10"
                )),
                Map.of("Idempotency-Key", key())));

        var checked = client.postWithCsrf(
                configRoot + "/checks", json(Map.of("draftRevision", "11")), Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/blockerCount").asLong()).isZero();
        var config = client.get(configRoot);
        assertOk(config);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"),
                "draftRevision", "11",
                "configRootVersion", text(config.body(), "/data/version"),
                "reason", "System field HTTP journey"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        return published;
    }

    private void createSystemField(
            TestClient client,
            String configRoot,
            String moduleId,
            String code,
            String name,
            String type,
            int sortOrder,
            Map<String, ?> properties,
            String indexMode,
            String draftRevision
    ) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("dictionaryId", null);
        body.put("targetModuleId", null);
        body.put("code", code);
        body.put("name", name);
        body.put("type", type);
        body.put("sortOrder", sortOrder);
        body.put("required", false);
        body.put("hidden", false);
        body.put("readonly", true);
        body.put("searchable", false);
        body.put("filterable", false);
        body.put("showInList", true);
        body.put("showInDetail", true);
        body.put("indexMode", indexMode);
        body.put("status", "ENABLED");
        body.put("properties", properties);
        body.put("draftRevision", draftRevision);
        assertOk(client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(body),
                Map.of("Idempotency-Key", key())));
    }

    private void createWritableTextField(
            TestClient client,
            String configRoot,
            String moduleId,
            String code,
            String name,
            int sortOrder,
            String draftRevision
    ) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("dictionaryId", null);
        body.put("targetModuleId", null);
        body.put("code", code);
        body.put("name", name);
        body.put("type", "TEXT");
        body.put("sortOrder", sortOrder);
        body.put("required", false);
        body.put("hidden", false);
        body.put("readonly", false);
        body.put("searchable", true);
        body.put("filterable", true);
        body.put("showInList", true);
        body.put("showInDetail", true);
        body.put("indexMode", "FILTER");
        body.put("status", "ENABLED");
        body.put("properties", Map.of("maxLength", 500));
        body.put("draftRevision", draftRevision);
        assertOk(client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/fields",
                json(body),
                Map.of("Idempotency-Key", key())));
    }

    private void assertGeneratedCreateValues(
            JsonNode response,
            long tenantId,
            long memberId,
            String expectedAutoNumber
    ) {
        assertThat(response.at("/data/values")).hasSize(6);
        assertThat(value(response, "tenant_id")).isEqualTo(Long.toString(tenantId));
        assertThat(value(response, "asset_no")).isEqualTo(expectedAutoNumber);
        assertThat(value(response, "created_by")).isEqualTo(Long.toString(memberId));
        assertThat(value(response, "updated_by")).isEqualTo(Long.toString(memberId));
        assertThat(value(response, "created_at")).isEqualTo(value(response, "updated_at"));
    }

    private long systemValueRowId(long recordId, String fieldCode) {
        return jdbcTemplate.queryForObject(
                "SELECT rv.id FROM un_module_record_value rv "
                        + "JOIN un_module_field f ON f.system_id=rv.system_id AND f.id=rv.logical_field_id "
                        + "WHERE rv.record_id=? AND f.field_code=?",
                Long.class, recordId, fieldCode);
    }

    private long createOtherMember(long systemId, long tenantId, long ownerAccountId) {
        var accountId = idService.nextId();
        var memberId = idService.nextId();
        var memberTenantId = idService.nextId();
        var suffix = Long.toUnsignedString(memberId, 36);
        var accountCode = "system_field_other_" + suffix;
        var now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO un_plat_account "
                        + "(id,account_code,username,username_normalized,email,email_normalized,phone,"
                        + "display_name,locale,time_zone,status,last_login_at,created_at,created_by,"
                        + "updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,NULL,NULL,NULL,?,'zh-CN','Asia/Shanghai','ACTIVE',NULL,?,?"
                        + ",?,?,NULL,NULL,0)",
                accountId, accountCode, accountCode, accountCode, "Other draft owner",
                now, ownerAccountId, now, ownerAccountId);
        jdbcTemplate.update(
                "INSERT INTO un_plat_member "
                        + "(id,system_id,account_id,member_code,display_name,default_tenant_id,status,"
                        + "joined_at,created_at,created_by,updated_at,updated_by,deleted_at,version) "
                        + "VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,NULL,0)",
                memberId, systemId, accountId, "other_" + suffix, "Other draft owner", tenantId,
                now, now, ownerAccountId, now, ownerAccountId);
        jdbcTemplate.update(
                "INSERT INTO un_plat_member_tenant "
                        + "(id,system_id,member_id,tenant_id,status,granted_at,granted_by,expires_at,"
                        + "created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,'ACTIVE',?,?,NULL,?,?,?,?,NULL,NULL,0)",
                memberTenantId, systemId, memberId, tenantId, now, ownerAccountId,
                now, ownerAccountId, now, ownerAccountId);
        return memberId;
    }

    private Registration register() throws Exception {
        var suffix = Long.toUnsignedString(System.nanoTime());
        var body = json(Map.of(
                "username", "system_field_" + suffix,
                "displayName", "System Field Owner",
                "password", "System-Field-Test-Password-42!",
                "systemName", "System Field Journey",
                "systemCode", "sf_" + suffix
        ));
        var client = new TestClient();
        return new Registration(client, client.post(
                "/api/v1/auth/register", body, Map.of("Idempotency-Key", key())));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static JsonNode field(JsonNode body, String arrayPointer, String fieldCode) {
        for (var candidate : body.at(arrayPointer)) {
            if (fieldCode.equals(candidate.path("fieldCode").asText())) {
                return candidate;
            }
        }
        throw new AssertionError("Missing field " + fieldCode + " at " + arrayPointer + " in " + body);
    }

    private static String value(JsonNode body, String fieldCode) {
        return field(body, "/data/values", fieldCode).path("value").asText();
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status()).describedAs(response.body().toPrettyString()).isEqualTo(200);
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status()).describedAs(response.body().toPrettyString()).isEqualTo(201);
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status()).describedAs(response.body().toPrettyString()).isEqualTo(status);
        assertThat(text(response.body(), "/code")).describedAs(response.body().toPrettyString()).isEqualTo(code);
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

    private record Registration(TestClient client, TestResponse response) {
    }

    private record TestResponse(int status, JsonNode body) {
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            return send(builder);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            var allHeaders = new HashMap<>(headers);
            allHeaders.put("X-CSRF-Token", csrf());
            return post(path, body, allHeaders);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-Token", csrf())
                    .PUT(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            return send(builder);
        }

        TestResponse deleteWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-Token", csrf())
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "system-field-test-" + UUID.randomUUID()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("CSRF cookie is missing"));
        }
    }
}
