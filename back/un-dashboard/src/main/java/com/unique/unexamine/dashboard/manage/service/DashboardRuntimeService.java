package com.unique.unexamine.dashboard.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.dashboard.base.entity.UnDashboard;
import com.unique.unexamine.dashboard.base.entity.UnDashboardComponent;
import com.unique.unexamine.dashboard.base.entity.UnDashboardUser;
import com.unique.unexamine.dashboard.base.entity.UnDataSource;
import com.unique.unexamine.dashboard.base.entity.UnDataSourceConfig;
import com.unique.unexamine.dashboard.base.service.IUnDashboardComponentService;
import com.unique.unexamine.dashboard.base.service.IUnDashboardService;
import com.unique.unexamine.dashboard.base.service.IUnDashboardUserService;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceConfigService;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceService;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.ColumnView;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.ComponentView;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.DashboardView;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.ModuleDataView;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.RowView;
import com.unique.unexamine.sys.base.entity.UnSysMemberRole;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.service.IUnSysMemberRoleService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordListService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 仪表盘运行查询服务。
 *
 * <p>运行态只读取已发布的仪表盘、组件和数据源快照。模块组件继续调用
 * {@link ModuleRecordListService}，因此模块动作权限、字段权限、租户范围和逻辑删除过滤仍由模块域统一执行。</p>
 */
@Service
public class DashboardRuntimeService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final int TARGET_ID_BATCH_SIZE = 500;

    private final IUnDashboardService dashboards;
    private final IUnDashboardComponentService components;
    private final IUnDashboardUserService dashboardUsers;
    private final IUnDataSourceService dataSources;
    private final IUnDataSourceConfigService dataSourceConfigs;
    private final IUnSysMemberRoleService memberRoles;
    private final IUnSysRoleService roles;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnModuleRelationConfigService relationConfigs;
    private final IUnModuleRelationService relations;
    private final IUnModuleService modules;
    private final SystemManageService systems;
    private final ModuleConfigService moduleConfig;
    private final ModuleRecordListService moduleRecords;
    private final ObjectMapper json;

    /** 注入仪表盘基础服务及模块列表运行入口。 */
    public DashboardRuntimeService(IUnDashboardService dashboards,
            IUnDashboardComponentService components, IUnDashboardUserService dashboardUsers,
            IUnDataSourceService dataSources, IUnDataSourceConfigService dataSourceConfigs,
            IUnSysMemberRoleService memberRoles, IUnSysRoleService roles,
            IUnSysTenantMemberService tenantMembers,
            IUnModuleRelationConfigService relationConfigs, IUnModuleRelationService relations,
            IUnModuleService modules,
            SystemManageService systems, ModuleConfigService moduleConfig,
            ModuleRecordListService moduleRecords, ObjectMapper json) {
        this.dashboards = dashboards;
        this.components = components;
        this.dashboardUsers = dashboardUsers;
        this.dataSources = dataSources;
        this.dataSourceConfigs = dataSourceConfigs;
        this.memberRoles = memberRoles;
        this.roles = roles;
        this.tenantMembers = tenantMembers;
        this.relationConfigs = relationConfigs;
        this.relations = relations;
        this.modules = modules;
        this.systems = systems;
        this.moduleConfig = moduleConfig;
        this.moduleRecords = moduleRecords;
        this.json = json;
    }

    /** 查询一个已发布仪表盘；未发布组件不会进入返回结果。 */
    public DashboardView query(String token, Long systemId, Long tenantId, Long dashboardId) {
        if (dashboardId == null || dashboardId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "仪表盘主键无效");
        }
        SystemContext context = systems.context(token, systemId, tenantId);
        UnDashboard dashboard = dashboards.queryById(dashboardId);
        if (dashboard == null || !systemId.equals(dashboard.getSystemId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "仪表盘不存在");
        }
        if (!PUBLISHED.equals(dashboard.getStatus())
                || dashboard.getPublishedLayoutJson() == null
                || dashboard.getPublishedVersion() == null
                || dashboard.getPublishedVersion() < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "仪表盘尚未发布");
        }
        checkScope(dashboard, context);
        Map<String, Object> layout = objectMap(dashboard.getPublishedLayoutJson(), "仪表盘布局配置");
        List<UnDashboardComponent> rows = components.list(new LambdaQueryWrapper<UnDashboardComponent>()
                .eq(UnDashboardComponent::getDashboardId, dashboardId)
                .eq(UnDashboardComponent::getStatus, PUBLISHED)
                .orderByAsc(UnDashboardComponent::getSortNo)
                .orderByAsc(UnDashboardComponent::getId));
        List<ComponentView> views = rows.stream().map(row -> component(token, context, row)).toList();
        return new DashboardView(dashboard.getId(), dashboard.getCode(), dashboard.getName(),
                dashboard.getScopeType(), dashboard.getPublishedVersion(), layout, views);
    }

    /** 校验系统、租户、个人和额外授权范围，避免只靠前端隐藏仪表盘。 */
    private void checkScope(UnDashboard dashboard, SystemContext context) {
        if (!canAccess(dashboard, context))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "没有当前仪表盘的访问权限");
    }

    /** 列表与运行入口共用访问判定，防止列表漏掉已授权仪表盘或暴露未授权配置。 */
    public boolean canAccess(UnDashboard dashboard, SystemContext context) {
        if (dashboard == null || !context.systemId().equals(dashboard.getSystemId())
                || dashboard.getTenantId() != null && !dashboard.getTenantId().equals(context.tenantId()))
            return false;
        Long accountId = context.accountId();
        if ("PERSONAL".equalsIgnoreCase(dashboard.getScopeType())
                && !accountId.equals(dashboard.getOwnerAccountId())) return false;
        List<UnDashboardUser> rules = dashboardUsers.list(new LambdaQueryWrapper<UnDashboardUser>()
                .eq(UnDashboardUser::getDashboardId, dashboard.getId()));
        return rules.isEmpty() || rules.stream().anyMatch(rule -> matches(rule, context, accountId));
    }

    /** 访问范围支持账号、系统成员、当前租户部门和当前租户角色。 */
    private boolean matches(UnDashboardUser rule, SystemContext context, Long accountId) {
        String type = rule.getSubjectType() == null ? "" : rule.getSubjectType().toUpperCase();
        if ("ACCOUNT".equals(type)) return accountId.equals(rule.getSubjectId());
        if ("MEMBER".equals(type)) return context.memberId().equals(rule.getSubjectId());
        if ("TENANT_MEMBER".equals(type)) return context.tenantMemberId().equals(rule.getSubjectId());
        if ("DEPARTMENT".equals(type)) {
            var tenantMember = tenantMembers.queryById(context.tenantMemberId());
            return tenantMember != null && tenantMember.getDepartmentId() != null
                    && tenantMember.getDepartmentId().equals(rule.getSubjectId());
        }
        if ("ROLE".equals(type)) {
            UnSysRole role = roles.queryById(rule.getSubjectId());
            return role != null && context.systemId().equals(role.getSystemId())
                    && context.tenantId().equals(role.getTenantId()) && "ACTIVE".equals(role.getStatus())
                    && memberRoles.count(new LambdaQueryWrapper<UnSysMemberRole>()
                    .eq(UnSysMemberRole::getTenantMemberId, context.tenantMemberId())
                    .eq(UnSysMemberRole::getRoleId, rule.getSubjectId())) > 0;
        }
        return false;
    }

    /** 组件优先使用直接模块；数据源组件读取已发布根模块配置。 */
    private ComponentView component(String token, SystemContext context, UnDashboardComponent row) {
        Map<String, Object> config = objectMap(row.getPublishedConfigJson(), "组件配置");
        Long moduleId = row.getModuleId();
        ModuleRecordSearchBO search = search(config);
        String dataStatus = "NO_DATA_SOURCE";
        if (moduleId != null) {
            ModuleDataView data = moduleData(token, context, moduleId, search);
            return view(row, config, "OK", data);
        }
        if (row.getDataSourceId() != null) {
            DataSourceRuntime source = dataSource(row.getDataSourceId(), context);
            moduleId = source.moduleId();
            if (moduleId == null) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源未配置起始模块");
            }
            ModuleDataView data = source.steps().isEmpty()
                    ? moduleData(token, context, moduleId, mergeSearch(source.search(), search))
                    : relationData(token, context, source, mergeSearch(source.search(), search));
            return view(row, config, "OK", data);
        }
        return view(row, config, dataStatus, null);
    }

    /** KPI、报表等同域能力复用已授权的直接模块列表查询。 */
    public ModuleDataView queryModule(String token, SystemContext context, Long moduleId,
                                      ModuleRecordSearchBO search) {
        if (moduleId == null || moduleId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模块主键无效");
        }
        return moduleData(token, context, moduleId, search);
    }

    /** KPI、报表等同域能力复用已发布数据源；关联路径由统一数据源执行器负责。 */
    public ModuleDataView queryDataSource(String token, SystemContext context, Long dataSourceId,
                                           ModuleRecordSearchBO search) {
        if (dataSourceId == null || dataSourceId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源主键无效");
        }
        DataSourceRuntime source = dataSource(dataSourceId, context);
        if (source.moduleId() == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源未配置起始模块");
        }
        ModuleRecordSearchBO effective = mergeSearch(source.search(), search);
        return source.steps().isEmpty() ? moduleData(token, context, source.moduleId(), effective)
                : relationData(token, context, source, effective);
    }

    /** 数据源固定条件与调用方本次条件求交集，分页和展示字段由调用方追加。 */
    private ModuleRecordSearchBO mergeSearch(ModuleRecordSearchBO fixed, ModuleRecordSearchBO requested) {
        ModuleRecordSearchBO result = copySearch(fixed);
        if (requested == null) return result;
        if (requested.getPage() != null) result.setPage(requested.getPage());
        if (requested.getLimit() != null) result.setLimit(requested.getLimit());
        // 发布条件和本次条件取交集；默认 ALL 不能覆盖数据源预先限定的范围。
        String fixedScope = result.getScope() == null ? "ALL" : result.getScope();
        String requestedScope = requested.getScope() == null ? "ALL" : requested.getScope();
        if (!"ALL".equals(fixedScope) && !"ALL".equals(requestedScope)
                && !fixedScope.equals(requestedScope)) result.setRecordIds(List.of());
        else result.setScope("ALL".equals(fixedScope) ? requestedScope : fixedScope);
        if (requested.getKeyword() != null && !requested.getKeyword().isBlank()) {
            if (result.getKeyword() == null || result.getKeyword().isBlank())
                result.setKeyword(requested.getKeyword());
            else if (!result.getKeyword().equals(requested.getKeyword()))
                result.setAdditionalKeyword(requested.getKeyword());
        }
        if (requested.getSceneId() != null) {
            if (result.getSceneId() == null) result.setSceneId(requested.getSceneId());
            else if (!result.getSceneId().equals(requested.getSceneId()))
                result.setAdditionalSceneId(requested.getSceneId());
        }
        if (requested.getSortFieldId() != null) result.setSortFieldId(requested.getSortFieldId());
        if (requested.getDescending() != null) result.setDescending(requested.getDescending());
        if (requested.getFilters() != null && !requested.getFilters().isEmpty()) {
            List<com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto.Condition> filters =
                    new ArrayList<>(result.getFilters() == null ? List.of() : result.getFilters());
            filters.addAll(requested.getFilters());
            result.setFilters(filters);
        }
        if (requested.getColumnFieldIds() != null && !requested.getColumnFieldIds().isEmpty()) {
            List<Long> fields = new ArrayList<>(result.getColumnFieldIds() == null
                    ? List.of() : result.getColumnFieldIds());
            for (Long fieldId : requested.getColumnFieldIds()) if (!fields.contains(fieldId)) fields.add(fieldId);
            result.setColumnFieldIds(fields);
        }
        if (requested.getRecordIds() != null) {
            List<Long> fixedIds = result.getRecordIds();
            Set<Long> requestedIds = new java.util.HashSet<>(requested.getRecordIds());
            result.setRecordIds(fixedIds == null ? new ArrayList<>(requested.getRecordIds())
                    : fixedIds.stream().filter(requestedIds::contains).toList());
        }
        return result;
    }

    /** 数据源发布快照会被多个组件和 KPI 复用，合并请求必须复制列表避免跨请求状态污染。 */
    private ModuleRecordSearchBO copySearch(ModuleRecordSearchBO source) {
        ModuleRecordSearchBO result = new ModuleRecordSearchBO();
        if (source == null) return result;
        result.setPage(source.getPage());
        result.setLimit(source.getLimit());
        result.setKeyword(source.getKeyword());
        result.setScope(source.getScope());
        result.setSceneId(source.getSceneId());
        result.setSortFieldId(source.getSortFieldId());
        result.setDescending(source.getDescending());
        result.setFilters(source.getFilters() == null ? null : new ArrayList<>(source.getFilters()));
        result.setColumnFieldIds(source.getColumnFieldIds() == null ? null : new ArrayList<>(source.getColumnFieldIds()));
        result.setRecordIds(source.getRecordIds() == null ? null : new ArrayList<>(source.getRecordIds()));
        result.setAdditionalKeyword(source.getAdditionalKeyword());
        result.setAdditionalSceneId(source.getAdditionalSceneId());
        return result;
    }

    /** 查询模块时复用模块域的授权、动态字段和逻辑删除过滤。 */
    private ModuleDataView moduleData(String token, SystemContext context, Long moduleId,
                                      ModuleRecordSearchBO search) {
        // 已发布看板或 KPI 可能仍引用被逻辑删除的模块；该来源不再贡献业务数据，其他组件继续运行。
        if (modules.getById(moduleId) == null) {
            ModuleRecordSearchBO criteria = search == null ? new ModuleRecordSearchBO() : search;
            Page<RowView> empty = criteria.parse();
            return new ModuleDataView(List.of(), empty);
        }
        ModuleRecordListDto.ListView source = moduleRecords.query(token, context.systemId(),
                context.tenantId(), moduleId, search == null ? new ModuleRecordSearchBO() : search);
        Page<RowView> page = new Page<>(source.page().getCurrent(), source.page().getSize(), source.page().getTotal());
        List<RowView> rows = source.page().getRecords().stream().map(row -> new RowView(row.id(), row.name(),
                row.status(), row.ownerAccountId(), row.sourceTenantId(), row.shared(), row.values(),
                row.valueDisplay())).toList();
        page.setRecords(rows);
        List<ColumnView> columns = source.columns().stream()
                .map(column -> new ColumnView(column.fieldId(), column.code(), column.name(), column.type(),
                        column.widthPx(), column.fixedSide())).toList();
        return new ModuleDataView(columns, page);
    }

    /**
     * 按已发布关联配置逐层读取根记录的多级关联数据。
     * 关联记录本身仍由模块域保存，目标记录再次经过目标模块列表权限和逻辑删除过滤。
     */
    private ModuleDataView relationData(String token, SystemContext context, DataSourceRuntime source,
                                        ModuleRecordSearchBO search) {
        ModuleDataView root = moduleData(token, context, source.moduleId(), search);
        if (root.page().getRecords().isEmpty()) return root;
        List<Long> rootIds = root.page().getRecords().stream().map(RowView::id).toList();
        List<RowView> rows = new ArrayList<>(root.page().getRecords());
        List<ColumnView> columns = new ArrayList<>(root.columns());
        // 每一层关系的来源记录来自上一层目标记录，不能始终使用根模块记录主键。
        Map<Long, List<Long>> sourceIdsByRoot = new LinkedHashMap<>();
        for (Long rootId : rootIds) sourceIdsByRoot.put(rootId, List.of(rootId));
        Long sourceModuleId = source.moduleId();
        for (RelationStep step : source.steps()) {
            if (!java.util.Objects.equals(sourceModuleId, step.config().getSourceModuleId())) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源关联路径不是连续模块关系");
            }
            Set<Long> sourceIds = sourceIdsByRoot.values().stream().flatMap(List::stream)
                    .collect(java.util.stream.Collectors.toSet());
            List<UnModuleRelation> links = sourceIds.isEmpty() ? List.of() : relations.list(new LambdaQueryWrapper<UnModuleRelation>()
                    .in(UnModuleRelation::getSourceRecordId, sourceIds)
                    .eq(UnModuleRelation::getRelationConfigId, step.config().getId())
                    .eq(UnModuleRelation::getStatus, "ACTIVE")
                    .orderByAsc(UnModuleRelation::getSortNo).orderByAsc(UnModuleRelation::getId));
            Map<Long, List<Long>> targetIdsBySource = new LinkedHashMap<>();
            for (UnModuleRelation link : links) {
                targetIdsBySource.computeIfAbsent(link.getSourceRecordId(), ignored -> new ArrayList<>())
                        .add(link.getTargetRecordId());
            }
            Map<Long, RowView> targetRows = targetRows(token, context, step,
                    targetIdsBySource.values().stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet()));
            String relationKey = step.key();
            Map<Long, List<Long>> nextSourceIdsByRoot = new LinkedHashMap<>();
            for (int index = 0; index < rows.size(); index++) {
                RowView row = rows.get(index);
                List<Long> targetIds = sourceIdsByRoot.getOrDefault(row.id(), List.of()).stream()
                        .flatMap(sourceId -> targetIdsBySource.getOrDefault(sourceId, List.of()).stream())
                        .distinct().toList();
                List<Map<String, Object>> related = targetIds.stream()
                        .distinct().map(targetRows::get).filter(java.util.Objects::nonNull)
                        .map(this::payload).toList();
                Map<String, Object> values = new LinkedHashMap<>(row.values());
                Object value = related.size() == 1 ? related.getFirst() : related;
                values.put(relationKey, value);
                // 后续路径只能从当前账号可见且未删除的中间记录继续，避免通过隐藏中间记录泄露下一级数据。
                nextSourceIdsByRoot.put(row.id(), targetIds.stream().filter(targetRows::containsKey).toList());
                rows.set(index, new RowView(row.id(), row.name(), row.status(), row.ownerAccountId(),
                        row.sourceTenantId(), row.shared(), values, row.valueDisplay()));
            }
            sourceIdsByRoot = nextSourceIdsByRoot;
            sourceModuleId = step.config().getTargetModuleId();
            columns.add(new ColumnView(null, relationKey, "关联数据 " + step.config().getId(), 0, null, null));
        }
        Page<RowView> page = new Page<>(root.page().getCurrent(), root.page().getSize(), root.page().getTotal());
        page.setRecords(rows);
        return new ModuleDataView(columns, page);
    }

    /** 目标模块通过统一列表入口分页读取，避免绕过目标模块的数据权限。 */
    private Map<Long, RowView> targetRows(String token, SystemContext context, RelationStep step,
                                          java.util.Set<Long> targetIds) {
        if (targetIds.isEmpty()) return Map.of();
        ModuleRecordSearchBO targetSearch = new ModuleRecordSearchBO();
        targetSearch.setScope("ALL");
        targetSearch.setLimit(100);
        List<Long> outputIds = moduleConfig.currentFields(step.config().getTargetModuleId()).stream()
                .filter(field -> step.outputKeys().isEmpty() || step.outputKeys().contains(field.getFieldKey()))
                .map(com.unique.unexamine.sys.module.base.entity.UnModuleField::getId).toList();
        if (!outputIds.isEmpty()) targetSearch.setColumnFieldIds(outputIds);
        Map<Long, RowView> result = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>(targetIds);
        // 关联目标可能远大于单页限制，按主键分批回查；每批仍走模块列表入口，保留权限和逻辑删除过滤。
        for (int start = 0; start < ids.size() && result.size() < targetIds.size(); start += TARGET_ID_BATCH_SIZE) {
            int end = Math.min(start + TARGET_ID_BATCH_SIZE, ids.size());
            targetSearch.setRecordIds(ids.subList(start, end));
            long total = Long.MAX_VALUE;
            int pageNo = 1;
            while (pageNo == 1 || ((pageNo - 1L) * targetSearch.getLimit()) < total) {
                targetSearch.setPage(pageNo++);
                ModuleDataView page = moduleData(token, context, step.config().getTargetModuleId(), targetSearch);
                total = page.page().getTotal();
                for (RowView row : page.page().getRecords()) if (targetIds.contains(row.id())) result.put(row.id(), row);
                if (page.page().getRecords().isEmpty()) break;
            }
        }
        return result;
    }

    private Map<String, Object> payload(RowView row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.id());
        payload.put("name", row.name());
        payload.put("status", row.status());
        payload.putAll(row.values());
        return payload;
    }

    private ComponentView view(UnDashboardComponent row, Map<String, Object> config,
                               String status, ModuleDataView data) {
        return new ComponentView(row.getId(), row.getComponentCode(), row.getComponentType(),
                row.getSortNo(), row.getDataSourceId(), row.getModuleId(), config, status, data);
    }

    /** 关系发布配置支持 relation 或 relations 数组，数组项可直接写关联配置主键。 */
    private List<RelationStep> relationSteps(JsonNode relation, JsonNode relationsNode,
                                             SystemContext context, Long rootModuleId) {
        JsonNode node = relation != null && !relation.isNull() ? relation : relationsNode;
        if (node == null || node.isNull()) return List.of();
        if (rootModuleId == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "有关系路径的数据源必须配置根模块");
        if (node.isNumber()) node = json.createArrayNode().add(node.longValue());
        if (node.isObject() && node.has("steps")) node = node.get("steps");
        if (node.isObject() && node.has("relationConfigIds")) node = node.get("relationConfigIds");
        if (!node.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源关联路径必须是数组");
        List<RelationStep> result = new ArrayList<>();
        Long sourceModuleId = rootModuleId;
        for (JsonNode item : node) {
            Long relationId = item.isNumber() ? item.longValue()
                    : number(item, "relationConfigId", "configId", "id");
            if (relationId == null || relationId < 1)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源关联配置主键无效");
            UnModuleRelationConfig config = relationConfigs.queryById(relationId);
            if (config == null || !"ACTIVE".equals(config.getStatus())
                    || !context.systemId().equals(config.getSystemId())
                    || !context.tenantId().equals(config.getTenantId())
                    || !java.util.Objects.equals(sourceModuleId, config.getSourceModuleId())
                    || config.getTargetModuleId() == null) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源关联配置不是当前连续模块关系");
            }
            List<String> outputKeys = textList(item, "outputFieldKeys", "fieldKeys", "displayFieldKeys");
            if (outputKeys.isEmpty()) outputKeys = textList(readJson(config.getConfigJson()),
                    "outputFieldKeys", "fieldKeys", "displayFieldKeys");
            // 目标模块已逻辑删除时保留发布的关联路径，运行结果由模块查询返回空值。
            // 仍有效的目标模块继续执行归属和字段校验，不能借此跳过权限。
            if (modules.getById(config.getTargetModuleId()) != null) {
                moduleConfig.activeModule(context.systemId(), config.getTargetModuleId(), context.tenantId());
                Set<String> validOutputKeys = moduleConfig.currentFields(config.getTargetModuleId()).stream()
                        .map(com.unique.unexamine.sys.module.base.entity.UnModuleField::getFieldKey)
                        .collect(java.util.stream.Collectors.toSet());
                if (outputKeys.stream().anyMatch(key -> !validOutputKeys.contains(key))) {
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源关联展示字段不存在或已停用");
                }
            }
            String key = text(item, "key", "code");
            if (key == null || key.isBlank()) key = "relation_" + relationId;
            result.add(new RelationStep(config, outputKeys, key));
            sourceModuleId = config.getTargetModuleId();
        }
        return result;
    }

    private Long number(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String key : keys) if (node.has(key) && node.get(key).canConvertToLong()) return node.get(key).longValue();
        return null;
    }

    private String text(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String key : keys) if (node.has(key) && !node.get(key).isNull()) return node.get(key).asText();
        return null;
    }

    private List<String> textList(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return List.of();
        for (String key : keys) {
            JsonNode values = node.get(key);
            if (values != null && values.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode value : values) if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText());
                return result;
            }
        }
        return List.of();
    }

    private JsonNode readJson(String text) {
        if (text == null || text.isBlank()) return null;
        try { return json.readTree(text); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "关联配置格式无效"); }
    }

    /** 数据源只能引用同一系统和当前租户可见的已发布配置。 */
    private DataSourceRuntime dataSource(Long dataSourceId, SystemContext context) {
        if (dataSourceId == null || dataSourceId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源主键无效");
        }
        UnDataSource source = dataSources.queryById(dataSourceId);
        if (source == null || !PUBLISHED.equals(source.getStatus())
                || !context.systemId().equals(source.getSystemId())
                || source.getTenantId() != null && !source.getTenantId().equals(context.tenantId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "数据源不可用于当前系统租户");
        }
        UnDataSourceConfig config = dataSourceConfigs.getOne(new LambdaQueryWrapper<UnDataSourceConfig>()
                .eq(UnDataSourceConfig::getDataSourceId, dataSourceId));
        if (config == null || config.getPublishedJson() == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源尚未发布查询配置");
        }
        Map<String, Object> published = objectMap(config.getPublishedJson(), "数据源发布配置");
        JsonNode root = json.valueToTree(published);
        ModuleRecordSearchBO search = search(published);
        List<RelationStep> steps = relationSteps(root.get("relation"), root.get("relations"), context,
                source.getRootModuleId());
        if (steps.isEmpty()) steps = relationSteps(root.get("relationPath"), null, context,
                source.getRootModuleId());
        return new DataSourceRuntime(source.getRootModuleId(), steps, search);
    }

    /** 将发布配置转换为统一模块查询对象；查询页大小仍由模块域限制。 */
    private ModuleRecordSearchBO search(Map<String, Object> config) {
        if (config == null || config.isEmpty()) return new ModuleRecordSearchBO();
        Object query = config.get("search");
        if (config.containsKey("search") && !(query instanceof Map<?, ?>))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "组件筛选配置必须是对象");
        Map<?, ?> fields = query instanceof Map<?, ?> nested ? nested : config;
        Map<String, Object> supported = new LinkedHashMap<>();
        for (String name : List.of("page", "limit", "keyword", "scope", "sceneId", "filters",
                "sortFieldId", "descending", "columnFieldIds", "recordIds")) {
            if (fields.containsKey(name)) supported.put(name, fields.get(name));
        }
        try {
            return json.convertValue(supported, ModuleRecordSearchBO.class);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "组件筛选配置无效");
        }
    }

    private Map<String, Object> objectMap(String text, String label) {
        if (text == null || text.isBlank()) return Collections.emptyMap();
        try {
            Map<String, Object> value = json.readValue(text, new TypeReference<>() {});
            return value == null ? Collections.emptyMap() : new LinkedHashMap<>(value);
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, label + "格式无效");
        }
    }

    private record DataSourceRuntime(Long moduleId, List<RelationStep> steps,
                                     ModuleRecordSearchBO search) {}

    private record RelationStep(UnModuleRelationConfig config, List<String> outputKeys, String key) {}
}
