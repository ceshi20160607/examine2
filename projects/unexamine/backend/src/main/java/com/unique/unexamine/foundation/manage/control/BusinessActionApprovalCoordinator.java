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

    public BusinessActionApprovalCoordinator(BusinessActionMapper mapper, CoreIdempotencyRecordBaseService results,
            CoreEventOutboxBaseService events, PermissionResolver permissions, ObjectProvider<RuntimeDataService> runtime,
            ObjectProvider<FoundationExtensionDispatcher> dispatcher, AuditRecorder audit, ObjectMapper json,
            PlatformTransactionManager transactionManager) {
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
    }

    public void reconcileDue() {
        for (Long actionId : mapper.completedApprovalActions()) reconcile(actionId);
    }

    public void reconcile(long actionId) { settle(actionId, false); }

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
                    finish(intent, flow.getStatus(), null, null, null);
                    return null;
                }
                AuthenticatedContext actor = currentActor(intent.input().actor());
                // 动作结果既可能是记录，也可能包含转化目标；保留原类型结构供原请求回放。
                Object value = runtime.getObject().resumeApprovedAction(
                        new BusinessAction.ApprovedExecution(intent.input(), intent.payload().path("configurationSnapshot")), actor);
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
                event.setAvailableAt(LocalDateTime.now());
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
            return new Intent(record, event, payload, json.treeToValue(payload.get("action"), BusinessAction.Input.class),
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

    private AuthenticatedContext currentActor(AuthenticatedContext original) {
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
