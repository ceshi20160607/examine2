package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldValidationService;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.flow.base.entity.UnFlow;
import com.unique.unexamine.sys.flow.base.entity.UnFlowBinding;
import com.unique.unexamine.sys.flow.base.entity.UnModuleFlow;
import com.unique.unexamine.sys.flow.base.service.IUnFlowBindingService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnModuleFlowService;
import com.unique.unexamine.sys.flow.manage.dto.FlowBindingDto.SaveBindingRequest;
import com.unique.unexamine.sys.flow.manage.dto.FlowDefinitionDto.SaveFlowRequest;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.ConfigView;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.Route;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.SaveRequest;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.Stage;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleFieldService;
import com.unique.unexamine.sys.module.base.entity.UnModuleTenant;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleService;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块阶段配置入口；复用 Flow 定义、触发绑定与版本快照，不复制一套流程引擎。 */
@Service
public class ModuleStageService {
    private static final String START="stage_start";
    private static final String END="stage_end";
    private final SystemManageService systems;
    private final IUnModuleService modules;
    private final IUnModuleTenantService tenantModules;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnFlowService flows;
    private final IUnModuleFlowService moduleFlows;
    private final IUnFlowBindingService bindings;
    private final IUnModuleRelationConfigService relationConfigs;
    private final FlowDefinitionService definitions;
    private final FlowBindingService flowBindings;
    private final ObjectMapper json;
    private final ModuleConfigService moduleConfig;
    private final ModuleFieldService fieldRules;
    private final FieldValidationService validation;
    private final IUnSysLogOptService logs;

    public ModuleStageService(SystemManageService systems,IUnModuleService modules,
            IUnModuleTenantService tenantModules,IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers,IUnFlowService flows,
            IUnModuleFlowService moduleFlows,IUnFlowBindingService bindings,
            IUnModuleRelationConfigService relationConfigs,FlowDefinitionService definitions,
            FlowBindingService flowBindings,ObjectMapper json,ModuleConfigService moduleConfig,
            ModuleFieldService fieldRules,FieldValidationService validation,IUnSysLogOptService logs) {
        this.systems=systems; this.modules=modules; this.tenantModules=tenantModules;
        this.members=members; this.tenantMembers=tenantMembers; this.flows=flows;
        this.moduleFlows=moduleFlows; this.bindings=bindings; this.relationConfigs=relationConfigs;
        this.definitions=definitions; this.flowBindings=flowBindings; this.json=json;
        this.moduleConfig=moduleConfig; this.fieldRules=fieldRules; this.validation=validation;
        this.logs=logs;
    }

    /** 读取当前模块阶段配置；旧实例继续使用它们自己的 Flow 发布版本。 */
    public ConfigView configuration(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        ownedModule(context.systemId(),context.tenantId(),moduleId,false);
        UnFlow stageFlow=flows.getOne(new LambdaQueryWrapper<UnFlow>()
                .eq(UnFlow::getSystemId,systemId).eq(UnFlow::getTenantId,context.tenantId())
                .eq(UnFlow::getCode,"module_"+moduleId+"_stages"),false);
        if (stageFlow==null) return null;
        UnModuleFlow link=moduleFlows.getOne(new LambdaQueryWrapper<UnModuleFlow>()
                .eq(UnModuleFlow::getSystemId,systemId).eq(UnModuleFlow::getTenantId,context.tenantId())
                .eq(UnModuleFlow::getModuleId,moduleId).eq(UnModuleFlow::getFlowId,stageFlow.getId())
                .eq(UnModuleFlow::getStatus,"ACTIVE"),false);
        if (link==null) return null;
        var flow=definitions.query(token,systemId,context.tenantId(),link.getFlowId());
        Long versionId=flow.versions().stream().filter(item->item.versionNo().equals(flow.latestVersion()))
                .map(item->item.id()).findFirst().orElse(null);
        UnFlowBinding binding=bindings.getOne(new LambdaQueryWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getSystemId,systemId).eq(UnFlowBinding::getTenantId,context.tenantId())
                .eq(UnFlowBinding::getModuleId,moduleId).eq(UnFlowBinding::getFlowId,flow.id())
                .eq(UnFlowBinding::getStatus,"ACTIVE").orderByDesc(UnFlowBinding::getId),false);
        return new ConfigView(moduleId,flow.id(),versionId,binding==null?null:binding.getId(),
                decode(flow.name(),flow.description(),flow.snapshot(),binding));
    }

    /** 保存一套阶段并立即启用其新 Flow 版本；关联模块按版本建立稳定配置引用。 */
    @Transactional(rollbackFor=Exception.class)
    public ConfigView save(String token,Long systemId,Long tenantId,Long moduleId,SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        UnModule module=ownedModule(systemId,context.tenantId(),moduleId,true);
        validate(context.systemId(),context.tenantId(),moduleId,request);
        String code="module_"+moduleId+"_stages";
        UnFlow existing=flows.getOne(new LambdaQueryWrapper<UnFlow>()
                .eq(UnFlow::getSystemId,systemId).eq(UnFlow::getTenantId,context.tenantId())
                .eq(UnFlow::getCode,code),false);
        if (existing!=null && moduleFlows.count(new LambdaQueryWrapper<UnModuleFlow>()
                .eq(UnModuleFlow::getModuleId,moduleId).eq(UnModuleFlow::getFlowId,existing.getId()))==0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"阶段流程编码已被其他 Flow 配置占用");
        ObjectNode snapshot=snapshot(request);
        var saved=definitions.save(token,systemId,context.tenantId(),
                new SaveFlowRequest(code,request.name(),request.description(),null,snapshot));
        Long versionId=saved.versions().stream().filter(item->item.versionNo().equals(saved.latestVersion()))
                .map(item->item.id()).findFirst().orElseThrow(()->
                        new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"阶段 Flow 发布版本不存在"));
        List<UnFlowBinding> previous=bindings.list(new LambdaQueryWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getSystemId,systemId).eq(UnFlowBinding::getTenantId,context.tenantId())
                .eq(UnFlowBinding::getModuleId,moduleId).eq(UnFlowBinding::getFlowId,saved.id())
                .in(UnFlowBinding::getStatus,List.of("ACTIVE","PUBLISHED")));
        var bound=flowBindings.save(token,systemId,context.tenantId(),
                new SaveBindingRequest(null,saved.id(),moduleId,request.actionMenuId(),
                        request.triggerEventCode(),"AFTER_SAVE",0,true,request.triggerCondition()));
        for (UnFlowBinding old:previous) if (!old.getId().equals(bound.id()))
            flowBindings.disable(token,systemId,context.tenantId(),old.getId());
        UnModuleFlow link=moduleFlows.getOne(new LambdaQueryWrapper<UnModuleFlow>()
                .eq(UnModuleFlow::getTenantId,context.tenantId()).eq(UnModuleFlow::getModuleId,moduleId)
                .eq(UnModuleFlow::getFlowId,saved.id()),false);
        if (link==null) {
            link=new UnModuleFlow(); link.setSystemId(systemId); link.setTenantId(context.tenantId());
            link.setModuleId(module.getId()); link.setFlowId(saved.id()); link.setCreateTime(LocalDateTime.now());
        }
        link.setStatus("ACTIVE"); link.setPublishedVersion(saved.latestVersion());
        link.setUpdateTime(LocalDateTime.now()); moduleFlows.addOrUpdate(link);
        for (Stage stage:request.stages()) for (Long relatedModuleId:stage.relatedModuleIds()) {
            UnModuleRelationConfig relation=new UnModuleRelationConfig();
            relation.setSystemId(systemId); relation.setTenantId(context.tenantId());
            relation.setSourceModuleId(moduleId); relation.setTargetModuleId(relatedModuleId);
            relation.setRelationType("STAGE"); relation.setStatus("ACTIVE");
            relation.setConfigJson(json.valueToTree(Map.of("flowVersionId",versionId,
                    "stageCode",stage.code())).toString());
            relationConfigs.addOrUpdate(relation);
        }
        UnSysLogOpt log=new UnSysLogOpt(); log.setSystemId(systemId);
        log.setTenantId(context.tenantId()); log.setAccountId(context.accountId());
        log.setActionCode("MODULE_STAGE_SAVE"); log.setObjectType("MODULE_STAGE");
        log.setObjectId(moduleId);
        log.setDetailJson(json.valueToTree(Map.of("flowId",saved.id(),"flowVersionId",versionId,
                "bindingId",bound.id(),"configuration",request)).toString());
        logs.addOrUpdate(log);
        return new ConfigView(moduleId,saved.id(),versionId,bound.id(),request);
    }

    private UnModule ownedModule(Long systemId,Long tenantId,Long moduleId,boolean lock) {
        if (moduleId==null || moduleId<1) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模块主键无效");
        LambdaQueryWrapper<UnModule> where=new LambdaQueryWrapper<UnModule>()
                .eq(UnModule::getId,moduleId).eq(UnModule::getSystemId,systemId)
                .eq(UnModule::getTenantId,tenantId).eq(UnModule::getStatus,"ACTIVE");
        if (lock) where.last("FOR UPDATE");
        UnModule module=modules.getOne(where,false);
        if (module==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"模块不存在或已停用");
        return module;
    }

    private void validate(Long systemId,Long tenantId,Long moduleId,SaveRequest request) {
        if (request==null || request.name()==null || request.name().isBlank()
                || request.name().length()>120 || request.description()!=null && request.description().length()>500
                || request.triggerEventCode()==null || request.triggerEventCode().isBlank()
                || request.stages()==null || request.stages().isEmpty() || request.stages().size()>50
                || request.routes()==null || request.routes().size()>200)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模块阶段配置无效");
        Set<String> codes=new LinkedHashSet<>();
        for (Stage stage:request.stages()) {
            if (stage==null || stage.code()==null || !stage.code().matches("[a-z][a-zA-Z0-9_]{1,63}")
                    || Set.of(START,END).contains(stage.code()) || stage.code().endsWith("_approval")
                    || stage.name()==null || stage.name().isBlank() || stage.name().length()>120
                    || !codes.add(stage.code()) || stage.managerAccountIds()==null
                    || stage.managerAccountIds().isEmpty() || stage.relatedModuleIds()==null)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段编码、名称或管理人员无效");
            Set<Long> managers=new HashSet<>();
            for (Long accountId:stage.managerAccountIds()) {
                if (accountId==null || accountId<1 || !managers.add(accountId))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段管理人员重复或无效");
                UnSysMember member=members.getOne(new LambdaQueryWrapper<UnSysMember>()
                        .eq(UnSysMember::getSystemId,systemId).eq(UnSysMember::getAccountId,accountId)
                        .eq(UnSysMember::getStatus,"ACTIVE"),false);
                if (member==null || tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>()
                        .eq(UnSysTenantMember::getSystemId,systemId).eq(UnSysTenantMember::getTenantId,tenantId)
                        .eq(UnSysTenantMember::getMemberId,member.getId())
                        .eq(UnSysTenantMember::getStatus,"ACTIVE"))==0)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段管理人员不是有效租户成员");
            }
            Set<Long> related=new HashSet<>();
            for (Long targetId:stage.relatedModuleIds()) {
                if (targetId==null || targetId<1 || !related.add(targetId))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段关联模块重复或无效");
                UnModule target=modules.getById(targetId);
                if (target==null || !systemId.equals(target.getSystemId()) || !"ACTIVE".equals(target.getStatus())
                        || tenantModules.count(new LambdaQueryWrapper<UnModuleTenant>()
                        .eq(UnModuleTenant::getSystemId,systemId).eq(UnModuleTenant::getTenantId,tenantId)
                        .eq(UnModuleTenant::getModuleId,targetId).eq(UnModuleTenant::getStatus,"ACTIVE"))==0)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段关联模块不存在或未在当前租户启用");
            }
            if (stage.examineId()!=null && stage.examineId()<1)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段审批定义主键无效");
        }
        Map<String,Stage> byCode=new LinkedHashMap<>(); request.stages().forEach(stage->byCode.put(stage.code(),stage));
        Map<String,FieldDefinition> fieldDefinitions=new LinkedHashMap<>();
        moduleConfig.currentFields(moduleId).forEach(field->fieldDefinitions.put(field.getFieldKey(),
                fieldRules.configured(field)));
        for (Route route:request.routes()) {
            if (route==null || !byCode.containsKey(route.sourceCode())
                    || route.targetCode()!=null && !byCode.containsKey(route.targetCode())
                    || route.priorityNo()!=null && route.priorityNo()<0)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段线路引用无效");
            boolean hasApproval=byCode.get(route.sourceCode()).examineId()!=null;
            if (hasApproval != (route.approvalStatus()!=null)
                    || route.approvalStatus()!=null && !Set.of("COMPLETED","REJECTED","CANCELLED")
                    .contains(route.approvalStatus())
                    || route.autoEventCode()!=null && (hasApproval
                    || !route.autoEventCode().matches("[A-Z][A-Z0-9_]{1,79}")
                    || route.autoEventCode().startsWith("FLOW_")
                    || route.autoEventCode().startsWith("EXAMINE_")))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段审批结果线路配置无效");
            try {
                String condition=route.condition()==null?null:route.condition().toString();
                TriggerConditionMatcher.validate(json,condition);
                TriggerConditionMatcher.validateFieldKeys(json,condition,fieldDefinitions.keySet());
                TriggerConditionMatcher.validateFieldValues(json,condition,fieldDefinitions,validation);
            } catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段线路字段条件无效: "+ex.getMessage(),ex);
            }
        }
        for (Stage stage:request.stages()) {
            List<Route> outgoing=request.routes().stream().filter(route->stage.code().equals(route.sourceCode())).toList();
            if (outgoing.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段缺少后续线路: "+stage.name());
            if (!stage.manualEnabled() && (stage.examineId()!=null
                    || outgoing.stream().noneMatch(route->route.autoEventCode()!=null)))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"仅自动推进的阶段须配置业务事件线路: "+stage.name());
            if (stage.examineId()!=null && !outgoing.stream().map(Route::approvalStatus).collect(
                    java.util.stream.Collectors.toSet()).containsAll(Set.of("COMPLETED","REJECTED","CANCELLED")))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段审批须配置通过、拒绝和取消线路");
            Set<String> outcomes=stage.examineId()==null?Set.of(""):
                    Set.of("COMPLETED","REJECTED","CANCELLED");
            for (String outcome:outcomes) {
                List<Route> branch=outgoing.stream().filter(route->
                        outcome.equals(route.approvalStatus()==null?"":route.approvalStatus())).toList();
                if (branch.isEmpty() || branch.getLast().autoEventCode()!=null
                        || !unconditional(branch.getLast().condition()))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,
                            "阶段每种结果的最后一条线路必须无条件兜底: "+stage.name());
                int fallbackPriority=priority(request.routes(),branch.getLast());
                if (branch.stream().anyMatch(route->priority(request.routes(),route)>fallbackPriority))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"阶段兜底线路优先级必须最后");
            }
        }
    }

    private boolean unconditional(JsonNode condition) {
        if (condition==null || condition.isNull()) return true;
        if (condition.isArray()) return condition.isEmpty();
        if (condition.isObject()) {
            JsonNode children=condition.has("conditions")?condition.get("conditions"):condition.get("all");
            return children!=null && children.isArray() && children.isEmpty();
        }
        return false;
    }

    private int priority(List<Route> routes,Route route) {
        return route.priorityNo()==null?routes.indexOf(route):route.priorityNo();
    }

    private ObjectNode snapshot(SaveRequest request) {
        ObjectNode root=json.createObjectNode(); ArrayNode nodes=root.putArray("nodes"); ArrayNode edges=root.putArray("edges");
        nodes.add(node(START,"START","开始",List.of(),null,0));
        int order=1;
        for (Stage stage:request.stages()) {
            ObjectNode current=node(stage.code(),"STAGE",stage.name(),stage.managerAccountIds(),null,order++);
            current.put("manualEnabled",stage.manualEnabled());
            current.set("relatedModuleIds",json.valueToTree(stage.relatedModuleIds())); nodes.add(current);
            if (stage.examineId()!=null) {
                nodes.add(node(stage.code()+"_approval","APPROVAL",stage.name()+"审批",
                        List.of(),stage.examineId(),order++));
                edge(edges,stage.code(),stage.code()+"_approval",null,0,true);
            }
        }
        nodes.add(node(END,"END","结束",List.of(),null,order));
        edge(edges,START,request.stages().getFirst().code(),null,0,true);
        for (Route route:request.routes()) {
            Stage source=request.stages().stream().filter(stage->stage.code().equals(route.sourceCode()))
                    .findFirst().orElseThrow();
            JsonNode condition=route.condition();
            if (route.approvalStatus()!=null) {
                ArrayNode combined=json.createArrayNode();
                combined.add(json.createObjectNode().put("fieldKey","__examineStatus")
                        .put("operator","EQ").put("value",route.approvalStatus()));
                if (condition!=null && !condition.isNull()) {
                    JsonNode actual=condition.has("conditions")?condition.get("conditions")
                            :condition.has("all")?condition.get("all"):condition;
                    if (actual.isArray()) actual.forEach(combined::add); else combined.add(actual);
                }
                condition=combined;
            } else if (route.autoEventCode()!=null) {
                ArrayNode combined=json.createArrayNode();
                combined.add(json.createObjectNode().put("fieldKey","__flowEvent")
                        .put("operator","EQ").put("value",route.autoEventCode()));
                if (condition!=null && !condition.isNull()) {
                    JsonNode actual=condition.has("conditions")?condition.get("conditions")
                            :condition.has("all")?condition.get("all"):condition;
                    if (actual.isArray()) actual.forEach(combined::add); else combined.add(actual);
                }
                condition=combined;
            }
            edge(edges,source.examineId()==null?source.code():source.code()+"_approval",
                    route.targetCode()==null?END:route.targetCode(),condition,
                    priority(request.routes(),route),route.exclusiveFlag()==null || route.exclusiveFlag());
        }
        return root;
    }

    private ObjectNode node(String code,String type,String name,List<Long> managers,Long examineId,int sortNo) {
        ObjectNode node=json.createObjectNode().put("nodeCode",code).put("nodeType",type)
                .put("name",name).put("sortNo",sortNo);
        if (!managers.isEmpty()) node.set("accountIds",json.valueToTree(managers));
        if (examineId!=null) node.put("examineId",examineId);
        return node;
    }

    private void edge(ArrayNode edges,String source,String target,JsonNode condition,int priority,boolean exclusive) {
        ObjectNode edge=json.createObjectNode().put("sourceNodeCode",source)
                .put("targetNodeCode",target).put("priorityNo",priority).put("exclusiveFlag",exclusive);
        if (condition!=null && !condition.isNull()) {
            TriggerConditionMatcher.validate(json,condition.toString()); edge.set("condition",condition);
        }
        edges.add(edge);
    }

    private SaveRequest decode(String name,String description,JsonNode snapshot,UnFlowBinding binding) {
        if (snapshot==null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"阶段 Flow 快照不存在");
        List<Stage> stages=new ArrayList<>();
        for (JsonNode node:snapshot.path("nodes")) if ("STAGE".equals(node.path("nodeType").asText())) {
            List<Long> managers=new ArrayList<>(),related=new ArrayList<>();
            node.path("accountIds").forEach(value->managers.add(value.asLong()));
            node.path("relatedModuleIds").forEach(value->related.add(value.asLong()));
            String code=node.path("nodeCode").asText();
            JsonNode approval=null;
            for (JsonNode candidate:snapshot.path("nodes"))
                if ((code+"_approval").equals(candidate.path("nodeCode").asText())) approval=candidate;
            stages.add(new Stage(code,node.path("name").asText(),node.path("manualEnabled").asBoolean(true),managers,related,
                    approval==null?null:approval.path("examineId").asLong()));
        }
        List<Route> routes=new ArrayList<>();
        for (JsonNode edge:snapshot.path("edges")) {
            String source=edge.path("sourceNodeCode").asText();
            if (START.equals(source) || stages.stream().noneMatch(stage->source.equals(stage.code())
                    || source.equals(stage.code()+"_approval"))) continue;
            if (stages.stream().anyMatch(stage->source.equals(stage.code()) && stage.examineId()!=null)) continue;
            String target=edge.path("targetNodeCode").asText();
            JsonNode condition=edge.get("condition"); String outcome=null,autoEvent=null;
            if (condition!=null && condition.isArray()) {
                for (JsonNode item:condition) if ("__examineStatus".equals(item.path("fieldKey").asText()))
                    outcome=item.path("value").asText();
                for (JsonNode item:condition) if ("__flowEvent".equals(item.path("fieldKey").asText()))
                    autoEvent=item.path("value").asText();
                ArrayNode business=json.createArrayNode();
                for (JsonNode item:condition) if (!"__examineStatus".equals(item.path("fieldKey").asText())
                        && !"__flowEvent".equals(item.path("fieldKey").asText()))
                    business.add(item);
                condition=business.isEmpty()?null:business.size()==1?business.get(0):business;
            }
            routes.add(new Route(source.endsWith("_approval")?source.substring(0,source.length()-9):source,
                    END.equals(target)?null:target,outcome,autoEvent,condition,
                    edge.path("priorityNo").asInt(0),edge.path("exclusiveFlag").asBoolean(true)));
        }
        JsonNode trigger=null;
        try { if (binding!=null && binding.getConditionJson()!=null)
            trigger=json.readTree(binding.getConditionJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"阶段触发条件已损坏",ex); }
        return new SaveRequest(name,description,binding==null?null:binding.getEventCode(),
                binding==null?null:binding.getActionMenuId(),trigger,stages,routes);
    }
}
