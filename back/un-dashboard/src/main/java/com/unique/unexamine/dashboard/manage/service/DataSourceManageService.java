package com.unique.unexamine.dashboard.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.dashboard.base.entity.UnDataSource;
import com.unique.unexamine.dashboard.base.entity.UnDataSourceConfig;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceConfigService;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceService;
import com.unique.unexamine.dashboard.manage.dto.DataSourceManageDto.ConfigView;
import com.unique.unexamine.dashboard.manage.dto.DataSourceManageDto.DataSourceView;
import com.unique.unexamine.dashboard.manage.dto.DataSourceManageDto.SaveConfigRequest;
import com.unique.unexamine.dashboard.manage.dto.DataSourceManageDto.SaveRequest;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelationConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleFieldService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 数据源草稿与发布管理；禁止执行数据库直连配置。 */
@Service
public class DataSourceManageService {
    private final IUnDataSourceService dataSources;
    private final IUnDataSourceConfigService configs;
    private final IUnSysLogOptService logs;
    private final SystemManageService systems;
    private final ModuleConfigService modules;
    private final ModuleFieldService fieldRules;
    private final IUnModuleRelationConfigService relationConfigs;
    private final ObjectMapper json;

    /** 注入数据源基础服务、系统管理权限和审计日志。 */
    public DataSourceManageService(IUnDataSourceService dataSources,
            IUnDataSourceConfigService configs, IUnSysLogOptService logs,
            SystemManageService systems, ModuleConfigService modules,
            ModuleFieldService fieldRules,
            IUnModuleRelationConfigService relationConfigs, ObjectMapper json) {
        this.dataSources = dataSources;
        this.configs = configs;
        this.logs = logs;
        this.systems = systems;
        this.modules = modules;
        this.fieldRules = fieldRules;
        this.relationConfigs = relationConfigs;
        this.json = json;
    }

    /** 查询当前系统租户的数据源配置。 */
    public DataSourceView query(String token, Long systemId, Long tenantId, Long dataSourceId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        return view(owned(context, dataSourceId));
    }

    /** 列出当前系统租户可维护的数据源，包含系统级和当前租户级配置。 */
    public List<DataSourceView> list(String token, Long systemId, Long tenantId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        return dataSources.list(new LambdaQueryWrapper<UnDataSource>()
                        .eq(UnDataSource::getSystemId, context.systemId())
                        .and(scope -> scope.isNull(UnDataSource::getTenantId)
                                .or().eq(UnDataSource::getTenantId, context.tenantId()))
                        .orderByDesc(UnDataSource::getUpdateTime).orderByDesc(UnDataSource::getId))
                .stream().map(this::view).toList();
    }

    /** 保存数据源草稿；作用域和模块引用由当前系统管理员维护。 */
    @Transactional(rollbackFor = Exception.class)
    public DataSourceView save(String token, Long systemId, Long tenantId, SaveRequest request) {
        if (request == null || request.scopeType() == null || request.scopeType().isBlank()
                || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 120
                || request.description() != null && request.description().length() > 500)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源资料无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        String scope = normalizeScope(request.scopeType());
        validateRootModule(context, request.rootModuleId());
        UnDataSource source = request.id() == null ? new UnDataSource() : ownedForUpdate(context, request.id());
        boolean existing = source.getId() != null;
        if (source.getId() == null) {
            source.setSystemId(context.systemId());
            source.setCreateAccountId(context.accountId());
            source.setCreateTime(LocalDateTime.now());
            source.setPublishedVersion(0);
        }
        source.setScopeType(scope);
        source.setTenantId("TENANT".equals(scope) ? context.tenantId() : null);
        source.setCode(request.code().trim());
        source.setName(request.name().trim());
        source.setRootModuleId(request.rootModuleId());
        source.setDescription(request.description());
        source.setStatus("DRAFT");
        source.setUpdateTime(LocalDateTime.now());
        dataSources.addOrUpdate(source);
        if (existing && (source.getTenantId() == null || source.getDescription() == null)) {
            // 系统级归属及清空说明都需显式落库，基础更新默认跳过 null。
            var clear = dataSources.lambdaUpdate().eq(UnDataSource::getId, source.getId());
            if (source.getTenantId() == null) clear.set(UnDataSource::getTenantId, null);
            if (source.getDescription() == null) clear.set(UnDataSource::getDescription, null);
            clear.update();
        }
        audit(context, "DATA_SOURCE_SAVE", source.getId());
        return view(source);
    }

    /** 保存数据源草稿配置，结构化配置在发布时整体固化。 */
    @Transactional(rollbackFor = Exception.class)
    public DataSourceView saveConfig(String token, Long systemId, Long tenantId,
                                     Long dataSourceId, SaveConfigRequest request) {
        if (request == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源配置不能为空");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnDataSource source = ownedForUpdate(context, dataSourceId);
        validateRootModule(context, source.getRootModuleId());
        UnDataSourceConfig config = configs.getOne(new LambdaQueryWrapper<UnDataSourceConfig>()
                .eq(UnDataSourceConfig::getDataSourceId, dataSourceId).last("FOR UPDATE"), false);
        boolean existingConfig = config != null;
        if (config == null) {
            config = new UnDataSourceConfig();
            config.setDataSourceId(dataSourceId);
        }
        config.setRelationJson(writeOrNull(request.relation(), "关联配置"));
        config.setOutputJson(writeOrEmpty(request.output(), "输出配置"));
        config.setFilterJson(writeOrNull(request.filter(), "筛选配置"));
        config.setSortJson(writeOrNull(request.sort(), "排序配置"));
        config.setUpdateTime(LocalDateTime.now());
        configs.addOrUpdate(config);
        if (existingConfig && (config.getRelationJson() == null || config.getFilterJson() == null
                || config.getSortJson() == null)) {
            // 移除关联路径或筛选排序时，发布配置不能继续读取旧草稿列。
            var clear = configs.lambdaUpdate().eq(UnDataSourceConfig::getId, config.getId());
            if (config.getRelationJson() == null) clear.set(UnDataSourceConfig::getRelationJson, null);
            if (config.getFilterJson() == null) clear.set(UnDataSourceConfig::getFilterJson, null);
            if (config.getSortJson() == null) clear.set(UnDataSourceConfig::getSortJson, null);
            clear.update();
        }
        source.setStatus("DRAFT");
        source.setUpdateTime(LocalDateTime.now());
        dataSources.addOrUpdate(source);
        audit(context, "DATA_SOURCE_CONFIG_SAVE", dataSourceId);
        return view(source);
    }

    /** 发布数据源及其配置快照；运行态之后才允许使用这个版本。 */
    @Transactional(rollbackFor = Exception.class)
    public DataSourceView publish(String token, Long systemId, Long tenantId, Long dataSourceId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnDataSource source = ownedForUpdate(context, dataSourceId);
        validateRootModule(context, source.getRootModuleId());
        if (source.getRootModuleId() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源发布前必须配置起始模块");
        UnDataSourceConfig config = configs.getOne(new LambdaQueryWrapper<UnDataSourceConfig>()
                .eq(UnDataSourceConfig::getDataSourceId, dataSourceId).last("FOR UPDATE"), false);
        if (config == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源尚未配置查询条件");
        validateConfig(context, source.getRootModuleId(), readJson(config.getRelationJson()),
                readJson(config.getOutputJson()), readJson(config.getFilterJson()), readJson(config.getSortJson()));
        ObjectNode published = json.createObjectNode();
        putJson(published, "relation", readJson(config.getRelationJson()));
        putJson(published, "output", readJson(config.getOutputJson()));
        putJson(published, "filter", readJson(config.getFilterJson()));
        putJson(published, "sort", readJson(config.getSortJson()));
        ObjectNode search = json.createObjectNode();
        JsonNode filter = readJson(config.getFilterJson());
        JsonNode output = readJson(config.getOutputJson());
        JsonNode sort = readJson(config.getSortJson());
        if (filter != null && filter.isArray()) search.set("filters", filter);
        if (output != null && output.isArray()) search.set("columnFieldIds", output);
        if (sort != null && sort.isObject()) {
            if (sort.has("fieldId")) search.set("sortFieldId", sort.get("fieldId"));
            if (sort.has("descending")) search.set("descending", sort.get("descending"));
        }
        published.set("search", search);
        int version = (source.getPublishedVersion() == null ? 0 : source.getPublishedVersion()) + 1;
        config.setPublishedJson(write(published));
        config.setPublishedVersion(version);
        config.setUpdateTime(LocalDateTime.now());
        configs.addOrUpdate(config);
        source.setPublishedVersion(version);
        source.setStatus("PUBLISHED");
        source.setUpdateTime(LocalDateTime.now());
        dataSources.addOrUpdate(source);
        audit(context, "DATA_SOURCE_PUBLISH", dataSourceId);
        return view(source);
    }

    private String normalizeScope(String scope) {
        if (scope == null || !List.of("SYSTEM", "TENANT").contains(scope.trim().toUpperCase())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源作用域无效");
        }
        return scope.trim().toUpperCase();
    }

    private UnDataSource owned(SystemContext context, Long id) {
        if (id == null || id < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源主键无效");
        }
        UnDataSource source = dataSources.queryById(id);
        if (source == null || !context.systemId().equals(source.getSystemId())
                || source.getTenantId() != null && !context.tenantId().equals(source.getTenantId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "数据源不存在");
        }
        return source;
    }

    /** 写入数据源前锁定所属行，串行化草稿、配置和发布版本的递增。 */
    private UnDataSource ownedForUpdate(SystemContext context, Long id) {
        if (id == null || id < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "数据源主键无效");
        }
        UnDataSource source = dataSources.getOne(new LambdaQueryWrapper<UnDataSource>()
                .eq(UnDataSource::getId, id).last("FOR UPDATE"), false);
        if (source == null || !context.systemId().equals(source.getSystemId())
                || source.getTenantId() != null && !context.tenantId().equals(source.getTenantId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "数据源不存在");
        }
        return source;
    }

    /** 根模块必须属于当前系统并处于当前租户可用范围，避免发布后才暴露跨系统引用错误。 */
    private void validateRootModule(SystemContext context, Long moduleId) {
        if (moduleId != null && moduleId < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "起始模块主键无效");
        }
        if (moduleId != null) modules.activeModule(context.systemId(), moduleId, context.tenantId());
    }

    /** 发布前校验动态字段、筛选排序和关联路径，避免运行查询阶段才暴露坏配置。 */
    private void validateConfig(SystemContext context, Long rootModuleId, JsonNode relation,
                                JsonNode output, JsonNode filter, JsonNode sort) {
        if (rootModuleId == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源必须配置起始模块");
        Map<Long, UnModuleField> rootFields = fields(rootModuleId);
        validateFieldIdArray(output, rootFields, "输出字段");
        validateFilters(context, filter, rootFields);
        validateSort(sort, rootFields);
        validateRelations(context, rootModuleId, relation);
    }

    /** 输出列使用模块字段主键；运行查询会把它转换成统一 SearchBO。 */
    private void validateFieldIdArray(JsonNode value, Map<Long, UnModuleField> valid, String label) {
        if (value == null || value.isNull()) return;
        if (!value.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, label + "必须是数组");
        Set<Long> selected = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.canConvertToLong() || item.longValue() < 1 || !valid.containsKey(item.longValue()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, label + "包含不存在或不属于起始模块的字段");
            if (!selected.add(item.longValue()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, label + "不能重复引用字段");
        }
    }

    /** 固定筛选必须引用根模块字段并声明可执行操作符。 */
    private void validateFilters(SystemContext context, JsonNode value, Map<Long, UnModuleField> valid) {
        if (value == null || value.isNull()) return;
        if (!value.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "筛选配置必须是数组");
        if (value.size() > 20) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "筛选条件不能超过20个");
        for (JsonNode item : value) {
            if (!item.isObject() || !item.has("fieldId") || !item.get("fieldId").canConvertToLong()
                    || !valid.containsKey(item.get("fieldId").longValue()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "筛选字段不存在或不属于起始模块");
            UnModuleField field = valid.get(item.get("fieldId").longValue());
            String operator = item.path("operator").asText("").trim().toUpperCase();
            if (!Set.of("EQ", "CONTAINS").contains(operator))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "筛选操作符无效");
            FieldDefinition definition = fieldRules.configured(field.getFieldKey(), modules.fieldConfig(field).configuration());
            if ("EQ".equals(operator)) {
                Object filterValue = item.has("value") && !item.get("value").isNull()
                        ? json.convertValue(item.get("value"), Object.class) : null;
                fieldRules.validate(context.systemId(), context.tenantId(), definition, filterValue, null);
            } else {
                if (definition.type().kind() != FieldType.Kind.TEXT
                        || !item.has("value") || !item.get("value").isTextual()
                        || item.get("value").asText().isBlank() || item.get("value").asText().length() > 100)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "模糊筛选只支持非空短文本字段");
            }
        }
    }

    /** 默认排序只允许根模块字段，避免把任意 SQL 片段带入列表查询。 */
    private void validateSort(JsonNode value, Map<Long, UnModuleField> valid) {
        if (value == null || value.isNull()) return;
        if (!value.isObject()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "排序配置必须是对象");
        if (value.has("descending") && !value.get("descending").isBoolean())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "排序方向必须是布尔值");
        if (!value.has("fieldId")) return;
        JsonNode fieldId = value.get("fieldId");
        if (!fieldId.canConvertToLong() || !valid.containsKey(fieldId.longValue()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "排序字段不存在或不属于起始模块");
        UnModuleField field = valid.get(fieldId.longValue());
        FieldDefinition definition = fieldRules.configured(field.getFieldKey(), modules.fieldConfig(field).configuration());
        FieldType.Kind kind = definition.type().kind();
        if (Set.of(FieldType.Kind.OPTIONS, FieldType.Kind.REFERENCES).contains(kind)
                || definition.type().valueColumn() == FieldType.ValueColumn.JSON
                || (kind == FieldType.Kind.REFERENCE || definition.type() == FieldType.SELECT)
                && Boolean.TRUE.equals(definition.rules().get("multiple")))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "该字段类型暂不支持数据源排序");
    }

    /** 关联配置必须形成从根模块开始的连续路径，且目标模块当前仍可用。 */
    private void validateRelations(SystemContext context, Long rootModuleId, JsonNode value) {
        if (value == null || value.isNull()) return;
        JsonNode node = value;
        if (node.isNumber()) node = json.createArrayNode().add(node.longValue());
        if (node.isObject() && node.has("steps")) node = node.get("steps");
        if (node.isObject() && node.has("relationConfigIds")) node = node.get("relationConfigIds");
        if (!node.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "关联路径必须是数组");
        Long sourceModuleId = rootModuleId;
        for (JsonNode item : node) {
            Long relationId = item.isNumber() ? item.longValue() : number(item, "relationConfigId", "configId", "id");
            if (relationId == null || relationId < 1)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "关联配置主键无效");
            UnModuleRelationConfig relation = relationConfigs.queryById(relationId);
            if (relation == null || !"ACTIVE".equals(relation.getStatus())
                    || !context.systemId().equals(relation.getSystemId())
                    || !context.tenantId().equals(relation.getTenantId())
                    || !sourceModuleId.equals(relation.getSourceModuleId()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "关联路径不是当前租户的连续模块关系");
            modules.activeModule(context.systemId(), relation.getTargetModuleId(), context.tenantId());
            // 步骤级展示字段可以覆盖关联配置默认值，发布时同样校验目标字段归属，避免运行查询静默丢列。
            validateRelationOutputs(relation, item);
            sourceModuleId = relation.getTargetModuleId();
        }
    }

    /** 关系配置和数据源步骤中的展示字段必须仍属于目标模块，防止运行时静默丢列。 */
    private void validateRelationOutputs(UnModuleRelationConfig relation, JsonNode step) {
        JsonNode config = readJson(relation.getConfigJson());
        JsonNode keys = null;
        if (step != null && step.isObject()) {
            for (String name : List.of("outputFieldKeys", "fieldKeys", "displayFieldKeys")) {
                if (step.has(name)) { keys = step.get(name); break; }
            }
        }
        if ((keys == null || keys.isNull()) && config != null && config.isObject()) {
            for (String name : List.of("outputFieldKeys", "fieldKeys", "displayFieldKeys")) {
                if (config.has(name)) { keys = config.get(name); break; }
            }
        }
        if (keys == null || keys.isNull()) return;
        if (!keys.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "关联展示字段配置必须是数组");
        Set<String> valid = new HashSet<>(modules.currentFields(relation.getTargetModuleId()).stream()
                .map(UnModuleField::getFieldKey).toList());
        Set<String> selected = new HashSet<>();
        for (JsonNode key : keys) {
            if (!key.isTextual() || !valid.contains(key.asText()) || !selected.add(key.asText()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "关联展示字段不存在或已停用");
        }
    }

    private Long number(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String key : keys) if (node.has(key) && node.get(key).canConvertToLong()) return node.get(key).longValue();
        return null;
    }

    private Map<Long, UnModuleField> fields(Long moduleId) {
        return modules.currentFields(moduleId).stream().collect(Collectors.toMap(UnModuleField::getId,
                field -> field, (left, right) -> left, LinkedHashMap::new));
    }

    private DataSourceView view(UnDataSource source) {
        UnDataSourceConfig config = configs.getOne(new LambdaQueryWrapper<UnDataSourceConfig>()
                .eq(UnDataSourceConfig::getDataSourceId, source.getId()), false);
        ConfigView configView = config == null ? null : new ConfigView(config.getId(), config.getDataSourceId(),
                readJson(config.getRelationJson()), readJson(config.getOutputJson()), readJson(config.getFilterJson()),
                readJson(config.getSortJson()), readJson(config.getPublishedJson()), config.getPublishedVersion());
        return new DataSourceView(source.getId(), source.getScopeType(), source.getSystemId(), source.getTenantId(),
                source.getCode(), source.getName(), source.getRootModuleId(), source.getDescription(), source.getStatus(),
                source.getPublishedVersion(), source.getUpdateTime(), configView);
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try { return json.readTree(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "数据源配置格式无效"); }
    }

    private String writeOrNull(JsonNode node, String label) {
        if (node == null || node.isNull()) return null;
        return write(node, label);
    }

    private String writeOrEmpty(JsonNode node, String label) {
        return write(node == null || node.isNull() ? json.createArrayNode() : node, label);
    }

    private String write(JsonNode node) {
        return write(node, "数据源配置");
    }

    private String write(JsonNode node, String label) {
        try { return json.writeValueAsString(node); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, label + "无法保存"); }
    }

    private void putJson(ObjectNode target, String name, JsonNode value) {
        if (value != null) target.set(name, value);
    }

    private void audit(SystemContext context, String actionCode, Long objectId) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId());
        log.setActionCode(actionCode);
        log.setObjectType("DATA_SOURCE");
        log.setObjectId(objectId);
        logs.addOrUpdate(log);
    }
}
