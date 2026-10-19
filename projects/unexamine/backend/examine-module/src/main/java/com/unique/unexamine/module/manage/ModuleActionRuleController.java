package com.unique.unexamine.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.module.base.entity.UnModuleAction;
import com.unique.unexamine.module.base.entity.UnModuleLog;
import com.unique.unexamine.module.base.entity.UnModuleMenu;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleRule;
import com.unique.unexamine.module.base.mapper.UnModuleActionMapper;
import com.unique.unexamine.module.base.mapper.UnModuleLogMapper;
import com.unique.unexamine.module.base.mapper.UnModuleMenuMapper;
import com.unique.unexamine.module.base.mapper.UnModuleRecordMapper;
import com.unique.unexamine.module.base.mapper.UnModuleRuleMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** 模块菜单、动作及保存时校验规则。 */
@Tag(name = "模块动作规则", description = "模块菜单、动作和规则。")
@RestController
@RequestMapping("/api/modules/{moduleId}")
public class ModuleActionRuleController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final UnModuleMenuMapper menus;
    private final UnModuleActionMapper actions;
    private final UnModuleRuleMapper rules;
    private final UnModuleRecordMapper records;
    private final UnModuleLogMapper logs;
    private final ObjectMapper json;

    public ModuleActionRuleController(ModuleSupport support, ModuleRecordAccess access,
                               UnModuleMenuMapper menus, UnModuleActionMapper actions,
                               UnModuleRuleMapper rules, UnModuleRecordMapper records,
                               UnModuleLogMapper logs, ObjectMapper json) {
        this.support = support;
        this.access = access;
        this.menus = menus;
        this.actions = actions;
        this.rules = rules;
        this.records = records;
        this.logs = logs;
        this.json = json;
    }

    @Operation(summary = "查询菜单（模块动作规则）")
    @GetMapping("/menus")
    public List<UnModuleMenu> menus(@PathVariable long moduleId, @RequestParam long tenantId, HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return menus.selectList(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId, moduleId)
                .eq(UnModuleMenu::getStatus, 1).orderByAsc(UnModuleMenu::getSortNo, UnModuleMenu::getId));
    }

    @Operation(summary = "新建菜单（模块动作规则）")
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

    @Operation(summary = "修改菜单（模块动作规则）")
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

    @Operation(summary = "停用菜单（模块动作规则）")
    @DeleteMapping("/menus/{id}")
    public void stopMenu(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleMenu row = menus.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("菜单不存在");
        row.setStatus((byte) 0);
        menus.updateById(row);
    }

    @Operation(summary = "查询处理动作（模块动作规则）")
    @GetMapping("/actions")
    public List<UnModuleAction> actions(@PathVariable long moduleId, @RequestParam long tenantId, HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return actions.selectList(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId, moduleId)
                .eq(UnModuleAction::getStatus, 1).orderByAsc(UnModuleAction::getSortNo, UnModuleAction::getId));
    }

    @Operation(summary = "新建动作（模块动作规则）")
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

    @Operation(summary = "修改动作（模块动作规则）")
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

    @Operation(summary = "停用动作（模块动作规则）")
    @DeleteMapping("/actions/{id}")
    public void stopAction(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleAction row = actions.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("动作不存在");
        row.setStatus((byte) 0);
        actions.updateById(row);
    }

    /** 先实现模块自身的状态动作；审批和流程动作须调用对应运行接口，不在此伪造实例。 */
    @Operation(summary = "执行模块动作（模块动作规则）")
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

    @Operation(summary = "查询规则（模块动作规则）")
    @GetMapping("/rules")
    public List<UnModuleRule> rules(@PathVariable long moduleId, @RequestParam long tenantId, HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return rules.selectList(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId, moduleId)
                .eq(UnModuleRule::getStatus, 1).orderByAsc(UnModuleRule::getSortNo, UnModuleRule::getId));
    }

    @Operation(summary = "新建规则（模块动作规则）")
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

    @Operation(summary = "修改规则（模块动作规则）")
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

    @Operation(summary = "停用规则（模块动作规则）")
    @DeleteMapping("/rules/{id}")
    public void stopRule(@PathVariable long moduleId, @PathVariable long id, @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleRule row = rules.selectById(id);
        if (row == null || !row.getModuleId().equals(moduleId)) missing("规则不存在");
        row.setStatus((byte) 0);
        rules.updateById(row);
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
    public record ActionExecuteForm(@NotNull Integer version) { }
}
