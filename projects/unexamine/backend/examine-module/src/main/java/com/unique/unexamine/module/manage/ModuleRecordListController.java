package com.unique.unexamine.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.module.base.entity.UnModuleQueryField;
import com.unique.unexamine.module.base.entity.UnModuleScence;
import com.unique.unexamine.module.base.entity.UnModuleScenceConfig;
import com.unique.unexamine.module.base.mapper.UnModuleQueryFieldMapper;
import com.unique.unexamine.module.base.mapper.UnModuleScenceConfigMapper;
import com.unique.unexamine.module.base.mapper.UnModuleScenceMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.unique.unexamine.module.manage.ModuleRecordController.RecordView;

/** 模块记录列表：按场景与查询字段白名单筛选、排序和分页。 */
@Tag(name = "模块记录列表", description = "模块记录分页、筛选和排序。")
@RestController
@RequestMapping("/api/modules/{moduleId}/records")
public class ModuleRecordListController {
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final UnModuleQueryFieldMapper queryFields;
    private final UnModuleScenceMapper scenes;
    private final UnModuleScenceConfigMapper sceneConfigs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModuleListConfigController config;
    private final ModuleRecordFieldValue valueSupport;
    private final ModuleRecordReader reader;

    public ModuleRecordListController(ModuleSupport support, ModuleRecordAccess access,
                                      UnModuleQueryFieldMapper queryFields, UnModuleScenceMapper scenes,
                                      UnModuleScenceConfigMapper sceneConfigs, JdbcTemplate jdbc,
                                      ObjectMapper json, ModuleListConfigController config,
                                      ModuleRecordFieldValue valueSupport, ModuleRecordReader reader) {
        this.support = support;
        this.access = access;
        this.queryFields = queryFields;
        this.scenes = scenes;
        this.sceneConfigs = sceneConfigs;
        this.jdbc = jdbc;
        this.json = json;
        this.config = config;
        this.valueSupport = valueSupport;
        this.reader = reader;
    }

    @Operation(summary = "查询列表（模块记录列表）")
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
            JsonNode configured = valueSupport.readJson(activeScene.getFiltersJson());
            if (configured != null && configured.isArray()) {
                for (JsonNode item : configured) {
                    filters.add(new Filter(item.path("fieldName").asText(), item.path("operator").asText(), item.get("value")));
                }
            }
            if (sortField == null) {
                JsonNode sort = valueSupport.readJson(activeScene.getSortJson());
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
                String column = valueSupport.valueColumn(field.type());
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
        for (Long id : ids) rows.add(reader.view(reader.record(tenantId, moduleId, id)));
        List<ModuleListConfigController.ColumnView> columns = activeScene == null
                ? config.columns(tenantId, moduleId, request) : sceneColumns(activeScene, fields);
        return new RecordPage(columns, rows, total == null ? 0 : total, page, size);
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
        String column = valueSupport.valueColumn(field.type());
        if (column.equals("value_json")) bad("集合字段暂不支持列表筛选");
        if ("in".equals(filter.operator())) {
            if (filter.value() == null || !filter.value().isArray()
                    || filter.value().isEmpty() || filter.value().size() > 50) bad("IN 筛选须提供 1 至 50 个值");
            where.append(" AND EXISTS (SELECT 1 FROM un_module_record_data fd WHERE fd.record_id=r.id AND fd.tenant_id=r.tenant_id")
                    .append(" AND fd.field_name=? AND fd.").append(column).append(" IN (")
                    .append(String.join(",", Collections.nCopies(filter.value().size(), "?"))).append("))");
            params.add(filter.fieldName());
            for (JsonNode value : filter.value()) params.add(valueSupport.comparableValue(field, value, tenantId, moduleId));
            return;
        }
        String comparator = switch (filter.operator()) {
            case "eq" -> "="; case "ne" -> "<>"; case "gt" -> ">"; case "gte" -> ">=";
            case "lt" -> "<"; case "lte" -> "<="; case "contains" -> "LIKE";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的筛选操作符");
        };
        if ("contains".equals(filter.operator()) && !column.equals("value_text")) bad("此字段不能模糊查询");
        Object value = valueSupport.comparableValue(field, filter.value(), tenantId, moduleId);
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

    private List<ModuleListConfigController.ColumnView> sceneColumns(UnModuleScenceConfig config,
                                                                   Map<String, ModuleSupport.FieldSpec> fields) {
        JsonNode configured = valueSupport.readJson(config.getColumnsJson());
        if (configured == null || !configured.isArray()) bad("场景表头配置无效");
        List<ModuleListConfigController.ColumnView> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int order = 0;
        for (JsonNode item : configured) {
            String name = item.path("fieldName").asText();
            ModuleSupport.FieldSpec field = fields.get(name);
            if (field == null || !field.index() || field.hidden() || !used.add(name)) bad("场景表头字段已失效");
            if (item.path("visible").asBoolean(true))
                result.add(new ModuleListConfigController.ColumnView(name, field.name(), field.type(), field.sortable(), order));
            order++;
        }
        return result;
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    public record RecordPage(List<ModuleListConfigController.ColumnView> columns, List<RecordView> records,
                             long total, int page, int size) { }
    private record Filter(String fieldName, String operator, JsonNode value) { }
}
