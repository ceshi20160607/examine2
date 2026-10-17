package com.unique.unexamine.foundation.manage.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.entity.CoreIdempotencyRecord;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.foundation.base.service.CoreIdempotencyRecordBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resumes the existing action from a durable Flow outcome. There is no client-supplied approval decision. */
@Service
public class BusinessActionApprovalCoordinator {
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "REJECTED", "WITHDRAWN", "TERMINATED", "RETURNED");
    private final BusinessActionMapper mapper;
    private final CoreIdempotencyRecordBaseService results;
    private final CoreEventOutboxBaseService events;
    private final PermissionResolver permissions;
    private final ObjectProvider<RuntimeDataService> runtime;
    private final ObjectProvider<FoundationExtensionDispatcher> dispatcher;
    private final AuditRecorder audit;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final BusinessActionValueProtection valueProtection;
    /** 节点已批准字段补丁独立于原待审批意图。 */
    private final BusinessActionFormSubmissions formSubmissions;

    public BusinessActionApprovalCoordinator(BusinessActionMapper mapper, CoreIdempotencyRecordBaseService results,
            CoreEventOutboxBaseService events, PermissionResolver permissions, ObjectProvider<RuntimeDataService> runtime,
            ObjectProvider<FoundationExtensionDispatcher> dispatcher, AuditRecorder audit, ObjectMapper json,
            PlatformTransactionManager transactionManager, BusinessActionValueProtection valueProtection,
            BusinessActionFormSubmissions formSubmissions) {
        this.mapper = mapper;
        this.results = results;
        this.events = events;
        this.permissions = permissions;
        this.runtime = runtime;
        this.dispatcher = dispatcher;
        this.audit = audit;
        this.json = BusinessAction.storageMapper(json);
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.valueProtection = valueProtection;
        this.formSubmissions = formSubmissions;
    }

    public void reconcileDue() {
        for (Long actionId : mapper.completedApprovalActions()) reconcile(actionId);
    }

    /** 审批申请以已持久化的原动作标识为准，终态尚未结算时仍阻止普通编辑。 */
    public boolean pendingForRecord(long systemId, long tenantId, String moduleCode, long recordId) {
        return mapper.pendingApprovalForRecord(systemId, tenantId, moduleCode, recordId) > 0;
    }

    public void reconcile(long actionId) { settle(actionId, false); }

    /** 本人申请目录只读取当前成员上下文；调用方还须复用原动作进度的业务权限校验。 */
    public java.util.List<CoreEventOutbox> ownFlowEvents(AuthenticatedContext viewer, Long cursor, int pageSize) {
        currentActor(viewer);
        return mapper.ownFlowEvents(viewer, cursor, pageSize);
    }

    /** 通知仅从内部 START/outbox 恢复原申请人，手动流程没有这种业务动作关联。 */
    public NotificationAction notificationAction(FlowInstance flow) {
        CoreEventOutbox event = mapper.boundFlowEvent(flow.getId());
        if (event == null) return null;
        try {
            var input = json.treeToValue(read(event.getPayloadJson()).get("action"), BusinessAction.Input.class);
            long actionId = Long.parseLong(event.getAggregateId());
            actionForProgress(flow, input.actor(), input.moduleCode(), actionId);
            return new NotificationAction(actionId, input);
        } catch (JsonProcessingException | NumberFormatException exception) {
            throw conflict("ACTION_INTENT_INVALID", "原业务动作无法读取，已阻止通知跳转");
        }
    }

    /** 已验证关联的原动作标识及实际发起人；不会作为接口响应输出原始输入。 */
    public record NotificationAction(long actionId, BusinessAction.Input input) {}

    // Called only after the receipt endpoint has checked ownership. Execution still rechecks original authority.
    void retry(long actionId) { settle(actionId, true); }

    private void settle(long actionId, boolean retry) {
        Long completion;
        try {
            completion = transactions.execute(tx -> {
                Intent intent = lockIntent(actionId);
                if (!eligible(intent, retry)) return null;
                FlowInstance flow = requireFlow(intent);
                if (!TERMINAL.contains(flow.getStatus())) return null;
                if (!"COMPLETED".equals(flow.getStatus())) {
                    String targetStatus = mappedStatus(intent, flow.getStatus());
                    if (targetStatus != null && Set.of("REJECTED", "WITHDRAWN").contains(flow.getStatus())) {
                        runtime.getObject().applyApprovalTerminalStatus(intent.input(), targetStatus,
                                flow.getStatus(), flow.getId());
                    }
                    finish(intent, flow.getStatus(), null, null, null);
                    return null;
                }
                AuthenticatedContext actor = currentActor(intent.input().actor());
                // 动作结果既可能是记录，也可能包含转化目标；保留原类型结构供原请求回放。
                BusinessAction.Input approvedInput = formSubmissions.mergeForExecution(intent.input(), flow);
                String approvedStatus = mappedStatus(intent, "COMPLETED");
                if (approvedStatus != null) {
                    ObjectNode payload = (ObjectNode) json.valueToTree(approvedInput.payload());
                    payload.put("status", approvedStatus);
                    approvedInput = new BusinessAction.Input(approvedInput.actor(), approvedInput.dataTenantId(),
                            approvedInput.moduleId(), approvedInput.moduleCode(), approvedInput.configVersionId(),
                            approvedInput.actionCode(), approvedInput.recordId(), approvedInput.expectedVersion(),
                            payload, approvedInput.options(), approvedInput.traceId());
                }
                Object value = runtime.getObject().resumeApprovedAction(
                        new BusinessAction.ApprovedExecution(approvedInput,
                                intent.payload().path("configurationSnapshot")), actor);
                finish(intent, "EXECUTED", value, null, null);
                CoreEventOutbox event = new CoreEventOutbox();
                event.setContextType("SYSTEM");
                event.setSystemId(actor.systemId());
                event.setTenantId(intent.input().dataTenantId());
                event.setAggregateType("BUSINESS_ACTION_COMPLETION");
                event.setAggregateId(Long.toString(actionId));
                event.setEventType("BUSINESS_ACTION_COMPLETED");
                event.setPayloadJson(write(intent.payload()));
                event.setStatus("PENDING");
                event.setAvailableTime(LocalDateTime.now());
                event.setRetryCount(0);
                events.insert(event);
                return event.getId();
            });
        } catch (RuntimeException exception) {
            // Failed SQL/business validation rolls back the whole attempt; only then persist its readable outcome.
            transactions.executeWithoutResult(tx -> {
                Intent intent = lockIntent(actionId);
                if (!eligible(intent, retry)) return;
                String code = exception instanceof DomainException domain ? domain.code() : "ACTION_EXECUTION_FAILED";
                String message = exception instanceof DomainException domain ? domain.getMessage()
                        : "审批已通过，但业务执行失败，数据未变更；请稍后重试";
                finish(intent, exception instanceof DomainException ? "CONFLICT" : "EXECUTION_FAILED", null, code, message);
            });
            return;
        }
        if (completion != null) {
            try {
                dispatcher.getObject().dispatch(completion);
            } catch (RuntimeException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Approval action {} committed; delivery remains queued", actionId, exception);
            }
        }
    }

    private boolean eligible(Intent intent, boolean retry) {
        return intent != null && ("PENDING_APPROVAL".equals(intent.status())
                || retry && Set.of("CONFLICT", "EXECUTION_FAILED").contains(intent.status()));
    }

    /** 只读取本次申请固定的已发布绑定快照；之后修改绑定不改变进行中的审批结果。 */
    private String mappedStatus(Intent intent, String outcome) {
        String value = intent.payload().path("binding").path("approvalStatusMapping").path(outcome).asText("").strip();
        return value.isBlank() ? null : value;
    }

    private Intent lockIntent(long actionId) {
        CoreIdempotencyRecord record = mapper.lockResult(actionId);
        if (record == null || record.getResponseJson() == null) return null;
        ObjectNode response = read(record.getResponseJson());
        if (!"AFTER_APPROVAL".equals(response.path("mode").asText())) return null;
        CoreEventOutbox event = mapper.lockEvent(response.path("outboxId").asLong());
        if (event == null || !"BUSINESS_ACTION_APPROVAL_REQUESTED".equals(event.getEventType())
                || !Long.toString(actionId).equals(event.getAggregateId())) {
            throw conflict("ACTION_INTENT_INVALID", "待审批操作记录不完整，已阻止执行");
        }
        ObjectNode payload = read(event.getPayloadJson());
        try {
            // 仅锁定后供原动作执行解密；payload仍是加密存储副本，终态回写不会重存明文意图。
            ObjectNode execution = valueProtection.restoreForExecution(payload);
            return new Intent(record, event, payload, json.treeToValue(execution.get("action"), BusinessAction.Input.class),
                    response.path("status").asText(), payload.path("flowInstanceId").asLong());
        } catch (JsonProcessingException exception) {
            throw conflict("ACTION_INTENT_INVALID", "待审批操作内容无法读取，已阻止执行");
        }
    }

    private FlowInstance requireFlow(Intent intent) {
        FlowInstance flow = mapper.lockApprovalFlow(intent.flowId());
        if (flow == null || !Objects.equals(flow.getSystemId(), intent.input().actor().systemId())
                || !Objects.equals(flow.getTenantId(), intent.input().dataTenantId())
                || !Objects.equals(flow.getStartedByAccountId(), intent.input().actor().accountId())
                || flow.getFlowVersionId() != intent.payload().path("binding").path("flowVersionId").asLong()
                || read(flow.getBusinessSnapshotJson()).path("actionId").asLong() != intent.record().getId()) {
            throw conflict("ACTION_APPROVAL_FLOW_MISMATCH", "审批流程与原操作不匹配，已阻止执行");
        }
        return flow;
    }

    /** 内部流程续跑只恢复服务端持久化的原动作，随后重新校验真实发起人的当前身份和权限。 */
    public AuthenticatedContext actorForBoundFlow(FlowInstance flow) {
        CoreEventOutbox event = mapper.boundFlowEvent(flow.getId());
        if (event == null) throw conflict("ACTION_FLOW_ORIGIN_MISSING", "流程的原业务动作不存在，已阻止执行");
        var payload = read(event.getPayloadJson());
        try {
            BusinessAction.Input input = json.treeToValue(payload.get("action"), BusinessAction.Input.class);
            if (!Objects.equals(flow.getSystemId(), input.actor().systemId())
                    || !Objects.equals(flow.getTenantId(), input.dataTenantId())
                    || !Objects.equals(flow.getStartedByAccountId(), input.actor().accountId())
                    || flow.getFlowVersionId() != payload.path("binding").path("flowVersionId").asLong()
                    || (payload.hasNonNull("flowInstanceId") && flow.getId() != payload.path("flowInstanceId").asLong())) {
                throw conflict("ACTION_APPROVAL_FLOW_MISMATCH", "流程与原业务动作不匹配，已阻止执行");
            }
            return currentActor(input.actor());
        } catch (JsonProcessingException exception) {
            throw conflict("ACTION_INTENT_INVALID", "原业务动作无法读取，已阻止执行");
        }
    }

    /** 查看入口必须从服务端 START/outbox 关联恢复本人原动作，不能相信流程业务快照中的身份。 */
    public BusinessAction.Input actionForProgress(FlowInstance flow, AuthenticatedContext viewer,
                                                  String moduleCode, long actionId) {
        CoreEventOutbox event = mapper.boundFlowEvent(flow.getId());
        if (event == null || !"BUSINESS_ACTION".equals(event.getAggregateType())
                || !Long.toString(actionId).equals(event.getAggregateId())
                || !Objects.equals(event.getSystemId(), viewer.systemId())) {
            throw new DomainException("ACTION_FLOW_NOT_FOUND", "该操作的审批进度不存在或无权访问", HttpStatus.NOT_FOUND);
        }
        var payload = read(event.getPayloadJson());
        try {
            BusinessAction.Input input = json.treeToValue(payload.get("action"), BusinessAction.Input.class);
            AuthenticatedContext original = input.actor();
            if (!moduleCode.equals(input.moduleCode()) || !Objects.equals(viewer.accountId(), original.accountId())
                    || !Objects.equals(viewer.systemId(), original.systemId())
                    || !Objects.equals(viewer.tenantId(), original.tenantId())
                    || !Objects.equals(viewer.memberId(), original.memberId())
                    || !Objects.equals(viewer.tenantMemberId(), original.tenantMemberId())
                    || !Objects.equals(flow.getFlowId(), payload.path("binding").path("flowId").asLong())
                    || !("MODULE:" + moduleCode).equals(flow.getBusinessType())) {
                throw new DomainException("ACTION_FLOW_NOT_FOUND", "该操作的审批进度不存在或无权访问", HttpStatus.NOT_FOUND);
            }
            // 同样验证真实来源数据租户、发起账号和绑定版本；返回时仍使用接收方当前权限。
            actorForBoundFlow(flow);
            return input;
        } catch (JsonProcessingException exception) {
            throw conflict("ACTION_INTENT_INVALID", "原业务动作无法读取，已阻止访问");
        }
    }

    /** 只读页面从服务端 START/outbox 恢复原提交；不借发起人权限，也不解密待审批密钥。 */
    public BoundBusinessRead businessReadForFlow(FlowInstance flow) {
        CoreEventOutbox event = mapper.boundFlowEvent(flow.getId());
        if (event == null) return null;
        var payload = read(event.getPayloadJson());
        // 启动事务先保存 START 标记，随后 outbox 才落定实例 ID；初始回执不投影未落定的关联。
        if (!payload.hasNonNull("flowInstanceId")) return null;
        var input = valueProtection.flowInput(payload);
        if (!"BUSINESS_ACTION".equals(event.getAggregateType())
                || !Objects.equals(event.getSystemId(), flow.getSystemId())
                || !Objects.equals(input.actor().systemId(), flow.getSystemId())
                || !Objects.equals(input.dataTenantId(), flow.getTenantId())
                || !Objects.equals(input.actor().accountId(), flow.getStartedByAccountId())
                || !Objects.equals(flow.getFlowId(), payload.path("binding").path("flowId").asLong())
                || !Objects.equals(flow.getFlowVersionId(), payload.path("binding").path("flowVersionId").asLong())
                || !Objects.equals(flow.getId(), payload.path("flowInstanceId").asLong())
                || !("MODULE:" + input.moduleCode()).equals(flow.getBusinessType())) {
            throw conflict("ACTION_APPROVAL_FLOW_MISMATCH", "审批业务关联无法核验，不能读取提交内容");
        }
        Long recordId = input.recordId();
        if (recordId == null && payload.path("result").path("id").isIntegralNumber()) {
            recordId = payload.path("result").path("id").longValue();
        }
        return new BoundBusinessRead(input, payload.path("configurationSnapshot").deepCopy(), recordId);
    }

    /** 数据只供当前查看者重新鉴权，不能用于恢复执行或直接返回给客户端。 */
    public record BoundBusinessRead(BusinessAction.Input input,
            com.fasterxml.jackson.databind.JsonNode configurationSnapshot, Long recordId) {}

    /** 仅内部已授权任务表单使用；查看页面继续使用不解密的 businessReadForFlow。 */
    public BoundBusinessRead formOriginForFlow(FlowInstance flow) {
        BoundBusinessRead safe = businessReadForFlow(flow);
        if (safe == null) return null;
        var event = mapper.boundFlowEvent(flow.getId());
        var payload = read(event.getPayloadJson());
        if (!"AFTER_APPROVAL".equals(payload.path("mode").asText())) return null;
        var restored = valueProtection.restoreForExecution(payload);
        var original = json.convertValue(restored.path("action"), BusinessAction.Input.class);
        return new BoundBusinessRead(original, safe.configurationSnapshot(), original.recordId());
    }

    /** 原发起人仍须属于其实际租户；不能借来源租户或当前审批人的权限续跑。 */
    public AuthenticatedContext currentActor(AuthenticatedContext original) {
        if (mapper.activeActor(original) != 1) {
            throw conflict("ACTION_ACTOR_INACTIVE", "原发起人的账号、系统、租户或成员身份已停用，未执行业务变更");
        }
        var current = permissions.resolve(original.systemId(), original.tenantId(), original.tenantMemberId());
        return new AuthenticatedContext(null, original.accountId(), original.platformId(), original.systemId(),
                original.tenantId(), original.memberId(), original.tenantMemberId(), original.username(), original.displayName(),
                original.mfaLevel(), current.roleIds(), current.permissions(), current.dataScopes());
    }

    private void finish(Intent intent, String status, Object value, String errorCode, String errorMessage) {
        intent.payload().put("approvalStatus", status);
        intent.payload().put("errorCode", errorCode);
        intent.payload().put("errorMessage", errorMessage);
        intent.payload().put("resolvedAt", LocalDateTime.now().toString());
        intent.payload().set("result", json.valueToTree(value));
        intent.event().setPayloadJson(write(intent.payload()));
        var result = new BusinessAction.Result<>(intent.record().getId(), intent.event().getId(),
                intent.record().getIdempotencyKey(), status, false, BusinessAction.Mode.AFTER_APPROVAL, value, intent.flowId());
        intent.record().setResponseJson(write(result));
        if (events.updateById(intent.event()) != 1 || results.updateById(intent.record()) != 1) {
            throw conflict("ACTION_APPROVAL_RESULT_CONFLICT", "审批执行结果保存冲突，数据已回滚");
        }
        var actor = intent.input().actor();
        audit.record(intent.input().traceId(), actor.accountId(), actor.systemId(), actor.tenantId(), actor.memberId(),
                "BUSINESS_ACTION_APPROVAL_" + status, "BUSINESS_ACTION", intent.record().getId().toString(), status,
                Map.of("moduleCode", intent.input().moduleCode(), "actionCode", intent.input().actionCode(),
                        "flowInstanceId", intent.flowId(), "errorCode", errorCode == null ? "" : errorCode));
    }

    private ObjectNode read(String value) {
        try { return (ObjectNode) json.readTree(value); }
        catch (JsonProcessingException exception) { throw conflict("ACTION_INTENT_INVALID", "操作快照无法读取"); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw conflict("ACTION_INTENT_INVALID", "操作结果无法保存"); }
    }

    private DomainException conflict(String code, String message) { return new DomainException(code, message, HttpStatus.CONFLICT); }
    private record Intent(CoreIdempotencyRecord record, CoreEventOutbox event, ObjectNode payload,
                          BusinessAction.Input input, String status, long flowId) {}
}
