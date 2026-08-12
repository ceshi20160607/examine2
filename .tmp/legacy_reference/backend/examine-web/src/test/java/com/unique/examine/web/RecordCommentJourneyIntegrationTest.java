package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.AfterAll;
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

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecordCommentJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "record_comment_journey_root";
    private static final String ROOT_PASSWORD = "Record-Comment-Journey-Root-86!";
    private static final String MODULE_CODE = "work_order";
    private static final Path FILE_STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "examine-record-files-" + UUID.randomUUID());

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_record_comment_journey")
            .withUsername("examine_record_comment")
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
        registry.add("spring.flyway.locations",
                () -> "filesystem:" + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.bootstrap.root.username", () -> ROOT_USERNAME);
        registry.add("examine.bootstrap.root.password", () -> ROOT_PASSWORD);
        registry.add("examine.bootstrap.root.display-name", () -> "Record Comment Journey Root");
        registry.add("examine.file.storage-root", FILE_STORAGE_ROOT::toString);
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

    @AfterAll
    static void cleanFileStorage() throws IOException {
        if (!Files.exists(FILE_STORAGE_ROOT)) {
            return;
        }
        try (var paths = Files.walk(FILE_STORAGE_ROOT)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void exercisesCommentLifecycleIdempotencyPersistenceAndTenantIsolationOverRealHttp()
            throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));

        var createdSystem = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "comment_journey_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Record Comment Journey",
                "description", "Real HTTP, MySQL, and Redis comment journey",
                "tenantMode", "MULTI"
        )), Map.of("Idempotency-Key", key()));
        assertOk(createdSystem);
        var systemId = text(createdSystem.body(), "/data/id");

        var switched = client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        var firstTenantId = text(switched.body(), "/data/context/tenantId");
        var rootMemberId = text(switched.body(), "/data/context/memberId");

        var schemaVersionId = publishCommentModule(client, systemId);
        var refreshed = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshed);
        var permissions = StreamSupport.stream(
                        refreshed.body().at("/data/context/permissions").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(permissions).contains(
                "system.runtime.access",
                "module." + MODULE_CODE + ".view",
                "module." + MODULE_CODE + ".create",
                "module." + MODULE_CODE + ".update",
                "file.create",
                "file.read",
                "file.reference",
                "file.manage");

        var runtimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/" + MODULE_CODE;
        var createdRecord = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Comment journey record",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(createdRecord);
        var recordId = text(createdRecord.body(), "/data/recordId");
        assertOk(client.postWithCsrf(
                runtimeRoot + "/records/" + recordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key())));
        var targetMemberId = createTargetMember(
                Long.parseLong(systemId), Long.parseLong(firstTenantId), Long.parseLong(rootMemberId));
        var teamRoot = runtimeRoot + "/records/" + recordId + "/team";
        assertCreated(client.postWithCsrf(teamRoot + ":initialize", "{}", Map.of()));
        assertCreated(client.postWithCsrf(teamRoot + "/members", json(Map.of(
                "memberId", targetMemberId,
                "role", "COLLABORATOR"
        )), Map.of()));

        var filesRoot = runtimeRoot + "/records/" + recordId + "/files";
        var emptyFiles = client.get(filesRoot + "?page=1&size=20");
        assertOk(emptyFiles);
        assertThat(emptyFiles.body().at("/data/items")).isEmpty();
        assertThat(emptyFiles.body().at("/data/total").asLong()).isZero();

        var fileContent = "record-file-journey-content".getBytes(StandardCharsets.UTF_8);
        var attached = client.multipartWithCsrf(
                filesRoot, "record-note.txt", "text/plain", fileContent, Map.of());
        assertCreated(attached);
        var fileId = text(attached.body(), "/data/fileId");
        assertThat(text(attached.body(), "/data/originalName")).isEqualTo("record-note.txt");
        assertThat(attached.body().at("/data/sizeBytes").asLong()).isEqualTo(fileContent.length);
        assertThat(text(attached.body(), "/data/referencedByMemberId")).isEqualTo(rootMemberId);
        assertThat(text(attached.body(), "/data/downloadUrl"))
                .isEqualTo(filesRoot + "/" + fileId + "/content");

        var filePage = client.get(filesRoot + "?page=1&size=20");
        assertOk(filePage);
        assertThat(filePage.body().at("/data/items")).hasSize(1);
        assertThat(filePage.body().at("/data/total").asLong()).isOne();
        assertThat(text(filePage.body(), "/data/items/0/fileId")).isEqualTo(fileId);
        var downloadedFile = client.download(filesRoot + "/" + fileId + "/content");
        assertThat(downloadedFile.status()).isEqualTo(200);
        assertThat(downloadedFile.body()).isEqualTo(fileContent);
        assertThat(downloadedFile.contentType()).startsWith("text/plain");
        var attachmentBundle = client.download(filesRoot + ":bundle");
        assertThat(attachmentBundle.status()).isEqualTo(200);
        assertThat(attachmentBundle.contentType()).startsWith("application/zip");
        try (var zip = new ZipInputStream(new ByteArrayInputStream(attachmentBundle.body()))) {
            var entry = zip.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("record-note.txt");
            assertThat(zip.readAllBytes()).isEqualTo(fileContent);
            assertThat(zip.getNextEntry()).isNull();
        }

        var commentsRoot = runtimeRoot + "/records/" + recordId + "/comments";
        var emptyPage = client.get(commentsRoot + "?page=1&size=100");
        assertOk(emptyPage);
        assertThat(emptyPage.body().at("/data/page").asInt()).isEqualTo(1);
        assertThat(emptyPage.body().at("/data/size").asInt()).isEqualTo(100);
        assertThat(emptyPage.body().at("/data/total").asLong()).isZero();
        assertThat(emptyPage.body().at("/data/items")).isEmpty();

        var rootRequestBody = json(Map.of(
                "body", "  First persisted comment  ",
                "mentionedMemberIds", java.util.List.of(targetMemberId)));
        var idempotencyKey = key();
        var createdRoot = client.postWithCsrf(
                commentsRoot,
                rootRequestBody,
                Map.of("Idempotency-Key", idempotencyKey));
        assertCreated(createdRoot);
        var rootCommentId = text(createdRoot.body(), "/data/commentId");
        assertComment(
                createdRoot.body().at("/data"),
                rootCommentId,
                recordId,
                null,
                rootMemberId,
                "First persisted comment",
                false,
                1L,
                true);
        assertThat(createdRoot.body().at("/data/mentionedMemberIds"))
                .extracting(JsonNode::asText)
                .containsExactly(targetMemberId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment_mention "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? AND comment_id=? "
                        + "AND mentioned_member_id=?",
                Long.class, Long.parseLong(systemId), Long.parseLong(firstTenantId),
                Long.parseLong(recordId), Long.parseLong(rootCommentId), Long.parseLong(targetMemberId)))
                .isOne();
        var mentionDelivery = jdbcTemplate.queryForMap(
                "SELECT status,message_id,target_path FROM un_event_message_delivery_log "
                        + "WHERE system_id=? AND tenant_id=? AND recipient_member_id=? "
                        + "AND template_code='RECORD_COMMENT_MENTIONED'",
                Long.parseLong(systemId), Long.parseLong(firstTenantId), Long.parseLong(targetMemberId));
        assertThat(mentionDelivery.get("status")).isEqualTo("DELIVERED");
        assertThat(mentionDelivery.get("message_id")).isNotNull();
        assertThat(mentionDelivery.get("target_path").toString())
                .contains("/systems/" + systemId + "/workbench?module=" + MODULE_CODE,
                        "record=" + recordId, "panel=comments", "comment=" + rootCommentId);
        var rootInbox = client.get(
                "/api/v1/systems/" + systemId + "/event/messages?status=ALL&page=1&size=100");
        assertOk(rootInbox);
        assertThat(rootInbox.body().at("/data/items")).noneMatch(item ->
                "RECORD_COMMENT_MENTIONED".equals(item.path("templateCode").asText()));

        var replayedRoot = client.postWithCsrf(
                commentsRoot,
                rootRequestBody,
                Map.of("Idempotency-Key", idempotencyKey));
        assertOk(replayedRoot);
        assertThat(text(replayedRoot.body(), "/data/commentId")).isEqualTo(rootCommentId);
        assertThat(replayedRoot.body().at("/data/version").asLong()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(recordId))).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment_mention WHERE comment_id=?",
                Long.class, Long.parseLong(rootCommentId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_event_message_delivery_log "
                        + "WHERE template_code='RECORD_COMMENT_MENTIONED' AND recipient_member_id=?",
                Long.class, Long.parseLong(targetMemberId))).isOne();

        assertError(client.postWithCsrf(
                        commentsRoot,
                        json(Map.of("body", "A different request")),
                        Map.of("Idempotency-Key", idempotencyKey)),
                409,
                "RECORD_COMMENT_IDEMPOTENCY_KEY_REUSED");

        var createdReply = client.postWithCsrf(
                commentsRoot,
                json(Map.of(
                        "body", "Reply to the first comment",
                        "parentCommentId", rootCommentId)),
                Map.of("Idempotency-Key", key()));
        assertCreated(createdReply);
        var replyCommentId = text(createdReply.body(), "/data/commentId");
        assertComment(
                createdReply.body().at("/data"),
                replyCommentId,
                recordId,
                rootCommentId,
                rootMemberId,
                "Reply to the first comment",
                false,
                1L,
                true);

        var orderedPage = client.get(commentsRoot + "?page=1&size=100");
        assertOk(orderedPage);
        assertThat(orderedPage.body().at("/data/total").asLong()).isEqualTo(2L);
        assertThat(orderedPage.body().at("/data/items")).hasSize(2);
        assertThat(text(orderedPage.body(), "/data/items/0/commentId")).isEqualTo(rootCommentId);
        assertThat(text(orderedPage.body(), "/data/items/1/commentId")).isEqualTo(replyCommentId);
        assertThat(text(orderedPage.body(), "/data/items/1/parentCommentId"))
                .isEqualTo(rootCommentId);

        assertError(client.putWithCsrf(
                        commentsRoot + "/" + rootCommentId,
                        json(Map.of("body", "Stale edit", "version", 99)),
                        Map.of()),
                409,
                "RECORD_COMMENT_VERSION_CONFLICT");

        var updatedRoot = client.putWithCsrf(
                commentsRoot + "/" + rootCommentId,
                json(Map.of("body", "  Updated by the author  ", "version", 1)),
                Map.of());
        assertOk(updatedRoot);
        assertComment(
                updatedRoot.body().at("/data"),
                rootCommentId,
                recordId,
                null,
                rootMemberId,
                "Updated by the author",
                false,
                2L,
                true);

        var deletedReply = client.deleteWithCsrf(
                commentsRoot + "/" + replyCommentId,
                json(Map.of("version", 1)),
                Map.of());
        assertOk(deletedReply);
        assertComment(
                deletedReply.body().at("/data"),
                replyCommentId,
                recordId,
                rootCommentId,
                rootMemberId,
                null,
                true,
                2L,
                false);

        var tombstonePage = client.get(commentsRoot + "?page=1&size=100");
        assertOk(tombstonePage);
        assertThat(tombstonePage.body().at("/data/total").asLong()).isEqualTo(2L);
        assertThat(text(tombstonePage.body(), "/data/items/0/commentId"))
                .isEqualTo(rootCommentId);
        assertThat(text(tombstonePage.body(), "/data/items/0/body"))
                .isEqualTo("Updated by the author");
        assertThat(text(tombstonePage.body(), "/data/items/1/commentId"))
                .isEqualTo(replyCommentId);
        assertThat(tombstonePage.body().at("/data/items/1/deleted").asBoolean()).isTrue();
        assertThat(tombstonePage.body().at("/data/items/1/body").isNull()
                || tombstonePage.body().at("/data/items/1/body").isMissingNode()).isTrue();
        assertThat(tombstonePage.body().at("/data/items/1/canEdit").asBoolean()).isFalse();
        assertThat(tombstonePage.body().at("/data/items/1/canDelete").asBoolean()).isFalse();

        var rootRow = jdbcTemplate.queryForMap(
                "SELECT tenant_id,parent_comment_id,author_member_id,body,deleted,version "
                        + "FROM un_collab_record_comment "
                        + "WHERE system_id=? AND record_id=? AND comment_id=?",
                Long.parseLong(systemId),
                Long.parseLong(recordId),
                Long.parseLong(rootCommentId));
        assertThat(((Number) rootRow.get("tenant_id")).longValue())
                .isEqualTo(Long.parseLong(firstTenantId));
        assertThat(rootRow.get("parent_comment_id")).isNull();
        assertThat(((Number) rootRow.get("author_member_id")).longValue())
                .isEqualTo(Long.parseLong(rootMemberId));
        assertThat(rootRow.get("body")).isEqualTo("Updated by the author");
        assertThat(rootRow.get("deleted")).isEqualTo(false);
        assertThat(((Number) rootRow.get("version")).longValue()).isEqualTo(2L);

        var replyRow = jdbcTemplate.queryForMap(
                "SELECT tenant_id,parent_comment_id,body,deleted,version,deleted_at,deleted_by "
                        + "FROM un_collab_record_comment "
                        + "WHERE system_id=? AND record_id=? AND comment_id=?",
                Long.parseLong(systemId),
                Long.parseLong(recordId),
                Long.parseLong(replyCommentId));
        assertThat(((Number) replyRow.get("tenant_id")).longValue())
                .isEqualTo(Long.parseLong(firstTenantId));
        assertThat(((Number) replyRow.get("parent_comment_id")).longValue())
                .isEqualTo(Long.parseLong(rootCommentId));
        assertThat(replyRow.get("body")).isNull();
        assertThat(replyRow.get("deleted")).isEqualTo(true);
        assertThat(((Number) replyRow.get("version")).longValue()).isEqualTo(2L);
        assertThat(replyRow.get("deleted_at")).isNotNull();
        assertThat(((Number) replyRow.get("deleted_by")).longValue())
                .isEqualTo(Long.parseLong(rootMemberId));

        var missingRecordId = Long.toString(idService.nextId());
        assertThat(missingRecordId).isNotEqualTo(recordId);
        assertError(client.get(runtimeRoot + "/records/" + missingRecordId + "/comments"),
                404,
                "RECORD_NOT_FOUND");
        assertError(client.get(runtimeRoot + "/records/" + missingRecordId + "/files"),
                404,
                "RECORD_NOT_FOUND");
        assertThat(client.download(
                runtimeRoot + "/records/" + missingRecordId + "/files:bundle").status())
                .isEqualTo(404);

        var secondTenant = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/tenants",
                json(Map.of("code", "isolated", "name", "Isolated Tenant")),
                Map.of("Idempotency-Key", key()));
        assertOk(secondTenant);
        var secondTenantId = text(secondTenant.body(), "/data/id");
        assertThat(secondTenantId).isNotEqualTo(firstTenantId);

        assertOk(client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));
        var switchedTenant = client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch", "{}", Map.of());
        assertOk(switchedTenant);
        assertThat(text(switchedTenant.body(), "/data/context/tenantId")).isEqualTo(secondTenantId);
        assertError(client.get(commentsRoot), 404, "RECORD_NOT_FOUND");
        assertError(client.get(filesRoot), 404, "RECORD_NOT_FOUND");
        assertThat(client.download(filesRoot + ":bundle").status()).isEqualTo(404);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(recordId))).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_comment "
                        + "WHERE system_id=? AND tenant_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(secondTenantId))).isZero();

        var switchedBack = client.postWithCsrf(
                "/api/v1/context/tenants/" + firstTenantId + ":switch", "{}", Map.of());
        assertOk(switchedBack);
        var detached = client.deleteWithCsrf(filesRoot + "/" + fileId, "{}", Map.of());
        assertOk(detached);
        assertThat(text(detached.body(), "/data/fileId")).isEqualTo(fileId);
        var filesAfterDetach = client.get(filesRoot + "?page=1&size=20");
        assertOk(filesAfterDetach);
        assertThat(filesAfterDetach.body().at("/data/items")).isEmpty();
        assertThat(filesAfterDetach.body().at("/data/total").asLong()).isZero();
        var emptyBundle = client.download(filesRoot + ":bundle");
        assertThat(emptyBundle.status()).isEqualTo(200);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(emptyBundle.body()))) {
            assertThat(zip.getNextEntry()).isNull();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_object WHERE system_id=? AND tenant_id=? AND id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(fileId))).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_file_reference WHERE system_id=? AND tenant_id=? AND file_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(fileId))).isZero();
    }

    private String publishCommentModule(TestClient client, String systemId) throws Exception {
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";
        var group = client.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "operations",
                "name", "Operations",
                "description", "",
                "iconKey", "folder",
                "sortOrder", 0,
                "status", "ENABLED",
                "draftRevision", "0"
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);

        var module = client.postWithCsrf(configRoot + "/modules", json(Map.ofEntries(
                Map.entry("groupId", text(group.body(), "/data/id")),
                Map.entry("code", MODULE_CODE),
                Map.entry("name", "Work Order"),
                Map.entry("description", ""),
                Map.entry("iconKey", "clipboard"),
                Map.entry("sortOrder", 0),
                Map.entry("status", "ENABLED"),
                Map.entry("allowComments", true),
                Map.entry("allowTeam", true),
                Map.entry("draftRevision", "1")
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        assertThat(module.body().at("/data/allowComments").asBoolean()).isTrue();

        var checked = client.postWithCsrf(
                configRoot + "/checks", json(Map.of("draftRevision", "2")), Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/blockerCount").asLong()).isZero();

        var config = client.get(configRoot);
        assertOk(config);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"),
                "draftRevision", "2",
                "configRootVersion", text(config.body(), "/data/version"),
                "reason", "Record comment HTTP journey"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        return text(published.body(), "/data/version/id");
    }

    private String createTargetMember(long systemId, long tenantId, long ownerMemberId) {
        var ownerAccountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM un_plat_member WHERE system_id=? AND id=?",
                Long.class, systemId, ownerMemberId);
        assertThat(ownerAccountId).isNotNull();
        var accountId = idService.nextId();
        var memberId = idService.nextId();
        var memberTenantId = idService.nextId();
        var suffix = Long.toUnsignedString(memberId, 36);
        var now = LocalDateTime.now();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_account "
                        + "(id,account_code,username,username_normalized,email,email_normalized,phone,"
                        + "display_name,locale,time_zone,status,last_login_at,created_at,created_by,"
                        + "updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,NULL,NULL,NULL,?,'zh-CN','Asia/Shanghai','ACTIVE',NULL,?,?,"
                        + "?,?,NULL,NULL,0)",
                accountId, "comment_target_" + suffix, "comment_target_" + suffix,
                "comment_target_" + suffix, "Comment Target", now, ownerAccountId, now, ownerAccountId))
                .isOne();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_member "
                        + "(id,system_id,account_id,member_code,display_name,default_tenant_id,status,"
                        + "joined_at,created_at,created_by,updated_at,updated_by,deleted_at,version) "
                        + "VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,NULL,0)",
                memberId, systemId, accountId, "comment_target_" + suffix, "Comment Target", tenantId,
                now, now, ownerAccountId, now, ownerAccountId)).isOne();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_member_tenant "
                        + "(id,system_id,member_id,tenant_id,status,granted_at,granted_by,expires_at,"
                        + "created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,'ACTIVE',?,?,NULL,?,?,?, ?,NULL,NULL,0)",
                memberTenantId, systemId, memberId, tenantId, now, ownerAccountId,
                now, ownerAccountId, now, ownerAccountId)).isOne();
        return Long.toString(memberId);
    }

    private TestClient login() throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of(
                "account", ROOT_USERNAME,
                "password", ROOT_PASSWORD
        )), Map.of()));
        return client;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static void assertComment(
            JsonNode comment,
            String commentId,
            String recordId,
            String parentCommentId,
            String authorMemberId,
            String body,
            boolean deleted,
            long version,
            boolean mutable
    ) {
        assertThat(comment.path("commentId").asText()).isEqualTo(commentId);
        assertThat(comment.path("recordId").asText()).isEqualTo(recordId);
        if (parentCommentId == null) {
            assertThat(comment.path("parentCommentId").isNull()
                    || comment.path("parentCommentId").isMissingNode()).isTrue();
        } else {
            assertThat(comment.path("parentCommentId").asText()).isEqualTo(parentCommentId);
        }
        assertThat(comment.path("authorMemberId").asText()).isEqualTo(authorMemberId);
        if (body == null) {
            assertThat(comment.path("body").isNull() || comment.path("body").isMissingNode()).isTrue();
        } else {
            assertThat(comment.path("body").asText()).isEqualTo(body);
        }
        assertThat(comment.path("deleted").asBoolean()).isEqualTo(deleted);
        assertThat(comment.path("version").asLong()).isEqualTo(version);
        assertThat(comment.path("createdAt").asText()).isNotBlank();
        assertThat(comment.path("updatedAt").asText()).isNotBlank();
        assertThat(comment.path("canEdit").asBoolean()).isEqualTo(mutable);
        assertThat(comment.path("canDelete").asBoolean()).isEqualTo(mutable);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 200, got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 201, got %s: %s", response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected %s, got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
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

    private record TestResponse(int status, JsonNode body) {
    }

    private record BinaryResponse(int status, byte[] body, String contentType) {
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers)
                throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers)
                throws Exception {
            return request("PUT", path, body, headers, true);
        }

        TestResponse deleteWithCsrf(String path, String body, Map<String, String> headers)
                throws Exception {
            return request("DELETE", path, body, headers, true);
        }

        TestResponse multipartWithCsrf(
                String path,
                String filename,
                String mediaType,
                byte[] content,
                Map<String, String> headers
        ) throws Exception {
            var boundary = "----ExamineRecordFile" + UUID.randomUUID().toString().replace("-", "");
            var output = new ByteArrayOutputStream();
            output.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + mediaType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(content);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-CSRF-Token", csrf())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()));
            headers.forEach(builder::header);
            return send(builder);
        }

        BinaryResponse download(String path) throws IOException, InterruptedException {
            var response = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + path))
                            .header("X-Request-ID", "record-file-test-" + key())
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(
                    response.statusCode(),
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse(""));
        }

        private TestResponse request(
                String method,
                String path,
                String body,
                Map<String, String> headers,
                boolean csrf
        ) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) {
                builder.header("X-CSRF-Token", csrf());
            }
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder)
                throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "record-comment-test-" + key()).build(),
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
