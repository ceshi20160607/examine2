package com.unique.unexamine.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.module.base.entity.UnModuleQueryField;
import com.unique.unexamine.module.base.entity.UnModuleScence;
import com.unique.unexamine.module.base.entity.UnModuleScenceConfig;
import com.unique.unexamine.module.base.service.IUnModuleQueryFieldService;
import com.unique.unexamine.module.base.service.IUnModuleScenceConfigService;
import com.unique.unexamine.module.base.service.IUnModuleScenceService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 模块场景及其列表表头、筛选和排序配置。 */
@Tag(name = "模块场景", description = "模块场景及场景配置。")
@RestController
@RequestMapping("/api/modules/{moduleId}/scenes")
public class ModuleSceneController {
    private final ModuleSupport support;
    private final IUnModuleScenceService scenes;
    private final IUnModuleScenceConfigService configs;
    private final IUnModuleQueryFieldService queryFields;
    private final ObjectMapper json;

    public ModuleSceneController(ModuleSupport support, IUnModuleScenceService scenes,
                                 IUnModuleScenceConfigService configs, IUnModuleQueryFieldService queryFields,
                                 ObjectMapper json) {
        this.support = support;
        this.scenes = scenes;
        this.configs = configs;
        this.queryFields = queryFields;
        this.json = json;
    }

    /** 个人场景只向本人返回，共享场景只在当前租户内返回。 */
    @Operation(summary = "查询列表（模块场景）")
    @GetMapping
    public List<SceneView> list(@PathVariable long moduleId, @RequestParam long tenantId, HttpServletRequest request) {
        long memberId = support.member(request, tenantId).getMemberId();
        support.module(tenantId, moduleId);
        List<SceneView> result = new ArrayList<>();
        for (UnModuleScence row : scenes.list(new LambdaQueryWrapper<UnModuleScence>()
                .eq(UnModuleScence::getTenantId, tenantId).eq(UnModuleScence::getModuleId, moduleId)
                .eq(UnModuleScence::getStatus, 1).orderByDesc(UnModuleScence::getDefaultFlag)
                .orderByAsc(UnModuleScence::getId))) {
            if (visible(row, memberId)) result.add(view(row));
        }
        return result;
    }

    @Operation(summary = "查询详情（模块场景）")
    @GetMapping("/{sceneId}")
    public SceneView detail(@PathVariable long moduleId, @PathVariable long sceneId, @RequestParam long tenantId,
                            HttpServletRequest request) {
        long memberId = support.member(request, tenantId).getMemberId();
        support.module(tenantId, moduleId);
        UnModuleScence row = scene(tenantId, moduleId, sceneId);
        if (!visible(row, memberId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看个人场景");
        return view(row);
    }

    /** 创建场景时同时校验字段并保存其唯一配置行。 */
    @Operation(summary = "新建（模块场景）")
    @PostMapping
    @Transactional
    public SceneView create(@PathVariable long moduleId, @RequestParam long tenantId,
                            @Valid @RequestBody SceneForm body, HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        support.module(tenantId, moduleId);
        validate(body, tenantId, moduleId);
        sharedWrite(request, tenantId, body.visibility());
        UnModuleScence row = new UnModuleScence();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setOwnerMemberId("personal".equals(body.visibility()) ? member.getMemberId() : null);
        row.setName(body.name());
        row.setVisibility(body.visibility());
        row.setDefaultFlag((byte) 0);
        row.setStatus((byte) 1);
        scenes.save(row);
        UnModuleScenceConfig config = new UnModuleScenceConfig();
        config.setScenceId(row.getId());
        fill(config, body);
        configs.save(config);
        if (Boolean.TRUE.equals(body.defaultFlag())) setDefault(row);
        return view(scenes.getById(row.getId()));
    }

    @Operation(summary = "修改（模块场景）")
    @PutMapping("/{sceneId}")
    @Transactional
    public SceneView update(@PathVariable long moduleId, @PathVariable long sceneId, @RequestParam long tenantId,
                            @Valid @RequestBody SceneForm body, HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleScence row = scene(tenantId, moduleId, sceneId);
        writable(row, member.getMemberId(), request, tenantId);
        if (!row.getVisibility().equals(body.visibility())) bad("场景可见范围不可直接改变，请新建场景");
        validate(body, tenantId, moduleId);
        row.setName(body.name());
        scenes.updateById(row);
        UnModuleScenceConfig config = config(sceneId);
        fill(config, body);
        configs.updateById(config);
        if (Boolean.TRUE.equals(body.defaultFlag())) setDefault(row);
        return view(scenes.getById(sceneId));
    }

    @Operation(summary = "设置默认场景（模块场景）")
    @PutMapping("/{sceneId}/default")
    @Transactional
    public SceneView makeDefault(@PathVariable long moduleId, @PathVariable long sceneId, @RequestParam long tenantId,
                                 HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleScence row = scene(tenantId, moduleId, sceneId);
        writable(row, member.getMemberId(), request, tenantId);
        setDefault(row);
        return view(scenes.getById(sceneId));
    }

    @Operation(summary = "删除（模块场景）")
    @DeleteMapping("/{sceneId}")
    public void delete(@PathVariable long moduleId, @PathVariable long sceneId, @RequestParam long tenantId,
                       HttpServletRequest request) {
        UnSysTenantMember member = support.member(request, tenantId);
        support.module(tenantId, moduleId);
        UnModuleScence row = scene(tenantId, moduleId, sceneId);
        writable(row, member.getMemberId(), request, tenantId);
        if (ModuleSupport.enabled(row.getDefaultFlag())) bad("请先指定其他默认场景");
        row.setStatus((byte) 0);
        scenes.updateById(row);
    }

    private void validate(SceneForm body, long tenantId, long moduleId) {
        if (!Set.of("personal", "tenant", "public").contains(body.visibility())) bad("场景可见范围无效");
        if (body.pageSize() != null && (body.pageSize() < 1 || body.pageSize() > 100)) bad("每页条数无效");
        if (body.columns() == null || body.columns().isEmpty()) bad("场景至少需要一个表头字段");
        Map<String, ModuleSupport.FieldSpec> fields = support.effectiveFields(tenantId, moduleId);
        Map<String, Set<String>> query = new HashMap<>();
        for (UnModuleQueryField row : queryFields.list(new LambdaQueryWrapper<UnModuleQueryField>()
                .eq(UnModuleQueryField::getModuleId, moduleId).eq(UnModuleQueryField::getStatus, 1))) {
            JsonNode operators = readJson(row.getOperatorsJson());
            Set<String> allowed = new HashSet<>();
            if (operators != null && operators.isArray()) for (JsonNode op : operators) allowed.add(op.asText());
            query.put(row.getFieldName(), allowed);
        }
        Set<String> seen = new HashSet<>();
        for (ColumnConfig column : body.columns()) {
            ModuleSupport.FieldSpec field = fields.get(column.fieldName());
            if (field == null || !field.index() || field.hidden() || !seen.add(column.fieldName())) bad("场景表头包含无效或重复字段");
            if (column.width() != null && (column.width() < 40 || column.width() > 1000)) bad("表头宽度无效");
        }
        if (body.filters() != null) {
            if (body.filters().size() > 10) bad("筛选项过多");
            for (FilterConfig filter : body.filters()) {
                ModuleSupport.FieldSpec field = fields.get(filter.fieldName());
                if (field == null || !query.getOrDefault(filter.fieldName(), Set.of()).contains(filter.operator())
                        || filter.value() == null || filter.value().isNull()) bad("场景筛选字段或操作符无效");
                if ("contains".equals(filter.operator()) && !Set.of("text", "textarea", "select").contains(field.type()))
                    bad("此字段不能模糊查询");
                if ("multiselect".equals(field.type())) bad("集合字段暂不支持列表筛选");
                if ("in".equals(filter.operator()) && (!filter.value().isArray()
                        || filter.value().isEmpty() || filter.value().size() > 50)) bad("IN 筛选须提供 1 至 50 个值");
                Iterable<JsonNode> values = "in".equals(filter.operator()) ? filter.value() : List.of(filter.value());
                for (JsonNode value : values) {
                    try {
                        switch (field.type()) {
                            case "number", "integer" -> new BigDecimal(value.asText());
                            case "date" -> LocalDate.parse(value.asText());
                            case "datetime" -> LocalDateTime.parse(value.asText());
                            case "boolean" -> { if (!value.isBoolean()) bad("布尔筛选值无效"); }
                            default -> { if (!value.isTextual()) bad("文本筛选值无效"); }
                        }
                    } catch (RuntimeException e) {
                        bad("场景筛选值与字段类型不匹配");
                    }
                }
            }
        }
        if (body.sort() != null) {
            ModuleSupport.FieldSpec field = fields.get(body.sort().fieldName());
            if (field == null || !field.sortable() || !query.containsKey(body.sort().fieldName())
                    || !Set.of("asc", "desc").contains(body.sort().order())) bad("场景排序字段或方向无效");
        }
    }

    private void sharedWrite(HttpServletRequest request, long tenantId, String visibility) {
        if (!"personal".equals(visibility)) support.admin(request, tenantId);
    }

    private void writable(UnModuleScence row, long memberId, HttpServletRequest request, long tenantId) {
        if ("personal".equals(row.getVisibility())) {
            if (!Objects.equals(row.getOwnerMemberId(), memberId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能修改自己的个人场景");
        } else support.admin(request, tenantId);
    }

    private boolean visible(UnModuleScence row, long memberId) {
        return !"personal".equals(row.getVisibility()) || Objects.equals(row.getOwnerMemberId(), memberId);
    }

    private UnModuleScence scene(long tenantId, long moduleId, long sceneId) {
        UnModuleScence row = scenes.getById(sceneId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || !ModuleSupport.enabled(row.getStatus())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "场景不存在");
        return row;
    }

    private UnModuleScenceConfig config(long sceneId) {
        UnModuleScenceConfig row = configs.getOne(new LambdaQueryWrapper<UnModuleScenceConfig>()
                .eq(UnModuleScenceConfig::getScenceId, sceneId));
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "场景配置不存在");
        return row;
    }

    private void fill(UnModuleScenceConfig config, SceneForm body) {
        config.setColumnsJson(writeJson(body.columns()));
        config.setFiltersJson(writeJson(body.filters() == null ? List.of() : body.filters()));
        config.setSortJson(body.sort() == null ? null : writeJson(body.sort()));
        config.setPageSize((short) (body.pageSize() == null ? 20 : body.pageSize()));
    }

    /** 默认场景在同一租户、模块和个人/共享范围内唯一。 */
    private void setDefault(UnModuleScence selected) {
        for (UnModuleScence row : scenes.list(new LambdaQueryWrapper<UnModuleScence>()
                .eq(UnModuleScence::getTenantId, selected.getTenantId()).eq(UnModuleScence::getModuleId, selected.getModuleId())
                .eq(UnModuleScence::getVisibility, selected.getVisibility()).eq(UnModuleScence::getStatus, 1))) {
            if ("personal".equals(selected.getVisibility()) && !Objects.equals(row.getOwnerMemberId(), selected.getOwnerMemberId())) continue;
            byte next = (byte) (row.getId().equals(selected.getId()) ? 1 : 0);
            if (row.getDefaultFlag() == null || row.getDefaultFlag() != next) {
                row.setDefaultFlag(next);
                scenes.updateById(row);
            }
        }
    }

    private SceneView view(UnModuleScence row) {
        UnModuleScenceConfig config = config(row.getId());
        return new SceneView(row, readJson(config.getColumnsJson()), readJson(config.getFiltersJson()),
                readJson(config.getSortJson()), config.getPageSize());
    }

    private JsonNode readJson(String value) {
        if (value == null) return null;
        try { return json.readTree(value); }
        catch (Exception e) { bad("场景配置 JSON 无效"); return null; }
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { bad("场景配置 JSON 无效"); return null; }
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    /** 字段编码与排序配置只引用当前租户有效的字段。 */
    public record SceneForm(@NotBlank String name, @NotBlank String visibility, Boolean defaultFlag,
                            List<ColumnConfig> columns, List<FilterConfig> filters, SortConfig sort, Integer pageSize) { }
    public record ColumnConfig(String fieldName, Boolean visible, Integer width) { }
    public record FilterConfig(String fieldName, String operator, JsonNode value) { }
    public record SortConfig(String fieldName, String order) { }
    public record SceneView(UnModuleScence scene, JsonNode columns, JsonNode filters, JsonNode sort, Short pageSize) { }
}
