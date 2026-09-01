package com.unique.unexamine.backgroundjobs.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.backgroundjobs.base.entity.JobAttempt;
import com.unique.unexamine.backgroundjobs.base.entity.JobBackground;
import com.unique.unexamine.backgroundjobs.base.entity.JobItemResult;
import com.unique.unexamine.backgroundjobs.base.service.JobAttemptBaseService;
import com.unique.unexamine.backgroundjobs.base.service.JobBackgroundBaseService;
import com.unique.unexamine.backgroundjobs.base.service.JobItemResultBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackgroundJobService {
    static final String READY_QUEUE = "unexamine:jobs:ready";
    static final String STATUS_PREFIX = "unexamine:jobs:status:";
    static final String LOCK_PREFIX = "unexamine:jobs:lock:";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JobBackgroundBaseService jobs;
    private final JobAttemptBaseService attempts;
    private final JobItemResultBaseService items;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final Map<String, BackgroundJobHandler> handlers;

    public BackgroundJobService(JobBackgroundBaseService jobs,
                                JobAttemptBaseService attempts,
                                JobItemResultBaseService items,
                                JdbcTemplate jdbc,
                                StringRedisTemplate redis,
                                ObjectMapper objectMapper,
                                AuditRecorder auditRecorder,
                                List<BackgroundJobHandler> handlerList) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.items = items;
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
        Map<String, BackgroundJobHandler> discovered = new LinkedHashMap<>();
        for (BackgroundJobHandler handler : handlerList) {
            for (String jobType : handler.jobTypes()) {
                if (discovered.putIfAbsent(jobType, handler) != null) {
                    throw new IllegalStateException("Duplicate background job handler: " + jobType);
                }
            }
        }
        this.handlers = Map.copyOf(discovered);
    }

    public List<BackgroundJobModels.HandlerView> handlers() {
        return handlers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new BackgroundJobModels.HandlerView(entry.getKey(), entry.getValue().name(entry.getKey()),
                        entry.getValue().description(entry.getKey())))
                .toList();
    }

    @Transactional
    public BackgroundJobModels.JobDetail submit(AuthenticatedContext context,
                                                 BackgroundJobModels.SubmitJobRequest input,
                                                 String traceId) {
        requireSystemContext(context);
        String jobType = input.jobType().trim().toUpperCase();
        BackgroundJobHandler handler = handlers.get(jobType);
        if (handler == null) {
            throw new DomainException("JOB_TYPE_NOT_REGISTERED", "后台作业类型没有注册处理器", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> parameters = new LinkedHashMap<>(input.parameters());
        parameters.put("systemId", context.systemId());
        parameters.put("tenantId", context.tenantId());
        long total = Math.max(0, handler.estimateTotal(jobType, parameters));

        JobBackground job = new JobBackground();
        job.setContextType("SYSTEM");
        job.setPlatformId(context.platformId());
        job.setSystemId(context.systemId());
        job.setTenantId(context.tenantId());
        job.setJobType(jobType);
        job.setSourceType(input.sourceType().trim().toUpperCase());
        job.setSourceId(blankToNull(input.sourceId()));
        job.setParameterJson(json(parameters));
        job.setAuthorizationSnapshotJson(json(Map.of(
                "accountId", context.accountId(),
                "memberId", context.memberId(),
                "tenantMemberId", context.tenantMemberId(),
                "roleIds", context.roleIds(),
                "permissions", context.permissions(),
                "dataScopes", context.dataScopes())));
        job.setStatus("QUEUED");
        job.setProgressCurrent(0L);
        job.setProgressTotal(total);
        job.setMaxAttempts(input.maxAttempts() == null ? 3 : input.maxAttempts());
        job.setAttemptCount(0);
        job.setNextRunAt(null);
        job.setCreatedByAccountId(context.accountId());
        jobs.insert(job);
        afterCommit(() -> {
            enqueue(job.getId(), LocalDateTime.now());
            publishRedisState(jobs.selectById(job.getId()));
        });
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BACKGROUND_JOB_SUBMITTED", "BACKGROUND_JOB", String.valueOf(job.getId()), "QUEUED",
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions()),
                Map.of("jobType", jobType, "sourceType", job.getSourceType(), "maxAttempts", job.getMaxAttempts()));
        return detail(context, job.getId());
    }

    public List<BackgroundJobModels.JobView> list(AuthenticatedContext context) {
        requireSystemContext(context);
        return jobs.selectList(new LambdaQueryWrapper<JobBackground>()
                        .eq(JobBackground::getSystemId, context.systemId())
                        .eq(JobBackground::getTenantId, context.tenantId())
                        .orderByDesc(JobBackground::getId)
                        .last("limit 50"))
                .stream().map(this::view).toList();
    }

    public BackgroundJobModels.JobDetail detail(AuthenticatedContext context, long jobId) {
        JobBackground job = scopedJob(context, jobId);
        List<BackgroundJobModels.AttemptView> attemptViews = attempts.selectList(
                        new LambdaQueryWrapper<JobAttempt>().eq(JobAttempt::getJobId, jobId)
                                .orderByAsc(JobAttempt::getAttemptNumber))
                .stream().map(this::attemptView).toList();
        List<BackgroundJobModels.ItemView> itemViews = items.selectList(
                        new LambdaQueryWrapper<JobItemResult>().eq(JobItemResult::getJobId, jobId)
                                .orderByAsc(JobItemResult::getRowNumber).orderByAsc(JobItemResult::getId))
                .stream().map(this::itemView).toList();
        return new BackgroundJobModels.JobDetail(view(job), attemptViews, itemViews,
                parse(job.getAuthorizationSnapshotJson()));
    }

    @Transactional
    public BackgroundJobModels.JobDetail retryNow(AuthenticatedContext context, long jobId,
                                                   BackgroundJobModels.RetryJobRequest input, String traceId) {
        JobBackground job = scopedJob(context, jobId);
        if (job.getAttemptCount() >= job.getMaxAttempts() || "PERMANENT_FAILED".equals(job.getStatus())) {
            throw new DomainException("JOB_ATTEMPTS_EXHAUSTED", "作业已达到最大尝试次数，不能无限重试", HttpStatus.CONFLICT);
        }
        if (!List.of("RETRY_WAIT", "FAILED").contains(job.getStatus())) {
            throw new DomainException("JOB_NOT_RETRYABLE", "当前作业状态不允许重试", HttpStatus.CONFLICT);
        }
        int updated = jdbc.update("update job_background set status='QUEUED', next_run_at=now(3), "
                        + "error_code=null, error_message=null, version=version+1 where id=? and status in ('RETRY_WAIT','FAILED') "
                        + "and attempt_count < max_attempts", jobId);
        if (updated != 1) {
            throw new DomainException("JOB_RETRY_CONFLICT", "作业状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        enqueue(jobId, LocalDateTime.now());
        JobBackground reread = jobs.selectById(jobId);
        publishRedisState(reread);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BACKGROUND_JOB_RETRY_REQUESTED", "BACKGROUND_JOB", String.valueOf(jobId), "QUEUED",
                Map.of("reason", input.reason(), "attemptCount", reread.getAttemptCount(), "maxAttempts", reread.getMaxAttempts()));
        return detail(context, jobId);
    }

    BackgroundJobHandler handler(String jobType) {
        return handlers.get(jobType);
    }

    Map<String, Object> parameters(JobBackground job) {
        return parse(job.getParameterJson());
    }

    void enqueue(long jobId, LocalDateTime nextRunAt) {
        try {
            double score = (nextRunAt == null ? LocalDateTime.now() : nextRunAt)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            redis.opsForZSet().add(READY_QUEUE, String.valueOf(jobId), score);
        } catch (RuntimeException ignored) {
            // The database row remains QUEUED and the recovery scan will enqueue it after Redis returns.
        }
    }

    void publishRedisState(JobBackground job) {
        try {
            String key = STATUS_PREFIX + job.getId();
            redis.opsForHash().putAll(key, Map.of(
                    "status", text(job.getStatus()),
                    "progressCurrent", String.valueOf(value(job.getProgressCurrent())),
                    "progressTotal", String.valueOf(value(job.getProgressTotal())),
                    "attemptCount", String.valueOf(job.getAttemptCount() == null ? 0 : job.getAttemptCount()),
                    "updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            redis.expire(key, java.time.Duration.ofDays(7));
        } catch (RuntimeException ignored) {
            // MySQL remains authoritative; Redis state is rebuilt on the next worker or page interaction.
        }
    }

    BackgroundJobModels.RedisState redisState(JobBackground job) {
        try {
            String key = STATUS_PREFIX + job.getId();
            Map<Object, Object> state = redis.opsForHash().entries(key);
            return new BackgroundJobModels.RedisState(
                    String.valueOf(state.getOrDefault("status", job.getStatus())),
                    longValue(state.get("progressCurrent"), value(job.getProgressCurrent())),
                    longValue(state.get("progressTotal"), value(job.getProgressTotal())),
                    (int) longValue(state.get("attemptCount"), job.getAttemptCount() == null ? 0 : job.getAttemptCount()),
                    String.valueOf(state.getOrDefault("updatedAt", format(job.getUpdatedAt()))),
                    redis.opsForZSet().score(READY_QUEUE, String.valueOf(job.getId())) != null,
                    Boolean.TRUE.equals(redis.hasKey(LOCK_PREFIX + job.getId())),
                    "REDIS");
        } catch (RuntimeException exception) {
            return new BackgroundJobModels.RedisState(job.getStatus(), value(job.getProgressCurrent()),
                    value(job.getProgressTotal()), job.getAttemptCount() == null ? 0 : job.getAttemptCount(),
                    format(job.getUpdatedAt()), false, false, "DATABASE_FALLBACK");
        }
    }

    private JobBackground scopedJob(AuthenticatedContext context, long jobId) {
        requireSystemContext(context);
        JobBackground job = jobs.selectById(jobId);
        if (job == null || !context.systemId().equals(job.getSystemId()) || !context.tenantId().equals(job.getTenantId())) {
            throw new DomainException("JOB_NOT_FOUND", "后台作业不存在", HttpStatus.NOT_FOUND);
        }
        return job;
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "需要先进入系统租户上下文", HttpStatus.CONFLICT);
        }
    }

    private BackgroundJobModels.JobView view(JobBackground job) {
        return new BackgroundJobModels.JobView(job.getId(), job.getContextType(), job.getSystemId(), job.getTenantId(),
                job.getJobType(), job.getSourceType(), job.getSourceId(), job.getStatus(), value(job.getProgressCurrent()),
                value(job.getProgressTotal()), job.getMaxAttempts(), job.getAttemptCount(), format(job.getNextRunAt()),
                format(job.getHeartbeatAt()), parse(job.getResultSummaryJson()), job.getErrorCode(), job.getErrorMessage(),
                format(job.getCreatedAt()), format(job.getStartedAt()), format(job.getFinishedAt()), redisState(job));
    }

    private BackgroundJobModels.AttemptView attemptView(JobAttempt attempt) {
        return new BackgroundJobModels.AttemptView(attempt.getId(), attempt.getAttemptNumber(), attempt.getWorkerId(),
                attempt.getStatus(), format(attempt.getStartedAt()), format(attempt.getFinishedAt()),
                attempt.getErrorCode(), attempt.getErrorMessage());
    }

    private BackgroundJobModels.ItemView itemView(JobItemResult item) {
        return new BackgroundJobModels.ItemView(item.getId(), item.getItemKey(), item.getRowNumber(), item.getStatus(),
                parse(item.getResultJson()), item.getErrorCode(), item.getErrorMessage(), format(item.getUpdatedAt()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException("JOB_PARAMETER_INVALID", "后台作业参数无法序列化", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> parse(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("unreadable", true);
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private long longValue(Object value, long fallback) {
        if (value == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
