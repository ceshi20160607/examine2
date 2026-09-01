package com.unique.unexamine.backgroundjobs.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(BackgroundJobHttpTest.JobTestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"unexamine.jobs.poll-delay-ms=600000", "unexamine.jobs.stale-seconds=1"})
class BackgroundJobHttpTest {
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

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private BackgroundJobWorker worker;

    @Test
    void redisRunnerPersistsProgressReclaimsHeartbeatAndStopsAtRetryLimit() {
        Fixture owner = register("cycle20_jobs_owner", "cycle20_jobs_system");
        Fixture isolated = register("cycle20_jobs_isolated", "cycle20_jobs_isolated_system");
        String base = "/api/admin/system/background-jobs";

        ResponseEntity<Map> handlerResponse = exchange(base + "/handlers", HttpMethod.GET, owner.token(), null);
        assertThat(handlerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) handlerResponse.getBody().get("data")).hasSizeGreaterThanOrEqualTo(5);

        long statisticsId = submit(owner, "SYSTEM_STATISTICS_REFRESH", 3);
        assertThat(redis.opsForZSet().score(BackgroundJobService.READY_QUEUE, String.valueOf(statisticsId)))
                .isNotNull().isLessThanOrEqualTo((double) System.currentTimeMillis());
        Map<String, Object> statistics = runUntil(owner, statisticsId, "SUCCEEDED");
        assertThat(job(statistics)).containsEntry("status", "SUCCEEDED")
                .containsEntry("progressCurrent", 3).containsEntry("progressTotal", 3)
                .containsEntry("attemptCount", 1);
        assertThat((List<?>) statistics.get("items")).hasSize(3);
        assertThat(map(job(statistics).get("redisState")))
                .containsEntry("status", "SUCCEEDED").containsEntry("source", "REDIS");
        assertThat(redis.opsForHash().get(BackgroundJobService.STATUS_PREFIX + statisticsId, "status"))
                .isEqualTo("SUCCEEDED");
        assertThat(count("job_background", "id=" + statisticsId + " and status='SUCCEEDED'" )).isOne();
        assertThat(count("job_attempt", "job_id=" + statisticsId + " and status='SUCCEEDED'" )).isOne();
        assertThat(count("job_item_result", "job_id=" + statisticsId + " and status='SUCCEEDED'" )).isEqualTo(3);

        ResponseEntity<Map> isolatedRead = exchange(base + "/" + statisticsId, HttpMethod.GET, isolated.token(), null);
        assertThat(isolatedRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(isolatedRead.getBody()).containsEntry("code", "JOB_NOT_FOUND");

        long retryId = submit(owner, "CYCLE20_RETRY", 3);
        Map<String, Object> waiting = runUntil(owner, retryId, "RETRY_WAIT");
        assertThat(job(waiting)).containsEntry("status", "RETRY_WAIT").containsEntry("attemptCount", 1)
                .containsEntry("errorCode", "JOB_EXECUTION_FAILED");
        ResponseEntity<Map> retried = exchange(base + "/" + retryId + "/retry", HttpMethod.POST, owner.token(),
                Map.of("reason", "真实失败项已检查，立即执行允许的第二次尝试"));
        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(job(data(retried))).containsEntry("status", "QUEUED");
        Map<String, Object> retrySucceeded = runUntil(owner, retryId, "SUCCEEDED");
        assertThat(job(retrySucceeded)).containsEntry("status", "SUCCEEDED").containsEntry("attemptCount", 2);
        assertThat((List<?>) retrySucceeded.get("attempts")).hasSize(2);
        assertThat(count("job_attempt", "job_id=" + retryId + " and status='FAILED'" )).isOne();
        assertThat(count("job_attempt", "job_id=" + retryId + " and status='SUCCEEDED'" )).isOne();

        long staleId = submit(owner, "CYCLE20_STALE", 3);
        redis.opsForZSet().remove(BackgroundJobService.READY_QUEUE, String.valueOf(staleId));
        jdbc.update("update job_background set status='RUNNING', attempt_count=1, "
                + "started_at=timestampadd(minute,-2,now(3)), heartbeat_at=timestampadd(minute,-2,now(3)), "
                + "next_run_at=null where id=?", staleId);
        jdbc.update("insert into job_attempt(job_id,attempt_number,worker_id,status,started_at) "
                + "values(?,1,'dead-worker','RUNNING',timestampadd(minute,-2,now(3)))", staleId);
        worker.recoverStale();
        assertThat(job(detail(owner, staleId))).containsEntry("status", "RETRY_WAIT")
                .containsEntry("errorCode", "WORKER_HEARTBEAT_EXPIRED");
        assertThat(count("job_attempt", "job_id=" + staleId + " and status='TIMED_OUT'" )).isOne();
        ResponseEntity<Map> staleRetry = exchange(base + "/" + staleId + "/retry", HttpMethod.POST, owner.token(),
                Map.of("reason", "心跳过期后重新领取"));
        assertThat(staleRetry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(job(runUntil(owner, staleId, "SUCCEEDED"))).containsEntry("status", "SUCCEEDED")
                .containsEntry("attemptCount", 2);

        long exhaustedId = submit(owner, "CYCLE20_ALWAYS_FAIL", 2);
        assertThat(job(runUntil(owner, exhaustedId, "RETRY_WAIT"))).containsEntry("status", "RETRY_WAIT");
        assertThat(exchange(base + "/" + exhaustedId + "/retry", HttpMethod.POST, owner.token(),
                Map.of("reason", "第二次且最后一次尝试")).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> exhausted = runUntil(owner, exhaustedId, "PERMANENT_FAILED");
        assertThat(job(exhausted)).containsEntry("status", "PERMANENT_FAILED").containsEntry("attemptCount", 2);
        ResponseEntity<Map> forbiddenRetry = exchange(base + "/" + exhaustedId + "/retry", HttpMethod.POST,
                owner.token(), Map.of("reason", "不允许无限循环"));
        assertThat(forbiddenRetry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(forbiddenRetry.getBody()).containsEntry("code", "JOB_ATTEMPTS_EXHAUSTED");
        assertThat(count("job_attempt", "job_id=" + exhaustedId)).isEqualTo(2);
        assertThat(count("audit_event", "event_code='BACKGROUND_JOB_SUBMITTED'")).isEqualTo(4);
        assertThat(count("audit_event", "event_code='BACKGROUND_JOB_SUCCEEDED'")).isEqualTo(3);
        assertThat(count("audit_event", "event_code='BACKGROUND_JOB_FAILED'")).isEqualTo(3);
    }

    private long submit(Fixture fixture, String jobType, int maxAttempts) {
        ResponseEntity<Map> response = exchange("/api/admin/system/background-jobs", HttpMethod.POST, fixture.token(),
                Map.of("jobType", jobType, "sourceType", "SYSTEM_OPERATIONS", "sourceId", "cycle20",
                        "parameters", Map.of("requestedBy", "integration-journey"), "maxAttempts", maxAttempts));
        assertThat(response.getStatusCode()).as("submit %s: %s", jobType, response.getBody()).isEqualTo(HttpStatus.OK);
        return ((Number) job(data(response)).get("id")).longValue();
    }

    private Map<String, Object> detail(Fixture fixture, long jobId) {
        ResponseEntity<Map> response = exchange("/api/admin/system/background-jobs/" + jobId,
                HttpMethod.GET, fixture.token(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return data(response);
    }

    private Map<String, Object> runUntil(Fixture fixture, long jobId, String expectedStatus) {
        Map<String, Object> current = Map.of();
        for (int index = 0; index < 30; index++) {
            worker.poll();
            current = detail(fixture, jobId);
            if (expectedStatus.equals(job(current).get("status"))) return current;
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(job(current).get("status")).as("job detail: %s", job(current)).isEqualTo(expectedStatus);
        return current;
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", "后台作业管理员",
                "email", username + "@example.com", "systemName", "后台作业系统", "systemCode", systemCode), Map.class);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> job(Map<String, Object> detail) {
        return (Map<String, Object>) detail.get("job");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private record Fixture(String token) {
    }

    @TestConfiguration
    static class JobTestConfiguration {
        @Bean
        BackgroundJobHandler cycle20TestHandler() {
            return new BackgroundJobHandler() {
                @Override
                public Set<String> jobTypes() {
                    return Set.of("CYCLE20_RETRY", "CYCLE20_ALWAYS_FAIL", "CYCLE20_STALE");
                }

                @Override
                public String name(String jobType) {
                    return jobType;
                }

                @Override
                public String description(String jobType) {
                    return "周期二十有限重试和心跳恢复集成处理器";
                }

                @Override
                public long estimateTotal(String jobType, Map<String, Object> parameters) {
                    return 1;
                }

                @Override
                public Map<String, Object> execute(String jobType, Map<String, Object> parameters,
                                                   BackgroundJobExecution execution) {
                    if ("CYCLE20_ALWAYS_FAIL".equals(jobType)
                            || ("CYCLE20_RETRY".equals(jobType) && execution.attemptNumber() == 1)) {
                        execution.itemFailed("retryable-item", 1L, "TRANSIENT_TEST_FAILURE",
                                "第一次尝试按处理器规则失败", 1, 1);
                        throw new IllegalStateException("处理器返回可重试失败");
                    }
                    execution.itemSucceeded("completed-item", 1L,
                            Map.of("attempt", execution.attemptNumber()), 1, 1);
                    return Map.of("completedAttempt", execution.attemptNumber());
                }
            };
        }
    }
}
