package com.unique.unexamine.manage.module;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** 模块权限菜单、动作、规则、关联、团队和审批流程绑定的基础操作。 */
@RestController
@RequestMapping("/api/modules/{moduleId}")
public class ModuleAuxController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final UnModuleMenuMapper menus;
    private final UnModuleActionMapper actions;
    private final UnModuleRuleMapper rules;
    private final UnModuleRelationConfigMapper relationConfigs;
    private final UnModuleRelationMapper relations;
    private final UnModuleTeamerMapper teamers;
    private final UnModuleRecordMapper records;
    private final UnModuleLogMapper logs;
    private final UnModuleMapper modules;
    private final UnSysTenantMemberMapper members;
    private final UnModuleExamineMapper examines;
    private final UnModuleFlowMapper flows;
    private final UnExamineMapper examineDefinitions;
    private final UnFlowMapper flowDefinitions;
    private final ObjectMapper json;

    public ModuleAuxController(ModuleSupport support, ModuleRecordAccess access,
                               UnModuleMenuMapper menus, UnModuleActionMapper actions,
                               UnModuleRuleMapper rules, UnModuleRelationConfigMapper relationConfigs,
                               UnModuleRelationMapper relations, UnModuleTeamerMapper teamers,
                               UnModuleRecordMapper records, UnModuleLogMapper logs, UnModuleMapper modules,
                               UnSysTenantMemberMapper members,
                               UnModuleExamineMapper examines, UnModuleFlowMapper flows,
                               UnExamineMapper examineDefinitions, UnFlowMapper flowDefinitions, ObjectMapper json) {
        this.support = support;
        this.access = access;
        this.menus = menus;
        this.actions = actions;
        this.rules = rules;
        this.relationConfigs = relationConfigs;
        this.relations = relations;
        this.teamers = teamers;
        this.records = records;
        this.logs = logs;
        this.modules = modules;
        this.members = members;
        this.examines = examines;
        this.flows = flows;
        this.examineDefinitions = examineDefinitions;
        this.flowDefinitions = flowDefinitions;
        this.json = json;
    }

    @GetMapping("/menus")
    public List<UnModuleMenu> menus(@PathVariable long moduleId, @RequestParam long tenantId, HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return menus.selectList(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId, moduleId)
                .eq(UnModuleMenu::getStatus, 1).orderByAsc(UnModuleMenu::getSortNo, UnModuleMenu::getId));
    }

    @PostMapping("/menus")
    public UnModuleMenu addMenu(@PathVariable long moduleId, @RequestParam long tenantId,
                                @Valid @RequestBody MenuForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        if (menus.selectCount(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId, moduleId)
                .eq(UnModuleMenu::getMenuCode, body.code())) > 0) conflict("菜单编码已存在");
        UnModuleMenu row = new UnModuleMenu();
        row.setModuleId(moduleId);
        row.setMenuCode(body.code());
        row.setMenuName(body.name());
        row.setMenuType(body.type());
        row.setPermissionCode(body.permissionCode());
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        row.setStatus((byte) 1);
        menus.insert(row);
        return menus.selectById(row.getId());
    }

    @PutMapping("/menus/{id}")
    public UnModuleMenu updateMenu(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId,
                                   @Valid @RequestBody MenuForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleMenu row = menus.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("菜单不存在");
        if (!row.getMenuCode().equals(body.code())) conflict("菜单编码不可修改");
        row.setMenuName(body.name());
        row.setMenuType(body.type());
        row.setPermissionCode(body.permissionCode());
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        menus.updateById(row);
        return menus.selectById(id);
    }

    @DeleteMapping("/menus/{id}")
    public void stopMenu(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleMenu row = menus.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("菜单不存在");
        row.setStatus((byte) 0);
        menus.updateById(row);
    }

    @GetMapping("/actions")
    public List<UnModuleAction> actions(@PathVariable long moduleId, @RequestParam long tenantId, HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return actions.selectList(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId, moduleId)
                .eq(UnModuleAction::getStatus, 1).orderByAsc(UnModuleAction::getSortNo, UnModuleAction::getId));
    }

    @PostMapping("/actions")
    public UnModuleAction addAction(@PathVariable long moduleId, @RequestParam long tenantId,
                                    @Valid @RequestBody ActionForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        if (actions.selectCount(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId, moduleId)
                .eq(UnModuleAction::getActionCode, body.code())) > 0) conflict("动作编码已存在");
        UnModuleAction row = new UnModuleAction();
        row.setModuleId(moduleId);
        row.setActionCode(body.code());
        fillAction(row, body);
        actions.insert(row);
        return actions.selectById(row.getId());
    }

    @PutMapping("/actions/{id}")
    public UnModuleAction updateAction(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId,
                                       @Valid @RequestBody ActionForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleAction row = actions.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("动作不存在");
        if (!row.getActionCode().equals(body.code())) conflict("动作编码不可修改");
        fillAction(row, body);
        actions.updateById(row);
        return actions.selectById(id);
    }

    @DeleteMapping("/actions/{id}")
    public void stopAction(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleAction row = actions.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("动作不存在");
        row.setStatus((byte) 0);
        actions.updateById(row);
    }

    /** 先实现模块自身的状态动作；审批和流程动作须调用对应运行接口，不在此伪造实例。 */
    @PostMapping("/records/{recordId}/actions/{actionCode}")
    @Transactional
    public UnModuleRecord executeAction(@PathVariable long moduleId, @PathVariable long recordId,
                                        @PathVariable String actionCode, @RequestParam long tenantId,
                                        @RequestBody ActionExecuteForm body, HttpServletRequest request) {
        UnSysTenantMember actor = support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleRecord record = record(tenantId, moduleId, recordId);
        access.requireWrite(access.scope(request, tenantId), record);
        UnModuleAction action = actions.selectOne(new LambdaQueryWrapper<UnModuleAction>()
                .eq(UnModuleAction::getModuleId, moduleId).eq(UnModuleAction::getActionCode, actionCode)
                .eq(UnModuleAction::getStatus, 1));
        if (action == null) missing("动作不存在");
        if (!"status".equals(action.getActionType())) conflict("此动作需由审批或流程运行接口执行");
        if (body.version() == null || !body.version().equals(record.getVersion())) conflict("记录版本已变化");
        JsonNode configured;
        try { configured = json.readTree(action.getConfigJson()); }
        catch (Exception e) { bad("动作配置无效"); return null; }
        if (configured == null) bad("状态动作缺少配置");
        int target = configured.path("recordStatus").asInt(-1);
        if (target != 0 && target != 1) bad("状态动作缺少有效目标状态");
        int changed = records.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId, recordId).eq(UnModuleRecord::getTenantId, tenantId)
                .eq(UnModuleRecord::getVersion, body.version()).ne(UnModuleRecord::getRecordStatus, 2)
                .set(UnModuleRecord::getRecordStatus, target).set(UnModuleRecord::getVersion, body.version() + 1)
                .set(UnModuleRecord::getUpdateMemberId, actor.getMemberId())
                .set(UnModuleRecord::getUpdateTime, LocalDateTime.now()));
        if (changed != 1) conflict("记录版本已变化");
        UnModuleLog log = new UnModuleLog();
        log.setTenantId(tenantId);
        log.setModuleId(moduleId);
        log.setRecordId(recordId);
        log.setActorMemberId(actor.getMemberId());
        log.setOperationCode(actionCode);
        log.setBeforeJson("{\"recordStatus\":" + record.getRecordStatus() + "}");
        log.setAfterJson("{\"recordStatus\":" + target + "}");
        logs.insert(log);
        return records.selectById(recordId);
    }

    @GetMapping("/rules")
    public List<UnModuleRule> rules(@PathVariable long moduleId, @RequestParam long tenantId, HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return rules.selectList(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId, moduleId)
                .eq(UnModuleRule::getStatus, 1).orderByAsc(UnModuleRule::getSortNo, UnModuleRule::getId));
    }

    @PostMapping("/rules")
    public UnModuleRule addRule(@PathVariable long moduleId, @RequestParam long tenantId,
                                @Valid @RequestBody RuleForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        if (rules.selectCount(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId, moduleId)
                .eq(UnModuleRule::getRuleCode, body.code())) > 0) conflict("规则编码已存在");
        UnModuleRule row = new UnModuleRule();
        row.setModuleId(moduleId);
        row.setRuleCode(body.code());
        fillRule(row, body);
        rules.insert(row);
        return rules.selectById(row.getId());
    }

    @PutMapping("/rules/{id}")
    public UnModuleRule updateRule(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId,
                                   @Valid @RequestBody RuleForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleRule row = rules.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("规则不存在");
        if (!row.getRuleCode().equals(body.code())) conflict("规则编码不可修改");
        fillRule(row, body);
        rules.updateById(row);
        return rules.selectById(id);
    }

    @DeleteMapping("/rules/{id}")
    public void stopRule(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleRule row = rules.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("规则不存在");
        row.setStatus((byte) 0);
        rules.updateById(row);
    }

    @GetMapping("/relation-configs")
    public List<UnModuleRelationConfig> relationConfigs(@PathVariable long moduleId, @RequestParam long tenantId,
                                                          HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return relationConfigs.selectList(new LambdaQueryWrapper<UnModuleRelationConfig>()
                .eq(UnModuleRelationConfig::getSourceModuleId, moduleId).eq(UnModuleRelationConfig::getStatus, 1));
    }

    @PostMapping("/relation-configs")
    public UnModuleRelationConfig addRelationConfig(@PathVariable long moduleId, @RequestParam long tenantId,
                                                     @Valid @RequestBody RelationConfigForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        if (body.direction() != null && !Set.of("forward", "both").contains(body.direction())) bad("关联方向无效");
        UnModule target = modules.selectById(body.targetModuleId());
        if (target == null || !target.getSystemId().equals(support.tenant(tenantId).getSystemId())) bad("关联目标模块不属于当前系统");
        if (relationConfigs.selectCount(new LambdaQueryWrapper<UnModuleRelationConfig>()
                .eq(UnModuleRelationConfig::getSourceModuleId, moduleId)
                .eq(UnModuleRelationConfig::getRelationCode, body.code())) > 0) conflict("关联编码已存在");
        UnModuleRelationConfig row = new UnModuleRelationConfig();
        row.setSourceModuleId(moduleId);
        row.setTargetModuleId(body.targetModuleId());
        row.setRelationCode(body.code());
        row.setName(body.name());
        row.setDirection(body.direction() == null ? "forward" : body.direction());
        row.setMappingJson(body.mapping() == null ? null : encode(body.mapping()));
        row.setStatus((byte) 1);
        relationConfigs.insert(row);
        return relationConfigs.selectById(row.getId());
    }

    @PutMapping("/relation-configs/{id}")
    public UnModuleRelationConfig updateRelationConfig(@PathVariable long moduleId, @PathVariable long id,
                                                        @RequestParam long tenantId, @Valid @RequestBody RelationConfigForm body,
                                                        HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleRelationConfig row = relationConfigs.selectById(id);
        if (row == null || !row.getSourceModuleId().equals(moduleId)) missing("关联配置不存在");
        if (!row.getRelationCode().equals(body.code()) || !row.getTargetModuleId().equals(body.targetModuleId()))
            conflict("关联编码和目标模块不可直接修改");
        if (body.direction() != null && !Set.of("forward", "both").contains(body.direction())) bad("关联方向无效");
        row.setName(body.name());
        row.setDirection(body.direction() == null ? "forward" : body.direction());
        row.setMappingJson(body.mapping() == null ? null : encode(body.mapping()));
        relationConfigs.updateById(row);
        return relationConfigs.selectById(id);
    }

    @DeleteMapping("/relation-configs/{id}")
    public void stopRelationConfig(@PathVariable long moduleId, @PathVariable long id,
                                   @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleRelationConfig row = relationConfigs.selectById(id);
        if (row == null || !row.getSourceModuleId().equals(moduleId)) missing("关联配置不存在");
        row.setStatus((byte) 0);
        relationConfigs.updateById(row);
    }

    @GetMapping("/records/{recordId}/relations")
    public List<UnModuleRelation> relations(@PathVariable long moduleId, @PathVariable long recordId,
                                             @RequestParam long tenantId, HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        access.requireRead(scope, record(tenantId, moduleId, recordId));
        return relations.selectList(new LambdaQueryWrapper<UnModuleRelation>()
                .eq(UnModuleRelation::getTenantId, tenantId)
                .and(q -> q.eq(UnModuleRelation::getSourceRecordId, recordId)
                        .or().eq(UnModuleRelation::getTargetRecordId, recordId)))
                .stream().filter(link -> {
                    if (!link.getSourceRecordId().equals(recordId)) {
                        UnModuleRelationConfig configuration = relationConfigs.selectById(link.getRelationConfigId());
                        if (configuration == null || !"both".equals(configuration.getDirection())) return false;
                    }
                    Long otherId = link.getSourceRecordId().equals(recordId)
                            ? link.getTargetRecordId() : link.getSourceRecordId();
                    UnModuleRecord other = records.selectById(otherId);
                    if (other == null || !other.getTenantId().equals(tenantId) || other.getRecordStatus() == 2) return false;
                    try { access.requireRead(scope, other); return true; }
                    catch (ResponseStatusException e) { return false; }
                }).toList();
    }

    @PostMapping("/records/{recordId}/relations")
    public UnModuleRelation addRelation(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                                         @RequestBody RelationForm body, HttpServletRequest request) {
        UnSysTenantMember actor = support.member(request, tenantId);
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        access.requireWrite(scope, record(tenantId, moduleId, recordId));
        UnModuleRelationConfig config = relationConfigs.selectById(body.configId());
        if (config == null || !config.getSourceModuleId().equals(moduleId) || !ModuleSupport.enabled(config.getStatus())) bad("关联配置无效");
        access.requireRead(scope, record(tenantId, config.getTargetModuleId(), body.targetRecordId()));
        if (relations.selectCount(new LambdaQueryWrapper<UnModuleRelation>().eq(UnModuleRelation::getTenantId, tenantId)
                .eq(UnModuleRelation::getRelationConfigId, body.configId()).eq(UnModuleRelation::getSourceRecordId, recordId)
                .eq(UnModuleRelation::getTargetRecordId, body.targetRecordId())) > 0) conflict("记录关联已存在");
        UnModuleRelation row = new UnModuleRelation();
        row.setTenantId(tenantId);
        row.setRelationConfigId(body.configId());
        row.setSourceRecordId(recordId);
        row.setTargetRecordId(body.targetRecordId());
        row.setCreateMemberId(actor.getMemberId());
        relations.insert(row);
        return relations.selectById(row.getId());
    }

    @DeleteMapping("/records/{recordId}/relations/{id}")
    public void deleteRelation(@PathVariable long moduleId, @PathVariable long recordId, @PathVariable long id,
                               @RequestParam long tenantId, HttpServletRequest request) {
        access.requireWrite(access.scope(request, tenantId), record(tenantId, moduleId, recordId));
        UnModuleRelation row = relations.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getSourceRecordId().equals(recordId)) missing("关联不存在");
        relations.deleteById(id);
    }

    @GetMapping("/records/{recordId}/teamers")
    public List<UnModuleTeamer> teamers(@PathVariable long moduleId, @PathVariable long recordId,
                                        @RequestParam long tenantId, HttpServletRequest request) {
        access.requireRead(access.scope(request, tenantId), record(tenantId, moduleId, recordId));
        return teamers.selectList(new LambdaQueryWrapper<UnModuleTeamer>().eq(UnModuleTeamer::getTenantId, tenantId)
                .eq(UnModuleTeamer::getModuleId, moduleId).eq(UnModuleTeamer::getRecordId, recordId));
    }

    @PostMapping("/records/{recordId}/teamers")
    public UnModuleTeamer addTeamer(@PathVariable long moduleId, @PathVariable long recordId,
                                    @RequestParam long tenantId, @Valid @RequestBody TeamerForm body,
                                    HttpServletRequest request) {
        write(request, tenantId, moduleId);
        record(tenantId, moduleId, recordId);
        if (!Set.of("viewer", "editor", "manager").contains(body.roleCode())) bad("团队成员角色无效");
        if (members.selectCount(new LambdaQueryWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, body.memberId()).eq(UnSysTenantMember::getStatus, 1)) == 0) bad("成员不属于当前租户");
        if (teamers.selectCount(new LambdaQueryWrapper<UnModuleTeamer>().eq(UnModuleTeamer::getRecordId, recordId)
                .eq(UnModuleTeamer::getMemberId, body.memberId()).eq(UnModuleTeamer::getRoleCode, body.roleCode())) > 0)
            conflict("团队成员已存在");
        UnModuleTeamer row = new UnModuleTeamer();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setRecordId(recordId);
        row.setMemberId(body.memberId());
        row.setRoleCode(body.roleCode());
        teamers.insert(row);
        return teamers.selectById(row.getId());
    }

    @DeleteMapping("/records/{recordId}/teamers/{id}")
    public void deleteTeamer(@PathVariable long moduleId, @PathVariable long recordId, @PathVariable long id,
                             @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        record(tenantId, moduleId, recordId);
        UnModuleTeamer row = teamers.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getRecordId().equals(recordId)) missing("团队成员不存在");
        teamers.deleteById(id);
    }

    /** 绑定审批前必须验证定义属于同租户且已经启用。 */
    @GetMapping("/examine-bindings")
    public List<UnModuleExamine> examineBindings(@PathVariable long moduleId, @RequestParam long tenantId,
                                                  HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return examines.selectList(new LambdaQueryWrapper<UnModuleExamine>().eq(UnModuleExamine::getTenantId, tenantId)
                .eq(UnModuleExamine::getModuleId, moduleId).eq(UnModuleExamine::getStatus, 1));
    }

    @PostMapping("/examine-bindings")
    public UnModuleExamine bindExamine(@PathVariable long moduleId, @RequestParam long tenantId,
                                      @Valid @RequestBody BindingForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        UnExamine definition = examineDefinitions.selectById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus()))
            bad("审批定义不属于当前租户或未启用");
        action(moduleId, body.actionCode(), "examine");
        if (examines.selectCount(new LambdaQueryWrapper<UnModuleExamine>().eq(UnModuleExamine::getTenantId, tenantId)
                .eq(UnModuleExamine::getModuleId, moduleId).eq(UnModuleExamine::getBindingCode, body.code())) > 0)
            conflict("审批绑定编码已存在");
        UnModuleExamine row = new UnModuleExamine();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setExamineId(body.definitionId());
        row.setBindingCode(body.code());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        examines.insert(row);
        return examines.selectById(row.getId());
    }

    @DeleteMapping("/examine-bindings/{id}")
    public void stopExamineBinding(@PathVariable long moduleId, @PathVariable long id,
                                   @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleExamine row = examines.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("审批绑定不存在");
        row.setStatus((byte) 0);
        examines.updateById(row);
    }

    @PutMapping("/examine-bindings/{id}")
    public UnModuleExamine updateExamineBinding(@PathVariable long moduleId, @PathVariable long id,
                                                 @RequestParam long tenantId, @Valid @RequestBody BindingForm body,
                                                 HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleExamine row = examines.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("审批绑定不存在");
        if (!row.getBindingCode().equals(body.code())) conflict("绑定编码不可修改");
        UnExamine definition = examineDefinitions.selectById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus()))
            bad("审批定义不属于当前租户或未启用");
        action(moduleId, body.actionCode(), "examine");
        row.setExamineId(body.definitionId());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        examines.updateById(row);
        return examines.selectById(id);
    }

    @GetMapping("/flow-bindings")
    public List<UnModuleFlow> flowBindings(@PathVariable long moduleId, @RequestParam long tenantId,
                                            HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return flows.selectList(new LambdaQueryWrapper<UnModuleFlow>().eq(UnModuleFlow::getTenantId, tenantId)
                .eq(UnModuleFlow::getModuleId, moduleId).eq(UnModuleFlow::getStatus, 1));
    }

    @PostMapping("/flow-bindings")
    public UnModuleFlow bindFlow(@PathVariable long moduleId, @RequestParam long tenantId,
                                 @Valid @RequestBody BindingForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        UnFlow definition = flowDefinitions.selectById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus())
                || definition.getCurrentVersionId() == null) bad("流程定义不属于当前租户或未发布启用");
        action(moduleId, body.actionCode(), "flow");
        if (flows.selectCount(new LambdaQueryWrapper<UnModuleFlow>().eq(UnModuleFlow::getTenantId, tenantId)
                .eq(UnModuleFlow::getModuleId, moduleId).eq(UnModuleFlow::getBindingCode, body.code())) > 0)
            conflict("流程绑定编码已存在");
        UnModuleFlow row = new UnModuleFlow();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setFlowId(body.definitionId());
        row.setBindingCode(body.code());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        flows.insert(row);
        return flows.selectById(row.getId());
    }

    @DeleteMapping("/flow-bindings/{id}")
    public void stopFlowBinding(@PathVariable long moduleId, @PathVariable long id,
                                @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleFlow row = flows.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("流程绑定不存在");
        row.setStatus((byte) 0);
        flows.updateById(row);
    }

    @PutMapping("/flow-bindings/{id}")
    public UnModuleFlow updateFlowBinding(@PathVariable long moduleId, @PathVariable long id,
                                           @RequestParam long tenantId, @Valid @RequestBody BindingForm body,
                                           HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleFlow row = flows.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)) missing("流程绑定不存在");
        if (!row.getBindingCode().equals(body.code())) conflict("绑定编码不可修改");
        UnFlow definition = flowDefinitions.selectById(body.definitionId());
        if (definition == null || !definition.getTenantId().equals(tenantId) || !"ENABLED".equals(definition.getStatus())
                || definition.getCurrentVersionId() == null) bad("流程定义不属于当前租户或未发布启用");
        action(moduleId, body.actionCode(), "flow");
        row.setFlowId(body.definitionId());
        row.setStartActionCode(body.actionCode());
        row.setStatus((byte) 1);
        flows.updateById(row);
        return flows.selectById(id);
    }

    private void fillAction(UnModuleAction row, ActionForm body) {
        if (!Set.of("status", "examine", "flow").contains(body.type())) bad("动作类型无效");
        if ("status".equals(body.type()) && (body.config() == null
                || !Set.of(0, 1).contains(body.config().path("recordStatus").asInt(-1)))) bad("状态动作缺少有效目标状态");
        row.setName(body.name());
        row.setActionType(body.type());
        row.setPermissionCode(body.permissionCode());
        row.setConfigJson(body.config() == null ? null : encode(body.config()));
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        row.setStatus((byte) 1);
    }

    private void fillRule(UnModuleRule row, RuleForm body) {
        if (!"save".equals(body.triggerType())) bad("当前仅支持保存时的字段校验规则");
        validateComparison(body.effect());
        if (body.condition() != null) validateComparison(body.condition());
        row.setName(body.name());
        row.setTriggerType(body.triggerType());
        row.setConditionJson(body.condition() == null ? null : encode(body.condition()));
        row.setEffectJson(encode(body.effect()));
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        row.setStatus((byte) 1);
    }

    private void validateComparison(JsonNode node) {
        if (node == null || !node.isObject() || !node.hasNonNull("fieldName") || !node.hasNonNull("operator")
                || !node.has("value") || !Set.of("eq", "ne", "gt", "gte", "lt", "lte", "contains", "empty", "notEmpty")
                .contains(node.path("operator").asText())) bad("保存规则比较配置无效");
    }

    private void action(long moduleId, String actionCode, String type) {
        if (actions.selectCount(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId, moduleId)
                .eq(UnModuleAction::getActionCode, actionCode).eq(UnModuleAction::getActionType, type)
                .eq(UnModuleAction::getStatus, 1)) == 0) bad("触发动作不存在或类型不匹配");
    }

    private UnModuleRecord record(long tenantId, long moduleId, long recordId) {
        support.module(tenantId, moduleId);
        UnModuleRecord row = records.selectById(recordId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || row.getRecordStatus() == 2) missing("记录不存在");
        return row;
    }

    private void read(HttpServletRequest request, long tenantId, long moduleId) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private void write(HttpServletRequest request, long tenantId, long moduleId) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private String encode(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { bad("配置 JSON 无效"); return null; }
    }

    private static void code(String code) { if (code == null || !CODE.matcher(code).matches()) bad("编码格式无效"); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void missing(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    public record MenuForm(@NotBlank String code, @NotBlank String name, @NotBlank String type,
                           @NotBlank String permissionCode, Integer sortNo) { }
    public record ActionForm(@NotBlank String code, @NotBlank String name, @NotBlank String type,
                             @NotBlank String permissionCode, JsonNode config, Integer sortNo) { }
    public record RuleForm(@NotBlank String code, @NotBlank String name, @NotBlank String triggerType,
                           JsonNode condition, @NotNull JsonNode effect, Integer sortNo) { }
    public record RelationConfigForm(@NotBlank String code, @NotBlank String name, @NotNull Long targetModuleId,
                                     String direction, JsonNode mapping) { }
    public record RelationForm(@NotNull Long configId, @NotNull Long targetRecordId) { }
    public record TeamerForm(@NotNull Long memberId, @NotBlank String roleCode) { }
    public record BindingForm(@NotBlank String code, @NotNull Long definitionId, @NotBlank String actionCode) { }
    public record ActionExecuteForm(@NotNull Integer version) { }
}
