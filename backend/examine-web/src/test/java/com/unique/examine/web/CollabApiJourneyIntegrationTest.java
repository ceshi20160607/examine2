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
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CollabApiJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "collab_api_root";
    private static final String ROOT_PASSWORD = "Collab-Api-Root-Password-84!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_collab_api_test")
            .withUsername("examine_collab_api_test")
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
        registry.add("examine.bootstrap.root.display-name", () -> "Collab API Test Root");
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
    void exercisesRecordTeamLifecycleAndTenantIsolationOverRealHttp() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));

        var createdSystem = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "collab_api_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Collab API Journey",
                "description", "HTTP journey for record teams",
                "tenantMode", "MULTI"
        )), Map.of("Idempotency-Key", key()));
        assertOk(createdSystem);
        var systemId = text(createdSystem.body(), "/data/id");

        var switched = client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        var firstTenantId = text(switched.body(), "/data/context/tenantId");
        var ownerMemberId = text(switched.body(), "/data/context/memberId");

        var schemaVersionId = publishCollabModule(client, systemId);
        var refreshed = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshed);
        var permissions = StreamSupport.stream(
                        refreshed.body().at("/data/context/permissions").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(permissions).contains(
                "system.runtime.access",
                "module.work_order.view",
                "module.work_order.update",
                "module.work_order.action.transfer");

        var runtimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/work_order";
        var createdRecord = client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId,
                "title", "Collab journey record",
                "values", Map.of()
        )), Map.of("Idempotency-Key", key()));
        assertCreated(createdRecord);
        var recordId = text(createdRecord.body(), "/data/recordId");
        var targetMemberId = createTargetMember(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(ownerMemberId));
        var teamRoot = "/api/v1/systems/" + systemId
                + "/runtime/modules/work_order/records/" + recordId + "/team";

        assertError(client.get(teamRoot), 404, "RECORD_TEAM_NOT_FOUND");

        var initialized = client.postWithCsrf(teamRoot + ":initialize", "{}", Map.of());
        assertCreated(initialized);
        assertThat(initialized.body().at("/data/created").asBoolean()).isTrue();
        assertThat(text(initialized.body(), "/data/team/systemId")).isEqualTo(systemId);
        assertThat(text(initialized.body(), "/data/team/tenantId")).isEqualTo(firstTenantId);
        assertThat(text(initialized.body(), "/data/team/recordId")).isEqualTo(recordId);
        assertThat(text(initialized.body(), "/data/team/ownerMemberId")).isEqualTo(ownerMemberId);
        assertThat(initialized.body().at("/data/team/version").asLong()).isEqualTo(1L);
        assertThat(memberRole(initialized.body().at("/data/team"), ownerMemberId)).isEqualTo("OWNER");

        var repeated = client.postWithCsrf(teamRoot + ":initialize", "{}", Map.of());
        assertOk(repeated);
        assertThat(repeated.body().at("/data/created").asBoolean()).isFalse();
        assertThat(text(repeated.body(), "/data/team/ownerMemberId")).isEqualTo(ownerMemberId);
        assertThat(repeated.body().at("/data/team/version").asLong()).isEqualTo(1L);

        var read = client.get(teamRoot);
        assertOk(read);
        assertThat(text(read.body(), "/data/ownerMemberId")).isEqualTo(ownerMemberId);
        assertThat(read.body().at("/data/version").asLong()).isEqualTo(1L);

        var added = client.postWithCsrf(teamRoot + "/members", json(Map.of(
                "memberId", targetMemberId,
                "role", "VIEWER"
        )), Map.of());
        assertCreated(added);
        assertThat(added.body().at("/data/version").asLong()).isEqualTo(2L);
        assertThat(memberRole(added.body().at("/data"), targetMemberId)).isEqualTo("VIEWER");

        var roleChanged = client.putWithCsrf(teamRoot + "/members/" + targetMemberId + "/role",
                json(Map.of("role", "COLLABORATOR")), Map.of());
        assertOk(roleChanged);
        assertThat(roleChanged.body().at("/data/version").asLong()).isEqualTo(3L);
        assertThat(memberRole(roleChanged.body().at("/data"), targetMemberId)).isEqualTo("COLLABORATOR");

        var removed = client.deleteWithCsrf(teamRoot + "/members/" + targetMemberId, "{}", Map.of());
        assertOk(removed);
        assertThat(removed.body().at("/data/version").asLong()).isEqualTo(4L);
        assertThat(removed.body().at("/data/members")).hasSize(1);
        assertThat(findMember(removed.body().at("/data"), targetMemberId)).isNull();

        var transferred = client.postWithCsrf(teamRoot + "/transfer",
                json(Map.of("targetMemberId", targetMemberId)), Map.of());
        assertOk(transferred);
        assertThat(transferred.body().at("/data/version").asLong()).isEqualTo(5L);
        assertThat(text(transferred.body(), "/data/ownerMemberId")).isEqualTo(targetMemberId);
        assertThat(memberRole(transferred.body().at("/data"), ownerMemberId)).isEqualTo("COLLABORATOR");
        assertThat(memberRole(transferred.body().at("/data"), targetMemberId)).isEqualTo("OWNER");

        var repeatedAfterTransfer = client.postWithCsrf(teamRoot + ":initialize", "{}", Map.of());
        assertOk(repeatedAfterTransfer);
        assertThat(repeatedAfterTransfer.body().at("/data/created").asBoolean()).isFalse();
        assertThat(text(repeatedAfterTransfer.body(), "/data/team/ownerMemberId")).isEqualTo(targetMemberId);
        assertThat(repeatedAfterTransfer.body().at("/data/team/version").asLong()).isEqualTo(5L);

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
        assertError(client.get(teamRoot), 404, "RECORD_TEAM_NOT_FOUND");

        var persisted = jdbcTemplate.queryForMap(
                "SELECT tenant_id,version FROM un_collab_record_team "
                        + "WHERE system_id=? AND record_id=?",
                Long.parseLong(systemId), Long.parseLong(recordId));
        assertThat(((Number) persisted.get("tenant_id")).longValue())
                .isEqualTo(Long.parseLong(firstTenantId));
        assertThat(((Number) persisted.get("version")).longValue()).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_collab_record_team_member "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                Long.parseLong(recordId))).isEqualTo(2L);
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
                accountId, "collab_target_" + suffix, "collab_target_" + suffix,
                "collab_target_" + suffix, "Collab Target", now, ownerAccountId, now, ownerAccountId))
                .isOne();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_member "
                        + "(id,system_id,account_id,member_code,display_name,default_tenant_id,status,"
                        + "joined_at,created_at,created_by,updated_at,updated_by,deleted_at,version) "
                        + "VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,NULL,0)",
                memberId, systemId, accountId, "collab_target_" + suffix, "Collab Target", tenantId,
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

    private String publishCollabModule(TestClient client, String systemId) throws Exception {
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
                Map.entry("code", "work_order"),
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
        var moduleId = text(module.body(), "/data/id");

        var transfer = client.postWithCsrf(
                configRoot + "/modules/" + moduleId + "/actions",
                json(Map.ofEntries(
                        Map.entry("code", "transfer"),
                        Map.entry("name", "Transfer ownership"),
                        Map.entry("type", "CUSTOM"),
                        Map.entry("placement", "DETAIL"),
                        Map.entry("confirmMessage", "Transfer record ownership?"),
                        Map.entry("sortOrder", 10),
                        Map.entry("status", "ENABLED"),
                        Map.entry("properties", Map.of(
                                "style", "DEFAULT",
                                "successMessage", "Ownership transferred")),
                        Map.entry("draftRevision", "2")
                )),
                Map.of("Idempotency-Key", key()));
        assertOk(transfer);

        var checked = client.postWithCsrf(
                configRoot + "/checks", json(Map.of("draftRevision", "3")), Map.of());
        assertOk(checked);
        assertThat(checked.body().at("/data/blockerCount").asLong()).isZero();
        var config = client.get(configRoot);
        assertOk(config);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"),
                "draftRevision", "3",
                "configRootVersion", text(config.body(), "/data/version"),
                "reason", "Collab API HTTP journey"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        return text(published.body(), "/data/version/id");
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

    private static JsonNode findMember(JsonNode team, String memberId) {
        for (var member : team.path("members")) {
            if (memberId.equals(member.path("memberId").asText())) {
                return member;
            }
        }
        return null;
    }

    private static String memberRole(JsonNode team, String memberId) {
        var member = findMember(team, memberId);
        if (member == null) {
            throw new AssertionError("Missing team member " + memberId + " in " + team);
        }
        return member.path("role").asText();
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

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }

        TestResponse deleteWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("DELETE", path, body, headers, true);
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

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "collab-api-test-" + key()).build(),
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
