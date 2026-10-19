package com.unique.unexamine.dashboard.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.dashboard.base.entity.UnDashboard;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponent;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponentField;
import com.unique.unexamine.dashboard.base.entity.UnDashboardUser;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardComponentFieldMapper;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardComponentMapper;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardMapper;
import com.unique.unexamine.dashboard.base.mapper.UnDashboardUserMapper;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMemberMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仪表盘和统计组件的配置、成员共享与实时数值读取。 */
@Tag(name = "仪表盘", description = "仪表盘、组件和共享成员。")
@RestController
@RequestMapping("/api/tenants/{tenantId}/dashboards")
public class DashboardController {
    private final SystemSessionAccess access;
    private final DataSourceController sources;
    private final ModuleMetric metrics;
    private final UnDashboardMapper dashboards;
    private final UnDashboardComponentMapper components;
    private final UnDashboardComponentFieldMapper fields;
    private final UnDashboardUserMapper users;
    private final UnSysTenantMemberMapper tenantMembers;
    private final ObjectMapper json;

    public DashboardController(SystemSessionAccess access, DataSourceController sources, ModuleMetric metrics,
                               UnDashboardMapper dashboards, UnDashboardComponentMapper components,
                               UnDashboardComponentFieldMapper fields, UnDashboardUserMapper users,
                               UnSysTenantMemberMapper tenantMembers, ObjectMapper json) {
        this.access = access;
        this.sources = sources;
        this.metrics = metrics;
        this.dashboards = dashboards;
        this.components = components;
        this.fields = fields;
        this.users = users;
        this.tenantMembers = tenantMembers;
        this.json = json;
    }

    /** 只展示自己创建或明确共享给自己的仪表盘。 */
    @Operation(summary = "查询列表（仪表盘）")
    @GetMapping
    public List<DashboardView> list(@PathVariable long tenantId, HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        List<Long> sharedIds = users.selectList(new LambdaQueryWrapper<UnDashboardUser>()
                .eq(UnDashboardUser::getMemberId, actorId)).stream().map(UnDashboardUser::getDashboardId).toList();
        LambdaQueryWrapper<UnDashboard> query = new LambdaQueryWrapper<UnDashboard>()
                .eq(UnDashboard::getTenantId, tenantId).eq(UnDashboard::getStatus, 1);
        if (sharedIds.isEmpty()) {
            query.eq(UnDashboard::getOwnerMemberId, actorId);
        } else {
            query.and(w -> w.eq(UnDashboard::getOwnerMemberId, actorId).or().in(UnDashboard::getId, sharedIds));
        }
        return dashboards.selectList(query.orderByDesc(UnDashboard::getCreateTime).orderByAsc(UnDashboard::getId))
                .stream().map(this::dashboardView).toList();
    }

    @Operation(summary = "新建（仪表盘）")
    @PostMapping
    public DashboardView create(@PathVariable long tenantId, @Valid @RequestBody DashboardInput input,
                                HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        UnDashboard dashboard = new UnDashboard();
        dashboard.setTenantId(tenantId);
        dashboard.setDashboardName(input.dashboardName().trim());
        dashboard.setDescription(input.description());
        dashboard.setOwnerMemberId(actorId);
        dashboard.setStatus((byte) 1);
        dashboards.insert(dashboard);
        return dashboardView(dashboard);
    }

    @Operation(summary = "查询详情（仪表盘）")
    @GetMapping("/{dashboardId}")
    public DashboardView detail(@PathVariable long tenantId, @PathVariable long dashboardId,
                                HttpServletRequest request) {
        return dashboardView(readable(tenantId, dashboardId, request));
    }

    @Operation(summary = "修改（仪表盘）")
    @PutMapping("/{dashboardId}")
    public DashboardView update(@PathVariable long tenantId, @PathVariable long dashboardId,
                                @Valid @RequestBody DashboardUpdate input, HttpServletRequest request) {
        UnDashboard dashboard = editable(tenantId, dashboardId, request);
        dashboard.setDashboardName(input.dashboardName().trim());
        dashboard.setDescription(input.description());
        dashboard.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        dashboards.updateById(dashboard);
        return dashboardView(dashboard);
    }

    @Operation(summary = "查询仪表盘组件（仪表盘）")
    @GetMapping("/{dashboardId}/components")
    public List<ComponentView> components(@PathVariable long tenantId, @PathVariable long dashboardId,
                                          HttpServletRequest request) {
        readable(tenantId, dashboardId, request);
        return components.selectList(new LambdaQueryWrapper<UnDashboardComponent>()
                        .eq(UnDashboardComponent::getDashboardId, dashboardId)
                        .orderByAsc(UnDashboardComponent::getSortNo, UnDashboardComponent::getId))
                .stream().map(this::componentView).toList();
    }

    /** 当前组件类型 metric 输出字段统计；其他图形能力待有对应数据形状时再开放。 */
    @Operation(summary = "新建仪表盘组件（仪表盘）")
    @PostMapping("/{dashboardId}/components")
    public ComponentView addComponent(@PathVariable long tenantId, @PathVariable long dashboardId,
                                      @Valid @RequestBody ComponentInput input, HttpServletRequest request) {
        editable(tenantId, dashboardId, request);
        if (!"metric".equals(input.componentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持数值指标组件");
        }
        sources.resolveModule(tenantId, input.dataSourceId());
        UnDashboardComponent component = new UnDashboardComponent();
        component.setDashboardId(dashboardId);
        component.setComponentType(input.componentType());
        component.setTitle(input.title().trim());
        component.setDataSourceId(input.dataSourceId());
        component.setConfigData(stringify(input.configData()));
        component.setLayoutData(stringify(input.layoutData()));
        component.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        components.insert(component);
        return componentView(component);
    }

    @Operation(summary = "修改组件（仪表盘）")
    @PutMapping("/{dashboardId}/components/{componentId}")
    public ComponentView updateComponent(@PathVariable long tenantId, @PathVariable long dashboardId,
                                         @PathVariable long componentId,
                                         @Valid @RequestBody ComponentInput input, HttpServletRequest request) {
        editable(tenantId, dashboardId, request);
        UnDashboardComponent component = requireComponent(dashboardId, componentId);
        if (!"metric".equals(input.componentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持数值指标组件");
        }
        sources.resolveModule(tenantId, input.dataSourceId());
        component.setComponentType(input.componentType());
        component.setTitle(input.title().trim());
        component.setDataSourceId(input.dataSourceId());
        component.setConfigData(stringify(input.configData()));
        component.setLayoutData(stringify(input.layoutData()));
        component.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        components.updateById(component);
        return componentView(component);
    }

    @Operation(summary = "新建组件字段（仪表盘）")
    @PostMapping("/{dashboardId}/components/{componentId}/fields")
    public FieldView addField(@PathVariable long tenantId, @PathVariable long dashboardId,
                              @PathVariable long componentId, @Valid @RequestBody FieldInput input,
                              HttpServletRequest request) {
        editable(tenantId, dashboardId, request);
        UnDashboardComponent component = requireComponent(dashboardId, componentId);
        long moduleId = sources.resolveModule(tenantId, component.getDataSourceId());
        metrics.value(tenantId, moduleId, input.fieldKey(), input.aggregation());
        UnDashboardComponentField field = new UnDashboardComponentField();
        field.setComponentId(componentId);
        field.setFieldKey(input.fieldKey());
        field.setFieldLabel(input.fieldLabel().trim());
        field.setAggregation(input.aggregation());
        field.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        fields.insert(field);
        return fieldView(field);
    }

    @Operation(summary = "查询字段（仪表盘）")
    @GetMapping("/{dashboardId}/components/{componentId}/fields")
    public List<FieldView> fields(@PathVariable long tenantId, @PathVariable long dashboardId,
                                  @PathVariable long componentId, HttpServletRequest request) {
        readable(tenantId, dashboardId, request);
        requireComponent(dashboardId, componentId);
        return fields.selectList(new LambdaQueryWrapper<UnDashboardComponentField>()
                        .eq(UnDashboardComponentField::getComponentId, componentId)
                        .orderByAsc(UnDashboardComponentField::getSortNo, UnDashboardComponentField::getId))
                .stream().map(this::fieldView).toList();
    }

    /** 实际指标按最新模块记录计算，不把配置 JSON 当成已经计算的图表。 */
    @Operation(summary = "查询组件数据（仪表盘）")
    @GetMapping("/{dashboardId}/components/{componentId}/data")
    public Map<String, BigDecimal> componentData(@PathVariable long tenantId, @PathVariable long dashboardId,
                                                  @PathVariable long componentId, HttpServletRequest request) {
        readable(tenantId, dashboardId, request);
        UnDashboardComponent component = requireComponent(dashboardId, componentId);
        long moduleId = sources.resolveModule(tenantId, component.getDataSourceId());
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (UnDashboardComponentField field : fields.selectList(new LambdaQueryWrapper<UnDashboardComponentField>()
                .eq(UnDashboardComponentField::getComponentId, componentId)
                .orderByAsc(UnDashboardComponentField::getSortNo, UnDashboardComponentField::getId))) {
            result.put(field.getFieldKey(), metrics.value(tenantId, moduleId,
                    field.getFieldKey(), field.getAggregation(), request));
        }
        return result;
    }

    /** 分享前验证目标人员在同一租户内，并保存查看或编辑权限。 */
    @Operation(summary = "共享仪表盘（仪表盘）")
    @PostMapping("/{dashboardId}/users")
    public UserView share(@PathVariable long tenantId, @PathVariable long dashboardId,
                          @Valid @RequestBody UserInput input, HttpServletRequest request) {
        editable(tenantId, dashboardId, request);
        if (tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, input.memberId())
                .eq(UnSysTenantMember::getStatus, 1)) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "共享对象不是有效租户成员");
        }
        if (!List.of("view", "edit").contains(input.permission())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "共享权限无效");
        }
        UnDashboardUser existing = users.selectOne(new LambdaQueryWrapper<UnDashboardUser>()
                .eq(UnDashboardUser::getDashboardId, dashboardId)
                .eq(UnDashboardUser::getMemberId, input.memberId()));
        if (existing == null) {
            existing = new UnDashboardUser();
            existing.setDashboardId(dashboardId);
            existing.setMemberId(input.memberId());
            existing.setPermissionCode(input.permission());
            users.insert(existing);
        } else {
            existing.setPermissionCode(input.permission());
            users.updateById(existing);
        }
        return new UserView(existing.getId(), existing.getMemberId(), existing.getPermissionCode());
    }

    @Operation(summary = "查询共享成员（仪表盘）")
    @GetMapping("/{dashboardId}/users")
    public List<UserView> users(@PathVariable long tenantId, @PathVariable long dashboardId,
                                HttpServletRequest request) {
        editable(tenantId, dashboardId, request);
        return users.selectList(new LambdaQueryWrapper<UnDashboardUser>()
                        .eq(UnDashboardUser::getDashboardId, dashboardId).orderByAsc(UnDashboardUser::getId))
                .stream().map(item -> new UserView(item.getId(), item.getMemberId(), item.getPermissionCode())).toList();
    }

    private UnDashboard readable(long tenantId, long dashboardId, HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        UnDashboard dashboard = dashboards.selectById(dashboardId);
        if (dashboard == null || !dashboard.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "仪表盘不存在");
        }
        if (!dashboard.getOwnerMemberId().equals(actorId) && users.selectCount(new LambdaQueryWrapper<UnDashboardUser>()
                .eq(UnDashboardUser::getDashboardId, dashboardId).eq(UnDashboardUser::getMemberId, actorId)) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有仪表盘访问权限");
        }
        return dashboard;
    }

    private UnDashboard editable(long tenantId, long dashboardId, HttpServletRequest request) {
        UnDashboard dashboard = readable(tenantId, dashboardId, request);
        long actorId = access.systemMember(request).getId();
        if (!dashboard.getOwnerMemberId().equals(actorId) && users.selectCount(new LambdaQueryWrapper<UnDashboardUser>()
                .eq(UnDashboardUser::getDashboardId, dashboardId)
                .eq(UnDashboardUser::getMemberId, actorId)
                .eq(UnDashboardUser::getPermissionCode, "edit")) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有仪表盘编辑权限");
        }
        return dashboard;
    }

    private UnDashboardComponent requireComponent(long dashboardId, long componentId) {
        UnDashboardComponent component = components.selectById(componentId);
        if (component == null || !component.getDashboardId().equals(dashboardId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "仪表组件不存在");
        }
        return component;
    }

    private String stringify(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "组件配置不是有效 JSON");
        }
    }

    private DashboardView dashboardView(UnDashboard item) {
        return new DashboardView(item.getId(), item.getDashboardName(), item.getDescription(),
                item.getOwnerMemberId(), item.getStatus());
    }

    private ComponentView componentView(UnDashboardComponent item) {
        return new ComponentView(item.getId(), item.getComponentType(), item.getTitle(), item.getDataSourceId(),
                item.getConfigData(), item.getLayoutData(), item.getSortNo());
    }

    private FieldView fieldView(UnDashboardComponentField item) {
        return new FieldView(item.getId(), item.getFieldKey(), item.getFieldLabel(),
                item.getAggregation(), item.getSortNo());
    }

    public record DashboardInput(@NotBlank @Size(max = 120) String dashboardName,
                                 @Size(max = 500) String description) { }
    public record DashboardUpdate(@NotBlank @Size(max = 120) String dashboardName,
                                  @Size(max = 500) String description, boolean enabled) { }
    public record DashboardView(Long id, String dashboardName, String description,
                                Long ownerMemberId, Byte status) { }
    public record ComponentInput(@NotBlank String componentType, @NotBlank @Size(max = 120) String title,
                                 long dataSourceId, Map<String, Object> configData,
                                 Map<String, Object> layoutData, Integer sortNo) { }
    public record ComponentView(Long id, String componentType, String title, Long dataSourceId,
                                String configJson, String layoutJson, Integer sortNo) { }
    public record FieldInput(@NotBlank String fieldKey, @NotBlank @Size(max = 100) String fieldLabel,
                             @NotBlank String aggregation, Integer sortNo) { }
    public record FieldView(Long id, String fieldKey, String fieldLabel, String aggregation, Integer sortNo) { }
    public record UserInput(long memberId, @NotBlank String permission) { }
    public record UserView(Long id, Long memberId, String permission) { }
}
