package com.unique.unexamine.foundation.manage.control;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.flow.manage.FlowBindingModels;
import org.springframework.beans.factory.ObjectProvider;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionResolutionResult;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.foundation.base.entity.CoreCacheEpoch;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.entity.CoreIdempotencyRecord;
import com.unique.unexamine.foundation.base.service.CoreCacheEpochBaseService;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.foundation.base.service.CoreIdempotencyRecordBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class FoundationControlService {
    public static final String DEFAULT_APPLICATION = "system-admin";
    public static final String DEFAULT_OPERATION = "CONFIG_RELOAD";
    public static final int RATE_LIMIT = 5;
    public static final Duration RATE_WINDOW = Duration.ofSeconds(60);
    private static final Set<String> OPERATIONS = Set.of(
            "CONFIG_RELOAD",
            "PERMISSION_CONTEXT_REFRESH",
            "RUNTIME_RECORD_CREATE_DIRECT",
            "RUNTIME_RECORD_UPDATE_DIRECT",
            "RUNTIME_RECORD_DELETE_DIRECT");

    private final CoreIdempotencyRecordBaseService idempotencyService;
    private final CoreEventOutboxBaseService outboxService;
    private final CoreCacheEpochBaseService cacheEpochService;
    private final FoundationContextKeyFactory keyFactory;
    private final RedisRateLimiter rateLimiter;
    private final PermissionResolver permissionResolver;
    private final FoundationExtensionDispatcher dispatcher;
    private final StringRedisTemplate redis;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final ObjectMapper actionJsonMapper;
    private final JdbcTemplate jdbc;
    private final BusinessActionMapper actionMapper;
    private final ObjectProvider<RuntimeDataCommandExtension> runtimeCommands;
    private final ObjectProvider<com.unique.unexamine.flow.manage.BusinessActionFlowExtension> businessFlows;
    private final ObjectProvider<BusinessActionApprovalCoordinator> approvals;

    public FoundationControlService(CoreIdempotencyRecordBaseService idempotencyService,
                                    CoreEventOutboxBaseService outboxService,
                                    CoreCacheEpochBaseService cacheEpochService,
                                    FoundationContextKeyFactory keyFactory,
                                    RedisRateLimiter rateLimiter,
                                    PermissionResolver permissionResolver,
                                    FoundationExtensionDispatcher dispatcher,
                                    StringRedisTemplate redis,
                                    AuditRecorder auditRecorder,
                                    ObjectMapper objectMapper,
                                    JdbcTemplate jdbc,
                                    BusinessActionMapper actionMapper,
                                    ObjectProvider<RuntimeDataCommandExtension> runtimeCommands,
                                    ObjectProvider<com.unique.unexamine.flow.manage.BusinessActionFlowExtension> businessFlows,
                                    ObjectProvider<BusinessActionApprovalCoordinator> approvals) {
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
        this.cacheEpochService = cacheEpochService;
        this.keyFactory = keyFactory;
        this.rateLimiter = rateLimiter;
        this.permissionResolver = permissionResolver;
        this.dispatcher = dispatcher;
        this.redis = redis;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.actionJsonMapper = BusinessAction.storageMapper(objectMapper);
        this.jdbc = jdbc;
        this.actionMapper = actionMapper;
        this.runtimeCommands = runtimeCommands;
        this.businessFlows = businessFlows;
        this.approvals = approvals;
    }

    @Transactional
    public FoundationControlModels.CommandResult submit(AuthenticatedContext context,
                                                         FoundationControlModels.SubmitCommandRequest input,
                                                         String traceId) {
        requireContext(context);
        String operation = input.operationCode().strip().toUpperCase(Locale.ROOT);
        if (operation.startsWith("RUNTIME_RECORD_")) {
            return runtimeCommands.getObject().execute(context, input, traceId);
        }
        if (!OPERATIONS.contains(operation)) {
            throw new DomainException("FOUNDATION_OPERATION_NOT_ALLOWED", "该运行控制动作不受支持",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String application = application(input.applicationCode());
        String contextKey = keyFactory.commandContext(context, application, operation);
        String requestHash = hash(Map.of("operationCode", operation, "payload", new TreeMap<>(input.payload())));
        String idempotencyKey = input.idempotencyKey().strip();
        CoreIdempotencyRecord record = findExisting(contextKey, operation, idempotencyKey);
        if (record != null) {
            if (!requestHash.equals(record.getRequestHash())) {
                throw new DomainException("IDEMPOTENCY_KEY_REUSED",
                        "同一幂等键已用于不同请求，请更换幂等键", HttpStatus.CONFLICT);
            }
            if ("COMPLETED".equals(record.getStatus())) {
                FoundationControlModels.CommandResult replayed = readResult(record, true);
                auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                        "FOUNDATION_COMMAND_REPLAYED", "IDEMPOTENCY_RECORD", record.getId().toString(), "SUCCESS",
                        Map.of("operationCode", operation, "resultReference", replayed.resultReference()));
                return replayed;
            }
            if (record.getLockedUntil() != null && record.getLockedUntil().isAfter(LocalDateTime.now())) {
                throw new DomainException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                        "相同命令正在处理，请稍后读取首次结果", HttpStatus.CONFLICT,
                        Map.of("retryAfterSeconds", 2, "recordId", record.getId()));
            }
            rateLimiter.check(context, application, operation, RATE_LIMIT, RATE_WINDOW, traceId);
            record.setStatus("PROCESSING");
            record.setLockedUntil(LocalDateTime.now().plusMinutes(2));
            if (idempotencyService.updateById(record) != 1) {
                throw new DomainException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                        "相同命令已被其他请求接管，请稍后读取首次结果", HttpStatus.CONFLICT,
                        Map.of("retryAfterSeconds", 2, "recordId", record.getId()));
            }
        } else {
            rateLimiter.check(context, application, operation, RATE_LIMIT, RATE_WINDOW, traceId);
            String claimToken = "CLAIM:" + traceId;
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("insert into core_idempotency_record(context_key,operation_code,idempotency_key,request_hash,"
                            + "status,response_code,locked_until,expires_at) values (?,?,?,?,?,?,?,?) "
                            + "on duplicate key update id=last_insert_id(id)",
                    contextKey, operation, idempotencyKey, requestHash, "PROCESSING", claimToken,
                    now.plusMinutes(2), now.plusHours(24));
            Long recordId = jdbc.queryForObject("select last_insert_id()", Long.class);
            record = currentRecord(recordId);
            if (!claimToken.equals(record.getResponseCode())) {
                if (!requestHash.equals(record.getRequestHash())) {
                    throw new DomainException("IDEMPOTENCY_KEY_REUSED",
                            "同一幂等键已用于不同请求，请更换幂等键", HttpStatus.CONFLICT);
                }
                if ("COMPLETED".equals(record.getStatus())) {
                    FoundationControlModels.CommandResult replayed = readResult(record, true);
                    auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                            "FOUNDATION_COMMAND_REPLAYED", "IDEMPOTENCY_RECORD", record.getId().toString(), "SUCCESS",
                            Map.of("operationCode", operation, "resultReference", replayed.resultReference()));
                    return replayed;
                }
                if (record.getLockedUntil() != null && record.getLockedUntil().isAfter(LocalDateTime.now())) {
                    throw new DomainException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                            "相同命令正在处理，请稍后读取首次结果", HttpStatus.CONFLICT,
                            Map.of("retryAfterSeconds", 2, "recordId", record.getId()));
                }
                int reclaimed = jdbc.update("update core_idempotency_record set status='PROCESSING',response_code=?,"
                                + "locked_until=?,version=version+1 where id=? and status<>'COMPLETED' "
                                + "and (locked_until is null or locked_until<=now(3))",
                        claimToken, now.plusMinutes(2), record.getId());
                if (reclaimed != 1) {
                    CoreIdempotencyRecord latest = currentRecord(record.getId());
                    if ("COMPLETED".equals(latest.getStatus())) return readResult(latest, true);
                    throw new DomainException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                            "相同命令已被其他请求接管，请稍后读取首次结果", HttpStatus.CONFLICT,
                            Map.of("retryAfterSeconds", 2, "recordId", record.getId()));
                }
                record = currentRecord(record.getId());
            }
        }

        CoreIdempotencyRecord claimed = record;
        CoreEventOutbox event = new CoreEventOutbox();
        event.setContextType("SYSTEM");
        event.setSystemId(context.systemId());
        event.setTenantId(context.tenantId());
        event.setAggregateType("FOUNDATION_COMMAND");
        event.setAggregateId(claimed.getId().toString());
        event.setEventType("FOUNDATION_COMMAND_COMMITTED");
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("operationCode", operation);
        eventPayload.put("applicationCode", application);
        eventPayload.put("requestId", traceId);
        eventPayload.put("accountId", context.accountId());
        eventPayload.put("platformId", context.platformId());
        eventPayload.put("systemId", context.systemId());
        eventPayload.put("tenantId", context.tenantId());
        eventPayload.put("tenantMemberId", context.tenantMemberId());
        eventPayload.put("memberId", context.memberId());
        eventPayload.put("username", context.username());
        eventPayload.put("displayName", context.displayName());
        eventPayload.put("payload", input.payload());
        event.setPayloadJson(json(eventPayload));
        event.setStatus("PENDING");
        event.setAvailableAt(LocalDateTime.now());
        event.setRetryCount(0);
        outboxService.insert(event);

        FoundationControlModels.CommandResult result = new FoundationControlModels.CommandResult(
                claimed.getId(), event.getId(), claimed.getIdempotencyKey(), operation,
                "foundation-command:" + event.getId(), "COMPLETED", false,
                OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(), "PENDING");
        claimed.setStatus("COMPLETED");
        claimed.setResponseCode("SUCCESS");
        claimed.setResponseJson(json(result));
        claimed.setLockedUntil(null);
        if (idempotencyService.updateById(claimed) != 1) {
            throw new DomainException("IDEMPOTENCY_COMPLETION_CONFLICT",
                    "运行控制结果保存冲突，业务事务已回滚", HttpStatus.CONFLICT);
        }

        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FOUNDATION_COMMAND_COMMITTED", "IDEMPOTENCY_RECORD", claimed.getId().toString(), "SUCCESS",
                Map.of("operationCode", operation, "outboxId", event.getId(),
                        "resultReference", result.resultReference()));
        afterCommit(event.getId());
        return result;
    }

    /** Business writes, their result and their completion event share this transaction. */
    @Transactional
    public <T> BusinessAction.Result<T> execute(
            BusinessAction.Input input, Class<T> resultType, JsonNode effectiveConfiguration,
            Supplier<FlowBindingModels.ResolutionView> resolveBinding, Runnable validateDeferred, Supplier<T> business) {
        AuthenticatedContext actor = input.actor();
        requireContext(actor);
        if (actionMapper.activeActor(actor) != 1) {
            throw new DomainException("ACTION_ACTOR_INACTIVE", "当前成员或租户已停用，无法执行业务动作", HttpStatus.FORBIDDEN);
        }
        BusinessAction.Options options = input.options();
        String key = options.effectiveKey();
        if (!key.matches("[A-Za-z0-9._:-]{3,200}")) {
            throw new DomainException("IDEMPOTENCY_KEY_INVALID", "请求标识格式不正确", HttpStatus.BAD_REQUEST);
        }
        if (options.depth() < 0 || options.depth() > 16) {
            throw new DomainException("ACTION_CAUSATION_DEPTH_EXCEEDED", "业务动作联动层数超出限制", HttpStatus.CONFLICT);
        }
        String operation = "BUSINESS_" + input.actionCode();
        String scope = "business:" + hash(List.of(actor.accountId(), actor.systemId(), actor.tenantId(),
                actor.tenantMemberId(), input.dataTenantId(), input.moduleId(), options.applicationCode()));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("recordId", input.recordId());
        request.put("expectedVersion", input.expectedVersion());
        request.put("payload", input.payload());
        String requestHash = hash(canonical(objectMapper.valueToTree(request)));
        String claim = UUID.randomUUID().toString();
        actionMapper.claim(scope, operation, key, requestHash, claim);
        CoreIdempotencyRecord record = actionMapper.lock(scope, operation, key);
        if (!requestHash.equals(record.getRequestHash())) {
            throw new DomainException("IDEMPOTENCY_KEY_REUSED", "同一请求标识已用于不同内容", HttpStatus.CONFLICT);
        }
        if ("COMPLETED".equals(record.getStatus())) {
            try {
                BusinessAction.Result<T> stored = actionJsonMapper.readValue(record.getResponseJson(),
                        objectMapper.getTypeFactory().constructParametricType(BusinessAction.Result.class, resultType));
                return new BusinessAction.Result<>(stored.idempotencyRecordId(), stored.outboxId(), key,
                        stored.status(), true, stored.mode(), stored.value(), stored.flowInstanceId());
            } catch (JsonProcessingException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Cannot decode action result {}", record.getId(), exception);
                throw new DomainException("IDEMPOTENCY_RESULT_INVALID", "首次结果无法读取，已阻止重复执行", HttpStatus.CONFLICT);
            }
        }
        if (!claim.equals(record.getResponseCode())) {
            throw new DomainException("IDEMPOTENCY_REQUEST_IN_PROGRESS", "相同动作正在处理", HttpStatus.CONFLICT);
        }
        FlowBindingModels.ResolutionView binding = resolveBinding.get();
        BusinessAction.Mode mode = binding == null ? BusinessAction.Mode.DIRECT
                : BusinessAction.Mode.valueOf(binding.executionMode());
        Long flowInstanceId = null;
        T value = null;
        if (mode == BusinessAction.Mode.FLOW_ONLY || mode == BusinessAction.Mode.AFTER_APPROVAL) {
            validateDeferred.run();
            flowInstanceId = businessFlows.getObject().start(input, binding, record.getId(), Map.of());
        } else {
            value = business.get();
        }
        CoreEventOutbox event = new CoreEventOutbox();
        event.setContextType("SYSTEM");
        event.setSystemId(actor.systemId());
        event.setTenantId(input.dataTenantId());
        event.setAggregateType("BUSINESS_ACTION");
        event.setAggregateId(record.getId().toString());
        event.setEventType(mode == BusinessAction.Mode.AFTER_APPROVAL ? "BUSINESS_ACTION_APPROVAL_REQUESTED" : "BUSINESS_ACTION_COMPLETED");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", input);
        payload.put("binding", binding);
        payload.put("mode", mode);
        payload.put("result", value);
        payload.put("flowInstanceId", flowInstanceId);
        if (mode == BusinessAction.Mode.AFTER_APPROVAL) {
            payload.put("approvalStatus", "PENDING_APPROVAL");
            payload.put("configurationSnapshot", effectiveConfiguration.deepCopy());
        }
        event.setPayloadJson(actionJson(payload));
        event.setStatus("PENDING");
        event.setAvailableAt(LocalDateTime.now());
        event.setRetryCount(0);
        outboxService.insert(event);
        BusinessAction.Result<T> result = new BusinessAction.Result<>(record.getId(), event.getId(), key,
                mode == BusinessAction.Mode.AFTER_APPROVAL ? "PENDING_APPROVAL"
                        : mode == BusinessAction.Mode.FLOW_ONLY ? "FLOW_STARTED" : "COMPLETED", false, mode, value, flowInstanceId);
        record.setStatus("COMPLETED");
        record.setResponseCode("SUCCESS");
        record.setResponseJson(actionJson(result));
        record.setLockedUntil(null);
        if (idempotencyService.updateById(record) != 1) {
            throw new DomainException("IDEMPOTENCY_COMPLETION_CONFLICT", "动作结果保存冲突，业务已回滚", HttpStatus.CONFLICT);
        }
        if (mode == BusinessAction.Mode.AFTER_APPROVAL) {
            auditRecorder.record(input.traceId(), actor.accountId(), actor.systemId(), actor.tenantId(), actor.memberId(),
                    "BUSINESS_ACTION_APPROVAL_REQUESTED", "BUSINESS_ACTION", record.getId().toString(), "PENDING_APPROVAL",
                    Map.of("moduleCode", input.moduleCode(), "actionCode", input.actionCode(), "flowInstanceId", flowInstanceId,
                            "configVersionId", input.configVersionId()));
        }
        afterCommit(event.getId());
        return result;
    }

    @Transactional(readOnly = true)
    public BusinessAction.Delivery actionDelivery(AuthenticatedContext actor, String moduleCode, long actionId) {
        requireContext(actor);
        CoreEventOutbox event = outboxService.selectList(Wrappers.<CoreEventOutbox>lambdaQuery()
                .eq(CoreEventOutbox::getAggregateType, "BUSINESS_ACTION")
                .eq(CoreEventOutbox::getAggregateId, Long.toString(actionId))
                .eq(CoreEventOutbox::getSystemId, actor.systemId())).stream().findFirst().orElse(null);
        try {
            JsonNode payload = event == null ? actionJsonMapper.createObjectNode() : actionJsonMapper.readTree(event.getPayloadJson());
            JsonNode original = payload.path("action");
            if (actionMapper.activeActor(actor) != 1 || !moduleCode.equals(original.path("moduleCode").asText())
                    || original.path("actor").path("accountId").asLong(-1) != actor.accountId()
                    || original.path("actor").path("tenantMemberId").asLong(-1) != actor.tenantMemberId()) {
                throw new DomainException("ACTION_RESULT_NOT_FOUND", "操作结果不存在或无权访问", HttpStatus.NOT_FOUND);
            }
            BusinessAction.Mode mode = BusinessAction.Mode.valueOf(payload.path("mode").asText());
            Long instanceId = payload.path("flowInstanceId").isNumber() ? payload.path("flowInstanceId").longValue() : null;
            String status = instanceId != null ? "STARTED" : mode == BusinessAction.Mode.DIRECT ? "NONE"
                    : "FAILED".equals(event.getStatus()) ? "RETRYING" : "PENDING";
            String businessStatus = mode == BusinessAction.Mode.AFTER_APPROVAL
                    ? payload.path("approvalStatus").asText("PENDING_APPROVAL")
                    : mode == BusinessAction.Mode.FLOW_ONLY ? "FLOW_STARTED" : "COMPLETED";
            return new BusinessAction.Delivery(actionId, original.path("actionCode").asText(), mode, status, instanceId,
                    businessStatus, payload.path("errorCode").asText(null), payload.path("errorMessage").asText(null),
                    "CONVERT".equals(original.path("actionCode").asText()) ? original.path("recordId").longValue()
                            : payload.path("result").path("id").isNumber() ? payload.path("result").path("id").longValue() : null);
        } catch (JsonProcessingException exception) {
            throw new DomainException("ACTION_RESULT_INVALID", "操作结果暂时无法读取", HttpStatus.CONFLICT);
        }
    }

    public BusinessAction.Delivery retryApproval(AuthenticatedContext actor, String moduleCode, long actionId) {
        BusinessAction.Delivery delivery = actionDelivery(actor, moduleCode, actionId);
        if (delivery.mode() != BusinessAction.Mode.AFTER_APPROVAL) {
            throw new DomainException("ACTION_NOT_APPROVAL", "该操作不是待审批的业务操作", HttpStatus.CONFLICT);
        }
        approvals.getObject().retry(actionId);
        return actionDelivery(actor, moduleCode, actionId);
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var sorted = objectMapper.createObjectNode();
            var fields = new TreeMap<String, JsonNode>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, canonical(value)));
            return sorted;
        }
        if (node.isArray()) {
            var sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(canonical(value)));
            return sorted;
        }
        return node;
    }

    private String actionJson(Object value) {
        try {
            return actionJsonMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException("ACTION_RESULT_INVALID", "业务动作结果无法保存", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional(readOnly = true)
    public FoundationControlModels.ControlOverview overview(AuthenticatedContext context) {
        requireContext(context);
        FoundationControlModels.CacheProbe cache = cacheProbe(context);
        return new FoundationControlModels.ControlOverview(DEFAULT_APPLICATION, DEFAULT_OPERATION,
                rateLimiter.current(context, DEFAULT_APPLICATION, DEFAULT_OPERATION, RATE_LIMIT),
                cache, recent(context, DEFAULT_APPLICATION, DEFAULT_OPERATION));
    }

    @Transactional(readOnly = true)
    public FoundationControlModels.CacheProbe cacheProbe(AuthenticatedContext context) {
        requireContext(context);
        PermissionResolutionResult result = permissionResolver.resolveWithDiagnostics(
                context.systemId(), context.tenantId(), context.tenantMemberId());
        return new FoundationControlModels.CacheProbe(result.epoch(), result.source(), result.cacheKey(),
                result.permissions().roleIds(), result.permissions().permissions().size());
    }

    @Transactional
    public FoundationControlModels.CacheProbe invalidateAuthorizationCache(
            AuthenticatedContext context, FoundationControlModels.InvalidateCacheRequest input, String traceId) {
        requireContext(context);
        FoundationControlModels.CacheProbe before = cacheProbe(context);
        String contextKey = "system:" + context.systemId() + ":tenant:" + context.tenantId();
        CoreCacheEpoch epoch = cacheEpochService.selectList(Wrappers.<CoreCacheEpoch>lambdaQuery()
                        .eq(CoreCacheEpoch::getContextKey, contextKey)
                        .eq(CoreCacheEpoch::getCacheNamespace, "AUTHORIZATION"))
                .stream().findFirst().orElse(null);
        long next = before.epoch() + 1;
        if (epoch == null) {
            epoch = new CoreCacheEpoch();
            epoch.setContextKey(contextKey);
            epoch.setCacheNamespace("AUTHORIZATION");
            epoch.setEpochValue(next);
            epoch.setReason(input.reason().strip());
            cacheEpochService.insert(epoch);
        } else {
            epoch.setEpochValue(next);
            epoch.setReason(input.reason().strip());
            if (cacheEpochService.updateById(epoch) != 1) {
                throw new DomainException("CACHE_EPOCH_CONFLICT", "缓存版本已被其他发布推进，请刷新后重试",
                        HttpStatus.CONFLICT);
            }
        }
        try {
            redis.delete(before.cacheKey());
        } catch (RuntimeException ignored) {
            // The epoch in authoritative storage already prevents the stale key from being reused.
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FOUNDATION_CACHE_INVALIDATED", "CACHE_EPOCH", epoch.getId().toString(), "SUCCESS",
                Map.of("namespace", "AUTHORIZATION", "previousEpoch", before.epoch(), "epoch", next,
                        "reason", input.reason().strip()));
        PermissionResolutionResult refreshed = permissionResolver.resolveWithDiagnostics(
                context.systemId(), context.tenantId(), context.tenantMemberId());
        return new FoundationControlModels.CacheProbe(refreshed.epoch(), refreshed.source(), refreshed.cacheKey(),
                refreshed.permissions().roleIds(), refreshed.permissions().permissions().size());
    }

    private List<FoundationControlModels.CommandRecordView> recent(AuthenticatedContext context,
                                                                    String application, String operation) {
        String contextKey = keyFactory.commandContext(context, application, operation);
        return idempotencyService.selectList(Wrappers.<CoreIdempotencyRecord>lambdaQuery()
                        .eq(CoreIdempotencyRecord::getContextKey, contextKey)
                        .eq(CoreIdempotencyRecord::getOperationCode, operation)
                        .orderByDesc(CoreIdempotencyRecord::getId).last("limit 20"))
                .stream().map(row -> new FoundationControlModels.CommandRecordView(row.getId(), row.getContextKey(),
                        row.getOperationCode(), row.getIdempotencyKey(), row.getStatus(), row.getResponseCode(),
                        row.getExpiresAt(), row.getVersion())).toList();
    }

    private CoreIdempotencyRecord existing(String contextKey, String operation, String idempotencyKey) {
        CoreIdempotencyRecord record = findExisting(contextKey, operation, idempotencyKey);
        if (record == null) throw new DomainException("IDEMPOTENCY_STATE_UNCERTAIN",
                "幂等状态不可确认，已安全拒绝重复执行", HttpStatus.SERVICE_UNAVAILABLE);
        return record;
    }

    private CoreIdempotencyRecord findExisting(String contextKey, String operation, String idempotencyKey) {
        return idempotencyService.selectList(Wrappers.<CoreIdempotencyRecord>lambdaQuery()
                        .eq(CoreIdempotencyRecord::getContextKey, contextKey)
                        .eq(CoreIdempotencyRecord::getOperationCode, operation)
                        .eq(CoreIdempotencyRecord::getIdempotencyKey, idempotencyKey))
                .stream().findFirst().orElse(null);
    }

    private CoreIdempotencyRecord currentRecord(long id) {
        return jdbc.queryForObject("select id,context_key,operation_code,idempotency_key,request_hash,status,"
                        + "response_code,response_json,locked_until,expires_at,created_at,updated_at,version "
                        + "from core_idempotency_record where id=? for update", (result, row) -> {
                    CoreIdempotencyRecord value = new CoreIdempotencyRecord();
                    value.setId(result.getLong("id"));
                    value.setContextKey(result.getString("context_key"));
                    value.setOperationCode(result.getString("operation_code"));
                    value.setIdempotencyKey(result.getString("idempotency_key"));
                    value.setRequestHash(result.getString("request_hash"));
                    value.setStatus(result.getString("status"));
                    value.setResponseCode(result.getString("response_code"));
                    value.setResponseJson(result.getString("response_json"));
                    value.setLockedUntil(result.getTimestamp("locked_until") == null ? null
                            : result.getTimestamp("locked_until").toLocalDateTime());
                    value.setExpiresAt(result.getTimestamp("expires_at").toLocalDateTime());
                    value.setCreatedAt(result.getTimestamp("created_at").toLocalDateTime());
                    value.setUpdatedAt(result.getTimestamp("updated_at").toLocalDateTime());
                    value.setVersion(result.getInt("version"));
                    return value;
                }, id);
    }

    private FoundationControlModels.CommandResult readResult(CoreIdempotencyRecord record, boolean replayed) {
        try {
            FoundationControlModels.CommandResult stored = objectMapper.readValue(
                    record.getResponseJson(), FoundationControlModels.CommandResult.class);
            CoreEventOutbox outbox = outboxService.selectById(stored.outboxId());
            return new FoundationControlModels.CommandResult(stored.idempotencyRecordId(), stored.outboxId(),
                    stored.idempotencyKey(), stored.operationCode(), stored.resultReference(), stored.status(), replayed,
                    stored.committedAt(), outbox == null ? "UNKNOWN" : outbox.getStatus());
        } catch (JsonProcessingException exception) {
            throw new DomainException("IDEMPOTENCY_RESULT_INVALID",
                    "首次结果无法安全读取，已拒绝重复执行业务", HttpStatus.CONFLICT);
        }
    }

    private void afterCommit(long outboxId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchCommitted(outboxId);
                }
            });
        } else {
            dispatchCommitted(outboxId);
        }
    }

    private void dispatchCommitted(long outboxId) {
        try {
            dispatcher.dispatch(outboxId);
        } catch (RuntimeException exception) {
            // The business transaction is already durable. The worker retries the pending event.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox delivery deferred: {}", outboxId, exception);
        }
    }

    private String application(String value) {
        return value == null || value.isBlank() ? DEFAULT_APPLICATION : value.strip().toLowerCase(Locale.ROOT);
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot hash idempotent request", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new DomainException("FOUNDATION_RESULT_INVALID", "运行控制结果无法保存", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.tenantMemberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统租户上下文", HttpStatus.BAD_REQUEST);
        }
    }
}
