package com.unique.unexamine.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.module.base.entity.UnModuleField;
import com.unique.unexamine.module.base.entity.UnModuleFieldSort;
import com.unique.unexamine.module.base.entity.UnModuleTenantField;
import com.unique.unexamine.module.base.mapper.UnModuleFieldMapper;
import com.unique.unexamine.module.base.mapper.UnModuleFieldSortMapper;
import com.unique.unexamine.module.base.mapper.UnModuleTenantFieldMapper;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;

/** 模块基础字段、租户字段和表单字段顺序。 */
@Tag(name = "模块字段与表单", description = "基础字段、租户字段和表单结构。")
@RestController
@RequestMapping("/api/modules")
public class ModuleFieldController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final UnModuleFieldMapper fields;
    private final UnModuleTenantFieldMapper tenantFields;
    private final UnModuleFieldSortMapper fieldSorts;
    private final ObjectMapper json;

    public ModuleFieldController(ModuleSupport support, UnModuleFieldMapper fields,
                                 UnModuleTenantFieldMapper tenantFields, UnModuleFieldSortMapper fieldSorts,
                                 ObjectMapper json) {
        this.support = support;
        this.fields = fields;
        this.tenantFields = tenantFields;
        this.fieldSorts = fieldSorts;
        this.json = json;
    }

    @Operation(summary = "查询字段（模块字段与表单）")
    @GetMapping("/{moduleId}/fields")
    public List<UnModuleField> fields(@RequestParam long tenantId, @PathVariable long moduleId, HttpServletRequest request) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
        return fields.selectList(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId, moduleId)
                .orderByAsc(UnModuleField::getId));
    }

    /** 新字段编码固定；存在记录的字段停用仍保留历史值。 */
    @Operation(summary = "新建字段（模块字段与表单）")
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

    @Operation(summary = "修改字段（模块字段与表单）")
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

    @Operation(summary = "停用字段（模块字段与表单）")
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

    @Operation(summary = "查询表单结构（模块字段与表单）")
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

    @Operation(summary = "保存字段顺序（模块字段与表单）")
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

    @Operation(summary = "查询租户字段（模块字段与表单）")
    @GetMapping("/{moduleId}/tenant-fields")
    public List<UnModuleTenantField> tenantFields(@RequestParam long tenantId, @PathVariable long moduleId,
                                                   HttpServletRequest request) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
        return tenantFields.selectList(new LambdaQueryWrapper<UnModuleTenantField>()
                .eq(UnModuleTenantField::getTenantId, tenantId).eq(UnModuleTenantField::getModuleId, moduleId));
    }

    @Operation(summary = "新建租户字段（模块字段与表单）")
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

    @Operation(summary = "修改租户字段（模块字段与表单）")
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
    @Operation(summary = "覆盖基础字段（模块字段与表单）")
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

    @Operation(summary = "停用租户字段（模块字段与表单）")
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

    public record FieldForm(@NotBlank String fieldName, @NotBlank String name, @NotBlank String type,
                            String remark, String inputTips, Integer maxLength, String defaultValue,
                            Boolean required, Boolean unique, Boolean hidden, Boolean add, Boolean index,
                            Boolean detail, Boolean sortable, String dictCode, JsonNode optionData,
                            Byte precisionScale, java.math.BigDecimal minNumber, java.math.BigDecimal maxNumber) { }
}
