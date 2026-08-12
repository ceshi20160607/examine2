package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
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
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Vs1JourneyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_test")
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
        registry.add("spring.flyway.locations", () -> "filesystem:" + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Test
    void completesIdentityAndContextJourneyWithSecurityBoundaries() throws Exception {
        var first = register("first");
        var second = register("second");

        var firstId = text(first.body(), "/data/account/id");
        var secondId = text(second.body(), "/data/account/id");
        var firstSystemId = text(first.body(), "/data/firstSystemId");
        var secondSystemId = text(second.body(), "/data/firstSystemId");
        assertThat(first.status()).isEqualTo(200);
        assertThat(text(first.body(), "/data/context/type")).isEqualTo("PLATFORM");

        var replay = first.client().post(
                "/api/v1/auth/register",
                first.registrationBody(),
                Map.of("Idempotency-Key", first.idempotencyKey())
        );
        assertThat(replay.status()).isEqualTo(200);
        assertThat(text(replay.body(), "/data/account/id")).isEqualTo(firstId);
        assertThat(text(replay.body(), "/data/firstSystemId")).isEqualTo(firstSystemId);

        var crossSystem = first.client().postWithCsrf(
                "/api/v1/context/systems/" + secondSystemId + ":switch",
                "{}",
                Map.of()
        );
        assertThat(crossSystem.status()).isEqualTo(403);
        assertThat(text(crossSystem.body(), "/code")).isEqualTo("SYSTEM_MEMBER_REQUIRED");

        var switched = first.client().postWithCsrf(
                "/api/v1/context/systems/" + firstSystemId + ":switch",
                "{}",
                Map.of()
        );
        assertThat(switched.status()).isEqualTo(200);
        assertThat(text(switched.body(), "/data/context/type")).isEqualTo("SYSTEM");
        assertThat(text(switched.body(), "/data/context/systemId")).isEqualTo(firstSystemId);
        assertThat(switched.body().at("/data/context/permissions").toString())
                .contains("system.runtime.access", "system.admin.access", "system.settings.manage");
        assertThat(switched.body().at("/data/context/shells").toString()).contains("SYSTEM_ADMIN");

        var missingCsrf = first.client().post("/api/v1/context/platform:switch", "{}", Map.of());
        assertThat(missingCsrf.status()).isEqualTo(403);
        assertThat(text(missingCsrf.body(), "/code")).isEqualTo("CSRF_INVALID");

        var refreshed = first.client().postWithCsrf("/api/v1/auth/refresh", "{}", Map.of());
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(text(refreshed.body(), "/data/context/type")).isEqualTo("SYSTEM");

        var loggedOut = first.client().postWithCsrf("/api/v1/auth/logout", "{}", Map.of());
        assertThat(loggedOut.status()).isEqualTo(200);
        assertThat(first.client().get("/api/v1/me/context").status()).isEqualTo(401);

        assertThat(jdbcTemplate.queryForObject(
                """
                select count(*) from un_plat_credential
                 where account_id in (?, ?) and password_algorithm = 'ARGON2ID'
                """,
                Integer.class,
                firstId,
                secondId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_plat_context_session where length(token_hash) <> 64",
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_plat_refresh_token where status = 'USED' and rotated_to_id is not null",
                Integer.class
        )).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from un_audit_security where event_type in ('REGISTER_FIRST_SYSTEM','CONTEXT_SWITCH_SYSTEM','SESSION_REFRESH','LOGOUT')",
                Integer.class
        )).isGreaterThanOrEqualTo(5);
    }

    @Test
    void changesPasswordAndRevokesEveryAccountSession() throws Exception {
        var registered = register("password");
        var accountId = text(registered.body(), "/data/account/id");
        var oldPassword = "Vs1-Test-Password-42!";
        var newPassword = "Vs1-Replaced-Password-84!";
        var sibling = new TestClient();
        var siblingLogin = sibling.post(
                "/api/v1/auth/login",
                objectMapper.writeValueAsString(Map.of(
                        "account", registered.username(),
                        "password", oldPassword)),
                Map.of());
        assertThat(siblingLogin.status()).isEqualTo(200);
        assertThat(activeSessionCount(accountId)).isEqualTo(2);

        jdbcTemplate.update("""
                update un_plat_credential
                   set failed_attempts = 3,
                       locked_until = date_add(current_timestamp(3), interval 10 minute)
                 where account_id = ? and credential_type = 'PASSWORD'
                """, accountId);

        var wrongCurrent = registered.client().postWithCsrf(
                "/api/v1/auth/password:change",
                objectMapper.writeValueAsString(Map.of(
                        "currentPassword", "Wrong-Current-Password-42!",
                        "newPassword", newPassword)),
                Map.of());
        assertThat(wrongCurrent.status()).isEqualTo(400);
        assertThat(text(wrongCurrent.body(), "/code")).isEqualTo("PASSWORD_CURRENT_INVALID");
        assertThat(activeSessionCount(accountId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select failed_attempts from un_plat_credential
                 where account_id = ? and credential_type = 'PASSWORD'
                """, Integer.class, accountId)).isEqualTo(3);

        var missingCsrf = registered.client().post(
                "/api/v1/auth/password:change",
                objectMapper.writeValueAsString(Map.of(
                        "currentPassword", oldPassword,
                        "newPassword", newPassword)),
                Map.of());
        assertThat(missingCsrf.status()).isEqualTo(403);
        assertThat(text(missingCsrf.body(), "/code")).isEqualTo("CSRF_INVALID");

        var changed = registered.client().postWithCsrf(
                "/api/v1/auth/password:change",
                objectMapper.writeValueAsString(Map.of(
                        "currentPassword", oldPassword,
                        "newPassword", newPassword)),
                Map.of());
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.headers().allValues("Set-Cookie"))
                .filteredOn(value -> value.contains("Max-Age=0"))
                .anyMatch(value -> value.startsWith("EXAMINE_ACCESS="))
                .anyMatch(value -> value.startsWith("EXAMINE_REFRESH="))
                .anyMatch(value -> value.startsWith("EXAMINE_CSRF="));
        assertThat(activeSessionCount(accountId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from un_plat_refresh_token rt
                  join un_plat_context_session cs on cs.id = rt.context_session_id
                 where cs.account_id = ? and rt.status = 'ACTIVE'
                """, Integer.class, accountId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from un_plat_credential
                 where account_id = ? and credential_type = 'PASSWORD'
                   and failed_attempts = 0 and locked_until is null
                   and password_algorithm = 'ARGON2ID'
                """, Integer.class, accountId)).isOne();

        assertThat(registered.client().get("/api/v1/me/context").status()).isEqualTo(401);
        assertThat(sibling.get("/api/v1/me/context").status()).isEqualTo(401);
        assertThat(sibling.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()).status())
                .isEqualTo(401);

        var oldLogin = login(registered.username(), oldPassword);
        assertThat(oldLogin.status()).isEqualTo(401);
        var newLogin = login(registered.username(), newPassword);
        assertThat(newLogin.status()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from un_audit_security
                 where account_id = ? and event_type = 'PASSWORD_CHANGE'
                   and result in ('SUCCESS', 'DENIED')
                """, Integer.class, accountId)).isEqualTo(2);
    }

    private int activeSessionCount(String accountId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from un_plat_context_session
                 where account_id = ? and status = 'ACTIVE'
                """, Integer.class, accountId);
    }

    private TestResponse login(String username, String password) throws Exception {
        return new TestClient().post(
                "/api/v1/auth/login",
                objectMapper.writeValueAsString(Map.of("account", username, "password", password)),
                Map.of());
    }

    private Registration register(String label) throws Exception {
        var suffix = label + System.nanoTime();
        var username = "user_" + suffix;
        var body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "displayName", "Test " + label,
                "password", "Vs1-Test-Password-42!",
                "systemName", "System " + label,
                "systemCode", "sys_" + suffix
        ));
        var idempotencyKey = UUID.randomUUID().toString();
        var client = new TestClient();
        var response = client.post(
                "/api/v1/auth/register",
                body,
                Map.of("Idempotency-Key", idempotencyKey)
        );
        return new Registration(client, response.status(), response.body(), body, idempotencyKey, username);
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

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private record Registration(
            TestClient client,
            int status,
            JsonNode body,
            String registrationBody,
            String idempotencyKey,
            String username
    ) {
    }

    private record TestResponse(int status, JsonNode body, HttpHeaders headers) {
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
            var allHeaders = new java.util.HashMap<>(headers);
            allHeaders.put("X-CSRF-Token", csrf());
            return post(path, body, allHeaders);
        }

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "test-" + UUID.randomUUID()).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()), response.headers());
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
