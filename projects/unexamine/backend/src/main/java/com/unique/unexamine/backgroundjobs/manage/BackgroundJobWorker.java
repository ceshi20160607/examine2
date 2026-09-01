package com.unique.unexamine.backgroundjobs.manage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.backgroundjobs.base.entity.JobAttempt;
import com.unique.unexamine.backgroundjobs.base.entity.JobBackground;
import com.unique.unexamine.backgroundjobs.base.service.JobAttemptBaseService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final JobAttemptBaseService attempts;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final AuditRecorder auditRecorder;
    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private final long staleSeconds;
    private final long lockSeconds;

    public BackgroundJobWorker(BackgroundJobService service,
                               JobBackgroundBaseService jobs,
                               JobAttemptBaseService attempts,
                               JdbcTemplate jdbc,
                               StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               TransactionTemplate transactions,
                               AuditRecorder auditRecorder,
                               @Value("${unexamine.jobs.stale-seconds:30}") long staleSeconds,
                               @Value("${unexamine.jobs.lock-seconds:45}") long lockSeconds) {
        this.service = service;
        this.jobs = jobs;
        this.attempts = attempts;
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
            // Redis/database health is exposed in Operations. The next finite poll retries coordination.
        }
    }

    public boolean runOnce() {
        ZSetOperations.TypedTuple<String> due;
        try {
            due = redis.opsForZSet().popMin(BackgroundJobService.READY_QUEUE);
        } catch (RuntimeException exception) {
            return false;
        }
        if (due == null || due.getValue() == null) return false;
        if (due.getScore() != null && due.getScore() > System.currentTimeMillis()) {
            redis.opsForZSet().add(BackgroundJobService.READY_QUEUE, due.getValue(), due.getScore());
            return false;
        }
        long jobId;
        try {
            jobId = Long.parseLong(due.getValue());
        } catch (NumberFormatException exception) {
            return false;
        }
        String lockKey = BackgroundJobService.LOCK_PREFIX + jobId;
        String lockToken = workerId + ':' + UUID.randomUUID();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofSeconds(lockSeconds));
        if (!Boolean.TRUE.equals(locked)) {
            service.enqueue(jobId, LocalDateTime.now().plusSeconds(1));
            return false;
        }
        try {
            JobBackground job = claim(jobId);
            if (job == null) return false;
            execute(job, lockKey, lockToken);
            return true;
        } finally {
            try {
                redis.execute(RELEASE_LOCK, List.of(lockKey), lockToken);
            } catch (RuntimeException ignored) {
                // Token-owned lock has a short TTL and cannot be deleted by another worker.
            }
        }
    }

    public void recoverStale() {
        LocalDateTime cutoff = jdbc.queryForObject("select timestampadd(second, ?, now(3))",
                LocalDateTime.class, -staleSeconds);
        List<Map<String, Object>> stale = jdbc.queryForList(
                "select id, attempt_count, max_attempts from job_background where status='RUNNING' and heartbeat_at < ? limit 50",
                cutoff);
        for (Map<String, Object> row : stale) {
            long id = ((Number) row.get("id")).longValue();
            int attempt = ((Number) row.get("attempt_count")).intValue();
            int max = ((Number) row.get("max_attempts")).intValue();
            boolean exhausted = attempt >= max;
            LocalDateTime databaseNow = jdbc.queryForObject("select now(3)", LocalDateTime.class);
            int updated = jdbc.update("update job_background set status=?, next_run_at=?, finished_at=?, "
                            + "error_code='WORKER_HEARTBEAT_EXPIRED', error_message='工作器心跳过期，已安全释放领取', "
                            + "version=version+1 where id=? and status='RUNNING' and heartbeat_at < ?",
                    exhausted ? "PERMANENT_FAILED" : "RETRY_WAIT",
                    exhausted ? null : databaseNow, exhausted ? databaseNow : null, id, cutoff);
            if (updated == 1) {
                jdbc.update("update job_attempt set status='TIMED_OUT', finished_at=now(3), "
                        + "error_code='WORKER_HEARTBEAT_EXPIRED', error_message='工作器心跳过期' "
                        + "where job_id=? and attempt_number=? and status='RUNNING'", id, attempt);
                JobBackground job = jobs.selectById(id);
                service.publishRedisState(job);
                if (!exhausted) service.enqueue(id, LocalDateTime.now());
                try {
                    auditRecorder.record("job-" + id + "-heartbeat-timeout", job.getCreatedByAccountId(), job.getSystemId(),
                            job.getTenantId(), null, "BACKGROUND_JOB_HEARTBEAT_EXPIRED", "BACKGROUND_JOB",
                            String.valueOf(id), exhausted ? "PERMANENT_FAILED" : "RETRY_WAIT",
                            Map.of("attempt", attempt, "maxAttempts", max));
                } catch (RuntimeException ignored) {
                    // Recovery state is already durable; audit unavailability must not create a duplicate claim.
                }
            }
        }
    }

    private void recoverQueue() {
        List<Map<String, Object>> due = jdbc.queryForList(
                "select id, next_run_at from job_background where status in ('QUEUED','RETRY_WAIT') "
                        + "and (next_run_at is null or next_run_at <= now(3)) and attempt_count < max_attempts "
                        + "order by created_at limit 50");
        for (Map<String, Object> row : due) {
            long id = ((Number) row.get("id")).longValue();
            service.enqueue(id, LocalDateTime.now());
        }
    }

    private JobBackground claim(long jobId) {
        return transactions.execute(status -> {
            int updated = jdbc.update("update job_background set status='RUNNING', attempt_count=attempt_count+1, "
                            + "started_at=coalesce(started_at, now(3)), heartbeat_at=now(3), next_run_at=null, "
                            + "error_code=null, error_message=null, version=version+1 where id=? "
                            + "and status in ('QUEUED','RETRY_WAIT') and (next_run_at is null or next_run_at <= now(3)) "
                            + "and attempt_count < max_attempts",
                    jobId);
            if (updated != 1) return null;
            JobBackground job = jobs.selectById(jobId);
            JobAttempt attempt = new JobAttempt();
            attempt.setJobId(jobId);
            attempt.setAttemptNumber(job.getAttemptCount());
            attempt.setWorkerId(workerId);
            attempt.setStatus("RUNNING");
            attempt.setStartedAt(LocalDateTime.now());
            attempts.insert(attempt);
            service.publishRedisState(job);
            return job;
        });
    }

    private void execute(JobBackground job, String lockKey, String lockToken) {
        int attempt = job.getAttemptCount();
        try {
            assertHardLimits(job);
            BackgroundJobHandler handler = service.handler(job.getJobType());
            if (handler == null) throw new IllegalStateException("Job handler is no longer registered");
            BackgroundJobExecution execution = new BackgroundJobExecution(job.getId(), attempt,
                    new BackgroundJobExecution.Reporter() {
                        @Override
                        public void heartbeat(long processed, long total) {
                            reportProgress(job.getId(), processed, total, lockKey, lockToken);
                        }

                        @Override
                        public void item(String itemKey, Long rowNumber, String itemStatus, Map<String, Object> result,
                                         String errorCode, String errorMessage, long processed, long total) {
                            reportItem(job.getId(), itemKey, rowNumber, itemStatus, result, errorCode, errorMessage);
                            reportProgress(job.getId(), processed, total, lockKey, lockToken);
                        }
                    });
            Map<String, Object> result = handler.execute(job.getJobType(), service.parameters(job), execution);
            succeed(job, result == null ? Map.of() : result);
        } catch (Exception exception) {
            fail(job, exception);
        }
    }

    private void assertHardLimits(JobBackground job) {
        Integer active = jdbc.queryForObject("select count(*) from sys_system s join sys_tenant t on t.system_id=s.id "
                        + "where s.id=? and t.id=? and s.status='ACTIVE' and t.status='ACTIVE'",
                Integer.class, job.getSystemId(), job.getTenantId());
        if (active == null || active != 1) {
            throw new IllegalStateException("System or tenant is no longer active");
        }
    }

    private void reportProgress(long jobId, long processed, long total, String lockKey, String lockToken) {
        jdbc.update("update job_background set progress_current=?, progress_total=?, heartbeat_at=now(3), "
                        + "version=version+1 where id=? and status='RUNNING'",
                Math.max(0, processed), Math.max(0, total), jobId);
        try {
            redis.execute(EXTEND_LOCK, List.of(lockKey), lockToken, String.valueOf(lockSeconds * 1000));
        } catch (RuntimeException ignored) {
            // Database heartbeat still allows deterministic recovery.
        }
        service.publishRedisState(jobs.selectById(jobId));
    }

    private void reportItem(long jobId, String itemKey, Long rowNumber, String status, Map<String, Object> result,
                            String errorCode, String errorMessage) {
        jdbc.update("insert into job_item_result(job_id,item_key,`row_number`,`status`,result_json,error_code,error_message) "
                        + "values(?,?,?,?,cast(? as json),?,?) on duplicate key update `row_number`=values(`row_number`), "
                        + "`status`=values(`status`),result_json=values(result_json),error_code=values(error_code), "
                        + "error_message=values(error_message),updated_at=now(3)",
                jobId, itemKey, rowNumber, status, json(result), errorCode, truncate(errorMessage));
    }

    private void succeed(JobBackground job, Map<String, Object> result) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("update job_background set status='SUCCEEDED', progress_current=progress_total, "
                            + "result_summary_json=cast(? as json), error_code=null, error_message=null, "
                            + "heartbeat_at=now(3), finished_at=now(3), version=version+1 where id=? and status='RUNNING'",
                    json(result), job.getId());
            jdbc.update("update job_attempt set status='SUCCEEDED', finished_at=now(3) "
                    + "where job_id=? and attempt_number=? and status='RUNNING'", job.getId(), job.getAttemptCount());
        });
        JobBackground reread = jobs.selectById(job.getId());
        service.publishRedisState(reread);
        try {
            auditRecorder.record("job-" + job.getId() + "-attempt-" + job.getAttemptCount(), job.getCreatedByAccountId(),
                    job.getSystemId(), job.getTenantId(), null, "BACKGROUND_JOB_SUCCEEDED", "BACKGROUND_JOB",
                    String.valueOf(job.getId()), "SUCCEEDED", Map.of("attempt", job.getAttemptCount(), "summary", result));
        } catch (RuntimeException ignored) {
            // A completed handler must never be retried only because the secondary audit write is unavailable.
        }
    }

    private void fail(JobBackground job, Exception exception) {
        boolean exhausted = job.getAttemptCount() >= job.getMaxAttempts();
        String code = exception instanceof IllegalArgumentException ? "JOB_PARAMETER_INVALID" : "JOB_EXECUTION_FAILED";
        String message = truncate(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        long retryDelaySeconds = Math.min(30, 1L << job.getAttemptCount());
        LocalDateTime retryAt = exhausted ? null : jdbc.queryForObject(
                "select timestampadd(second, ?, now(3))", LocalDateTime.class, retryDelaySeconds);
        transactions.executeWithoutResult(status -> {
            jdbc.update("update job_background set status=?, next_run_at=?, finished_at=?, error_code=?, error_message=?, "
                            + "heartbeat_at=now(3), version=version+1 where id=? and status='RUNNING'",
                    exhausted ? "PERMANENT_FAILED" : "RETRY_WAIT", retryAt,
                    exhausted ? LocalDateTime.now() : null, code, message, job.getId());
            jdbc.update("update job_attempt set status='FAILED', finished_at=now(3), error_code=?, error_message=? "
                            + "where job_id=? and attempt_number=? and status='RUNNING'",
                    code, message, job.getId(), job.getAttemptCount());
        });
        JobBackground reread = jobs.selectById(job.getId());
        service.publishRedisState(reread);
        if (!exhausted) service.enqueue(job.getId(), LocalDateTime.now().plusSeconds(retryDelaySeconds));
        try {
            auditRecorder.record("job-" + job.getId() + "-attempt-" + job.getAttemptCount(), job.getCreatedByAccountId(),
                    job.getSystemId(), job.getTenantId(), null, "BACKGROUND_JOB_FAILED", "BACKGROUND_JOB",
                    String.valueOf(job.getId()), reread.getStatus(), Map.of(
                            "attempt", job.getAttemptCount(), "maxAttempts", job.getMaxAttempts(), "errorCode", code,
                            "willRetry", !exhausted));
        } catch (RuntimeException ignored) {
            // Retry state is authoritative and remains bounded even if secondary audit storage is unavailable.
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(1900, value.length()));
    }
}
