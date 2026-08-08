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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberDirectoryJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "member_directory_root";
    private static final String ROOT_PASSWORD = "Member-Directory-Root-Password-84!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_member_directory_test")
            .withUsername("examine_member_directory_test")
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
        registry.add("examine.bootstrap.root.display-name", () -> "Member Directory Test Root");
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
    void servesTheMinimalMemberDirectoryFromTheCurrentHttpTenantContext() throws Exception {
        var client = login();
        assertOk(client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));

        var createdSystem = client.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "member_directory_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Member Directory Journey",
                "description", "HTTP journey for the ordinary member directory",
                "tenantMode", "MULTI"
        )), Map.of("Idempotency-Key", key()));
        assertOk(createdSystem);
        var systemId = text(createdSystem.body(), "/data/id");
        var directoryRoot = "/api/v1/systems/" + systemId + "/directory/members";

        assertError(client.get(directoryRoot), 403, "CONTEXT_SYSTEM_MISMATCH");

        var switchedSystem = client.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switchedSystem);
        var firstTenantId = text(switchedSystem.body(), "/data/context/tenantId");
        var ownerMemberId = text(switchedSystem.body(), "/data/context/memberId");

        var secondTenant = client.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/tenants",
                json(Map.of("code", "directory_two", "name", "Directory Tenant Two")),
                Map.of("Idempotency-Key", key()));
        assertOk(secondTenant);
        var secondTenantId = text(secondTenant.body(), "/data/id");
        assertThat(secondTenantId).isNotEqualTo(firstTenantId);

        var ownerAccountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM un_plat_member WHERE system_id=? AND id=?",
                Long.class,
                Long.parseLong(systemId),
                Long.parseLong(ownerMemberId));
        assertThat(ownerAccountId).isNotNull();

        var firstSame = createMember(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                ownerAccountId,
                "directory_same_first",
                "Aardvark Directory");
        var secondSame = createMember(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                ownerAccountId,
                "directory_same_second",
                "Aardvark Directory");
        var beta = createMember(
                Long.parseLong(systemId),
                Long.parseLong(firstTenantId),
                ownerAccountId,
                "directory_beta",
                "Beta Directory");
        var secondTenantOnly = createMember(
                Long.parseLong(systemId),
                Long.parseLong(secondTenantId),
                ownerAccountId,
                "directory_tenant_two",
                "Tenant Two Directory");

        var refreshed = client.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertOk(refreshed);
        assertThat(text(refreshed.body(), "/data/context/tenantId")).isEqualTo(firstTenantId);
        var permissions = StreamSupport.stream(
                        refreshed.body().at("/data/context/permissions").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(permissions).contains("system.runtime.access");

        var firstTenantDirectory = client.get(directoryRoot + "?size=50");
        assertOk(firstTenantDirectory);
        assertMinimalDirectoryDto(firstTenantDirectory.body());
        var firstTenantIds = memberIds(firstTenantDirectory.body());
        assertThat(firstTenantIds)
                .contains(ownerMemberId, firstSame.memberId(), secondSame.memberId(), beta.memberId())
                .doesNotContain(secondTenantOnly.memberId());

        var codeSearch = client.get(directoryRoot + "?keyword=" + beta.memberCode());
        assertOk(codeSearch);
        assertThat(codeSearch.body().at("/data/total").asLong()).isEqualTo(1);
        assertThat(memberIds(codeSearch.body())).containsExactly(beta.memberId());

        var firstPage = client.get(directoryRoot + "?keyword=Aardvark&page=1&size=1");
        var secondPage = client.get(directoryRoot + "?keyword=Aardvark&page=2&size=1");
        assertOk(firstPage);
        assertOk(secondPage);
        assertThat(firstPage.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(secondPage.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(firstPage.body().at("/data/page").asInt()).isEqualTo(1);
        assertThat(secondPage.body().at("/data/page").asInt()).isEqualTo(2);
        assertThat(firstPage.body().at("/data/size").asInt()).isEqualTo(1);
        assertThat(secondPage.body().at("/data/size").asInt()).isEqualTo(1);
        assertThat(memberIds(firstPage.body())).containsExactly(firstSame.memberId());
        assertThat(memberIds(secondPage.body())).containsExactly(secondSame.memberId());
        assertThat(Long.parseLong(firstSame.memberId())).isLessThan(Long.parseLong(secondSame.memberId()));

        var switchedTenant = client.postWithCsrf(
                "/api/v1/context/tenants/" + secondTenantId + ":switch", "{}", Map.of());
        assertOk(switchedTenant);
        assertThat(text(switchedTenant.body(), "/data/context/tenantId")).isEqualTo(secondTenantId);
        assertThat(switchedTenant.body().at("/data/context/permissions").toString())
                .contains("system.runtime.access");

        var secondTenantDirectory = client.get(directoryRoot + "?size=50");
        assertOk(secondTenantDirectory);
        assertMinimalDirectoryDto(secondTenantDirectory.body());
        assertThat(memberIds(secondTenantDirectory.body()))
                .contains(ownerMemberId, secondTenantOnly.memberId())
                .doesNotContain(firstSame.memberId(), secondSame.memberId(), beta.memberId());

        var isolatedSearch = client.get(directoryRoot + "?keyword=" + firstSame.memberCode());
        assertOk(isolatedSearch);
        assertThat(isolatedSearch.body().at("/data/total").asLong()).isZero();
        assertThat(memberIds(isolatedSearch.body())).isEmpty();
    }

    private FixtureMember createMember(
            long systemId,
            long tenantId,
            long ownerAccountId,
            String memberCodePrefix,
            String displayName
    ) {
        var accountId = idService.nextId();
        var memberId = idService.nextId();
        var memberTenantId = idService.nextId();
        var suffix = Long.toUnsignedString(memberId, 36);
        var accountCode = "directory_account_" + suffix;
        var memberCode = memberCodePrefix + "_" + suffix;
        var now = LocalDateTime.now();

        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_account "
                        + "(id,account_code,username,username_normalized,email,email_normalized,phone,"
                        + "display_name,locale,time_zone,status,last_login_at,created_at,created_by,"
                        + "updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,NULL,NULL,NULL,?,'zh-CN','Asia/Shanghai','ACTIVE',NULL,?,?"
                        + ",?,?,NULL,NULL,0)",
                accountId,
                accountCode,
                accountCode,
                accountCode,
                displayName,
                now,
                ownerAccountId,
                now,
                ownerAccountId)).isOne();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_member "
                        + "(id,system_id,account_id,member_code,display_name,default_tenant_id,status,"
                        + "joined_at,created_at,created_by,updated_at,updated_by,deleted_at,version) "
                        + "VALUES (?,?,?,?,?,?,'ACTIVE',?,?,?,?,?,NULL,0)",
                memberId,
                systemId,
                accountId,
                memberCode,
                displayName,
                tenantId,
                now,
                now,
                ownerAccountId,
                now,
                ownerAccountId)).isOne();
        assertThat(jdbcTemplate.update(
                "INSERT INTO un_plat_member_tenant "
                        + "(id,system_id,member_id,tenant_id,status,granted_at,granted_by,expires_at,"
                        + "created_at,created_by,updated_at,updated_by,deleted_at,deleted_by,version) "
                        + "VALUES (?,?,?,?,'ACTIVE',?,?,NULL,?,?,?,?,NULL,NULL,0)",
                memberTenantId,
                systemId,
                memberId,
                tenantId,
                now,
                ownerAccountId,
                now,
                ownerAccountId,
                now,
                ownerAccountId)).isOne();
        return new FixtureMember(Long.toString(memberId), memberCode);
    }

    private static void assertMinimalDirectoryDto(JsonNode response) {
        var data = response.at("/data");
        assertThat(data.path("items").isArray()).isTrue();
        assertThat(data.has("page")).isTrue();
        assertThat(data.has("size")).isTrue();
        assertThat(data.has("total")).isTrue();
        for (var item : data.path("items")) {
            var fields = new ArrayList<String>();
            item.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("memberId", "memberCode", "displayName");
            assertThat(item.path("memberId").isTextual()).isTrue();
            assertThat(item.path("memberCode").isTextual()).isTrue();
            assertThat(item.path("displayName").isTextual()).isTrue();
        }
    }

    private static List<String> memberIds(JsonNode response) {
        return StreamSupport.stream(response.at("/data/items").spliterator(), false)
                .map(item -> item.path("memberId").asText())
                .toList();
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

    private record FixtureMember(String memberId, String memberCode) {
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
                    builder.header("X-Request-ID", "member-directory-test-" + key()).build(),
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
