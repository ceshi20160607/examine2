package com.unique.unexamine.foundation.manage.control;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.flow.base.entity.FlowAction;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.entity.FlowTask;
import com.unique.unexamine.flow.base.service.FlowActionBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 节点修改附着在已同意或发起人重提的任务动作上，原 outbox 意图保持不变；只在原动作续跑时合并。 */
@Service
public class BusinessActionFormSubmissions {
    private final FlowActionBaseService actions;
    private final FlowTaskBaseService tasks;
    private final ObjectMapper json;
    private final BusinessActionValueProtection protection;

    public BusinessActionFormSubmissions(FlowActionBaseService actions, FlowTaskBaseService tasks,
            ObjectMapper json, BusinessActionValueProtection protection) {
        this.actions = actions;
        this.tasks = tasks;
        this.json = BusinessAction.storageMapper(json);
        this.protection = protection;
    }

    /** 此方法的字段补丁必须先通过当前处理人、冻结节点和来源字段验证，不接受客户端快照。 */
    public ObjectNode snapshot(BusinessAction.Input original, JsonNode configuration, FlowInstance flow,
            FlowTask task, Map<String, JsonNode> normalized) {
        ObjectNode event = json.createObjectNode();
        event.put("instanceId", flow.getId());
        event.put("flowVersionId", flow.getFlowVersionId());
        event.put("taskId", task.getId());
        event.put("nodeKey", task.getNodeKey());
        ObjectNode payload = json.createObjectNode();
        payload.set(container(original), json.valueToTree(normalized));
        event.set("action", json.valueToTree(withPayload(original, payload, submissionTrace(original, flow, task))));
        return protection.protect(event, protection.secretFields(configuration));
    }

    /** 最终业务执行才解密；发起身份、版本、动作和来源范围仍完全来自原意图。 */
    public BusinessAction.Input mergeForExecution(BusinessAction.Input original, FlowInstance flow) {
        Map<String, JsonNode> values = approvedValues(original, flow, true);
        return values.isEmpty() ? original : overlay(original, values, true);
    }

    /** 页面只投影已同意的补丁，不解密密钥，不混成原提交或当前正式值。 */
    public BusinessAction.Input submittedForRead(BusinessAction.Input original, FlowInstance flow) {
        Map<String, JsonNode> values = approvedValues(original, flow, false);
        return values.isEmpty() ? null : overlay(original, values, false);
    }

    /** 修订号仅标识已批准字段提交，不包含业务值或密钥。 */
    public long formRevision(FlowInstance flow) {
        long revision = 0;
        for (FlowAction action : submittedActions(flow)) {
            try {
                if (json.readTree(action.getInputJson()).has("formSubmission")) revision = action.getId();
            } catch (Exception invalid) { throw invalidSnapshot(); }
        }
        return revision;
    }

    private boolean currentRead() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    /** 写事务等待实例锁后必须读取最新提交，不能继续使用等待前建立的RR快照。 */
    private List<FlowAction> submittedActions(FlowInstance flow) {
        return actions.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, flow.getId())
                .and(action -> action.eq(FlowAction::getActionCode, "APPROVE")
                        .or().eq(FlowAction::getActionCode, "RESUBMIT"))
                .isNotNull(FlowAction::getTaskId).orderByAsc(FlowAction::getId).last(currentRead(), "FOR SHARE"));
    }

    private Map<String, JsonNode> approvedValues(BusinessAction.Input original, FlowInstance flow, boolean execute) {
        Map<String, JsonNode> merged = new LinkedHashMap<>();
        for (FlowAction action : submittedActions(flow)) {
            JsonNode stored;
            try { stored = json.readTree(action.getInputJson()); }
            catch (Exception invalid) { throw invalidSnapshot(); }
            JsonNode submission = stored.path("formSubmission");
            if (submission.isMissingNode()) continue;
            FlowTask task = currentRead() ? tasks.selectList(Wrappers.<FlowTask>lambdaQuery()
                    .eq(FlowTask::getId, action.getTaskId()).last("FOR SHARE")).stream().findFirst().orElse(null)
                    : tasks.selectById(action.getTaskId());
            if (!(submission instanceof ObjectNode event) || task == null
                    || !Objects.equals(action.getActionCode(), task.getStatus())
                    || !java.util.Set.of("APPROVE", "RESUBMIT").contains(action.getActionCode())
                    || !Objects.equals(task.getInstanceId(), flow.getId())
                    || !Objects.equals(task.getNodeKey(), action.getNodeKey())
                    || !approvedHandler(action, task)
                    || submission.path("instanceId").asLong() != flow.getId()
                    || submission.path("flowVersionId").asLong() != flow.getFlowVersionId()
                    || submission.path("taskId").asLong() != task.getId()
                    || !Objects.equals(task.getNodeKey(), submission.path("nodeKey").asText())) throw invalidSnapshot();
            BusinessAction.Input protectedInput = json.convertValue(event.path("action"), BusinessAction.Input.class);
            if (!Objects.equals(original.actor(), protectedInput.actor())
                    || !Objects.equals(original.moduleId(), protectedInput.moduleId())
                    || !Objects.equals(original.moduleCode(), protectedInput.moduleCode())
                    || !Objects.equals(original.dataTenantId(), protectedInput.dataTenantId())
                    || !Objects.equals(original.configVersionId(), protectedInput.configVersionId())
                    || !Objects.equals(original.recordId(), protectedInput.recordId())
                    || !Objects.equals(original.expectedVersion(), protectedInput.expectedVersion())
                    || !Objects.equals(original.actionCode(), protectedInput.actionCode())
                    || !Objects.equals(original.options(), protectedInput.options())
                    || !Objects.equals(submissionTrace(original, flow, task), protectedInput.traceId())) throw invalidSnapshot();
            BusinessAction.Input input = execute
                    ? json.convertValue(protection.restoreForExecution(event).path("action"), BusinessAction.Input.class)
                    : protection.flowInput(event);
            JsonNode values = json.valueToTree(input.payload()).path(container(original));
            if (!values.isObject()) throw invalidSnapshot();
            values.fields().forEachRemaining(entry -> merged.put(entry.getKey(), entry.getValue()));
        }
        return merged;
    }

    private BusinessAction.Input overlay(BusinessAction.Input original, Map<String, JsonNode> values, boolean includeOriginal) {
        ObjectNode payload = json.valueToTree(original.payload());
        String container = container(original);
        ObjectNode fields = includeOriginal && payload.path(container).isObject()
                ? ((ObjectNode) payload.path(container)).deepCopy() : json.createObjectNode();
        values.forEach(fields::set);
        payload.set(container, fields);
        return withPayload(original, payload, original.traceId());
    }

    private String container(BusinessAction.Input original) {
        return java.util.Set.of("CREATE", "UPDATE").contains(original.actionCode()) ? "fields" : "values";
    }

    private boolean approvedHandler(FlowAction action, FlowTask task) {
        if (task.getAssigneeAccountId() != null) return Objects.equals(action.getActedByAccountId(), task.getAssigneeAccountId());
        try {
            // 或签候选事项没有固定 assignee，须仍属于任务创建时服务端解析出的人员。
            for (JsonNode person : json.readTree(task.getAssigneeSnapshotJson()).path("resolvedPeople")) {
                if (person.path("accountId").isIntegralNumber()) {
                    if (Objects.equals(action.getActedByAccountId(), person.path("accountId").longValue())) return true;
                } else if (person.path("tenantMemberId").isIntegralNumber()
                        && person.path("tenantMemberId").asLong() > 0
                        && Objects.equals(action.getActedByTenantMemberId(), person.path("tenantMemberId").longValue())) {
                    // 旧系统或签快照仅保存成员；受控动作已记录真实来源成员，不将空账号解释为候选资格。
                    return true;
                }
            }
            return false;
        } catch (Exception invalid) { return false; }
    }

    private String submissionTrace(BusinessAction.Input original, FlowInstance flow, FlowTask task) {
        // 身份认证路径绑定任务及冻结版本，密钥快照搬到另一任务无法解密。
        return original.traceId() + ":node-form:" + flow.getId() + ":" + flow.getFlowVersionId()
                + ":" + task.getId() + ":" + task.getNodeKey();
    }

    private BusinessAction.Input withPayload(BusinessAction.Input original, Object payload, String trace) {
        return new BusinessAction.Input(original.actor(), original.dataTenantId(), original.moduleId(), original.moduleCode(),
                original.configVersionId(), original.actionCode(), original.recordId(), original.expectedVersion(), payload,
                original.options(), trace);
    }

    private DomainException invalidSnapshot() {
        return new DomainException("ACTION_NODE_FORM_INVALID", "审批节点提交快照无法核验，未执行业务变更", HttpStatus.CONFLICT);
    }
}
