package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.web.vnext.auth.VNextSessionCookieSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ActiveProfiles("vnext")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class P1LoginCycleIntegrationTest {
    private static final String ADMIN_PASSWORD = "P1-Cycle-Login-Secret-84!";
    private static final String WRONG_PASSWORD = "P1-Cycle-Wrong-Secret-84!";
    private static final Set<String> PLATFORM_PERMISSIONS = Set.of(
            "platform.runtime.access",
            "platform.admin.access",
            "platform.system.manage"
    );
    private static final Set<String> PLATFORM_SHELLS = Set.of(
            "PLATFORM_RUNTIME",
            "PLATFORM_ADMIN"
    );

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.10"))
            .withDatabaseName("examine2_p1_login_cycle")
            .withUsername("examine_p1_login_cycle")
            .withPassword("container-database-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void loginCycleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration-vnext");
        registry.add("spring.flyway.clean-disabled", () -> true);
        registry.add("examine.foundation.vnext.enabled", () -> true);
        registry.add("examine.foundation.vnext.bootstrap.username", () -> "admin");
        registry.add("examine.foundation.vnext.bootstrap.password", () -> ADMIN_PASSWORD);
        registry.add("examine.foundation.vnext.bootstrap.display-name", () -> "P1 Login Administrator");
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.security.login-max-failures", () -> 10);
        registry.add("examine.scheduling.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicInteger requestSequence = new AtomicInteger();

    @Test
    void completeLoginRefreshReplayLogoutAndConcurrentRefreshCycle(CapturedOutput output) throws Exception {
        assertThatFoundationHasNoSessions();
        var sensitiveValues = new ArrayList<String>();
        sensitiveValues.add(ADMIN_PASSWORD);
        sensitiveValues.add(WRONG_PASSWORD);

        var denied = login(WRONG_PASSWORD);
        assertEquals(401, denied.statusCode());
        assertEquals("AUTH_INVALID_CREDENTIALS", code(denied));
        assertNoSessionCookies(denied);
        assertEquals(0L, count("SELECT COUNT(*) FROM un_plat_context_session"));
        assertEquals(0L, count("SELECT COUNT(*) FROM un_plat_refresh_token"));
        assertEquals(1L, count("SELECT COUNT(*) FROM un_audit_security "
                + "WHERE event_type='LOGIN' AND result='DENIED' "
                + "AND failure_code='AUTH_INVALID_CREDENTIALS'"));

        var firstCookies = new CookieJar();
        var login = login(ADMIN_PASSWORD);
        assertEquals(200, login.statusCode());
        assertEquals("OK", code(login));
        assertSessionCookiePolicy(login);
        firstCookies.capture(login);
        var firstAccess = firstCookies.required(VNextSessionCookieSupport.ACCESS_COOKIE);
        var firstRefresh = firstCookies.required(VNextSessionCookieSupport.REFRESH_COOKIE);
        var firstCsrf = firstCookies.required(VNextSessionCookieSupport.CSRF_COOKIE);
        sensitiveValues.addAll(List.of(firstAccess, firstRefresh, firstCsrf));
        assertAccountContext(login);
        assertResponseHasNoTokens(login, firstAccess, firstRefresh);

        var firstContext = contextByHash(sha256(firstAccess));
        var firstRefreshRow = refreshByHash(sha256(firstRefresh));
        assertEquals("ACTIVE", firstContext.status());
        assertEquals(firstContext.id(), firstRefreshRow.contextSessionId());
        assertEquals("ACTIVE", firstRefreshRow.status());
        assertStoredHashOnly(firstAccess, firstContext.tokenHash(), "un_plat_context_session");
        assertStoredHashOnly(firstRefresh, firstRefreshRow.tokenHash(), "un_plat_refresh_token");

        var me = get("/api/v1/me/context", firstCookies);
        assertEquals(200, me.statusCode());
        assertEquals("admin", json(me).path("data").path("account").path("username").asText());

        var refreshed = post("/api/v1/auth/refresh", null, firstCookies, true);
        assertEquals(200, refreshed.statusCode());
        assertEquals("OK", code(refreshed));
        assertSessionCookiePolicy(refreshed);
        firstCookies.capture(refreshed);
        var refreshedAccess = firstCookies.required(VNextSessionCookieSupport.ACCESS_COOKIE);
        var refreshedToken = firstCookies.required(VNextSessionCookieSupport.REFRESH_COOKIE);
        var refreshedCsrf = firstCookies.required(VNextSessionCookieSupport.CSRF_COOKIE);
        sensitiveValues.addAll(List.of(refreshedAccess, refreshedToken, refreshedCsrf));
        assertResponseHasNoTokens(refreshed, refreshedAccess, refreshedToken);

        var rotatedOld = refreshByHash(sha256(firstRefresh));
        assertEquals("USED", rotatedOld.status());
        assertNotNull(rotatedOld.rotatedToId());
        var rotatedNew = refreshById(rotatedOld.rotatedToId());
        assertEquals("ACTIVE", rotatedNew.status());
        assertEquals(rotatedOld.tokenFamily(), rotatedNew.tokenFamily());
        assertEquals(sha256(refreshedToken), rotatedNew.tokenHash());
        assertEquals(sha256(refreshedAccess), contextById(firstContext.id()).tokenHash());
        assertStoredHashOnly(refreshedAccess, contextById(firstContext.id()).tokenHash(),
                "un_plat_context_session");
        assertStoredHashOnly(refreshedToken, rotatedNew.tokenHash(), "un_plat_refresh_token");

        var replayCookies = firstCookies.copy();
        replayCookies.put(VNextSessionCookieSupport.REFRESH_COOKIE, firstRefresh);
        var replay = post("/api/v1/auth/refresh", null, replayCookies, true);
        assertEquals(401, replay.statusCode());
        assertEquals("AUTH_REFRESH_REPLAY", code(replay));
        assertFamilyRevoked(firstRefreshRow.tokenFamily(), firstContext.id());

        var logoutCookies = new CookieJar();
        var relogin = login(ADMIN_PASSWORD);
        assertEquals(200, relogin.statusCode());
        logoutCookies.capture(relogin);
        var logoutAccess = logoutCookies.required(VNextSessionCookieSupport.ACCESS_COOKIE);
        var logoutRefresh = logoutCookies.required(VNextSessionCookieSupport.REFRESH_COOKIE);
        var logoutCsrf = logoutCookies.required(VNextSessionCookieSupport.CSRF_COOKIE);
        sensitiveValues.addAll(List.of(logoutAccess, logoutRefresh, logoutCsrf));
        var logoutContext = contextByHash(sha256(logoutAccess));

        var logout = post("/api/v1/auth/logout", null, logoutCookies, true);
        assertEquals(200, logout.statusCode());
        assertEquals("OK", code(logout));
        assertClearedSessionCookies(logout);
        logoutCookies.capture(logout);
        assertEquals("REVOKED", contextById(logoutContext.id()).status());
        assertTrue(refreshStatusesForContext(logoutContext.id()).stream()
                .allMatch("REVOKED"::equals));
        var meAfterLogout = get("/api/v1/me/context", logoutCookies);
        assertEquals(401, meAfterLogout.statusCode());
        assertEquals("AUTH_SESSION_REQUIRED", code(meAfterLogout));

        var replayAuditsBeforeConcurrency = count("SELECT COUNT(*) FROM un_audit_security "
                + "WHERE event_type='SESSION_REFRESH_REPLAY' AND result='DENIED' "
                + "AND failure_code='AUTH_REFRESH_REPLAY'");
        var concurrentCookies = new CookieJar();
        var concurrentLogin = login(ADMIN_PASSWORD);
        assertEquals(200, concurrentLogin.statusCode());
        concurrentCookies.capture(concurrentLogin);
        var concurrentAccess = concurrentCookies.required(VNextSessionCookieSupport.ACCESS_COOKIE);
        var concurrentRefresh = concurrentCookies.required(VNextSessionCookieSupport.REFRESH_COOKIE);
        var concurrentCsrf = concurrentCookies.required(VNextSessionCookieSupport.CSRF_COOKIE);
        sensitiveValues.addAll(List.of(concurrentAccess, concurrentRefresh, concurrentCsrf));
        var concurrentRow = refreshByHash(sha256(concurrentRefresh));

        var concurrentResponses = concurrentRefresh(concurrentCookies);
        assertEquals(List.of(200, 401), concurrentResponses.stream()
                .map(HttpResponse::statusCode)
                .sorted()
                .toList());
        var concurrentReplay = concurrentResponses.stream()
                .filter(response -> response.statusCode() == 401)
                .findFirst()
                .orElseThrow();
        assertEquals("AUTH_REFRESH_REPLAY", code(concurrentReplay));
        var concurrentSuccess = concurrentResponses.stream()
                .filter(response -> response.statusCode() == 200)
                .findFirst()
                .orElseThrow();
        var winnerCookies = concurrentCookies.copy();
        winnerCookies.capture(concurrentSuccess);
        var winnerAccess = winnerCookies.required(VNextSessionCookieSupport.ACCESS_COOKIE);
        var winnerRefresh = winnerCookies.required(VNextSessionCookieSupport.REFRESH_COOKIE);
        var winnerCsrf = winnerCookies.required(VNextSessionCookieSupport.CSRF_COOKIE);
        sensitiveValues.addAll(List.of(winnerAccess, winnerRefresh, winnerCsrf));
        assertResponseHasNoTokens(concurrentSuccess, winnerAccess, winnerRefresh);
        assertFamilyRevoked(concurrentRow.tokenFamily(), concurrentRow.contextSessionId());
        assertEquals(replayAuditsBeforeConcurrency + 1L, count("SELECT COUNT(*) FROM un_audit_security "
                + "WHERE event_type='SESSION_REFRESH_REPLAY' AND result='DENIED' "
                + "AND failure_code='AUTH_REFRESH_REPLAY'"));

        var captured = output.getAll();
        for (var sensitive : sensitiveValues) {
            assertFalse(captured.contains(sensitive));
        }
        assertFalse(captured.contains("un_audit_operation"));
        assertFalse(captured.contains("SQLSyntaxErrorException"));
    }

    private void assertThatFoundationHasNoSessions() {
        assertTrue(port > 0);
        assertEquals(1L, count("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version='1.0.0' AND success=TRUE"));
        assertEquals(1L, count("SELECT COUNT(*) FROM un_plat_account "
                + "WHERE username_normalized='admin' AND status='ACTIVE'"));
        assertEquals(0L, count("SELECT COUNT(*) FROM un_plat_context_session"));
        assertEquals(0L, count("SELECT COUNT(*) FROM un_plat_refresh_token"));
    }

    private void assertAccountContext(HttpResponse<String> response) throws Exception {
        var data = json(response).path("data");
        assertEquals("admin", data.path("account").path("username").asText());
        assertEquals("admin", data.path("context").path("account").path("username").asText());
        assertEquals("PLATFORM", data.path("context").path("type").asText());
        assertEquals(PLATFORM_PERMISSIONS, stringSet(data.path("context").path("permissions")));
        assertEquals(PLATFORM_SHELLS, stringSet(data.path("context").path("shells")));
        assertTrue(data.path("systems").isArray());
        assertTrue(data.path("systems").isEmpty());
    }

    private static Set<String> stringSet(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void assertStoredHashOnly(String raw, String storedHash, String table) {
        assertEquals(sha256(raw), storedHash);
        assertTrue(storedHash.matches("[0-9a-f]{64}"));
        assertFalse(storedHash.equals(raw));
        assertEquals(0L, count("SELECT COUNT(*) FROM " + table + " WHERE token_hash=?", raw));
    }

    private void assertFamilyRevoked(String tokenFamily, long contextSessionId) {
        var statuses = jdbc.queryForList(
                "SELECT status FROM un_plat_refresh_token WHERE token_family=? ORDER BY id",
                String.class,
                tokenFamily
        );
        assertTrue(statuses.size() >= 2);
        assertTrue(statuses.stream().allMatch("REVOKED"::equals));
        assertEquals("REVOKED", contextById(contextSessionId).status());
    }

    private List<String> refreshStatusesForContext(long contextSessionId) {
        var statuses = jdbc.queryForList(
                "SELECT status FROM un_plat_refresh_token WHERE context_session_id=? ORDER BY id",
                String.class,
                contextSessionId
        );
        assertFalse(statuses.isEmpty());
        return statuses;
    }

    private StoredContext contextByHash(String tokenHash) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id,token_hash,status FROM un_plat_context_session WHERE token_hash=?",
                (resultSet, rowNumber) -> new StoredContext(
                        resultSet.getLong("id"),
                        resultSet.getString("token_hash"),
                        resultSet.getString("status")
                ),
                tokenHash
        ));
    }

    private StoredContext contextById(long id) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id,token_hash,status FROM un_plat_context_session WHERE id=?",
                (resultSet, rowNumber) -> new StoredContext(
                        resultSet.getLong("id"),
                        resultSet.getString("token_hash"),
                        resultSet.getString("status")
                ),
                id
        ));
    }

    private StoredRefresh refreshByHash(String tokenHash) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id,context_session_id,token_hash,token_family,status,rotated_to_id "
                        + "FROM un_plat_refresh_token WHERE token_hash=?",
                (resultSet, rowNumber) -> storedRefresh(resultSet),
                tokenHash
        ));
    }

    private StoredRefresh refreshById(long id) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT id,context_session_id,token_hash,token_family,status,rotated_to_id "
                        + "FROM un_plat_refresh_token WHERE id=?",
                (resultSet, rowNumber) -> storedRefresh(resultSet),
                id
        ));
    }

    private static StoredRefresh storedRefresh(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new StoredRefresh(
                resultSet.getLong("id"),
                resultSet.getLong("context_session_id"),
                resultSet.getString("token_hash"),
                resultSet.getString("token_family"),
                resultSet.getString("status"),
                resultSet.getObject("rotated_to_id", Long.class)
        );
    }

    private long count(String sql, Object... arguments) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, arguments));
    }

    private HttpResponse<String> login(String password) throws Exception {
        var body = objectMapper.writeValueAsString(Map.of("account", "admin", "password", password));
        return post("/api/v1/auth/login", body, new CookieJar(), false);
    }

    private HttpResponse<String> get(String path, CookieJar cookies) throws Exception {
        return send("GET", path, null, cookies, false);
    }

    private HttpResponse<String> post(
            String path,
            String body,
            CookieJar cookies,
            boolean csrf
    ) throws Exception {
        return send("POST", path, body, cookies, csrf);
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String body,
            CookieJar cookies,
            boolean csrf
    ) throws Exception {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "p1-login-cycle-test")
                .header("X-Request-ID", "p1-login-cycle-" + requestSequence.incrementAndGet());
        var cookieHeader = cookies.header();
        if (!cookieHeader.isBlank()) {
            builder.header("Cookie", cookieHeader);
        }
        if (csrf) {
            builder.header("X-CSRF-Token", cookies.required(VNextSessionCookieSupport.CSRF_COOKIE));
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private List<HttpResponse<String>> concurrentRefresh(CookieJar cookies) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<HttpResponse<String>> request = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent refresh start was not released");
            }
            return post("/api/v1/auth/refresh", null, cookies.copy(), true);
        };
        try {
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private String code(HttpResponse<String> response) throws Exception {
        return json(response).path("code").asText();
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private static void assertNoSessionCookies(HttpResponse<String> response) {
        var cookies = response.headers().allValues("Set-Cookie");
        assertTrue(cookies.stream().noneMatch(value -> value.startsWith(
                VNextSessionCookieSupport.ACCESS_COOKIE + "=")));
        assertTrue(cookies.stream().noneMatch(value -> value.startsWith(
                VNextSessionCookieSupport.REFRESH_COOKIE + "=")));
    }

    private static void assertSessionCookiePolicy(HttpResponse<String> response) {
        var access = setCookie(response, VNextSessionCookieSupport.ACCESS_COOKIE);
        var refresh = setCookie(response, VNextSessionCookieSupport.REFRESH_COOKIE);
        var csrf = setCookie(response, VNextSessionCookieSupport.CSRF_COOKIE);
        assertHttpOnlySameSite(access);
        assertHttpOnlySameSite(refresh);
        assertTrue(csrf.toLowerCase(java.util.Locale.ROOT).contains("samesite=lax"));
        assertFalse(csrf.toLowerCase(java.util.Locale.ROOT).contains("httponly"));
        assertFalse(access.toLowerCase(java.util.Locale.ROOT).contains("; secure"));
        assertFalse(refresh.toLowerCase(java.util.Locale.ROOT).contains("; secure"));
    }

    private static void assertHttpOnlySameSite(String cookie) {
        var lower = cookie.toLowerCase(java.util.Locale.ROOT);
        assertTrue(lower.contains("httponly"));
        assertTrue(lower.contains("samesite=lax"));
    }

    private static void assertClearedSessionCookies(HttpResponse<String> response) {
        for (var name : List.of(
                VNextSessionCookieSupport.ACCESS_COOKIE,
                VNextSessionCookieSupport.REFRESH_COOKIE,
                VNextSessionCookieSupport.CSRF_COOKIE
        )) {
            var cookie = setCookie(response, name);
            assertTrue(cookie.startsWith(name + "=;"));
            assertTrue(cookie.toLowerCase(java.util.Locale.ROOT).contains("max-age=0"));
        }
    }

    private static String setCookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Required session cookie is missing"));
    }

    private static void assertResponseHasNoTokens(
            HttpResponse<String> response,
            String accessToken,
            String refreshToken
    ) {
        assertFalse(response.body().contains(accessToken));
        assertFalse(response.body().contains(refreshToken));
        assertFalse(response.body().contains("accessToken"));
        assertFalse(response.body().contains("refreshToken"));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record StoredContext(long id, String tokenHash, String status) {
    }

    private record StoredRefresh(
            long id,
            long contextSessionId,
            String tokenHash,
            String tokenFamily,
            String status,
            Long rotatedToId
    ) {
    }

    private static final class CookieJar {
        private final Map<String, String> values = new LinkedHashMap<>();

        private void capture(HttpResponse<String> response) {
            for (var header : response.headers().allValues("Set-Cookie")) {
                var pair = header.substring(0, header.indexOf(';'));
                var separator = pair.indexOf('=');
                if (separator > 0) {
                    values.put(pair.substring(0, separator), pair.substring(separator + 1));
                }
            }
        }

        private String required(String name) {
            var value = values.get(name);
            assertNotNull(value);
            assertFalse(value.isBlank());
            return value;
        }

        private void put(String name, String value) {
            values.put(name, value);
        }

        private String header() {
            return values.entrySet().stream()
                    .filter(entry -> !entry.getValue().isBlank())
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining("; "));
        }

        private CookieJar copy() {
            var copy = new CookieJar();
            copy.values.putAll(values);
            return copy;
        }
    }
}
