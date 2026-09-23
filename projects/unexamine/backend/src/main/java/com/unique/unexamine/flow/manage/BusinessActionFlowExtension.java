package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.manage.control.BusinessAction;
import com.unique.unexamine.foundation.manage.control.FoundationCommandExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Joins the dispatcher's locked transaction; starting a Flow and acknowledging delivery are atomic. */
@Component
@org.springframework.core.annotation.Order(0)
public class BusinessActionFlowExtension implements FoundationCommandExtension {
    private final ObjectMapper json;
    private final ObjectProvider<FlowRuntimeService> runtime;
    private final com.unique.unexamine.foundation.manage.control.BusinessActionValueProtection valueProtection;

    public BusinessActionFlowExtension(ObjectMapper json, ObjectProvider<FlowRuntimeService> runtime,
            com.unique.unexamine.foundation.manage.control.BusinessActionValueProtection valueProtection) {
        this.json = json;
        this.runtime = runtime;
        this.valueProtection = valueProtection;
    }

    public Long start(BusinessAction.Input action, FlowBindingModels.ResolutionView binding,
                      Long actionId, Map<String, Object> result) {
        return runtime.getObject().startBoundAction(action, binding, actionId, result);
    }

    @Override
    public void afterCommit(CoreEventOutbox event) {
        if (!"BUSINESS_ACTION".equals(event.getAggregateType())) return;
        try {
            ObjectNode payload = (ObjectNode) json.readTree(event.getPayloadJson());
            if (!"AFTER_EXECUTION".equals(payload.path("mode").asText())) return;
            Long instanceId = start(valueProtection.flowInput(payload),
                    json.treeToValue(payload.get("binding"), FlowBindingModels.ResolutionView.class),
                    Long.valueOf(event.getAggregateId()),
                    json.convertValue(payload.get("result"), new TypeReference<Map<String, Object>>() {}));
            payload.put("flowInstanceId", instanceId);
            event.setPayloadJson(json.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("动作的流程快照无法读取", exception);
        }
    }
}
