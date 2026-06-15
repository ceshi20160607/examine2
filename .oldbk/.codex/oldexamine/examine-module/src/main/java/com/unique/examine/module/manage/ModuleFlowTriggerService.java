package com.unique.examine.module.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.flow.entity.po.FlowBinding;
import com.unique.examine.flow.entity.po.FlowTemp;
import com.unique.examine.flow.service.IFlowBindingService;
import com.unique.examine.flow.service.IFlowTempService;
import com.unique.examine.module.entity.po.ModuleRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * module → flow 触发门面：只查 {@link FlowBinding} 并调用 flow 引擎，不内嵌引擎规则。
 */
@Service
public class ModuleFlowTriggerService {

    /**
     * 与 {@code un_flow_binding.biz_type} 对齐：同一 system 内 app+model 唯一标识一条业务域。
     */
    public static String moduleBizType(long appId, long modelId) {
        return "module:app:" + appId + ":model:" + modelId;
    }

    @Autowired
    private IFlowBindingService flowBindingService;
    @Autowired
    private IFlowTempService flowTempService;
    @Autowired
    private com.unique.examine.flow.manage.FlowEngineService flowEngineService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * @param triggerAction 与 binding.trigger_action 一致，例如 {@code create} / {@code update}
     */
    public void tryTriggerAfterRecordChange(ModuleRecord record, String triggerAction, JsonNode data) {
        if (record == null || record.getId() == null) {
            return;
        }
        if (triggerAction == null || triggerAction.isBlank()) {
            return;
        }
        if (record.getAppId() == null || record.getModelId() == null
                || record.getSystemId() == null || record.getTenantId() == null) {
            return;
        }

        String bizType = moduleBizType(record.getAppId(), record.getModelId());
        FlowBinding binding = flowBindingService.lambdaQuery()
                .eq(FlowBinding::getSystemId, record.getSystemId())
                .eq(FlowBinding::getTenantId, record.getTenantId())
                .eq(FlowBinding::getBizType, bizType)
                .eq(FlowBinding::getTriggerAction, triggerAction.trim())
                .eq(FlowBinding::getStatus, 1)
                .last("limit 1")
                .one();
        if (binding == null || binding.getTempId() == null) {
            return;
        }

        FlowTemp temp = flowTempService.lambdaQuery()
                .eq(FlowTemp::getSystemId, record.getSystemId())
                .eq(FlowTemp::getTenantId, record.getTenantId())
                .eq(FlowTemp::getId, binding.getTempId())
                .eq(FlowTemp::getStatus, 1)
                .last("limit 1")
                .one();
        if (temp == null || temp.getTempCode() == null || temp.getTempCode().isBlank()) {
            throw new BusinessException(400, "流程绑定指向的模板不存在或已停用");
        }

        Map<String, Object> vars = dataObjectToVarMap(data);
        String title = "record:" + record.getId();
        flowEngineService.startByTempCode(
                temp.getTempCode().trim(),
                bizType,
                String.valueOf(record.getId()),
                title,
                vars
        );
    }

    private Map<String, Object> dataObjectToVarMap(JsonNode data) {
        if (data == null || !data.isObject()) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        var it = data.fields();
        while (it.hasNext()) {
            var e = it.next();
            String k = e.getKey();
            if (k == null || k.isBlank()) {
                continue;
            }
            JsonNode v = e.getValue();
            if (v == null || v.isNull()) {
                m.put(k, null);
            } else if (v.isNumber()) {
                m.put(k, v.numberValue());
            } else if (v.isBoolean()) {
                m.put(k, v.booleanValue());
            } else if (v.isTextual()) {
                m.put(k, v.asText());
            } else {
                m.put(k, objectMapper.convertValue(v, Object.class));
            }
        }
        return m;
    }
}
