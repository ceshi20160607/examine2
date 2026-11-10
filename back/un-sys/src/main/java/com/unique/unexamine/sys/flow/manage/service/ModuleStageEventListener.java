package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventListener;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.flow.base.entity.UnFlowExample;
import com.unique.unexamine.sys.flow.base.entity.UnFlow;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTask;
import com.unique.unexamine.sys.flow.base.entity.UnModuleFlow;
import com.unique.unexamine.sys.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskService;
import com.unique.unexamine.sys.flow.base.service.IUnModuleFlowService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 将来源记录或阶段补充记录的业务事件投递给当前阶段。
 * 补充关联按发布版本筛选，配置更新不会改写已经启动的实例。
 */
@Component
public class ModuleStageEventListener implements BusinessEventListener {
    private final IUnFlowExampleService examples;
    private final IUnFlowService flows;
    private final IUnFlowTaskService tasks;
    private final IUnModuleFlowService moduleFlows;
    private final IUnModuleRelationService relations;
    private final IUnModuleRelationConfigService configs;
    private final ObjectMapper json;
    private final ObjectProvider<FlowRuntimeService> runtime;

    public ModuleStageEventListener(IUnFlowExampleService examples, IUnFlowService flows,
            IUnFlowTaskService tasks,
            IUnModuleFlowService moduleFlows, IUnModuleRelationService relations,
            IUnModuleRelationConfigService configs, ObjectMapper json,
            ObjectProvider<FlowRuntimeService> runtime) {
        this.examples = examples;
        this.flows = flows;
        this.tasks = tasks;
        this.moduleFlows = moduleFlows;
        this.relations = relations;
        this.configs = configs;
        this.json = json;
        this.runtime = runtime;
    }

    /** 同步推进，异常交给发布业务的原事务回滚。 */
    @Override
    public void onEvent(BusinessEventContext event) {
        if (event == null || event.systemId() == null || event.tenantId() == null
                || event.moduleId() == null || event.recordId() == null || event.eventCode() == null
                || event.eventCode().startsWith("FLOW_") || event.eventCode().startsWith("EXAMINE_")) return;
        Map<Long, String> matched = new LinkedHashMap<>();
        for (UnFlowExample example : examples.list(new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getSystemId, event.systemId())
                .eq(UnFlowExample::getTenantId, event.tenantId())
                .eq(UnFlowExample::getModuleId, event.moduleId())
                .eq(UnFlowExample::getRecordId, event.recordId())
                .eq(UnFlowExample::getStatus, "RUNNING"))) {
            if (stageFlow(example)) matched.put(example.getId(), null);
        }
        for (UnModuleRelation relation : relations.list(new LambdaQueryWrapper<UnModuleRelation>()
                .eq(UnModuleRelation::getTargetRecordId, event.recordId())
                .eq(UnModuleRelation::getRelationType, "STAGE")
                .eq(UnModuleRelation::getStatus, "ACTIVE"))) {
            UnModuleRelationConfig config = configs.getById(relation.getRelationConfigId());
            if (config == null || !Objects.equals(config.getSystemId(), event.systemId())
                    || !Objects.equals(config.getTenantId(), event.tenantId())
                    || !Objects.equals(config.getTargetModuleId(), event.moduleId())
                    || !"STAGE".equals(config.getRelationType())) continue;
            JsonNode detail = configDetail(config);
            long versionId = detail.path("flowVersionId").asLong(0);
            String stageCode = detail.path("stageCode").asText("");
            if (versionId < 1 || stageCode.isBlank()) continue;
            for (UnFlowExample example : examples.list(new LambdaQueryWrapper<UnFlowExample>()
                    .eq(UnFlowExample::getSystemId, event.systemId())
                    .eq(UnFlowExample::getTenantId, event.tenantId())
                    .eq(UnFlowExample::getModuleId, config.getSourceModuleId())
                    .eq(UnFlowExample::getRecordId, relation.getSourceRecordId())
                    .eq(UnFlowExample::getFlowVersionId, versionId)
                    .eq(UnFlowExample::getStatus, "RUNNING"))) {
                if (stageFlow(example) && !matched.containsKey(example.getId()))
                    matched.put(example.getId(), stageCode);
            }
        }
        for (Map.Entry<Long, String> entry : matched.entrySet()) {
            List<UnFlowTask> pending = tasks.list(new LambdaQueryWrapper<UnFlowTask>()
                    .eq(UnFlowTask::getExampleId, entry.getKey())
                    .eq(UnFlowTask::getTaskType, "STAGE")
                    .eq(UnFlowTask::getStatus, "PENDING"));
            for (UnFlowTask task : pending) {
                if (entry.getValue() == null || entry.getValue().equals(task.getNodeCode()))
                    runtime.getObject().advanceByEvent(entry.getKey(), task.getId(), event);
            }
        }
    }

    private boolean stageFlow(UnFlowExample example) {
        UnFlow flow=flows.getById(example.getFlowId());
        if (flow==null || !Objects.equals(flow.getSystemId(),example.getSystemId())
                || !Objects.equals(flow.getTenantId(),example.getTenantId())
                || !("module_"+example.getModuleId()+"_stages").equals(flow.getCode())) return false;
        return moduleFlows.count(new LambdaQueryWrapper<UnModuleFlow>()
                .eq(UnModuleFlow::getSystemId, example.getSystemId())
                .eq(UnModuleFlow::getTenantId, example.getTenantId())
                .eq(UnModuleFlow::getModuleId, example.getModuleId())
                .eq(UnModuleFlow::getFlowId, example.getFlowId())) > 0;
    }

    private JsonNode configDetail(UnModuleRelationConfig config) {
        try { return json.readTree(config.getConfigJson()); }
        catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "阶段补充关系配置无效", ex);
        }
    }
}
