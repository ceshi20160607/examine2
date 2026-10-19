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

import java.util.*;
import java.util.regex.Pattern;

/** 模块、字段、字典及列表表头的配置接口。 */
@RestController
@RequestMapping("/api/modules")
public class ModuleConfigController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final UnModuleGroupMapper groups;
    private final UnModuleMapper modules;
    private final UnModuleTenantMapper tenantModules;
    private final UnModuleFieldMapper fields;
    private final UnModuleTenantFieldMapper tenantFields;
    private final UnModuleFieldSortMapper fieldSorts;
    private final UnModuleQueryFieldMapper queryFields;
    private final UnModuleDictMapper dictionaries;
    private final ObjectMapper json;

    public ModuleConfigController(ModuleSupport support, UnModuleGroupMapper groups, UnModuleMapper modules,
                                  UnModuleTenantMapper tenantModules, UnModuleFieldMapper fields,
                                  UnModuleTenantFieldMapper tenantFields, UnModuleFieldSortMapper fieldSorts,
                                  UnModuleQueryFieldMapper queryFields, UnModuleDictMapper dictionaries, ObjectMapper json) {
        this.support = support;
        this.groups = groups;
        this.modules = modules;
        this.tenantModules = tenantModules;
        this.fields = fields;
        this.tenantFields = tenantFields;
        this.fieldSorts = fieldSorts;
        this.queryFields = queryFields;
        this.dictionaries = dictionaries;
        this.json = json;
    }

    /** 查询租户所属系统的模块分组。 */
    @GetMapping("/groups")
    public List<UnModuleGroup> groups(@RequestParam long tenantId, HttpServletRequest request) {
        support.member(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        return groups.selectList(new LambdaQueryWrapper<UnModuleGroup>()
                .eq(UnModuleGroup::getSystemId, systemId).orderByAsc(UnModuleGroup::getParentId, UnModuleGroup::getSortNo, UnModuleGroup::getId));
    }

    @PostMapping("/groups")
    public UnModuleGroup createGroup(@RequestParam long tenantId, @Valid @RequestBody GroupForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        code(body.code());
        if (groups.selectCount(new LambdaQueryWrapper<UnModuleGroup>().eq(UnModuleGroup::getSystemId, systemId)
                .eq(UnModuleGroup::getCode, body.code())) > 0) conflict("分组编码已存在");
        long parentId = body.parentId() == null ? 0 : body.parentId();
        checkParent(systemId, parentId);
        UnModuleGroup group = new UnModuleGroup();
        group.setSystemId(systemId);
        group.setParentId(parentId);
        group.setCode(body.code());
        group.setName(body.name());
        group.setIcon(body.icon());
        group.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        group.setStatus((byte) 1);
        groups.insert(group);
        return groups.selectById(group.getId());
    }

    @PutMapping("/groups/{groupId}")
    public UnModuleGroup updateGroup(@RequestParam long tenantId, @PathVariable long groupId,
                                     @Valid @RequestBody GroupForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModuleGroup group = group(tenantId, groupId);
        if (!group.getCode().equals(body.code())) conflict("分组编码不可修改");
        long parentId = body.parentId() == null ? 0 : body.parentId();
        if (parentId == groupId) bad("分组不能作为自己的上级");
        checkParent(group.getSystemId(), parentId);
        group.setParentId(parentId);
        group.setName(body.name());
        group.setIcon(body.icon());
        group.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        groups.updateById(group);
        return groups.selectById(groupId);
    }

    @DeleteMapping("/groups/{groupId}")
    public void stopGroup(@RequestParam long tenantId, @PathVariable long groupId, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModuleGroup group = group(tenantId, groupId);
        if (groups.selectCount(new LambdaQueryWrapper<UnModuleGroup>().eq(UnModuleGroup::getParentId, groupId).eq(UnModuleGroup::getStatus, 1)) > 0
                || modules.selectCount(new LambdaQueryWrapper<UnModule>().eq(UnModule::getGroupId, groupId).eq(UnModule::getStatus, 1)) > 0) {
            conflict("分组下有启用的分组或模块");
        }
        group.setStatus((byte) 0);
        groups.updateById(group);
    }

    /** 列出同一业务系统的模块，并返回租户实际是否启用。 */
    @GetMapping
    public List<Map<String, Object>> modules(@RequestParam long tenantId, HttpServletRequest request) {
        support.member(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        List<UnModule> all = modules.selectList(new LambdaQueryWrapper<UnModule>().eq(UnModule::getSystemId, systemId)
                .orderByAsc(UnModule::getGroupId, UnModule::getSortNo, UnModule::getId));
        Map<Long, UnModuleTenant> enabled = new HashMap<>();
        tenantModules.selectList(new LambdaQueryWrapper<UnModuleTenant>().eq(UnModuleTenant::getTenantId, tenantId))
                .forEach(row -> enabled.put(row.getModuleId(), row));
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnModule module : all) {
            result.add(Map.of("module", module, "tenantEnabled",
                    enabled.containsKey(module.getId()) && ModuleSupport.enabled(enabled.get(module.getId()).getStatus())));
        }
        return result;
    }

    @PostMapping
    @Transactional
    public UnModule createModule(@RequestParam long tenantId, @Valid @RequestBody ModuleForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        code(body.code());
        UnModuleGroup group = group(tenantId, body.groupId());
        if (!ModuleSupport.enabled(group.getStatus())) bad("分组已停用");
        if (modules.selectCount(new LambdaQueryWrapper<UnModule>().eq(UnModule::getSystemId, systemId).eq(UnModule::getCode, body.code())) > 0)
            conflict("模块编码已存在");
        UnModule module = new UnModule();
        module.setSystemId(systemId);
        module.setGroupId(body.groupId());
        module.setCode(body.code());
        module.setName(body.name());
        module.setIcon(body.icon());
        module.setDescription(body.description());
        module.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        module.setStatus((byte) 1);
        modules.insert(module);
        UnModuleTenant relation = new UnModuleTenant();
        relation.setTenantId(tenantId);
        relation.setModuleId(module.getId());
        relation.setSortNo(0);
        relation.setStatus((byte) 1);
        tenantModules.insert(relation);
        return modules.selectById(module.getId());
    }

    @PutMapping("/{moduleId}")
    public UnModule updateModule(@RequestParam long tenantId, @PathVariable long moduleId,
                                 @Valid @RequestBody ModuleForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        if (!module.getCode().equals(body.code())) conflict("模块编码不可修改");
        group(tenantId, body.groupId());
        module.setGroupId(body.groupId());
        module.setName(body.name());
        module.setIcon(body.icon());
        module.setDescription(body.description());
        module.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        modules.updateById(module);
        return modules.selectById(moduleId);
    }

    @PutMapping("/{moduleId}/tenant-status")
    public UnModuleTenant tenantStatus(@RequestParam long tenantId, @PathVariable long moduleId,
                                       @RequestBody TenantStatus body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = modules.selectById(moduleId);
        if (module == null || !module.getSystemId().equals(support.tenant(tenantId).getSystemId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模块不存在");
        UnModuleTenant relation = tenantModules.selectOne(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getTenantId, tenantId).eq(UnModuleTenant::getModuleId, moduleId));
        if (relation == null) {
            relation = new UnModuleTenant();
            relation.setTenantId(tenantId);
            relation.setModuleId(moduleId);
            relation.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
            relation.setStatus((byte) (body.enabled() ? 1 : 0));
            tenantModules.insert(relation);
        } else {
            relation.setStatus((byte) (body.enabled() ? 1 : 0));
            if (body.sortNo() != null) relation.setSortNo(body.sortNo());
            tenantModules.updateById(relation);
        }
        return tenantModules.selectById(relation.getId());
    }

    @GetMapping("/{moduleId}/fields")
    public List<UnModuleField> fields(@RequestParam long tenantId, @PathVariable long moduleId, HttpServletRequest request) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
        return fields.selectList(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId, moduleId)
                .orderByAsc(UnModuleField::getId));
    }

    /** 新字段编码固定；存在记录的字段停用仍保留历史值。 */
    @PostMapping("/{moduleId}/fields")
    public UnModuleField createField(@RequestParam long tenantId, @PathVariable long moduleId,
                                     @Valid @RequestBody FieldForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        code(body.fieldName());
        support.validateFieldType(body.type());
        validateFieldForm(body);
        if (fields.selectCount(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId, moduleId)
                .eq(UnModuleField::getFieldName, body.fieldName())) > 0) conflict("字段编码已存在");
        UnModuleField field = new UnModuleField();
        field.setModuleId(moduleId);
        field.setFieldName(body.fieldName());
        applyField(field, body);
        fields.insert(field);
        return fields.selectById(field.getId());
    }

    @PutMapping("/{moduleId}/fields/{fieldId}")
    public UnModuleField updateField(@RequestParam long tenantId, @PathVariable long moduleId, @PathVariable long fieldId,
                                     @Valid @RequestBody FieldForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleField field = fields.selectById(fieldId);
        if (field == null || !field.getModuleId().equals(moduleId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字段不存在");
        if (!field.getFieldName().equals(body.fieldName()) || !field.getType().equals(body.type()))
            conflict("已有字段的编码和类型不可直接修改，请新增字段并迁移数据");
        validateFieldForm(body);
        applyField(field, body);
        fields.updateById(field);
        return fields.selectById(fieldId);
    }

    @DeleteMapping("/{moduleId}/fields/{fieldId}")
    public void stopField(@RequestParam long tenantId, @PathVariable long moduleId, @PathVariable long fieldId,
                          HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleField field = fields.selectById(fieldId);
        if (field == null || !field.getModuleId().equals(moduleId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字段不存在");
        field.setStatus((byte) 0);
        fields.updateById(field);
    }

    @GetMapping("/{moduleId}/form-schema")
    public List<ModuleSupport.FieldSpec> formSchema(@RequestParam long tenantId, @PathVariable long moduleId,
                                                     @RequestParam(defaultValue = "create") String purpose,
                                                     HttpServletRequest request) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
        if (!Set.of("create", "edit", "detail").contains(purpose)) bad("表单用途无效");
        String viewType = "detail".equals(purpose) ? "detail" : "form";
        Map<String, Integer> order = new HashMap<>();
        for (UnModuleFieldSort row : fieldSorts.selectList(new LambdaQueryWrapper<UnModuleFieldSort>()
                .eq(UnModuleFieldSort::getModuleId, moduleId).eq(UnModuleFieldSort::getTenantId, tenantId)
                .eq(UnModuleFieldSort::getViewType, viewType))) order.put(row.getFieldName(), row.getSortNo());
        List<ModuleSupport.FieldSpec> result = new ArrayList<>();
        for (ModuleSupport.FieldSpec field : support.effectiveFields(tenantId, moduleId).values())
            if (!field.hidden() && ("detail".equals(purpose) ? field.detail() : field.add())) result.add(field);
        result.sort(Comparator.comparingInt((ModuleSupport.FieldSpec field) -> order.getOrDefault(field.fieldName(), 100000))
                .thenComparing(ModuleSupport.FieldSpec::fieldName));
        return result;
    }

    @PutMapping("/{moduleId}/fields/order")
    @Transactional
    public List<ModuleSupport.FieldSpec> saveFieldOrder(@RequestParam long tenantId, @PathVariable long moduleId,
                                                         @RequestParam(defaultValue = "form") String viewType,
                                                         @RequestBody List<String> fieldNames,
                                                         HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        if (!Set.of("form", "detail").contains(viewType)) bad("字段排序位置无效");
        Map<String, ModuleSupport.FieldSpec> effective = support.effectiveFields(tenantId, moduleId);
        if (new HashSet<>(fieldNames).size() != fieldNames.size()) bad("字段顺序有重复编码");
        for (String name : fieldNames) if (!effective.containsKey(name)) bad("字段顺序包含无效字段");
        fieldSorts.delete(new LambdaQueryWrapper<UnModuleFieldSort>().eq(UnModuleFieldSort::getModuleId, moduleId)
                .eq(UnModuleFieldSort::getTenantId, tenantId).eq(UnModuleFieldSort::getViewType, viewType));
        int order = 0;
        for (String name : fieldNames) {
            UnModuleFieldSort row = new UnModuleFieldSort();
            row.setModuleId(moduleId);
            row.setTenantId(tenantId);
            row.setViewType(viewType);
            row.setFieldName(name);
            row.setSortNo(order++);
            fieldSorts.insert(row);
        }
        return formSchema(tenantId, moduleId, "detail".equals(viewType) ? "detail" : "create", request);
    }

    @GetMapping("/{moduleId}/tenant-fields")
    public List<UnModuleTenantField> tenantFields(@RequestParam long tenantId, @PathVariable long moduleId,
                                                   HttpServletRequest request) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
        return tenantFields.selectList(new LambdaQueryWrapper<UnModuleTenantField>()
                .eq(UnModuleTenantField::getTenantId, tenantId).eq(UnModuleTenantField::getModuleId, moduleId));
    }

    @PostMapping("/{moduleId}/tenant-fields")
    public UnModuleTenantField createTenantField(@RequestParam long tenantId, @PathVariable long moduleId,
                                                  @Valid @RequestBody FieldForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        code(body.fieldName());
        support.validateFieldType(body.type());
        validateFieldForm(body);
        if (fields.selectCount(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId, moduleId)
                .eq(UnModuleField::getFieldName, body.fieldName())) > 0
                || tenantFields.selectCount(new LambdaQueryWrapper<UnModuleTenantField>()
                .eq(UnModuleTenantField::getTenantId, tenantId).eq(UnModuleTenantField::getModuleId, moduleId)
                .eq(UnModuleTenantField::getFieldName, body.fieldName())) > 0) conflict("字段编码已存在");
        UnModuleTenantField field = new UnModuleTenantField();
        field.setTenantId(tenantId);
        field.setModuleId(moduleId);
        field.setFieldName(body.fieldName());
        applyTenantField(field, body);
        tenantFields.insert(field);
        return tenantFields.selectById(field.getId());
    }

    @PutMapping("/{moduleId}/tenant-fields/{fieldId}")
    public UnModuleTenantField updateTenantField(@RequestParam long tenantId, @PathVariable long moduleId,
                                                  @PathVariable long fieldId, @Valid @RequestBody FieldForm body,
                                                  HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleTenantField field = tenantFields.selectById(fieldId);
        if (field == null || !field.getTenantId().equals(tenantId) || !field.getModuleId().equals(moduleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "租户字段不存在");
        if (field.getBaseFieldId() != null) conflict("基础字段覆盖请使用 base 接口修改");
        if (!field.getFieldName().equals(body.fieldName()) || (field.getType() != null && !field.getType().equals(body.type())))
            conflict("已有字段的编码和类型不可直接修改");
        validateFieldForm(body);
        applyTenantField(field, body);
        tenantFields.updateById(field);
        return tenantFields.selectById(fieldId);
    }

    /** 租户可调整基础字段的表单显示，但不放宽基础字段的存储类型与数值约束。 */
    @PutMapping("/{moduleId}/tenant-fields/base/{baseFieldId}")
    public UnModuleTenantField overrideBaseField(@RequestParam long tenantId, @PathVariable long moduleId,
                                                  @PathVariable long baseFieldId, @Valid @RequestBody FieldForm body,
                                                  HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleField base = fields.selectById(baseFieldId);
        if (base == null || !base.getModuleId().equals(moduleId) || !ModuleSupport.enabled(base.getStatus()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "基础字段不存在");
        if (!base.getFieldName().equals(body.fieldName()) || !base.getType().equals(body.type()))
            conflict("基础字段编码和类型不能在租户内修改");
        if (ModuleSupport.enabled(base.getMustFlag()) && Boolean.FALSE.equals(body.required()))
            conflict("不能取消基础字段的必填约束");
        UnModuleTenantField field = tenantFields.selectOne(new LambdaQueryWrapper<UnModuleTenantField>()
                .eq(UnModuleTenantField::getTenantId, tenantId).eq(UnModuleTenantField::getModuleId, moduleId)
                .eq(UnModuleTenantField::getBaseFieldId, baseFieldId));
        if (field == null) {
            field = new UnModuleTenantField();
            field.setTenantId(tenantId);
            field.setModuleId(moduleId);
            field.setBaseFieldId(baseFieldId);
            field.setFieldName(base.getFieldName());
        }
        applyTenantField(field, body);
        field.setType(base.getType());
        field.setMaxLength(base.getMaxLength());
        field.setUnionFlag(base.getUnionFlag());
        field.setPrecisionScale(base.getPrecisionScale());
        field.setMinNumRestrict(base.getMinNumRestrict());
        field.setMaxNumRestrict(base.getMaxNumRestrict());
        field.setDictCode(base.getDictCode());
        field.setOptionData(base.getOptionData());
        if (ModuleSupport.enabled(base.getMustFlag())) field.setMustFlag((byte) 1);
        if (!ModuleSupport.enabled(base.getSortableFlag())) field.setSortableFlag((byte) 0);
        if (field.getId() == null) tenantFields.insert(field); else tenantFields.updateById(field);
        return tenantFields.selectById(field.getId());
    }

    @DeleteMapping("/{moduleId}/tenant-fields/{fieldId}")
    public void stopTenantField(@RequestParam long tenantId, @PathVariable long moduleId, @PathVariable long fieldId,
                                HttpServletRequest request) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleTenantField field = tenantFields.selectById(fieldId);
        if (field == null || !field.getTenantId().equals(tenantId) || !field.getModuleId().equals(moduleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "租户字段不存在");
        field.setStatus((byte) 0);
        tenantFields.updateById(field);
    }

    /** 查询字段白名单也决定列表允许使用的筛选项。 */
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

    @GetMapping("/{moduleId}/dicts")
    public List<UnModuleDict> dicts(@RequestParam long tenantId, @PathVariable long moduleId, HttpServletRequest request) {
        support.member(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        return dictionaries.selectList(new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getSystemId, module.getSystemId())
                .in(UnModuleDict::getModuleId, List.of(0L, moduleId)).eq(UnModuleDict::getStatus, 1)
                .orderByAsc(UnModuleDict::getDictCode, UnModuleDict::getSortNo));
    }

    @PostMapping("/{moduleId}/dicts")
    public UnModuleDict createDictItem(@RequestParam long tenantId, @PathVariable long moduleId,
                                        @Valid @RequestBody DictForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        code(body.dictCode());
        code(body.itemCode());
        if (dictionaries.selectCount(new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getSystemId, module.getSystemId())
                .eq(UnModuleDict::getModuleId, moduleId).eq(UnModuleDict::getDictCode, body.dictCode())
                .eq(UnModuleDict::getItemCode, body.itemCode())) > 0) conflict("字典选项编码已存在");
        UnModuleDict row = new UnModuleDict();
        row.setSystemId(module.getSystemId());
        row.setModuleId(moduleId);
        row.setDictCode(body.dictCode());
        row.setItemCode(body.itemCode());
        row.setItemName(body.itemName());
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        row.setStatus((byte) 1);
        dictionaries.insert(row);
        return dictionaries.selectById(row.getId());
    }

    @PutMapping("/{moduleId}/dicts/{dictId}")
    public UnModuleDict updateDictItem(@RequestParam long tenantId, @PathVariable long moduleId,
                                        @PathVariable long dictId, @Valid @RequestBody DictForm body,
                                        HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        UnModuleDict row = dictionaries.selectById(dictId);
        if (row == null || !row.getSystemId().equals(module.getSystemId()) || !row.getModuleId().equals(moduleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字典选项不存在");
        if (!row.getDictCode().equals(body.dictCode()) || !row.getItemCode().equals(body.itemCode()))
            conflict("字典编码与选项编码不可修改");
        row.setItemName(body.itemName());
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        dictionaries.updateById(row);
        return dictionaries.selectById(dictId);
    }

    @DeleteMapping("/{moduleId}/dicts/{dictId}")
    public void stopDictItem(@RequestParam long tenantId, @PathVariable long moduleId,
                             @PathVariable long dictId, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        UnModuleDict row = dictionaries.selectById(dictId);
        if (row == null || !row.getSystemId().equals(module.getSystemId()) || !row.getModuleId().equals(moduleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字典选项不存在");
        row.setStatus((byte) 0);
        dictionaries.updateById(row);
    }

    private UnModuleGroup group(long tenantId, long groupId) {
        UnModuleGroup row = groups.selectById(groupId);
        if (row == null || !row.getSystemId().equals(support.tenant(tenantId).getSystemId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分组不存在");
        return row;
    }

    private void checkParent(long systemId, long parentId) {
        if (parentId == 0) return;
        UnModuleGroup parent = groups.selectById(parentId);
        if (parent == null || !parent.getSystemId().equals(systemId) || !ModuleSupport.enabled(parent.getStatus())) bad("上级分组无效");
    }

    private void applyField(UnModuleField field, FieldForm body) {
        field.setName(body.name());
        field.setType(body.type());
        field.setRemark(body.remark());
        field.setInputTips(body.inputTips());
        field.setMaxLength(body.maxLength());
        field.setDefaultValue(body.defaultValue());
        field.setMustFlag(flag(body.required()));
        field.setUnionFlag(flag(body.unique()));
        field.setHiddenFlag(flag(body.hidden()));
        field.setAddFlag(defaultOn(body.add()));
        field.setIndexFlag(defaultOn(body.index()));
        field.setDetailFlag(defaultOn(body.detail()));
        field.setSortableFlag(flag(body.sortable()));
        field.setDictCode(body.dictCode());
        field.setOptionData(body.optionData() == null ? null : writeJson(body.optionData()));
        field.setPrecisionScale(body.precisionScale());
        field.setMinNumRestrict(body.minNumber());
        field.setMaxNumRestrict(body.maxNumber());
        field.setStatus((byte) 1);
    }

    private void applyTenantField(UnModuleTenantField field, FieldForm body) {
        field.setName(body.name());
        field.setType(body.type());
        field.setRemark(body.remark());
        field.setInputTips(body.inputTips());
        field.setMaxLength(body.maxLength());
        field.setDefaultValue(body.defaultValue());
        field.setMustFlag(flag(body.required()));
        field.setUnionFlag(flag(body.unique()));
        field.setHiddenFlag(flag(body.hidden()));
        field.setAddFlag(defaultOn(body.add()));
        field.setIndexFlag(defaultOn(body.index()));
        field.setDetailFlag(defaultOn(body.detail()));
        field.setSortableFlag(flag(body.sortable()));
        field.setDictCode(body.dictCode());
        field.setOptionData(body.optionData() == null ? null : writeJson(body.optionData()));
        field.setPrecisionScale(body.precisionScale());
        field.setMinNumRestrict(body.minNumber());
        field.setMaxNumRestrict(body.maxNumber());
        field.setStatus((byte) 1);
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 配置无效"); }
    }

    /** 数值精度与选项在配置阶段检查，避免发布后出现无法保存的表单。 */
    private void validateFieldForm(FieldForm body) {
        if (body.maxLength() != null && (body.maxLength() < 1 || body.maxLength() > 100000)) bad("字段长度无效");
        if (body.precisionScale() != null && (body.precisionScale() < 0 || body.precisionScale() > 8)) bad("数字精度无效");
        if (body.minNumber() != null && body.maxNumber() != null && body.minNumber().compareTo(body.maxNumber()) > 0)
            bad("数字最小值不能大于最大值");
        if (Set.of("select", "multiselect").contains(body.type())
                && (body.dictCode() == null || body.dictCode().isBlank())
                && (body.optionData() == null || !body.optionData().isArray() || body.optionData().isEmpty()))
            bad("选项字段需要字典或选项配置");
        if ("multiselect".equals(body.type()) && (Boolean.TRUE.equals(body.unique()) || Boolean.TRUE.equals(body.sortable())))
            bad("多选字段不能设置唯一或排序");
    }

    private static byte flag(Boolean value) { return (byte) (Boolean.TRUE.equals(value) ? 1 : 0); }
    private static byte defaultOn(Boolean value) { return (byte) (Boolean.FALSE.equals(value) ? 0 : 1); }
    private static void code(String code) { if (code == null || !CODE.matcher(code).matches()) bad("编码格式错误"); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    /** 分组编码是长期业务标识，修改时保持原值。 */
    public record GroupForm(@NotBlank String code, @NotBlank String name, Long parentId, String icon, Integer sortNo) { }
    public record ModuleForm(@NotNull Long groupId, @NotBlank String code, @NotBlank String name,
                             String icon, String description, Integer sortNo) { }
    public record TenantStatus(boolean enabled, Integer sortNo) { }
    public record FieldForm(@NotBlank String fieldName, @NotBlank String name, @NotBlank String type,
                            String remark, String inputTips, Integer maxLength, String defaultValue,
                            Boolean required, Boolean unique, Boolean hidden, Boolean add, Boolean index,
                            Boolean detail, Boolean sortable, String dictCode, JsonNode optionData,
                            Byte precisionScale, java.math.BigDecimal minNumber, java.math.BigDecimal maxNumber) { }
    public record QueryFieldForm(String fieldName, List<String> operators) { }
    public record ColumnView(String fieldName, String title, String type, boolean sortable, int sortNo) { }
    public record DictForm(@NotBlank String dictCode, @NotBlank String itemCode, @NotBlank String itemName, Integer sortNo) { }
}
