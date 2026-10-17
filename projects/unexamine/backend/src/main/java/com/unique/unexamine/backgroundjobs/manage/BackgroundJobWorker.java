package com.unique.unexamine.backgroundjobs.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.backgroundjobs.base.entity.JobBackground;
import com.unique.unexamine.backgroundjobs.base.service.JobBackgroundBaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Redis 工作器：运行态和重试事实留在 Redis，终态经 Base 写入业务审计表。 */
@Component
public class BackgroundJobWorker {
    private static final DefaultRedisScript<Long> EXTEND_LOCK = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final BackgroundJobService service;
    private final JobBackgroundBaseService jobs;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final AuditRecorder auditRecorder;
    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private final long staleSeconds;
    private final long lockSeconds;

    public BackgroundJobWorker(BackgroundJobService service, JobBackgroundBaseService jobs,
                               JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper objectMapper,
                               TransactionTemplate transactions, AuditRecorder auditRecorder,
                               @Value("${unexamine.jobs.stale-seconds:30}") long staleSeconds,
                               @Value("${unexamine.jobs.lock-seconds:45}") long lockSeconds) {
        this.service = service;
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.auditRecorder = auditRecorder;
        this.staleSeconds = staleSeconds;
        this.lockSeconds = lockSeconds;
    }

    @Scheduled(fixedDelayString = "${unexamine.jobs.poll-delay-ms:500}")
    public void poll() {
        try {
            recoverStale();
            recoverQueue();
            runOnce();
        } catch (RuntimeException ignored) {
            // Redis 或数据库短暂不可用时，下一轮重新扫描未完成的业务申请。
        }
    }

    public boolean runOnce() {
        ZSetOperations.TypedTuple<String> due;
        try { due = redis.opsForZSet().popMin(service.readyQueue()); }
        catch (RuntimeException exception) { return false; }
        if (due == null || due.getValue() == null) return false;
        if (due.getScore() != null && due.getScore() > System.currentTimeMillis()) {
            redis.opsForZSet().add(service.readyQueue(), due.getValue(), due.getScore());
            return false;
        }
        long jobId;
        try { jobId = Long.parseLong(due.getValue()); }
        catch (NumberFormatException exception) { return false; }
        String lockKey = service.lockKey(jobId);
        String lockToken = workerId + ':' + UUID.randomUUID();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofSeconds(lockSeconds)))) {
            service.enqueue(jobId, LocalDateTime.now().plusSeconds(1));
            return false;
        }
        try {
            JobBackground job = claim(jobId);
            if (job == null) return false;
            execute(job, lockKey, lockToken);
            return true;
        } finally {
            try { redis.execute(RELEASE_LOCK, List.of(lockKey), lockToken); }
            catch (RuntimeException ignored) { /* 令牌锁到期后由 Redis 自行释放。 */ }
        }
    }

    /** 锁已失效且心跳过期的作业才允许恢复，避免与仍在工作的处理器同时执行。 */
    public void recoverStale() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(staleSeconds);
        for (JobBackground job : pending()) {
            Map<Object, Object> state = service.runtime(job);
            if (!"RUNNING".equals(state.get("status"))) continue;
            LocalDateTime heartbeat = date(state.get("heartbeatAt"));
            if (heartbeat != null && !heartbeat.isBefore(cutoff)) continue;
            if (Boolean.TRUE.equals(redis.hasKey(service.lockKey(job.getId())))) continue;
            int attempt = (int) number(state.get("attemptCount"), 0);
            int max = (int) number(state.get("maxAttempts"), 3);
            boolean exhausted = attempt >= max;
            service.finishAttempt(job.getId(), "TIMED_OUT", "WORKER_HEARTBEAT_EXPIRED", "工作器心跳过期");
            if (exhausted) {
                if (!finishDatabase(job, "FAILED", Map.of("attemptCount", attempt),
                        "WORKER_HEARTBEAT_EXPIRED", "工作器心跳过期，已达到重试上限")) continue;
                service.updateRuntime(job.getId(), Map.of("status", "PERMANENT_FAILED",
                        "updatedAt", LocalDateTime.now().toString()));
                service.expireRuntime(job.getId());
            } else {
                LocalDateTime now = LocalDateTime.now();
                service.updateRuntime(job.getId(), Map.of("status", "RETRY_WAIT", "nextRunAt", now.toString(),
                        "heartbeatAt", "", "errorCode", "WORKER_HEARTBEAT_EXPIRED",
                        "errorMessage", "工作器心跳过期", "updatedAt", now.toString()));
                service.enqueue(job.getId(), now);
            }
            try {
                auditRecorder.record("job-" + job.getId() + "-heartbeat-timeout", job.getCreatedByAccountId(),
                        job.getSystemId(), job.getTenantId(), null, "BACKGROUND_JOB_HEARTBEAT_EXPIRED",
                        "BACKGROUND_JOB", String.valueOf(job.getId()),
                        exhausted ? "PERMANENT_FAILED" : "RETRY_WAIT",
                        Map.of("attempt", attempt, "maxAttempts", max));
            } catch (RuntimeException ignored) { /* 主业务终态已经写入，审计副写失败不重复执行。 */ }
        }
    }

    private void recoverQueue() {
        LocalDateTime now = LocalDateTime.now();
        for (JobBackground job : pending()) {
            Map<Object, Object> state = service.runtime(job);
            String status = String.valueOf(state.getOrDefault("status", "QUEUED"));
            if (!List.of("QUEUED", "RETRY_WAIT").contains(status)) continue;
            LocalDateTime next = date(state.get("nextRunAt"));
            if (next == null || !next.isAfter(now)) service.enqueue(job.getId(), now);
        }
    }

    private List<JobBackground> pending() {
        return jobs.selectList(new LambdaQueryWrapper<JobBackground>()
                .isNull(JobBackground::getFinalStatus)
                .orderByAsc(JobBackground::getCreateTime).last("limit 200"));
    }

    private JobBackground claim(long jobId) {
        JobBackground job = jobs.selectById(jobId);
        if (job == null || job.getFinalStatus() != null) return null;
        Map<Object, Object> state = service.runtime(job);
        String status = String.valueOf(state.getOrDefault("status", "QUEUED"));
        if (!List.of("QUEUED", "RETRY_WAIT").contains(status)) return null;
        LocalDateTime next = date(state.get("nextRunAt"));
        if (next != null && next.isAfter(LocalDateTime.now())) {
            service.enqueue(jobId, next);
            return null;
        }
        int count = (int) number(state.get("attemptCount"), 0);
        int max = (int) number(state.get("maxAttempts"), 3);
        if (count >= max) return null;
        LocalDateTime now = LocalDateTime.now();
        service.updateRuntime(jobId, Map.of("status", "RUNNING", "attemptCount", String.valueOf(count + 1),
                "heartbeatAt", now.toString(), "startedAt",
                state.get("startedAt") == null ? now.toString() : String.valueOf(state.get("startedAt")),
                "nextRunAt", "", "errorCode", "", "errorMessage", "", "updatedAt", now.toString()));
        service.beginAttempt(jobId, count + 1, workerId, now);
        return job;
    }

    private void execute(JobBackground job, String lockKey, String lockToken) {
        int attempt = (int) number(service.runtime(job).get("attemptCount"), 1);
        Map<String, Object> result;
        try {
            assertHardLimits(job);
            BackgroundJobHandler handler = service.handler(job.getJobType());
            if (handler == null) throw new IllegalStateException("Job handler is no longer registered");
            BackgroundJobExecution execution = new BackgroundJobExecution(job.getId(), attempt,
                    new BackgroundJobExecution.Reporter() {
                        @Override public void heartbeat(long processed, long total) {
                            reportProgress(job.getId(), processed, total, lockKey, lockToken);
                        }
                        @Override public void item(String itemKey, Long rowNumber, String itemStatus,
                                                   Map<String, Object> result, String errorCode,
                                                   String errorMessage, long processed, long total) {
                            if (!ownsLock(lockKey, lockToken)) throw new IllegalStateException("作业锁已失效");
                            service.recordItem(job.getId(), itemKey, rowNumber, itemStatus, result,
                                    errorCode, truncate(errorMessage));
                            reportProgress(job.getId(), processed, total, lockKey, lockToken);
                        }
                    });
            result = handler.execute(job.getJobType(), service.parameters(job), execution);
        } catch (Exception exception) {
            fail(job, attempt, exception, lockKey, lockToken);
            return;
        }
        // 业务执行成功后的归档异常不能被当作处理器失败重试，否则可能重复业务副作用。
        succeed(job, result == null ? Map.of() : result, lockKey, lockToken);
    }

    private void assertHardLimits(JobBackground job) {
        Integer active = jdbc.queryForObject("select count(*) from un_sys_system s join un_sys_tenant t on t.system_id=s.id "
                        + "where s.id=? and t.id=? and s.status='ACTIVE' and t.status='ACTIVE'",
                Integer.class, job.getSystemId(), job.getTenantId());
        if (active == null || active != 1) throw new IllegalStateException("System or tenant is no longer active");
    }

    private void reportProgress(long jobId, long processed, long total, String lockKey, String lockToken) {
        if (!ownsLock(lockKey, lockToken)) throw new IllegalStateException("作业锁已失效");
        Long extended = redis.execute(EXTEND_LOCK, List.of(lockKey), lockToken, String.valueOf(lockSeconds * 1000));
        if (extended == null || extended != 1L) throw new IllegalStateException("作业锁续期失败");
        service.updateRuntime(jobId, Map.of("progressCurrent", String.valueOf(Math.max(0, processed)),
                "progressTotal", String.valueOf(Math.max(0, total)), "heartbeatAt", LocalDateTime.now().toString(),
                "updatedAt", LocalDateTime.now().toString()));
    }

    private void succeed(JobBackground job, Map<String, Object> result, String lockKey, String lockToken) {
        if (!ownsLock(lockKey, lockToken)) return;
        if (!finishDatabase(job, "SUCCEEDED", result, null, null)) return;
        service.finishAttempt(job.getId(), "SUCCEEDED", null, null);
        Map<Object, Object> state = service.runtime(jobs.selectById(job.getId()));
        service.updateRuntime(job.getId(), Map.of("status", "SUCCEEDED",
                "progressCurrent", String.valueOf(number(state.get("progressTotal"), 0)),
                "updatedAt", LocalDateTime.now().toString()));
        service.expireRuntime(job.getId());
        try {
            auditRecorder.record("job-" + job.getId() + "-attempt-" + number(state.get("attemptCount"), 1),
                    job.getCreatedByAccountId(), job.getSystemId(), job.getTenantId(), null,
                    "BACKGROUND_JOB_SUCCEEDED", "BACKGROUND_JOB", String.valueOf(job.getId()), "SUCCEEDED",
                    Map.of("attempt", number(state.get("attemptCount"), 1), "summary", result));
        } catch (RuntimeException ignored) { /* 终态已归档，不因附加审计失败而重复执行。 */ }
    }

    private void fail(JobBackground job, int attempt, Exception exception, String lockKey, String lockToken) {
        if (!ownsLock(lockKey, lockToken)) return;
        Map<Object, Object> state = service.runtime(job);
        int max = (int) number(state.get("maxAttempts"), 3);
        boolean exhausted = attempt >= max;
        String code = exception instanceof IllegalArgumentException ? "JOB_PARAMETER_INVALID" : "JOB_EXECUTION_FAILED";
        String message = truncate(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        service.finishAttempt(job.getId(), "FAILED", code, message);
        if (exhausted) {
            if (!finishDatabase(job, "FAILED", Map.of("attemptCount", attempt), code, message)) return;
            service.updateRuntime(job.getId(), Map.of("status", "PERMANENT_FAILED", "updatedAt", LocalDateTime.now().toString()));
            service.expireRuntime(job.getId());
        } else {
            long delay = Math.min(30, 1L << attempt);
            LocalDateTime next = LocalDateTime.now().plusSeconds(delay);
            service.updateRuntime(job.getId(), Map.of("status", "RETRY_WAIT", "nextRunAt", next.toString(),
                    "errorCode", code, "errorMessage", message, "updatedAt", LocalDateTime.now().toString()));
            service.enqueue(job.getId(), next);
        }
        try {
            auditRecorder.record("job-" + job.getId() + "-attempt-" + attempt, job.getCreatedByAccountId(),
                    job.getSystemId(), job.getTenantId(), null, "BACKGROUND_JOB_FAILED", "BACKGROUND_JOB",
                    String.valueOf(job.getId()), exhausted ? "PERMANENT_FAILED" : "RETRY_WAIT",
                    Map.of("attempt", attempt, "maxAttempts", max, "errorCode", code, "willRetry", !exhausted));
        } catch (RuntimeException ignored) { /* 重试运行态已写入 Redis。 */ }
    }

    /** 只写一次终态；乐观锁避免超时恢复与工作器完成同时覆盖最终结果。 */
    private boolean finishDatabase(JobBackground job, String finalStatus, Map<String, Object> result,
                                   String errorCode, String errorMessage) {
        Boolean written = transactions.execute(status -> {
            JobBackground current = jobs.selectById(job.getId());
            if (current == null || current.getFinalStatus() != null) return false;
            current.setFinalStatus(finalStatus);
            current.setResultSummaryJson(json(result));
            current.setErrorCode(errorCode);
            current.setErrorMessage(errorMessage);
            current.setFinishTime(LocalDateTime.now());
            current.setUpdateTime(LocalDateTime.now());
            return jobs.updateById(current) == 1;
        });
        return Boolean.TRUE.equals(written);
    }

    private boolean ownsLock(String key, String token) {
        try { return token.equals(redis.opsForValue().get(key)); }
        catch (RuntimeException exception) { return false; }
    }

    private LocalDateTime date(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return LocalDateTime.parse(String.valueOf(value)); }
        catch (DateTimeParseException exception) { return null; }
    }

    private long number(Object value, long fallback) {
        if (value == null) return fallback;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { return "{}"; }
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(1900, value.length()));
    }
}
