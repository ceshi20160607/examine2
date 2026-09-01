package com.unique.unexamine.foundation.manage.control;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationControlHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
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
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void systemControlJourneyIsIdempotentIsolatedAndDatabaseAuthoritative() throws Exception {
        Fixture owner = register("foundation_owner", "foundation_system");
        String base = "/api/admin/system/foundation-control";

        ResponseEntity<Map> firstProbe = exchange(base + "/cache/permission", HttpMethod.GET, owner.systemToken(), null);
        ResponseEntity<Map> cachedProbe = exchange(base + "/cache/permission", HttpMethod.GET, owner.systemToken(), null);
        assertThat(firstProbe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(cachedProbe)).containsEntry("source", "REDIS");
        long firstEpoch = ((Number) data(firstProbe).get("epoch")).longValue();

        Map<String, Object> command = Map.of(
                "idempotencyKey", "cycle19-command-001",
                "applicationCode", "system-admin",
                "operationCode", "CONFIG_RELOAD",
                "payload", Map.of("reason", "integration journey"));
        ResponseEntity<Map> first = exchange(base + "/commands", HttpMethod.POST, owner.systemToken(), command);
        ResponseEntity<Map> replay = exchange(base + "/commands", HttpMethod.POST, owner.systemToken(), command);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getStatusCode()).as("replay response: %s", replay.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(data(first)).containsEntry("replayed", false).containsEntry("extensionStatus", "PENDING");
        assertThat(data(replay)).containsEntry("replayed", true).containsEntry("extensionStatus", "PUBLISHED");
        assertThat(data(replay).get("resultReference")).isEqualTo(data(first).get("resultReference"));
        assertThat(count("core_idempotency_record", "idempotency_key='cycle19-command-001'")).isOne();
        assertThat(count("core_event_outbox", "aggregate_type='FOUNDATION_COMMAND' and status='PUBLISHED'")).isOne();

        ResponseEntity<Map> changedPayload = exchange(base + "/commands", HttpMethod.POST, owner.systemToken(),
                Map.of("idempotencyKey", "cycle19-command-001", "applicationCode", "system-admin",
                        "operationCode", "CONFIG_RELOAD", "payload", Map.of("reason", "different")));
        assertThat(changedPayload.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changedPayload.getBody()).containsEntry("code", "IDEMPOTENCY_KEY_REUSED");
        assertThat(count("core_event_outbox", "aggregate_type='FOUNDATION_COMMAND'")).isOne();

        Map<String, Object> concurrentCommand = Map.of(
                "idempotencyKey", "cycle19-concurrent-001",
                "applicationCode", "system-admin",
                "operationCode", "PERMISSION_CONTEXT_REFRESH",
                "payload", Map.of("reason", "parallel duplicate"));
        List<Callable<ResponseEntity<Map>>> duplicateCalls = List.of(
                () -> exchange(base + "/commands", HttpMethod.POST, owner.systemToken(), concurrentCommand),
                () -> exchange(base + "/commands", HttpMethod.POST, owner.systemToken(), concurrentCommand));
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<ResponseEntity<Map>> duplicates = executor.invokeAll(duplicateCalls).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).toList();
            assertThat(duplicates).allSatisfy(result ->
                    assertThat(result.getStatusCode()).as("parallel idempotency: %s", result.getBody()).isEqualTo(HttpStatus.OK));
            assertThat(duplicates.stream().map(result -> data(result).get("resultReference")).distinct()).hasSize(1);
        }
        assertThat(count("core_idempotency_record", "idempotency_key='cycle19-concurrent-001'")).isOne();
        assertThat(count("core_event_outbox", "aggregate_id=(select cast(id as char) from core_idempotency_record "
                + "where idempotency_key='cycle19-concurrent-001')")).isOne();

        for (int index = 0; index < 4; index++) {
            ResponseEntity<Map> allowed = command(owner, "rate-owner-" + index, index);
            assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<Map> denied = command(owner, "rate-owner-denied", 99);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(denied.getBody()).containsEntry("code", "RATE_LIMIT_EXCEEDED");
        assertThat(((Map<?, ?>) denied.getBody().get("data")).get("retryAfterSeconds")).isNotNull();

        ResponseEntity<Map> replayWhileLimited = exchange(base + "/commands", HttpMethod.POST, owner.systemToken(), command);
        assertThat(replayWhileLimited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(replayWhileLimited)).containsEntry("replayed", true);

        Fixture isolated = register("foundation_isolated", "foundation_isolated_system");
        ResponseEntity<Map> isolatedResult = exchange(base + "/commands", HttpMethod.POST, isolated.systemToken(), command);
        assertThat(isolatedResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(isolatedResult)).containsEntry("replayed", false);
        assertThat(count("core_idempotency_record", "idempotency_key='cycle19-command-001'")).isEqualTo(2);

        ResponseEntity<Map> invalidated = exchange(base + "/cache/permission/invalidate", HttpMethod.POST,
                owner.systemToken(), Map.of("reason", "integration version advance"));
        assertThat(invalidated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) data(invalidated).get("epoch")).longValue()).isEqualTo(firstEpoch + 1);
        assertThat(data(invalidated)).containsEntry("source", "DATABASE");
        ResponseEntity<Map> recached = exchange(base + "/cache/permission", HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(recached)).containsEntry("source", "REDIS");

        ResponseEntity<Map> flag = exchange(base + "/feature-flags", HttpMethod.POST, owner.systemToken(), Map.of(
                "flagKey", "cycle19.new-workspace", "enabled", true, "targetType", "ROLE",
                "targetCodes", List.of("system_admin"), "fallbackEnabled", false, "stableVariant", "stable"));
        assertThat(flag.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(flag)).containsEntry("version", 0).containsEntry("targetType", "ROLE");
        ResponseEntity<Map> flagHit = exchange(base + "/feature-flags/cycle19.new-workspace/resolve?targetCode=system_admin",
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(flagHit)).containsEntry("enabled", true).containsEntry("permissionStillRequired", true);
        ResponseEntity<Map> flagMiss = exchange(base + "/feature-flags/cycle19.new-workspace/resolve?targetCode=viewer",
                HttpMethod.GET, owner.systemToken(), null);
        assertThat(data(flagMiss)).containsEntry("enabled", false).containsEntry("permissionStillRequired", true);

        ResponseEntity<Map> quota = exchange(base + "/quotas", HttpMethod.POST, owner.systemToken(), Map.of(
                "quotaCode", "CYCLE19_EXPORTS", "periodType", "TOTAL", "hardLimit", 3, "warningThreshold", 2));
        assertThat(quota.getStatusCode()).isEqualTo(HttpStatus.OK);
        long quotaId = ((Number) data(quota).get("id")).longValue();
        List<Callable<ResponseEntity<Map>>> quotaCalls = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            int current = index;
            quotaCalls.add(() -> exchange(base + "/quotas/" + quotaId + "/consume", HttpMethod.POST,
                    owner.systemToken(), Map.of("amount", 1, "reference", "concurrent-" + current)));
        }
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<ResponseEntity<Map>> quotaResults = executor.invokeAll(quotaCalls).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).toList();
            assertThat(quotaResults.stream().filter(result -> result.getStatusCode() == HttpStatus.OK)).hasSize(3);
            assertThat(quotaResults.stream().filter(result -> result.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS))
                    .as("quota responses: %s", quotaResults.stream().map(result -> result.getStatusCode() + ":" + result.getBody()).toList())
                    .hasSize(3);
        }
        assertThat(jdbc.queryForObject("select used_value from core_quota_usage where quota_policy_id=?", Long.class, quotaId))
                .isEqualTo(3L);

        ResponseEntity<Map> sequence = exchange(base + "/sequences", HttpMethod.POST, owner.systemToken(), Map.of(
                "sequenceCode", "cycle19_order", "pattern", "C19-{yyyyMMdd}-{seq:4}",
                "resetPeriod", "DAY", "stepValue", 1));
        assertThat(sequence.getStatusCode()).isEqualTo(HttpStatus.OK);
        long sequenceId = ((Number) data(sequence).get("id")).longValue();
        List<Callable<ResponseEntity<Map>>> sequenceCalls = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            sequenceCalls.add(() -> exchange(base + "/sequences/" + sequenceId + "/next", HttpMethod.POST,
                    owner.systemToken(), null));
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<ResponseEntity<Map>> allocated = executor.invokeAll(sequenceCalls).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).toList();
            assertThat(allocated).allSatisfy(result -> assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK));
            assertThat(new HashSet<>(allocated.stream().map(result -> data(result).get("value")).toList())).hasSize(8);
        }
        assertThat(jdbc.queryForObject("select current_value from core_sequence where id=?", Long.class, sequenceId))
                .isEqualTo(8L);
        assertThat(count("audit_event", "event_code in ('FOUNDATION_FEATURE_FLAG_SAVED','FOUNDATION_QUOTA_SAVED','FOUNDATION_SEQUENCE_SAVED')"))
                .isEqualTo(3);
    }

    private ResponseEntity<Map> command(Fixture fixture, String key, int index) {
        return exchange("/api/admin/system/foundation-control/commands", HttpMethod.POST, fixture.systemToken(), Map.of(
                "idempotencyKey", key, "applicationCode", "system-admin", "operationCode", "CONFIG_RELOAD",
                "payload", Map.of("index", index)));
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", "基础能力管理员",
                "email", username + "@example.com", "systemName", "基础能力系统", "systemCode", systemCode), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Fixture((String) ((Map<?, ?>) data(registration).get("tokens")).get("accessToken"));
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private record Fixture(String systemToken) {
    }
}
