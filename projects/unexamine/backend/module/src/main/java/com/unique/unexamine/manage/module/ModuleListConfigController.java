package com.unique.unexamine.manage.module;

import com.unique.unexamine.module.base.entity.UnModuleFieldSort;
import com.unique.unexamine.module.base.entity.UnModuleQueryField;
import com.unique.unexamine.module.base.mapper.UnModuleFieldSortMapper;
import com.unique.unexamine.module.base.mapper.UnModuleQueryFieldMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 模块列表查询字段白名单与表头顺序。 */
@RestController
@RequestMapping("/api/modules")
public class ModuleListConfigController {
    private final ModuleSupport support;
    private final UnModuleFieldSortMapper fieldSorts;
    private final UnModuleQueryFieldMapper queryFields;
    private final ObjectMapper json;

    public ModuleListConfigController(ModuleSupport support, UnModuleFieldSortMapper fieldSorts,
                                      UnModuleQueryFieldMapper queryFields, ObjectMapper json) {
        this.support = support;
        this.fieldSorts = fieldSorts;
        this.queryFields = queryFields;
        this.json = json;
    }

    @GetMapping("/{moduleId}/query-fields")
    public List<UnModuleQueryField> queryFields(@RequestParam long tenantId, @PathVariable long moduleId, HttpServletRequest request) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
        return queryFields.selectList(new LambdaQueryWrapper<UnModuleQueryField>().eq(UnModuleQueryField::getModuleId, moduleId)
                .eq(UnModuleQueryField::getStatus, 1).orderByAsc(UnModuleQueryField::getSortNo));
    }

    @PutMapping("/{moduleId}/query-fields")
    @Transactional
    public List<UnModuleQueryField> saveQueryFields(@RequestParam long tenantId, @PathVariable long moduleId,
                                                     @RequestBody List<QueryFieldForm> body, HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        Map<String, ModuleSupport.FieldSpec> effective = support.effectiveFields(tenantId, moduleId);
        Set<String> used = new HashSet<>();
        for (QueryFieldForm item : body) {
            if (!effective.containsKey(item.fieldName()) || !used.add(item.fieldName())) bad("查询字段不存在或重复");
            if (item.operators() == null || item.operators().isEmpty()) bad("查询操作符不能为空");
            for (String operator : item.operators()) {
                if (!Set.of("eq", "ne", "contains", "gt", "gte", "lt", "lte", "in").contains(operator)) bad("不支持的查询操作符");
            }
        }
        queryFields.delete(new LambdaQueryWrapper<UnModuleQueryField>().eq(UnModuleQueryField::getModuleId, moduleId));
        int order = 0;
        for (QueryFieldForm item : body) {
            UnModuleQueryField row = new UnModuleQueryField();
            row.setModuleId(moduleId);
            row.setFieldName(item.fieldName());
            row.setOperatorsJson(writeJson(item.operators()));
            row.setSortNo(order++);
            row.setStatus((byte) 1);
            queryFields.insert(row);
        }
        return queryFields(tenantId, moduleId, request);
    }

    @GetMapping("/{moduleId}/columns")
    public List<ColumnView> columns(@RequestParam long tenantId, @PathVariable long moduleId, HttpServletRequest request) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
        Map<String, Integer> order = new HashMap<>();
        for (UnModuleFieldSort row : fieldSorts.selectList(new LambdaQueryWrapper<UnModuleFieldSort>()
                .eq(UnModuleFieldSort::getModuleId, moduleId).eq(UnModuleFieldSort::getTenantId, tenantId)
                .eq(UnModuleFieldSort::getViewType, "list"))) order.put(row.getFieldName(), row.getSortNo());
        List<ColumnView> columns = new ArrayList<>();
        int fallback = 100000;
        for (ModuleSupport.FieldSpec field : support.effectiveFields(tenantId, moduleId).values()) {
            if (field.index() && !field.hidden())
                columns.add(new ColumnView(field.fieldName(), field.name(), field.type(), field.sortable(),
                        order.getOrDefault(field.fieldName(), fallback++)));
        }
        columns.sort(Comparator.comparingInt(ColumnView::sortNo).thenComparing(ColumnView::fieldName));
        return columns;
    }

    @PutMapping("/{moduleId}/columns")
    @Transactional
    public List<ColumnView> saveColumns(@RequestParam long tenantId, @PathVariable long moduleId,
                                        @RequestBody List<String> fieldNames, HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        Map<String, ModuleSupport.FieldSpec> effective = support.effectiveFields(tenantId, moduleId);
        if (new HashSet<>(fieldNames).size() != fieldNames.size()) bad("表头字段重复");
        for (String name : fieldNames) {
            ModuleSupport.FieldSpec field = effective.get(name);
            if (field == null || !field.index() || field.hidden()) bad("表头包含无效字段");
        }
        fieldSorts.delete(new LambdaQueryWrapper<UnModuleFieldSort>().eq(UnModuleFieldSort::getModuleId, moduleId)
                .eq(UnModuleFieldSort::getTenantId, tenantId).eq(UnModuleFieldSort::getViewType, "list"));
        int order = 0;
        for (String name : fieldNames) {
            UnModuleFieldSort row = new UnModuleFieldSort();
            row.setModuleId(moduleId);
            row.setTenantId(tenantId);
            row.setViewType("list");
            row.setFieldName(name);
            row.setSortNo(order++);
            fieldSorts.insert(row);
        }
        return columns(tenantId, moduleId, request);
    }


    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 配置无效"); }
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record QueryFieldForm(String fieldName, List<String> operators) { }
    public record ColumnView(String fieldName, String title, String type, boolean sortable, int sortNo) { }
}
