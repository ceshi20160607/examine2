package com.unique.unexamine.manage.module;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/** 模块记录的表单保存、详情和带字段白名单的列表查询。 */
@RestController
@RequestMapping("/api/modules/{moduleId}/records")
public class ModuleRecordController {
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final UnModuleRecordMapper records;
    private final UnModuleRecordDataMapper data;
    private final UnModuleLogMapper logs;
    private final UnModuleQueryFieldMapper queryFields;
    private final UnModuleRuleMapper rules;
    private final UnModuleScenceMapper scenes;
    private final UnModuleScenceConfigMapper sceneConfigs;
    private final UnSysTenantMemberMapper tenantMembers;
    private final UnModuleDictMapper dictionaries;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModuleConfigController config;

    public ModuleRecordController(ModuleSupport support, ModuleRecordAccess access,
                                  UnModuleRecordMapper records, UnModuleRecordDataMapper data,
                                  UnModuleLogMapper logs, UnModuleQueryFieldMapper queryFields, UnModuleRuleMapper rules,
                                  UnModuleScenceMapper scenes, UnModuleScenceConfigMapper sceneConfigs,
                                  UnSysTenantMemberMapper tenantMembers, UnModuleDictMapper dictionaries,
                                  JdbcTemplate jdbc, ObjectMapper json, ModuleConfigController config) {
        this.support = support;
        this.access = access;
        this.records = records;
        this.data = data;
        this.logs = logs;
        this.queryFields = queryFields;
        this.rules = rules;
        this.scenes = scenes;
        this.sceneConfigs = sceneConfigs;
        this.tenantMembers = tenantMembers;
        this.dictionaries = dictionaries;
        this.jdbc = jdbc;
        this.json = json;
        this.config = config;
    }

    /** 列表筛选和排序在数据库执行，再分页；字段名只能来自有效查询字段。 */
    @GetMapping
    public RecordPage list(@PathVariable long moduleId, @RequestParam long tenantId,
                           @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) String sortField, @RequestParam(defaultValue = "asc") String sortOrder,
                           @RequestParam(required = false) Long sceneId, @RequestParam(required = false) String filterField,
                           @RequestParam(required = false) String filterValue, HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        if (page < 1 || size < 1 || size > 100) bad("分页参数无效");
        Map<String, ModuleSupport.FieldSpec> fields = support.effectiveFields(tenantId, moduleId);
        Map<String, Set<String>> allowedQueries = queryWhitelist(moduleId);
        List<Filter> filters = new ArrayList<>();
        UnModuleScenceConfig activeScene = null;
        if ((filterField == null) != (filterValue == null)) bad("筛选字段和值必须同时提供");
        if (filterField != null) filters.add(new Filter(filterField, "eq", json.valueToTree(filterValue)));
        if (sceneId != null) {
            activeScene = scene(tenantId, moduleId, sceneId, request);
            JsonNode configured = readJson(activeScene.getFiltersJson());
            if (configured != null && configured.isArray()) {
                for (JsonNode item : configured) {
                    filters.add(new Filter(item.path("fieldName").asText(), item.path("operator").asText(), item.get("value")));
                }
            }
            if (sortField == null) {
                JsonNode sort = readJson(activeScene.getSortJson());
                if (sort != null && sort.isObject()) {
                    sortField = sort.path("fieldName").asText(null);
                    sortOrder = sort.path("order").asText("asc");
                }
            }
        }
        if (filters.size() > 10) bad("筛选项过多");
        StringBuilder where = new StringBuilder(" FROM un_module_record r WHERE r.tenant_id=? AND r.module_id=? AND r.record_status<>2");
        List<Object> params = new ArrayList<>(List.of(tenantId, moduleId));
        access.appendReadPredicate(where, params, scope);
        for (Filter filter : filters) appendFilter(where, params, filter, fields, allowedQueries, tenantId, moduleId);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, params.toArray());
        String order = " ORDER BY r.id DESC";
        if (sortField != null && !sortField.isBlank()) {
            if (!"asc".equalsIgnoreCase(sortOrder) && !"desc".equalsIgnoreCase(sortOrder)) bad("排序方向无效");
            String direction = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
            if ("id".equals(sortField)) order = " ORDER BY r.id " + direction;
            else if ("createTime".equals(sortField)) order = " ORDER BY r.create_time " + direction + ", r.id " + direction;
            else {
                ModuleSupport.FieldSpec field = fields.get(sortField);
                if (field == null || !field.sortable() || !allowedQueries.containsKey(sortField)) bad("排序字段不可用");
                String column = valueColumn(field.type());
                if (column.equals("value_json")) bad("集合字段不能排序");
                order = " ORDER BY (SELECT sd." + column + " FROM un_module_record_data sd WHERE sd.record_id=r.id AND sd.field_name=?) "
                        + direction + ", r.id " + direction;
            }
        }
        List<Object> pageParams = new ArrayList<>(params);
        if (sortField != null && !sortField.isBlank() && !"id".equals(sortField) && !"createTime".equals(sortField)) pageParams.add(sortField);
        pageParams.add(size);
        pageParams.add((long) (page - 1) * size);
        List<Long> ids = jdbc.query("SELECT r.id" + where + order + " LIMIT ? OFFSET ?", (rs, row) -> rs.getLong(1), pageParams.toArray());
        List<RecordView> rows = new ArrayList<>();
        for (Long id : ids) rows.add(view(record(tenantId, moduleId, id)));
        List<ModuleConfigController.ColumnView> columns = activeScene == null
                ? config.columns(tenantId, moduleId, request) : sceneColumns(activeScene, fields);
        return new RecordPage(columns, rows, total == null ? 0 : total, page, size);
    }

    @GetMapping("/{recordId}")
    public RecordView detail(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                             HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleRecord row = record(tenantId, moduleId, recordId);
        access.requireRead(scope, row);
        return view(row);
    }

    /** 主记录、字段值和变动历史作为一个事务提交。 */
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
        records.insert(row);
        for (UnModuleRecordData value : values.values()) {
            value.setRecordId(row.getId());
            data.insert(value);
        }
        log(row, member.getMemberId(), "create", null, body.values());
        return view(records.selectById(row.getId()));
    }

    @PutMapping("/{recordId}")
    @Transactional
    public RecordView update(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                             @RequestBody RecordForm body, HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleRecord before = record(tenantId, moduleId, recordId);
        access.requireWrite(scope, before);
        if (body.version() == null || !body.version().equals(before.getVersion())) conflict("记录已被其他操作修改");
        Map<String, JsonNode> previousValues = view(before).values();
        Map<String, UnModuleRecordData> values = validateValues(tenantId, moduleId, recordId, body.values());
        checkStatus(body.status());
        checkNumber(tenantId, moduleId, recordId, body.num());
        Long nextOwner = body.ownerMemberId() == null ? before.getOwnerMemberId() : body.ownerMemberId();
        if (!Objects.equals(nextOwner, before.getOwnerMemberId())) access.requireOwnerTransfer(scope);
        checkOwner(tenantId, nextOwner);
        Long nextDepartment = body.ownerDepartmentId() == null ? before.getOwnerDepartmentId() : body.ownerDepartmentId();
        access.requireDepartmentAssignment(scope, nextDepartment, before, member.getDepartmentId());
        int changed = records.update(null, new LambdaUpdateWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId, recordId).eq(UnModuleRecord::getTenantId, tenantId)
                .eq(UnModuleRecord::getVersion, body.version()).ne(UnModuleRecord::getRecordStatus, 2)
                .set(UnModuleRecord::getName, body.name()).set(UnModuleRecord::getNum, body.num())
                .set(UnModuleRecord::getRemark, body.remark())
                .set(UnModuleRecord::getOwnerMemberId, nextOwner)
                .set(UnModuleRecord::getOwnerDepartmentId, nextDepartment)
                .set(UnModuleRecord::getRecordStatus, body.status() != null && body.status() == 0 ? 0 : 1)
                .set(UnModuleRecord::getUpdateMemberId, member.getMemberId())
                .set(UnModuleRecord::getVersion, body.version() + 1));
        if (changed != 1) conflict("记录已被其他操作修改");
        // 隐藏或停用字段的历史值不属于本次表单，编辑时保留，避免字段配置变更导致数据丢失。
        Set<String> writableNames = new HashSet<>();
        for (ModuleSupport.FieldSpec field : support.effectiveFields(tenantId, moduleId).values())
            if (field.add() && !field.hidden()) writableNames.add(field.fieldName());
        if (!writableNames.isEmpty()) data.delete(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId, recordId).in(UnModuleRecordData::getFieldName, writableNames));
        for (UnModuleRecordData value : values.values()) {
            value.setRecordId(recordId);
            data.insert(value);
        }
        log(before, member.getMemberId(), "update", previousValues, body.values());
        return view(records.selectById(recordId));
    }

    @DeleteMapping("/{recordId}")
    @Transactional
    public void delete(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                       @RequestParam int version, HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleRecord row = record(tenantId, moduleId, recordId);
        access.requireWrite(scope, row);
        int changed = records.update(null, new LambdaUpdateWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId, recordId).eq(UnModuleRecord::getTenantId, tenantId)
                .eq(UnModuleRecord::getVersion, version).ne(UnModuleRecord::getRecordStatus, 2)
                .set(UnModuleRecord::getRecordStatus, 2).set(UnModuleRecord::getVersion, version + 1)
                .set(UnModuleRecord::getDeleteTime, LocalDateTime.now())
                .set(UnModuleRecord::getUpdateMemberId, member.getMemberId()));
        if (changed != 1) conflict("记录已被其他操作修改");
        log(row, member.getMemberId(), "delete", view(row).values(), null);
    }

    @GetMapping("/{recordId}/logs")
    public List<UnModuleLog> logs(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                                  HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        access.requireRead(scope, record(tenantId, moduleId, recordId));
        return logs.selectList(new LambdaQueryWrapper<UnModuleLog>().eq(UnModuleLog::getTenantId, tenantId)
                .eq(UnModuleLog::getModuleId, moduleId).eq(UnModuleLog::getRecordId, recordId)
                .orderByDesc(UnModuleLog::getCreateTime, UnModuleLog::getId));
    }

    private Map<String, Set<String>> queryWhitelist(long moduleId) {
        Map<String, Set<String>> result = new HashMap<>();
        for (UnModuleQueryField row : queryFields.selectList(new LambdaQueryWrapper<UnModuleQueryField>()
                .eq(UnModuleQueryField::getModuleId, moduleId).eq(UnModuleQueryField::getStatus, 1))) {
            try {
                Set<String> operators = new HashSet<>();
                for (JsonNode node : json.readTree(row.getOperatorsJson())) operators.add(node.asText());
                result.put(row.getFieldName(), operators);
            } catch (Exception e) { bad("查询字段配置无效"); }
        }
        return result;
    }

    /** 查询采用固定 SQL 片段和参数绑定，客户端字段名不进入 SQL 标识符。 */
    private void appendFilter(StringBuilder where, List<Object> params, Filter filter,
                              Map<String, ModuleSupport.FieldSpec> fields, Map<String, Set<String>> allowed,
                              long tenantId, long moduleId) {
        ModuleSupport.FieldSpec field = fields.get(filter.fieldName());
        if (field == null || !allowed.getOrDefault(filter.fieldName(), Set.of()).contains(filter.operator())) bad("筛选字段或操作符无效");
        String column = valueColumn(field.type());
        if (column.equals("value_json")) bad("集合字段暂不支持列表筛选");
        String comparator = switch (filter.operator()) {
            case "eq" -> "="; case "ne" -> "<>"; case "gt" -> ">"; case "gte" -> ">=";
            case "lt" -> "<"; case "lte" -> "<="; case "contains" -> "LIKE";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的筛选操作符");
        };
        if ("contains".equals(filter.operator()) && !column.equals("value_text")) bad("此字段不能模糊查询");
        Object value = comparableValue(field, filter.value(), tenantId, moduleId);
        if ("contains".equals(filter.operator())) value = "%" + value.toString().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        where.append(" AND EXISTS (SELECT 1 FROM un_module_record_data fd WHERE fd.record_id=r.id AND fd.tenant_id=r.tenant_id")
                .append(" AND fd.field_name=? AND fd.").append(column).append(' ').append(comparator).append(" ?)");
        params.add(filter.fieldName());
        params.add(value);
    }

    private UnModuleScenceConfig scene(long tenantId, long moduleId, long sceneId, HttpServletRequest request) {
        UnModuleScence row = scenes.selectById(sceneId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || !ModuleSupport.enabled(row.getStatus())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "场景不存在");
        long memberId = support.member(request, tenantId).getMemberId();
        if ("personal".equals(row.getVisibility()) && !Objects.equals(row.getOwnerMemberId(), memberId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看个人场景");
        UnModuleScenceConfig config = sceneConfigs.selectOne(new LambdaQueryWrapper<UnModuleScenceConfig>()
                .eq(UnModuleScenceConfig::getScenceId, sceneId));
        if (config == null) bad("场景尚未配置");
        return config;
    }

    private JsonNode readJson(String value) {
        if (value == null) return null;
        try { return json.readTree(value); }
        catch (Exception e) { bad("场景配置 JSON 无效"); return null; }
    }

    /** 场景列顺序直接决定本次列表表头，隐藏或停用的字段不会重新暴露。 */
    private List<ModuleConfigController.ColumnView> sceneColumns(UnModuleScenceConfig config,
                                                                   Map<String, ModuleSupport.FieldSpec> fields) {
        JsonNode configured = readJson(config.getColumnsJson());
        if (configured == null || !configured.isArray()) bad("场景表头配置无效");
        List<ModuleConfigController.ColumnView> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int order = 0;
        for (JsonNode item : configured) {
            String name = item.path("fieldName").asText();
            ModuleSupport.FieldSpec field = fields.get(name);
            if (field == null || !field.index() || field.hidden() || !used.add(name)) bad("场景表头字段已失效");
            if (item.path("visible").asBoolean(true))
                result.add(new ModuleConfigController.ColumnView(name, field.name(), field.type(), field.sortable(), order));
            order++;
        }
        return result;
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
            String column = valueColumn(spec.type());
            row.setValueType(switch (column) { case "value_number" -> "number"; case "value_time" -> "time";
                case "value_json" -> "json"; default -> "text"; });
            Object parsed = comparableValue(spec, value, tenantId, moduleId);
            switch (column) {
                case "value_number" -> row.setValueNumber((BigDecimal) parsed);
                case "value_time" -> row.setValueTime((LocalDateTime) parsed);
                case "value_json" -> row.setValueJson(writeJson(value));
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
        for (UnModuleRule rule : rules.selectList(new LambdaQueryWrapper<UnModuleRule>()
                .eq(UnModuleRule::getModuleId, moduleId).eq(UnModuleRule::getTriggerType, "save")
                .eq(UnModuleRule::getStatus, 1).orderByAsc(UnModuleRule::getSortNo))) {
            JsonNode condition = readJson(rule.getConditionJson());
            if (condition != null && !matchesRule(condition, specs, values)) continue;
            JsonNode effect = readJson(rule.getEffectJson());
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
        String column = valueColumn(spec.type());
        if ("value_json".equals(column)) bad("集合字段暂不支持比较规则");
        Object left = switch (column) { case "value_number" -> current.getValueNumber(); case "value_time" -> current.getValueTime();
            default -> current.getValueText(); };
        Object right = comparableValue(spec, rule.get("value"), current.getTenantId(), current.getModuleId());
        @SuppressWarnings("unchecked") int comparison = ((Comparable<Object>) left).compareTo(right);
        return switch (operator) {
            case "eq" -> comparison == 0; case "ne" -> comparison != 0;
            case "gt" -> comparison > 0; case "gte" -> comparison >= 0;
            case "lt" -> comparison < 0; case "lte" -> comparison <= 0;
            case "contains" -> left instanceof String text && text.contains(right.toString());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的规则操作符");
        };
    }

    private Object comparableValue(ModuleSupport.FieldSpec spec, JsonNode value, long tenantId, long moduleId) {
        if (value == null || value.isNull()) bad("筛选值不能为空");
        return switch (spec.type()) {
            case "text", "textarea", "select" -> {
                if (!value.isTextual()) bad("字段必须是文本：" + spec.name());
                String text = value.asText();
                if (spec.maxLength() != null && text.length() > spec.maxLength()) bad("字段超过长度限制：" + spec.name());
                if ("select".equals(spec.type())) validateOption(spec, text, tenantId, moduleId);
                yield text;
            }
            case "number", "integer" -> {
                BigDecimal number;
                try { number = new BigDecimal(value.asText()); }
                catch (Exception e) { bad("字段必须是数字：" + spec.name()); yield null; }
                if ("integer".equals(spec.type()) && number.stripTrailingZeros().scale() > 0) bad("字段必须是整数：" + spec.name());
                if (spec.precisionScale() != null && number.scale() > spec.precisionScale()) bad("数字小数位超限：" + spec.name());
                if (spec.minNumber() != null && number.compareTo(spec.minNumber()) < 0) bad("数字低于最小值：" + spec.name());
                if (spec.maxNumber() != null && number.compareTo(spec.maxNumber()) > 0) bad("数字高于最大值：" + spec.name());
                yield number;
            }
            case "date", "datetime" -> {
                if (!value.isTextual()) bad("字段必须是日期时间：" + spec.name());
                try { yield "date".equals(spec.type()) ? LocalDate.parse(value.asText()).atStartOfDay() : LocalDateTime.parse(value.asText()); }
                catch (DateTimeParseException e) { bad("日期时间格式无效：" + spec.name()); yield null; }
            }
            case "boolean" -> {
                if (!value.isBoolean()) bad("字段必须是布尔值：" + spec.name());
                yield value.asBoolean() ? "true" : "false";
            }
            case "multiselect" -> {
                if (!value.isArray()) bad("多选字段必须是数组：" + spec.name());
                for (JsonNode item : value) {
                    if (!item.isTextual()) bad("多选选项必须是文本");
                    validateOption(spec, item.asText(), tenantId, moduleId);
                }
                yield writeJson(value);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的字段类型");
        };
    }

    private void validateOption(ModuleSupport.FieldSpec spec, String option, long tenantId, long moduleId) {
        Set<String> allowed = new HashSet<>();
        JsonNode configured = readJson(spec.optionData());
        if (configured != null && configured.isArray()) {
            for (JsonNode item : configured) allowed.add(item.isObject() ? item.path("value").asText() : item.asText());
        }
        if (spec.dictCode() != null) {
            long systemId = support.module(tenantId, moduleId).getSystemId();
            for (UnModuleDict item : dictionaries.selectList(new LambdaQueryWrapper<UnModuleDict>()
                    .eq(UnModuleDict::getSystemId, systemId).in(UnModuleDict::getModuleId, List.of(0L, moduleId))
                    .eq(UnModuleDict::getDictCode, spec.dictCode()).eq(UnModuleDict::getStatus, 1)))
                allowed.add(item.getItemCode());
        }
        if (!allowed.contains(option)) bad("选项不在配置范围内：" + spec.name());
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
        if (tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getTenantId, tenantId)
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

    private UnModuleRecord record(long tenantId, long moduleId, long recordId) {
        UnModuleRecord row = records.selectById(recordId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || row.getRecordStatus() == 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return row;
    }

    private RecordView view(UnModuleRecord row) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (UnModuleRecordData item : data.selectList(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId, row.getId()))) {
            JsonNode value = switch (item.getValueType()) {
                case "number" -> json.valueToTree(item.getValueNumber());
                case "time" -> json.valueToTree(item.getValueTime());
                case "json" -> readJson(item.getValueJson());
                default -> json.valueToTree(item.getValueText());
            };
            values.put(item.getFieldName(), value);
        }
        return new RecordView(row, values);
    }

    private void log(UnModuleRecord record, long actorId, String operation, Object before, Object after) {
        UnModuleLog row = new UnModuleLog();
        row.setTenantId(record.getTenantId());
        row.setModuleId(record.getModuleId());
        row.setRecordId(record.getId());
        row.setActorMemberId(actorId);
        row.setOperationCode(operation);
        row.setBeforeJson(before == null ? null : writeJson(before));
        row.setAfterJson(after == null ? null : writeJson(after));
        logs.insert(row);
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段值 JSON 无效"); }
    }

    private String valueColumn(String type) {
        return switch (type) {
            case "number", "integer" -> "value_number";
            case "date", "datetime" -> "value_time";
            case "multiselect" -> "value_json";
            default -> "value_text";
        };
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    /** 表单 values 以稳定字段编码为键；更新必须传当前版本。 */
    public record RecordForm(String name, String num, String remark, Byte status, Long ownerMemberId,
                             Long ownerDepartmentId, Integer version, Map<String, JsonNode> values) { }
    public record RecordView(UnModuleRecord record, Map<String, JsonNode> values) { }
    public record RecordPage(List<ModuleConfigController.ColumnView> columns, List<RecordView> records,
                             long total, int page, int size) { }
    private record Filter(String fieldName, String operator, JsonNode value) { }
}
