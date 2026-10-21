package com.unique.unexamine.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.module.base.entity.UnModuleLog;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.module.base.entity.UnModuleRule;
import com.unique.unexamine.module.base.service.IUnModuleLogService;
import com.unique.unexamine.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.module.base.service.IUnModuleRuleService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 模块记录的表单保存、详情与变动历史。 */
@Tag(name = "模块记录", description = "模块记录的详情、保存、修改和历史。")
@RestController
@RequestMapping("/api/modules/{moduleId}/records")
public class ModuleRecordController {
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService data;
    private final IUnModuleLogService logs;
    private final IUnModuleRuleService rules;
    private final IUnSysTenantMemberService tenantMembers;
    private final JdbcTemplate jdbc;
    private final ModuleRecordFieldValue valueSupport;
    private final ModuleRecordReader reader;
    private final ModuleRecordFlowTrigger flows;

    public ModuleRecordController(ModuleSupport support, ModuleRecordAccess access,
                                  IUnModuleRecordService records, IUnModuleRecordDataService data,
                                  IUnModuleLogService logs, IUnModuleRuleService rules,
                                  IUnSysTenantMemberService tenantMembers, JdbcTemplate jdbc,
                                  ModuleRecordFieldValue valueSupport, ModuleRecordReader reader,
                                  ModuleRecordFlowTrigger flows) {
        this.support = support;
        this.access = access;
        this.records = records;
        this.data = data;
        this.logs = logs;
        this.rules = rules;
        this.tenantMembers = tenantMembers;
        this.jdbc = jdbc;
        this.valueSupport = valueSupport;
        this.reader = reader;
        this.flows = flows;
    }

    @Operation(summary = "查询详情（模块记录）")
    @GetMapping("/{recordId}")
    public RecordView detail(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                             HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleRecord row = reader.record(tenantId, moduleId, recordId);
        access.requireRead(scope, row);
        return reader.view(row);
    }

    /** 主记录、字段值和变动历史作为一个事务提交。 */
    @Operation(summary = "新建（模块记录）")
    @PostMapping
    @Transactional
    public RecordView create(@PathVariable long moduleId, @RequestParam long tenantId,
                             @RequestBody RecordForm body, HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        Map<String, UnModuleRecordData> values = validateValues(tenantId, moduleId, null, body.values());
        checkStatus(body.status());
        checkNumber(tenantId, moduleId, null, body.num());
        access.requireOwnerAssignment(scope, body.ownerMemberId());
        checkOwner(tenantId, body.ownerMemberId());
        Long ownerDepartmentId = body.ownerDepartmentId() == null ? member.getDepartmentId() : body.ownerDepartmentId();
        access.requireDepartmentAssignment(scope, ownerDepartmentId, null, member.getDepartmentId());
        UnModuleRecord row = new UnModuleRecord();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setName(body.name());
        row.setNum(body.num());
        row.setRemark(body.remark());
        row.setRecordStatus(body.status() != null && body.status() == 0 ? (byte) 0 : (byte) 1);
        row.setOwnerMemberId(body.ownerMemberId() == null ? member.getMemberId() : body.ownerMemberId());
        row.setOwnerDepartmentId(ownerDepartmentId);
        row.setVersion(1);
        row.setCreateMemberId(member.getMemberId());
        row.setUpdateMemberId(member.getMemberId());
        records.save(row);
        for (UnModuleRecordData value : values.values()) {
            value.setRecordId(row.getId());
            data.save(value);
        }
        log(row, member.getMemberId(), "create", null, body.values());
        // 自动绑定流程与记录保存处于同一事务，绑定版本和实例随记录一起提交。
        flows.triggerRecordEvent(tenantId, moduleId, row.getId(), row.getVersion(), member.getMemberId(), "CREATE");
        return reader.view(records.getById(row.getId()));
    }

    @Operation(summary = "修改（模块记录）")
    @PutMapping("/{recordId}")
    @Transactional
    public RecordView update(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                             @RequestBody RecordForm body, HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleRecord before = reader.record(tenantId, moduleId, recordId);
        access.requireWrite(scope, before);
        if (body.version() == null || !body.version().equals(before.getVersion())) conflict("记录已被其他操作修改");
        Map<String, JsonNode> previousValues = reader.view(before).values();
        Map<String, UnModuleRecordData> values = validateValues(tenantId, moduleId, recordId, body.values());
        checkStatus(body.status());
        checkNumber(tenantId, moduleId, recordId, body.num());
        Long nextOwner = body.ownerMemberId() == null ? before.getOwnerMemberId() : body.ownerMemberId();
        if (!Objects.equals(nextOwner, before.getOwnerMemberId())) access.requireOwnerTransfer(scope);
        checkOwner(tenantId, nextOwner);
        Long nextDepartment = body.ownerDepartmentId() == null ? before.getOwnerDepartmentId() : body.ownerDepartmentId();
        access.requireDepartmentAssignment(scope, nextDepartment, before, member.getDepartmentId());
        boolean changed = records.update(null, new LambdaUpdateWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId, recordId).eq(UnModuleRecord::getTenantId, tenantId)
                .eq(UnModuleRecord::getVersion, body.version()).ne(UnModuleRecord::getRecordStatus, 2)
                .set(UnModuleRecord::getName, body.name()).set(UnModuleRecord::getNum, body.num())
                .set(UnModuleRecord::getRemark, body.remark())
                .set(UnModuleRecord::getOwnerMemberId, nextOwner)
                .set(UnModuleRecord::getOwnerDepartmentId, nextDepartment)
                .set(UnModuleRecord::getRecordStatus, body.status() != null && body.status() == 0 ? 0 : 1)
                .set(UnModuleRecord::getUpdateMemberId, member.getMemberId())
                .set(UnModuleRecord::getVersion, body.version() + 1));
        if (!changed) conflict("记录已被其他操作修改");
        // 隐藏或停用字段的历史值不属于本次表单，编辑时保留，避免字段配置变更导致数据丢失。
        Set<String> writableNames = new HashSet<>();
        for (ModuleSupport.FieldSpec field : support.effectiveFields(tenantId, moduleId).values())
            if (field.add() && !field.hidden()) writableNames.add(field.fieldName());
        if (!writableNames.isEmpty()) data.remove(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId, recordId).in(UnModuleRecordData::getFieldName, writableNames));
        for (UnModuleRecordData value : values.values()) {
            value.setRecordId(recordId);
            data.save(value);
        }
        log(before, member.getMemberId(), "update", previousValues, body.values());
        flows.triggerRecordEvent(tenantId, moduleId, recordId, body.version() + 1,
                member.getMemberId(), "UPDATE");
        return reader.view(records.getById(recordId));
    }

    @Operation(summary = "删除（模块记录）")
    @DeleteMapping("/{recordId}")
    @Transactional
    public void delete(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                       @RequestParam int version, HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleRecord row = reader.record(tenantId, moduleId, recordId);
        access.requireWrite(scope, row);
        boolean changed = records.update(null, new LambdaUpdateWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId, recordId).eq(UnModuleRecord::getTenantId, tenantId)
                .eq(UnModuleRecord::getVersion, version).ne(UnModuleRecord::getRecordStatus, 2)
                .set(UnModuleRecord::getRecordStatus, 2).set(UnModuleRecord::getVersion, version + 1)
                .set(UnModuleRecord::getDeleteTime, LocalDateTime.now())
                .set(UnModuleRecord::getUpdateMemberId, member.getMemberId()));
        if (!changed) conflict("记录已被其他操作修改");
        log(row, member.getMemberId(), "delete", reader.view(row).values(), null);
    }

    @Operation(summary = "查询记录日志（模块记录）")
    @GetMapping("/{recordId}/logs")
    public List<UnModuleLog> logs(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                                  HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        access.requireRead(scope, reader.record(tenantId, moduleId, recordId));
        return logs.list(new LambdaQueryWrapper<UnModuleLog>().eq(UnModuleLog::getTenantId, tenantId)
                .eq(UnModuleLog::getModuleId, moduleId).eq(UnModuleLog::getRecordId, recordId)
                .orderByDesc(UnModuleLog::getCreateTime, UnModuleLog::getId));
    }

    private Map<String, UnModuleRecordData> validateValues(long tenantId, long moduleId, Long recordId,
                                                              Map<String, JsonNode> input) {
        if (input == null) bad("values 不能为空");
        Map<String, ModuleSupport.FieldSpec> specs = support.effectiveFields(tenantId, moduleId);
        for (String name : input.keySet()) {
            ModuleSupport.FieldSpec spec = specs.get(name);
            if (spec == null || spec.hidden() || !spec.add()) bad("提交了不存在或不可填写的字段：" + name);
        }
        Map<String, UnModuleRecordData> result = new LinkedHashMap<>();
        for (ModuleSupport.FieldSpec spec : specs.values()) {
            if (spec.hidden() || !spec.add()) continue;
            JsonNode value = input.get(spec.fieldName());
            if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
                if (spec.required()) bad("必填字段不能为空：" + spec.name());
                continue;
            }
            UnModuleRecordData row = new UnModuleRecordData();
            row.setTenantId(tenantId);
            row.setModuleId(moduleId);
            row.setFieldName(spec.fieldName());
            String column = valueSupport.valueColumn(spec.type());
            row.setValueType(switch (column) { case "value_number" -> "number"; case "value_time" -> "time";
                case "value_json" -> "json"; default -> "text"; });
            Object parsed = valueSupport.comparableValue(spec, value, tenantId, moduleId);
            switch (column) {
                case "value_number" -> row.setValueNumber((BigDecimal) parsed);
                case "value_time" -> row.setValueTime((LocalDateTime) parsed);
                case "value_json" -> row.setValueJson(valueSupport.writeJson(value));
                default -> row.setValueText((String) parsed);
            }
            if (spec.unique()) checkUnique(tenantId, moduleId, recordId, row, column);
            result.put(spec.fieldName(), row);
        }
        checkSaveRules(moduleId, specs, result);
        return result;
    }

    /** 基础保存规则只做字段比较校验，失败时拒绝整个事务。 */
    private void checkSaveRules(long moduleId, Map<String, ModuleSupport.FieldSpec> specs,
                                Map<String, UnModuleRecordData> values) {
        for (UnModuleRule rule : rules.list(new LambdaQueryWrapper<UnModuleRule>()
                .eq(UnModuleRule::getModuleId, moduleId).eq(UnModuleRule::getTriggerType, "save")
                .eq(UnModuleRule::getStatus, 1).orderByAsc(UnModuleRule::getSortNo))) {
            JsonNode condition = valueSupport.readJson(rule.getConditionJson());
            if (condition != null && !matchesRule(condition, specs, values)) continue;
            JsonNode effect = valueSupport.readJson(rule.getEffectJson());
            if (effect == null || !effect.isObject() || !effect.hasNonNull("fieldName") || !effect.hasNonNull("operator"))
                bad("保存规则配置无效：" + rule.getName());
            if (!matchesRule(effect, specs, values)) bad(effect.path("message").asText("未满足保存规则：" + rule.getName()));
        }
    }

    private boolean matchesRule(JsonNode rule, Map<String, ModuleSupport.FieldSpec> specs,
                                Map<String, UnModuleRecordData> values) {
        String name = rule.path("fieldName").asText();
        String operator = rule.path("operator").asText();
        ModuleSupport.FieldSpec spec = specs.get(name);
        if (spec == null || !rule.has("value")) bad("规则引用的字段或比较值无效");
        UnModuleRecordData current = values.get(name);
        if (current == null) return "empty".equals(operator);
        if ("empty".equals(operator)) return false;
        if ("notEmpty".equals(operator)) return true;
        String column = valueSupport.valueColumn(spec.type());
        if ("value_json".equals(column)) bad("集合字段暂不支持比较规则");
        Object left = switch (column) { case "value_number" -> current.getValueNumber(); case "value_time" -> current.getValueTime();
            default -> current.getValueText(); };
        Object right = valueSupport.comparableValue(spec, rule.get("value"), current.getTenantId(), current.getModuleId());
        @SuppressWarnings("unchecked") int comparison = ((Comparable<Object>) left).compareTo(right);
        return switch (operator) {
            case "eq" -> comparison == 0; case "ne" -> comparison != 0;
            case "gt" -> comparison > 0; case "gte" -> comparison >= 0;
            case "lt" -> comparison < 0; case "lte" -> comparison <= 0;
            case "contains" -> left instanceof String text && text.contains(right.toString());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的规则操作符");
        };
    }

    private void checkUnique(long tenantId, long moduleId, Long recordId, UnModuleRecordData row, String column) {
        if ("value_json".equals(column)) bad("集合字段不支持唯一约束");
        Object value = switch (column) { case "value_number" -> row.getValueNumber(); case "value_time" -> row.getValueTime();
            default -> row.getValueText(); };
        String sql = "SELECT COUNT(*) FROM un_module_record_data d JOIN un_module_record r ON r.id=d.record_id "
                + "WHERE r.tenant_id=? AND r.module_id=? AND r.record_status<>2 AND d.field_name=? AND d." + column + "=?";
        List<Object> params = new ArrayList<>(List.of(tenantId, moduleId, row.getFieldName(), value));
        if (recordId != null) { sql += " AND r.id<>?"; params.add(recordId); }
        Long count = jdbc.queryForObject(sql, Long.class, params.toArray());
        if (count != null && count > 0) conflict("字段值必须唯一：" + row.getFieldName());
    }

    private void checkOwner(long tenantId, Long ownerMemberId) {
        if (ownerMemberId == null) return;
        if (tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, ownerMemberId).eq(UnSysTenantMember::getStatus, 1)) == 0)
            bad("负责人不是当前租户的有效成员");
    }

    private void checkStatus(Byte status) {
        if (status != null && status != 0 && status != 1) bad("记录状态只能是草稿或正常");
    }

    private void checkNumber(long tenantId, long moduleId, Long recordId, String number) {
        if (number == null || number.isBlank()) return;
        String sql = "SELECT COUNT(*) FROM un_module_record WHERE tenant_id=? AND module_id=? AND num=?";
        List<Object> params = new ArrayList<>(List.of(tenantId, moduleId, number));
        if (recordId != null) { sql += " AND id<>?"; params.add(recordId); }
        Long count = jdbc.queryForObject(sql, Long.class, params.toArray());
        if (count != null && count > 0) conflict("业务编号已存在");
    }

    private void log(UnModuleRecord record, long actorId, String operation, Object before, Object after) {
        UnModuleLog row = new UnModuleLog();
        row.setTenantId(record.getTenantId());
        row.setModuleId(record.getModuleId());
        row.setRecordId(record.getId());
        row.setActorMemberId(actorId);
        row.setOperationCode(operation);
        row.setBeforeJson(before == null ? null : valueSupport.writeJson(before));
        row.setAfterJson(after == null ? null : valueSupport.writeJson(after));
        logs.save(row);
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    public record RecordForm(String name, String num, String remark, Byte status, Long ownerMemberId,
                             Long ownerDepartmentId, Integer version, Map<String, JsonNode> values) { }
    public record RecordView(UnModuleRecord record, Map<String, JsonNode> values) { }
}
