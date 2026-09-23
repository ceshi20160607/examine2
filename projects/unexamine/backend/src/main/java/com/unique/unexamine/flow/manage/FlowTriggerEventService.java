package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.foundation.manage.control.FoundationExtensionDispatcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 业务事件进入 Flow 的可靠入口。业务服务只发布安全的事件事实，绑定解析和流程发起由发件箱派发器完成。
 */
@Service
public class FlowTriggerEventService {
    public static final String AGGREGATE_TYPE = "FLOW_TRIGGER_EVENT";
    private static final DateTimeFormatter SCHEDULE_SLOT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final CoreEventOutboxBaseService outboxService;
    private final FoundationExtensionDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<FlowBindingService> bindings;

    public FlowTriggerEventService(CoreEventOutboxBaseService outboxService,
            FoundationExtensionDispatcher dispatcher, ObjectMapper objectMapper,
            ObjectProvider<FlowBindingService> bindings) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
        this.bindings = bindings;
    }

    /**
     * 发布导入终态事件。事件只保留批次统计和身份引用，不保存原始行值、密码、密钥或文件内容。
     */
    public List<Long> enqueueImportEvents(AuthenticatedContext context, Long moduleId, String moduleCode,
            Long batchId, String importStatus, long totalRows, long successRows, long failedRows, Long jobId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("importStatus", importStatus);
        variables.put("totalRows", totalRows);
        variables.put("successRows", successRows);
        variables.put("failedRows", failedRows);
        variables.put("exceptionSource", "IMPORT_BATCH");
        List<String> eventTypes = failedRows > 0
                ? List.of("IMPORT_COMPLETED", "EXCEPTION") : List.of("IMPORT_COMPLETED");
        return eventTypes.stream().map(eventType -> ensureEvent(context, moduleId, moduleCode, batchId,
                eventType, variables, jobId)).toList();
    }

    /**
     * 独立保存模块业务校验异常。调用方原事务即使回滚，脱敏异常事实仍可由公共发件箱可靠投递。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enqueueModuleValidationException(
            AuthenticatedContext context, Long moduleId, String moduleCode, Long configVersionId,
            String actionCode, Long recordId, String actionKey, String errorCategory,
            String ruleType, Long ruleId, String ruleCode, String ruleName) {
        String fingerprint = sha256(String.join("\u001F", List.of(
                String.valueOf(context.systemId()), String.valueOf(context.tenantId()), String.valueOf(moduleId),
                actionCode, actionKey, errorCategory, ruleType, String.valueOf(ruleId), ruleCode, ruleName)));
        String aggregateId = "MODULE_VALIDATION:" + fingerprint;
        CoreEventOutbox event = outboxService.selectList(Wrappers.<CoreEventOutbox>lambdaQuery()
                        .eq(CoreEventOutbox::getAggregateType, AGGREGATE_TYPE)
                        .eq(CoreEventOutbox::getSystemId, context.systemId())
                        .eq(CoreEventOutbox::getTenantId, context.tenantId())
                        .eq(CoreEventOutbox::getAggregateId, aggregateId)
                        .eq(CoreEventOutbox::getEventType, "EXCEPTION")
                        .orderByAsc(CoreEventOutbox::getId).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (event != null) return event.getId();

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("exceptionSource", "MODULE_VALIDATION");
        variables.put("actionCode", actionCode);
        variables.put("errorCategory", errorCategory);
        variables.put("ruleType", ruleType);
        if (ruleId != null) variables.put("ruleId", ruleId);
        if (!ruleCode.isBlank()) variables.put("ruleCode", ruleCode);
        if (!ruleName.isBlank()) variables.put("ruleName", ruleName);
        if (recordId != null) variables.put("recordId", recordId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "EXCEPTION");
        payload.put("sourceType", "MODULE_VALIDATION");
        payload.put("sourceId", actionCode + ":" + fingerprint.substring(0, 12));
        payload.put("moduleId", moduleId);
        payload.put("moduleCode", moduleCode);
        payload.put("configVersionId", configVersionId);
        payload.put("recordId", recordId);
        payload.put("variables", variables);
        payload.put("actor", actorReferences(context));
        payload.put("occurredTime", LocalDateTime.now().toString());

        event = new CoreEventOutbox();
        event.setContextType("SYSTEM");
        event.setSystemId(context.systemId());
        event.setTenantId(context.tenantId());
        event.setAggregateType(AGGREGATE_TYPE);
        event.setAggregateId(aggregateId);
        event.setEventType("EXCEPTION");
        event.setPayloadJson(write(payload));
        event.setStatus("PENDING");
        event.setAvailableAt(LocalDateTime.now());
        event.setRetryCount(0);
        event.setVersion(0);
        outboxService.insert(event);
        // 独立事务提交后由公共重试扫描立即投递，避免派发器在提交前看不到新事件。
        return event.getId();
    }

    /** 读取当前工作空间的近期投递结果，供普通绑定页展示真实触发历史。 */
    public List<FlowBindingModels.EventDeliveryView> recent(
            AuthenticatedContext context, Long moduleId, String eventType) {
        return outboxService.selectList(Wrappers.<CoreEventOutbox>lambdaQuery()
                        .eq(CoreEventOutbox::getAggregateType, AGGREGATE_TYPE)
                        .eq(CoreEventOutbox::getSystemId, context.systemId())
                        .eq(CoreEventOutbox::getTenantId, context.tenantId())
                        .eq(CoreEventOutbox::getEventType, eventType)
                        .orderByDesc(CoreEventOutbox::getId).last("limit 30"))
                .stream().map(this::view).filter(view -> Objects.equals(view.moduleId(), moduleId))
                .limit(10).toList();
    }

    /**
     * 用发件箱的未来可用时间承载周期计划。扫描只补缺失的下一条计划，不创建第二套调度基础设施。
     */
    @Scheduled(fixedDelayString = "${unexamine.flow.schedule.poll-delay-ms:1000}")
    public void reconcileSchedules() {
        for (FlowBindingService.ScheduledBinding binding : bindings.getObject().activeSchedules()) {
            try {
                ensureScheduledEvent(binding);
            } catch (RuntimeException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "Flow schedule reconcile deferred for binding {}", binding.bindingId(), exception);
            }
        }
    }

    private Long ensureEvent(AuthenticatedContext context, Long moduleId, String moduleCode, Long batchId,
            String eventType, Map<String, Object> variables, Long jobId) {
        String aggregateId = "IMPORT_BATCH:" + batchId + ":" + eventType;
        CoreEventOutbox event = outboxService.selectList(Wrappers.<CoreEventOutbox>lambdaQuery()
                        .eq(CoreEventOutbox::getAggregateType, AGGREGATE_TYPE)
                        .eq(CoreEventOutbox::getSystemId, context.systemId())
                        .eq(CoreEventOutbox::getTenantId, context.tenantId())
                        .eq(CoreEventOutbox::getAggregateId, aggregateId)
                        .eq(CoreEventOutbox::getEventType, eventType)
                        .orderByAsc(CoreEventOutbox::getId).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (event == null) {
            event = new CoreEventOutbox();
            event.setContextType("SYSTEM");
            event.setSystemId(context.systemId());
            event.setTenantId(context.tenantId());
            event.setAggregateType(AGGREGATE_TYPE);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("sourceType", "IMPORT_BATCH");
            payload.put("sourceId", String.valueOf(batchId));
            payload.put("moduleId", moduleId);
            payload.put("moduleCode", moduleCode);
            payload.put("jobId", jobId);
            payload.put("variables", variables);
            payload.put("actor", actor(context));
            payload.put("occurredTime", LocalDateTime.now().toString());
            event.setPayloadJson(write(payload));
            event.setStatus("PENDING");
            event.setAvailableAt(LocalDateTime.now());
            event.setRetryCount(0);
            event.setVersion(0);
            outboxService.insert(event);
        }
        // 派发器内部记录失败并退避；导入事务事实不会因为流程暂不可用而回滚。
        dispatcher.dispatch(event.getId());
        return event.getId();
    }

    private void ensureScheduledEvent(FlowBindingService.ScheduledBinding binding) {
        String prefix = "FLOW_SCHEDULE:" + binding.bindingId() + ":";
        List<CoreEventOutbox> events = outboxService.selectList(Wrappers.<CoreEventOutbox>lambdaQuery()
                .eq(CoreEventOutbox::getAggregateType, AGGREGATE_TYPE)
                .eq(CoreEventOutbox::getSystemId, binding.systemId())
                .eq(CoreEventOutbox::getTenantId, binding.tenantId())
                .eq(CoreEventOutbox::getEventType, "SCHEDULED")
                .likeRight(CoreEventOutbox::getAggregateId, prefix)
                .orderByDesc(CoreEventOutbox::getId).last("limit 100"));
        if (events.stream().anyMatch(event -> Set.of("PENDING", "FAILED").contains(event.getStatus()))) return;

        LocalDateTime now = LocalDateTime.now(FlowSchedulePolicy.BUSINESS_ZONE);
        LocalDateTime anchor = binding.updatedTime() == null ? now : binding.updatedTime();
        CoreEventOutbox latest = events.stream().filter(event -> "PUBLISHED".equals(event.getStatus())).findFirst().orElse(null);
        if (latest != null) {
            LocalDateTime previous = scheduledTime(latest);
            anchor = previous.isBefore(now.minusMinutes(1)) ? now : previous;
        }
        LocalDateTime scheduledTime = FlowSchedulePolicy.nextAfter(binding.schedule(), anchor);
        boolean missed = !scheduledTime.isAfter(now);
        if (missed && "SKIP".equals(binding.schedule().misfirePolicy())) {
            scheduledTime = FlowSchedulePolicy.nextAfter(binding.schedule(), now);
            missed = false;
        }
        String aggregateId = prefix + scheduledTime.format(SCHEDULE_SLOT);
        if (events.stream().anyMatch(event -> aggregateId.equals(event.getAggregateId()))) return;

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("scheduledTime", scheduledTime.toString());
        variables.put("scheduleMode", binding.schedule().scheduleMode());
        variables.put("misfire", missed);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "SCHEDULED");
        payload.put("sourceType", "FLOW_SCHEDULE");
        payload.put("sourceId", scheduledTime.toString());
        payload.put("moduleId", binding.moduleId());
        payload.put("moduleCode", binding.moduleCode());
        payload.put("scheduleBindingId", binding.bindingId());
        payload.put("schedule", binding.schedule());
        payload.put("variables", variables);
        payload.put("actor", actor(binding.actor()));
        payload.put("occurredTime", scheduledTime.toString());

        CoreEventOutbox event = new CoreEventOutbox();
        event.setContextType("SYSTEM");
        event.setSystemId(binding.systemId());
        event.setTenantId(binding.tenantId());
        event.setAggregateType(AGGREGATE_TYPE);
        event.setAggregateId(aggregateId);
        event.setEventType("SCHEDULED");
        event.setPayloadJson(write(payload));
        event.setStatus("PENDING");
        // 发件箱以 JVM 本地时间轮询；容器可能运行在 UTC，需把上海业务时刻换算到同一时间轴。
        event.setAvailableAt(toPersistenceTime(missed ? now : scheduledTime));
        event.setRetryCount(0);
        event.setVersion(0);
        outboxService.insert(event);
        if (!event.getAvailableAt().isAfter(now)) dispatcher.dispatch(event.getId());
    }

    private LocalDateTime scheduledTime(CoreEventOutbox event) {
        String value = string(read(event.getPayloadJson()).get("sourceId"));
        if (value == null) return event.getAvailableAt();
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ignored) {
            return event.getAvailableAt();
        }
    }

    private LocalDateTime toPersistenceTime(LocalDateTime businessTime) {
        return LocalDateTime.ofInstant(businessTime.atZone(FlowSchedulePolicy.BUSINESS_ZONE).toInstant(),
                ZoneId.systemDefault());
    }

    private Map<String, Object> actor(AuthenticatedContext context) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("accountId", context.accountId());
        actor.put("platformId", context.platformId());
        actor.put("systemId", context.systemId());
        actor.put("tenantId", context.tenantId());
        actor.put("memberId", context.memberId());
        actor.put("tenantMemberId", context.tenantMemberId());
        actor.put("username", context.username());
        actor.put("displayName", context.displayName());
        actor.put("mfaLevel", context.mfaLevel());
        return actor;
    }

    private Map<String, Object> actorReferences(AuthenticatedContext context) {
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("accountId", context.accountId());
        actor.put("platformId", context.platformId());
        actor.put("systemId", context.systemId());
        actor.put("tenantId", context.tenantId());
        actor.put("memberId", context.memberId());
        actor.put("tenantMemberId", context.tenantMemberId());
        return actor;
    }

    private FlowBindingModels.EventDeliveryView view(CoreEventOutbox event) {
        Map<String, Object> payload = read(event.getPayloadJson());
        Map<String, Object> variables = payload.get("variables") instanceof Map<?, ?> value
                ? objectMapper.convertValue(value, new TypeReference<>() { }) : Map.of();
        return new FlowBindingModels.EventDeliveryView(event.getId(), longValue(payload.get("moduleId")),
                event.getEventType(), String.valueOf(payload.getOrDefault("sourceType", "BUSINESS_EVENT")),
                String.valueOf(payload.getOrDefault("sourceId", "")), event.getStatus(),
                event.getRetryCount() == null ? 0 : event.getRetryCount(),
                payload.get("flowInstanceId") == null ? null : longValue(payload.get("flowInstanceId")),
                "PUBLISHED".equals(event.getStatus()) || event.getLastError() == null || event.getLastError().isBlank()
                        ? null : "触发失败，系统将自动重试",
                string(payload.get("occurredTime")), "SCHEDULED".equals(event.getEventType())
                        ? scheduledTime(event) : event.getAvailableAt(), event.getPublishedAt(),
                string(payload.get("deliveryResult")), string(variables.get("actionCode")),
                string(variables.get("errorCategory")), string(variables.get("ruleName")),
                longValue(variables.get("recordId")));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Flow 业务事件无法序列化", exception);
        }
    }

    private Map<String, Object> read(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Flow 业务事件无法读取", exception);
        }
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
