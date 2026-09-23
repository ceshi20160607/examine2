package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.manage.control.BusinessActionApprovalCoordinator;
import com.unique.unexamine.foundation.manage.control.FoundationCommandExtension;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

/** 在发件箱锁事务中解析当前绑定并发起流程，流程实例和投递确认保持原子一致。 */
@Component
@Order(10)
public class FlowTriggerEventExtension implements FoundationCommandExtension {
    private final ObjectMapper objectMapper;
    private final FlowBindingService bindings;
    private final ObjectProvider<FlowRuntimeService> runtime;
    private final BusinessActionApprovalCoordinator actors;

    public FlowTriggerEventExtension(ObjectMapper objectMapper, FlowBindingService bindings,
            ObjectProvider<FlowRuntimeService> runtime, BusinessActionApprovalCoordinator actors) {
        this.objectMapper = objectMapper;
        this.bindings = bindings;
        this.runtime = runtime;
        this.actors = actors;
    }

    @Override
    public void afterCommit(CoreEventOutbox event) {
        if (!FlowTriggerEventService.AGGREGATE_TYPE.equals(event.getAggregateType())) return;
        try {
            ObjectNode payload = (ObjectNode) objectMapper.readTree(event.getPayloadJson());
            AuthenticatedContext context = actors.currentActor(actor(payload.path("actor")));
            Map<String, Object> variables = objectMapper.convertValue(payload.path("variables"), new TypeReference<>() { });
            boolean scheduleMissed = "SCHEDULED".equals(event.getEventType())
                    && LocalDateTime.parse(payload.path("sourceId").asText())
                    .isBefore(LocalDateTime.now(FlowSchedulePolicy.BUSINESS_ZONE).minusMinutes(1));
            if ("SCHEDULED".equals(event.getEventType())) variables.put("misfire", scheduleMissed);
            FlowBindingModels.ResolutionView binding = bindings.resolveForEvent(context,
                    payload.path("moduleId").asLong(), event.getEventType(), variables,
                    "flow-event-resolve-" + event.getId());
            if (binding == null) {
                payload.put("deliveryResult", "NO_MATCHING_BINDING");
            } else if ("SCHEDULED".equals(event.getEventType())
                    && binding.bindingId() != payload.path("scheduleBindingId").asLong()) {
                payload.put("deliveryResult", "STALE_SCHEDULE");
            } else if (scheduleMissed && "SKIP".equals(payload.path("schedule").path("misfirePolicy").asText())) {
                payload.put("deliveryResult", "MISFIRE_SKIPPED");
            } else {
                Long instanceId = runtime.getObject().startBoundEvent(context, binding, event.getAggregateId(), event.getEventType(),
                        payload.path("sourceType").asText(), payload.path("sourceId").asText(),
                        payload.path("moduleId").asLong(), payload.path("moduleCode").asText(), variables);
                payload.put("flowInstanceId", instanceId);
                payload.put("bindingId", binding.bindingId());
                payload.put("flowVersionId", binding.flowVersionId());
                payload.put("deliveryResult", "FLOW_STARTED");
            }
            payload.set("variables", objectMapper.valueToTree(variables));
            event.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Flow 业务事件快照无法读取", exception);
        }
    }

    private AuthenticatedContext actor(com.fasterxml.jackson.databind.JsonNode node) {
        return new AuthenticatedContext(null, nullableLong(node, "accountId"), nullableLong(node, "platformId"),
                nullableLong(node, "systemId"), nullableLong(node, "tenantId"), nullableLong(node, "memberId"),
                nullableLong(node, "tenantMemberId"), node.path("username").asText("event"),
                node.path("displayName").asText("业务事件"), node.path("mfaLevel").asText("PASSWORD"),
                List.of(), List.<PermissionGrant>of(), Map.<String, DataScopeExpression>of());
    }

    private Long nullableLong(com.fasterxml.jackson.databind.JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asLong() : null;
    }
}
