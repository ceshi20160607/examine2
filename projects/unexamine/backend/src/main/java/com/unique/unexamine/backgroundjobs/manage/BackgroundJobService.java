package com.unique.unexamine.backgroundjobs.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.foundation.manage.control.FoundationContextKeyFactory;
import com.unique.unexamine.backgroundjobs.base.entity.JobBackground;
import com.unique.unexamine.backgroundjobs.base.entity.JobItemResult;
import com.unique.unexamine.backgroundjobs.base.service.JobBackgroundBaseService;
import com.unique.unexamine.backgroundjobs.base.service.JobItemResultBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 后台作业申请与查询；MySQL 只保存申请、最终审计和逐项业务结果。 */
@Service
public class BackgroundJobService {
    private final String readyQueue;
    private final String statusPrefix;
    private final String attemptPrefix;
    private final String lockPrefix;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JobBackgroundBaseService jobs;
    private final JobItemResultBaseService items;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final Map<String, BackgroundJobHandler> handlers;

    public BackgroundJobService(JobBackgroundBaseService jobs, JobItemResultBaseService items,
                                StringRedisTemplate redis, FoundationContextKeyFactory keyFactory, ObjectMapper objectMapper,
                                AuditRecorder auditRecorder, List<BackgroundJobHandler> handlerList) {
        this.jobs = jobs;
        this.items = items;
        this.redis = redis;
        String prefix = keyFactory.backgroundJobKeyPrefix();
        this.readyQueue = prefix + "ready";
        this.statusPrefix = prefix + "status:";
        this.attemptPrefix = prefix + "attempts:";
        this.lockPrefix = prefix + "lock:";
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
        Map<String, BackgroundJobHandler> discovered = new LinkedHashMap<>();
        for (BackgroundJobHandler handler : handlerList) {
            for (String jobType : handler.jobTypes()) {
                if (discovered.putIfAbsent(jobType, handler) != null)
                    throw new IllegalStateException("Duplicate background job handler: " + jobType);
            }
        }
        this.handlers = Map.copyOf(discovered);
    }

    public List<BackgroundJobModels.HandlerView> handlers() {
        return handlers.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new BackgroundJobModels.HandlerView(entry.getKey(), entry.getValue().name(entry.getKey()),
                        entry.getValue().description(entry.getKey()))).toList();
    }

    @Transactional
    public BackgroundJobModels.JobDetail submit(AuthenticatedContext context,
                                                 BackgroundJobModels.SubmitJobRequest input, String traceId) {
        requireSystemContext(context);
        String jobType = input.jobType().trim().toUpperCase();
        BackgroundJobHandler handler = handlers.get(jobType);
        if (handler == null)
            throw new DomainException("JOB_TYPE_NOT_REGISTERED", "后台作业类型没有注册处理器", HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> parameters = new LinkedHashMap<>(input.parameters());
        parameters.put("systemId", context.systemId());
        parameters.put("tenantId", context.tenantId());
        long total = Math.max(0, handler.estimateTotal(jobType, parameters));
        int max = input.maxAttempts() == null ? 3 : input.maxAttempts();
        // 固定提交策略用于 Redis 故障后的排队恢复，运行尝试次数不写数据库。
        parameters.put("_jobPolicy", Map.of("maxAttempts", max, "estimatedTotal", total));
        LocalDateTime now = LocalDateTime.now();
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
                "accountId", context.accountId(), "memberId", context.memberId(),
                "tenantMemberId", context.tenantMemberId(), "roleIds", context.roleIds(),
                "permissions", context.permissions(), "dataScopes", context.dataScopes())));
        job.setCreatedByAccountId(context.accountId());
        job.setCreateTime(now);
        job.setUpdateTime(now);
        job.setVersion(0);
        jobs.insert(job);
        afterCommit(() -> {
            try {
                initializeState(job);
                enqueue(job.getId(), now);
            } catch (RuntimeException ignored) {
                // 申请已经提交；Redis 恢复后工作器按未完成申请重建队列。
            }
        });
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BACKGROUND_JOB_SUBMITTED", "BACKGROUND_JOB", String.valueOf(job.getId()), "QUEUED",
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions()),
                Map.of("jobType", jobType, "sourceType", job.getSourceType(), "maxAttempts", max));
        BackgroundJobModels.RedisState initial = new BackgroundJobModels.RedisState(
                "QUEUED", 0, total, 0, format(now), true, false, "PENDING_COMMIT");
        return new BackgroundJobModels.JobDetail(view(job, initial, Map.of("maxAttempts", max)),
                List.of(), List.of(), parse(job.getAuthorizationSnapshotJson()));
    }

    public List<BackgroundJobModels.JobView> list(AuthenticatedContext context) {
        requireSystemContext(context);
        return jobs.selectList(new LambdaQueryWrapper<JobBackground>()
                        .eq(JobBackground::getSystemId, context.systemId())
                        .eq(JobBackground::getTenantId, context.tenantId())
                        .orderByDesc(JobBackground::getId).last("limit 50"))
                .stream().map(this::view).toList();
    }

    public BackgroundJobModels.JobDetail detail(AuthenticatedContext context, long jobId) {
        JobBackground job = scopedJob(context, jobId);
        List<BackgroundJobModels.ItemView> itemViews = items.selectList(new LambdaQueryWrapper<JobItemResult>()
                        .eq(JobItemResult::getJobId, jobId)
                        .orderByAsc(JobItemResult::getRowNumber).orderByAsc(JobItemResult::getId))
                .stream().map(this::itemView).toList();
        return new BackgroundJobModels.JobDetail(view(job), attempts(jobId), itemViews,
                parse(job.getAuthorizationSnapshotJson()));
    }

    public BackgroundJobModels.JobDetail retryNow(AuthenticatedContext context, long jobId,
                                                   BackgroundJobModels.RetryJobRequest input, String traceId) {
        JobBackground job = scopedJob(context, jobId);
        Map<Object, Object> state = runtime(job);
        int count = (int) number(state.get("attemptCount"), 0);
        int max = (int) number(state.get("maxAttempts"), policy(job, "maxAttempts", 3));
        String status = String.valueOf(state.getOrDefault("status", "QUEUED"));
        if (job.getFinalStatus() != null || count >= max || "PERMANENT_FAILED".equals(status))
            throw new DomainException("JOB_ATTEMPTS_EXHAUSTED", "作业已达到最大尝试次数，不能无限重试", HttpStatus.CONFLICT);
        if (!"RETRY_WAIT".equals(status))
            throw new DomainException("JOB_NOT_RETRYABLE", "当前作业状态不允许重试", HttpStatus.CONFLICT);
        updateRuntime(jobId, Map.of("status", "QUEUED", "nextRunAt", "", "errorCode", "",
                "errorMessage", "", "updatedAt", format(LocalDateTime.now())));
        enqueue(jobId, LocalDateTime.now());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BACKGROUND_JOB_RETRY_REQUESTED", "BACKGROUND_JOB", String.valueOf(jobId), "QUEUED",
                Map.of("reason", input.reason(), "attemptCount", count, "maxAttempts", max));
        return detail(context, jobId);
    }

    BackgroundJobHandler handler(String jobType) { return handlers.get(jobType); }

    String readyQueue() { return readyQueue; }

    String statusKey(long jobId) { return statusPrefix + jobId; }

    String lockKey(long jobId) { return lockPrefix + jobId; }

    Map<String, Object> parameters(JobBackground job) {
        Map<String, Object> result = new LinkedHashMap<>(parse(job.getParameterJson()));
        result.remove("_jobPolicy");
        return result;
    }

    void enqueue(long jobId, LocalDateTime nextRunTime) {
        double score = (nextRunTime == null ? LocalDateTime.now() : nextRunTime)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        redis.opsForZSet().add(readyQueue, String.valueOf(jobId), score);
    }

    /** Redis 丢失运行态时，按固定提交策略重建尚未完成的排队任务。 */
    Map<Object, Object> runtime(JobBackground job) {
        if (job.getFinalStatus() == null) initializeState(job);
        return redis.opsForHash().entries(statusPrefix + job.getId());
    }

    void initializeState(JobBackground job) {
        String key = statusPrefix + job.getId();
        if (Boolean.TRUE.equals(redis.hasKey(key))) return;
        redis.opsForHash().putAll(key, Map.of(
                "status", "QUEUED", "progressCurrent", "0",
                "progressTotal", String.valueOf(policy(job, "estimatedTotal", 0)),
                "attemptCount", "0", "maxAttempts", String.valueOf(policy(job, "maxAttempts", 3)),
                "heartbeatAt", "", "nextRunAt", "", "updatedAt", format(LocalDateTime.now())));
    }

    void updateRuntime(long jobId, Map<String, String> changes) {
        redis.opsForHash().putAll(statusPrefix + jobId, changes);
    }

    void expireRuntime(long jobId) {
        redis.expire(statusPrefix + jobId, Duration.ofDays(7));
        redis.expire(attemptPrefix + jobId, Duration.ofDays(7));
    }

    void beginAttempt(long jobId, int attemptNumber, String workerId, LocalDateTime startTime) {
        BackgroundJobModels.AttemptView attempt = new BackgroundJobModels.AttemptView(
                (long) attemptNumber, attemptNumber, workerId, "RUNNING", format(startTime), null, null, null);
        redis.opsForList().leftPush(attemptPrefix + jobId, json(attempt));
    }

    void finishAttempt(long jobId, String status, String errorCode, String errorMessage) {
        String key = attemptPrefix + jobId;
        String latest = redis.opsForList().index(key, 0);
        if (latest == null) return;
        try {
            BackgroundJobModels.AttemptView prior = objectMapper.readValue(latest, BackgroundJobModels.AttemptView.class);
            redis.opsForList().set(key, 0, json(new BackgroundJobModels.AttemptView(prior.id(), prior.attemptNumber(),
                    prior.workerId(), status, prior.startedAt(), format(LocalDateTime.now()), errorCode, errorMessage)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("后台作业尝试记录无法读取", exception);
        }
    }

    private List<BackgroundJobModels.AttemptView> attempts(long jobId) {
        try {
            List<String> values = redis.opsForList().range(attemptPrefix + jobId, 0, -1);
            if (values == null) return List.of();
            List<BackgroundJobModels.AttemptView> result = new ArrayList<>();
            for (String value : values) result.add(objectMapper.readValue(value, BackgroundJobModels.AttemptView.class));
            Collections.reverse(result);
            return result;
        } catch (RuntimeException | JsonProcessingException exception) {
            return List.of();
        }
    }

    /** 稳定业务项键支持同一作业重试时覆盖逐项结果。 */
    void recordItem(long jobId, String itemKey, Long rowNumber, String status, Map<String, Object> result,
                    String errorCode, String errorMessage) {
        JobItemResult item = items.selectList(new LambdaQueryWrapper<JobItemResult>()
                .eq(JobItemResult::getJobId, jobId).eq(JobItemResult::getItemKey, itemKey).last("limit 1"))
                .stream().findFirst().orElse(null);
        LocalDateTime now = LocalDateTime.now();
        boolean created = item == null;
        if (created) {
            item = new JobItemResult();
            item.setJobId(jobId);
            item.setItemKey(itemKey);
            item.setCreateTime(now);
        }
        item.setRowNumber(rowNumber);
        item.setStatus(status);
        item.setResultJson(json(result));
        item.setErrorCode(errorCode);
        item.setErrorMessage(errorMessage);
        item.setUpdateTime(now);
        if (created) items.insert(item);
        else items.updateById(item);
    }

    private JobBackground scopedJob(AuthenticatedContext context, long jobId) {
        requireSystemContext(context);
        JobBackground job = jobs.selectById(jobId);
        if (job == null || !context.systemId().equals(job.getSystemId()) || !context.tenantId().equals(job.getTenantId()))
            throw new DomainException("JOB_NOT_FOUND", "后台作业不存在", HttpStatus.NOT_FOUND);
        return job;
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null)
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "需要先进入系统租户上下文", HttpStatus.CONFLICT);
    }

    private BackgroundJobModels.JobView view(JobBackground job) {
        Map<Object, Object> state;
        try { state = runtime(job); }
        catch (RuntimeException exception) { state = Map.of(); }
        return view(job, redisState(job, state), state);
    }

    private BackgroundJobModels.JobView view(JobBackground job, BackgroundJobModels.RedisState redisState,
                                             Map<Object, Object> state) {
        String status = job.getFinalStatus() == null ? redisState.status()
                : "FAILED".equals(job.getFinalStatus()) ? "PERMANENT_FAILED" : job.getFinalStatus();
        return new BackgroundJobModels.JobView(job.getId(), job.getContextType(), job.getSystemId(), job.getTenantId(),
                job.getJobType(), job.getSourceType(), job.getSourceId(), status,
                redisState.progressCurrent(), redisState.progressTotal(),
                (int) number(state.get("maxAttempts"), policy(job, "maxAttempts", 3)),
                redisState.attemptCount(), text(state.get("nextRunAt")), text(state.get("heartbeatAt")),
                parse(job.getResultSummaryJson()), job.getFinalStatus() == null ? text(state.get("errorCode")) : job.getErrorCode(),
                job.getFinalStatus() == null ? text(state.get("errorMessage")) : job.getErrorMessage(),
                format(job.getCreateTime()), text(state.get("startedAt")), format(job.getFinishTime()), redisState);
    }

    private BackgroundJobModels.RedisState redisState(JobBackground job, Map<Object, Object> state) {
        String status = job.getFinalStatus() == null ? String.valueOf(state.getOrDefault("status", "QUEUED"))
                : "FAILED".equals(job.getFinalStatus()) ? "PERMANENT_FAILED" : job.getFinalStatus();
        try {
            return new BackgroundJobModels.RedisState(status,
                    number(state.get("progressCurrent"), 0),
                    number(state.get("progressTotal"), policy(job, "estimatedTotal", 0)),
                    (int) number(state.get("attemptCount"), 0),
                    String.valueOf(state.getOrDefault("updatedAt", format(job.getUpdateTime()))),
                    redis.opsForZSet().score(readyQueue, String.valueOf(job.getId())) != null,
                    Boolean.TRUE.equals(redis.hasKey(lockKey(job.getId()))),
                    state.isEmpty() ? "DATABASE_FALLBACK" : "REDIS");
        } catch (RuntimeException exception) {
            return new BackgroundJobModels.RedisState(status, 0, policy(job, "estimatedTotal", 0), 0,
                    format(job.getUpdateTime()), false, false, "DATABASE_FALLBACK");
        }
    }

    private BackgroundJobModels.ItemView itemView(JobItemResult item) {
        return new BackgroundJobModels.ItemView(item.getId(), item.getItemKey(), item.getRowNumber(), item.getStatus(),
                parse(item.getResultJson()), item.getErrorCode(), item.getErrorMessage(), format(item.getUpdateTime()));
    }

    private long policy(JobBackground job, String field, long fallback) {
        Object policy = parse(job.getParameterJson()).get("_jobPolicy");
        return policy instanceof Map<?, ?> values ? number(values.get(field), fallback) : fallback;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) {
            throw new DomainException("JOB_PARAMETER_INVALID", "后台作业参数无法序列化", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> parse(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, MAP_TYPE); }
        catch (JsonProcessingException exception) { return Map.of("unreadable", true); }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
    private long number(Object value, long fallback) {
        if (value == null) return fallback;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException exception) { return fallback; }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
