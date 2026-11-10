package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.sys.flow.base.entity.UnFlowExample;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTask;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTaskCandidate;
import com.unique.unexamine.sys.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.sys.flow.base.entity.UnModuleFlow;
import com.unique.unexamine.sys.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskCandidateService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowVersionService;
import com.unique.unexamine.sys.flow.base.service.IUnModuleFlowService;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.RecordStageView;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.RelatedRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.AuditDetail;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordAccessAdapter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块阶段的补充资料关系；实际阶段与管理人来自关联 Flow 实例的发布快照。 */
@Service
public class ModuleStageRecordService {
    private final ModuleRecordAccessAdapter access;
    private final FlowRuntimeService runtime;
    private final IUnModuleFlowService moduleFlows;
    private final IUnFlowExampleService examples;
    private final IUnFlowVersionService versions;
    private final IUnFlowTaskService tasks;
    private final IUnFlowTaskCandidateService candidates;
    private final IUnModuleRecordService records;
    private final IUnModuleRelationConfigService configs;
    private final IUnModuleRelationService relations;
    private final IUnModuleLogService logs;
    private final ObjectMapper json;

    public ModuleStageRecordService(ModuleRecordAccessAdapter access,FlowRuntimeService runtime,
            IUnModuleFlowService moduleFlows,IUnFlowExampleService examples,
            IUnFlowVersionService versions,IUnFlowTaskService tasks,
            IUnFlowTaskCandidateService candidates,IUnModuleRecordService records,
            IUnModuleRelationConfigService configs,IUnModuleRelationService relations,
            IUnModuleLogService logs,ObjectMapper json) {
        this.access=access; this.runtime=runtime; this.moduleFlows=moduleFlows; this.examples=examples;
        this.versions=versions; this.tasks=tasks; this.candidates=candidates; this.records=records;
        this.configs=configs; this.relations=relations; this.logs=logs; this.json=json;
    }

    /** 详情页按实例旧版本展示阶段与仍有权限读取的补充记录。 */
    public List<RecordStageView> forRecord(String token,Long systemId,Long tenantId,
            Long moduleId,Long recordId) {
        var subject=access.require(token,systemId,tenantId,moduleId,recordId);
        Set<Long> stageFlows=new HashSet<>();
        moduleFlows.list(new LambdaQueryWrapper<UnModuleFlow>()
                .eq(UnModuleFlow::getSystemId,systemId).eq(UnModuleFlow::getTenantId,subject.tenantId())
                .eq(UnModuleFlow::getModuleId,moduleId)).forEach(link->stageFlows.add(link.getFlowId()));
        if (stageFlows.isEmpty()) return List.of();
        List<RecordStageView> result=new ArrayList<>();
        for (var instance:runtime.listForRecord(token,systemId,tenantId,moduleId,recordId)) {
            if (!stageFlows.contains(instance.flowId())) continue;
            UnFlowVersion version=versions.getById(instance.flowVersionId());
            if (version==null || !instance.flowId().equals(version.getFlowId()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"阶段实例的 Flow 版本不存在");
            if (!stageVersion(version)) continue;
            for (JsonNode node:PublishedProcessSnapshot.nodes(json,version.getSnapshotJson())) {
                if (!"STAGE".equalsIgnoreCase(PublishedProcessSnapshot.type(node))) continue;
                String code=PublishedProcessSnapshot.code(node);
                Long taskId=instance.tasks().stream().filter(task->code.equals(task.nodeCode())
                        && "PENDING".equals(task.status())).map(task->task.taskId()).findFirst().orElse(null);
                List<RelatedRecord> related=new ArrayList<>();
                for (UnModuleRelationConfig config:stageConfigs(systemId,subject.tenantId(),moduleId,
                        instance.flowVersionId(),code)) {
                    for (UnModuleRelation relation:relations.list(new LambdaQueryWrapper<UnModuleRelation>()
                            .eq(UnModuleRelation::getSourceRecordId,recordId)
                            .eq(UnModuleRelation::getRelationConfigId,config.getId())
                            .eq(UnModuleRelation::getRelationType,"STAGE")
                            .eq(UnModuleRelation::getStatus,"ACTIVE").orderByAsc(UnModuleRelation::getSortNo)
                            .orderByAsc(UnModuleRelation::getId))) {
                        try {
                            access.require(token,systemId,subject.tenantId(),config.getTargetModuleId(),
                                    relation.getTargetRecordId());
                            UnModuleRecord target=records.getById(relation.getTargetRecordId());
                            if (target!=null && target.getDeletedTime()==null)
                                related.add(new RelatedRecord(relation.getId(),target.getModuleId(),
                                        target.getId(),target.getName()));
                        } catch (BaseException ex) {
                            if (ex.getCode()<400 || ex.getCode()>=500) throw ex;
                        }
                    }
                }
                result.add(new RecordStageView(instance.exampleId(),instance.flowVersionId(),code,
                        node.path("name").asText(code),taskId!=null,taskId,related));
            }
        }
        return result;
    }

    /** 当前阶段管理人把已有且可读的目标记录关联为该阶段的补充资料。 */
    @Transactional(rollbackFor=Exception.class)
    public RelatedRecord attach(String token,Long systemId,Long tenantId,Long moduleId,Long recordId,
            Long exampleId,String stageCode,Long targetRecordId) {
        StageContext stage=current(token,systemId,tenantId,moduleId,recordId,exampleId,stageCode);
        if (targetRecordId==null || targetRecordId<1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"补充记录主键无效");
        UnModuleRecord target=records.getById(targetRecordId);
        if (target==null || target.getDeletedTime()!=null
                || !systemId.equals(target.getSystemId()) || !stage.tenantId().equals(target.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"补充记录不存在");
        UnModuleRelationConfig config=stageConfigs(systemId,stage.tenantId(),moduleId,
                stage.example().getFlowVersionId(),stageCode).stream()
                .filter(item->target.getModuleId().equals(item.getTargetModuleId())).findFirst()
                .orElseThrow(()->new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"该阶段未配置目标模块"));
        access.require(token,systemId,stage.tenantId(),target.getModuleId(),targetRecordId);
        UnModuleRelation relation=relations.getOne(new LambdaQueryWrapper<UnModuleRelation>()
                .eq(UnModuleRelation::getSourceRecordId,recordId)
                .eq(UnModuleRelation::getTargetRecordId,targetRecordId)
                .eq(UnModuleRelation::getRelationConfigId,config.getId())
                .eq(UnModuleRelation::getRelationType,"STAGE"),false);
        if (relation==null) {
            relation=new UnModuleRelation(); relation.setSourceRecordId(recordId);
            relation.setTargetRecordId(targetRecordId); relation.setRelationConfigId(config.getId());
            relation.setRelationType("STAGE"); relation.setSortNo(0);
        }
        if (!"ACTIVE".equals(relation.getStatus())) {
            relation.setStatus("ACTIVE"); relations.addOrUpdate(relation);
            audit(stage,relation,"STAGE_RELATION_ADD",target.getName());
        }
        return new RelatedRecord(relation.getId(),target.getModuleId(),target.getId(),target.getName());
    }

    /** 当前阶段管理人解除补充资料关系；关系行保留并可在同一阶段再次启用。 */
    @Transactional(rollbackFor=Exception.class)
    public void detach(String token,Long systemId,Long tenantId,Long moduleId,Long recordId,
            Long exampleId,String stageCode,Long relationId) {
        StageContext stage=current(token,systemId,tenantId,moduleId,recordId,exampleId,stageCode);
        UnModuleRelation relation=relations.getById(relationId);
        if (relation==null || !recordId.equals(relation.getSourceRecordId())
                || !"STAGE".equals(relation.getRelationType()) || !"ACTIVE".equals(relation.getStatus())
                || stageConfigs(systemId,stage.tenantId(),moduleId,stage.example().getFlowVersionId(),stageCode)
                        .stream().noneMatch(item->item.getId().equals(relation.getRelationConfigId())))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"阶段补充资料关联不存在");
        relation.setStatus("INACTIVE"); relations.addOrUpdate(relation);
        audit(stage,relation,"STAGE_RELATION_REMOVE",null);
    }

    private StageContext current(String token,Long systemId,Long tenantId,Long moduleId,Long recordId,
            Long exampleId,String stageCode) {
        var actor=access.require(token,systemId,tenantId,moduleId,recordId);
        if (exampleId==null || stageCode==null || !stageCode.matches("[a-z][a-zA-Z0-9_]{1,63}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段实例或编码无效");
        UnFlowExample example=examples.getOne(new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getId,exampleId).last("FOR UPDATE"),false);
        if (example==null || !systemId.equals(example.getSystemId())
                || !actor.tenantId().equals(example.getTenantId())
                || !moduleId.equals(example.getModuleId()) || !recordId.equals(example.getRecordId())
                || !"RUNNING".equals(example.getStatus())
                || moduleFlows.count(new LambdaQueryWrapper<UnModuleFlow>()
                    .eq(UnModuleFlow::getSystemId,systemId).eq(UnModuleFlow::getTenantId,actor.tenantId())
                    .eq(UnModuleFlow::getModuleId,moduleId).eq(UnModuleFlow::getFlowId,example.getFlowId()))==0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"当前阶段实例不存在");
        UnFlowVersion version=versions.getById(example.getFlowVersionId());
        if (version==null || !example.getFlowId().equals(version.getFlowId()) || !stageVersion(version))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"当前阶段实例不存在");
        UnFlowTask task=tasks.getOne(new LambdaQueryWrapper<UnFlowTask>()
                .eq(UnFlowTask::getExampleId,exampleId).eq(UnFlowTask::getNodeCode,stageCode)
                .eq(UnFlowTask::getStatus,"PENDING").orderByDesc(UnFlowTask::getId),false);
        if (task==null || !"STAGE".equals(task.getTaskType())
                || candidates.count(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                    .eq(UnFlowTaskCandidate::getTaskId,task.getId())
                    .eq(UnFlowTaskCandidate::getAccountId,actor.accountId())
                    .eq(UnFlowTaskCandidate::getStatus,"PENDING"))==0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"当前账号不是该阶段的待办管理人");
        return new StageContext(actor.tenantId(),actor.accountId(),recordId,example,task);
    }

    private List<UnModuleRelationConfig> stageConfigs(Long systemId,Long tenantId,Long moduleId,
            Long versionId,String stageCode) {
        List<UnModuleRelationConfig> result=new ArrayList<>();
        for (UnModuleRelationConfig item:configs.list(new LambdaQueryWrapper<UnModuleRelationConfig>()
                .eq(UnModuleRelationConfig::getSystemId,systemId)
                .eq(UnModuleRelationConfig::getTenantId,tenantId)
                .eq(UnModuleRelationConfig::getSourceModuleId,moduleId)
                .eq(UnModuleRelationConfig::getRelationType,"STAGE")
                .eq(UnModuleRelationConfig::getStatus,"ACTIVE"))) {
            try {
                JsonNode config=json.readTree(item.getConfigJson());
                if (config.path("flowVersionId").asLong(-1)==versionId
                        && stageCode.equals(config.path("stageCode").asText())) result.add(item);
            } catch (Exception ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"阶段关联配置已损坏",ex);
            }
        }
        return result;
    }

    private boolean stageVersion(UnFlowVersion version) {
        return PublishedProcessSnapshot.nodes(json,version.getSnapshotJson()).stream().anyMatch(node->
                "stage_start".equals(PublishedProcessSnapshot.code(node))
                        && "START".equalsIgnoreCase(PublishedProcessSnapshot.type(node)));
    }

    private void audit(StageContext stage,UnModuleRelation relation,String action,String targetName) {
        UnModuleLog log=new UnModuleLog();
        log.setSystemId(stage.example().getSystemId()); log.setTenantId(stage.tenantId());
        log.setActorTenantId(stage.tenantId()); log.setModuleId(stage.example().getModuleId());
        log.setRecordId(stage.recordId()); log.setOperatorAccountId(stage.accountId());
        log.setActionCode(action);
        log.setPermissionJson(json.valueToTree(Map.of("flowExampleId",stage.example().getId(),
                "taskId",stage.task().getId(),"relationConfigId",relation.getRelationConfigId())).toString());
        log.setDetailJson(json.valueToTree(new AuditDetail(action,null,List.of(),null,
                relation.getTargetRecordId(),targetName)).toString());
        logs.addOrUpdate(log);
    }

    private record StageContext(Long tenantId,Long accountId,Long recordId,
                                UnFlowExample example,UnFlowTask task) {}
}
