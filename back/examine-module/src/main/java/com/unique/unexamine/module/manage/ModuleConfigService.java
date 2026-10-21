package com.unique.unexamine.module.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.entity.UnModuleField;
import com.unique.unexamine.module.base.entity.UnModuleGroup;
import com.unique.unexamine.module.base.entity.UnModuleTenant;
import com.unique.unexamine.module.base.service.IUnModuleFieldService;
import com.unique.unexamine.module.base.service.IUnModuleGroupService;
import com.unique.unexamine.module.base.service.IUnModuleRecordService.FieldConfig;
import com.unique.unexamine.module.base.service.IUnModuleService;
import com.unique.unexamine.module.base.service.IUnModuleTenantService;
import com.unique.unexamine.sys.manage.SystemManageService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 模块组、模块、字段设计和发布的业务规则，布局由设计器保存后直接提供给表单。 */
@Service
public class ModuleConfigService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "TEXT", "MULTILINE", "NUMBER", "MONEY", "DATE", "DATETIME", "BOOLEAN", "EMAIL", "PHONE");
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnModuleGroupService groups;
    private final IUnModuleService modules;
    private final IUnModuleFieldService fields;
    private final IUnModuleTenantService tenantModules;
    private final ObjectMapper json;

    /** 创建运行态导航模块组。 */
    public record CreateGroupRequest(@NotBlank @Size(max = 80) String code,
                                     @NotBlank @Size(max = 100) String name) {}

    /** 创建必须归属现有模块组的业务模块。 */
    public record CreateModuleRequest(@NotNull Long groupId,
                                      @NotBlank @Size(max = 80) String code,
                                      @NotBlank @Size(max = 100) String name) {}

    /** 设计器提交的字段类型与已拖拽布局，服务端原样保存布局对象。 */
    public record SaveFieldRequest(@NotBlank @Size(max = 80) String fieldCode,
                                   @NotBlank @Size(max = 100) String fieldName,
                                   @NotBlank String fieldType,
                                   boolean required, int sortNo,
                                   @NotNull Map<String, Object> layout,
                                   @NotNull Map<String, Object> config) {}

    /** 配置态和运行态均可读取的模块主信息。 */
    public record ModuleView(Long id, String code, String name, String status, int version) {}

    /** 新建表单仅传递已发布字段配置，不自行计算布局。 */
    public record FormView(Long moduleId, int version, List<FieldConfig> fields) {}

    /** 注入模块贴表 Service 和系统上下文业务服务。 */
    public ModuleConfigService(SystemManageService systems, IUnSysSystemService systemRows,
            IUnModuleGroupService groups,
            IUnModuleService modules, IUnModuleFieldService fields,
            IUnModuleTenantService tenantModules, ObjectMapper json) {
        this.systems = systems;
        this.systemRows = systemRows;
        this.groups = groups;
        this.modules = modules;
        this.fields = fields;
        this.tenantModules = tenantModules;
        this.json = json;
    }

    /** 系统后台创建模块组；非管理员不能修改配置。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(String token, Long systemId, Long tenantId, CreateGroupRequest request) {
        var context = adminContext(token, systemId, tenantId);
        if (!context.tenantId().equals(systemRows.queryById(systemId).getRootTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "模块组只由主租户配置");
        }
        String code = code(request.code());
        if (groups.count(new LambdaQueryWrapper<UnModuleGroup>()
                .eq(UnModuleGroup::getSystemId, systemId)
                .eq(UnModuleGroup::getTenantId, context.tenantId())
                .eq(UnModuleGroup::getCode, code)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模块组编码已存在");
        }
        UnModuleGroup group = new UnModuleGroup();
        group.setSystemId(systemId);
        group.setTenantId(context.tenantId());
        group.setCode(code);
        group.setName(request.name().trim());
        group.setStatus("DRAFT");
        group.setCreateAccountId(accountId(token, systemId));
        groups.addOrUpdate(group);
        return group.getId();
    }

    /** 创建业务模块，同时绑定当前租户并建立默认标题字段草稿。 */
    @Transactional(rollbackFor = Exception.class)
    public ModuleView createModule(String token, Long systemId, Long tenantId, CreateModuleRequest request) {
        var context = adminContext(token, systemId, tenantId);
        UnModuleGroup group = groups.queryById(request.groupId());
        Long rootTenantId = systemRows.queryById(systemId).getRootTenantId();
        if (!systemId.equals(group.getSystemId()) || !rootTenantId.equals(group.getTenantId())
                || (!context.tenantId().equals(rootTenantId) && !"PUBLISHED".equals(group.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "模块组不属于当前系统租户");
        }
        String code = code(request.code());
        if (modules.count(new LambdaQueryWrapper<UnModule>()
                .eq(UnModule::getSystemId, systemId)
                .eq(UnModule::getTenantId, context.tenantId())
                .eq(UnModule::getCode, code)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模块编码已存在");
        }
        UnModule module = new UnModule();
        module.setSystemId(systemId);
        module.setTenantId(context.tenantId());
        module.setGroupId(group.getId());
        module.setCode(code);
        module.setName(request.name().trim());
        module.setStatus("DRAFT");
        module.setCreateAccountId(accountId(token, systemId));
        modules.addOrUpdate(module);
        UnModuleTenant tenantModule = new UnModuleTenant();
        tenantModule.setSystemId(systemId);
        tenantModule.setTenantId(context.tenantId());
        tenantModule.setModuleId(module.getId());
        tenantModule.setStatus("ACTIVE");
        tenantModules.addOrUpdate(tenantModule);
        // 默认标题字段只在模块创建时初始化；之后设计器保存的位置和宽度才是唯一布局来源。
        saveFieldInTransaction(module, new SaveFieldRequest("name", "标题", "TEXT", true, 0,
                Map.of("position", 0, "widthPercent", 100), Map.of()));
        return view(module);
    }

    /** 保存字段草稿；已发布字段继续使用上次发布快照，直到再次发布。 */
    @Transactional(rollbackFor = Exception.class)
    public Long saveField(String token, Long systemId, Long tenantId, Long moduleId, SaveFieldRequest request) {
        var context = adminContext(token, systemId, tenantId);
        UnModule module = module(systemId, moduleId, context.tenantId());
        return saveFieldInTransaction(module, request);
    }

    /** 把当前设计器字段顺序与原布局一起发布，运行态只读取本次快照。 */
    @Transactional(rollbackFor = Exception.class)
    public ModuleView publish(String token, Long systemId, Long tenantId, Long moduleId) {
        var context = adminContext(token, systemId, tenantId);
        UnModule module = module(systemId, moduleId, context.tenantId());
        UnModuleGroup group = groups.queryById(module.getGroupId());
        List<UnModuleField> definitions = draftFields(moduleId);
        if (definitions.isEmpty() || definitions.stream().noneMatch(field -> "name".equals(field.getFieldCode()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模块缺少标题字段");
        }
        int version = (module.getPublishedVersion() == null ? 0 : module.getPublishedVersion()) + 1;
        for (UnModuleField field : definitions) {
            field.setPublishedLayoutJson(field.getLayoutJson());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("fieldName", field.getFieldName());
            snapshot.put("fieldType", field.getFieldType());
            snapshot.put("required", field.getRequiredFlag());
            snapshot.put("config", readMap(field.getConfigJson()));
            field.setPublishedConfigJson(writeJson(snapshot));
            field.setPublishedSortNo(field.getSortNo());
            field.setPublishedVersion(version);
            field.setStatus("PUBLISHED");
            fields.addOrUpdate(field);
        }
        if (context.tenantId().equals(group.getTenantId())) {
            group.setStatus("PUBLISHED");
            group.setPublishedVersion((group.getPublishedVersion() == null ? 0 : group.getPublishedVersion()) + 1);
            groups.addOrUpdate(group);
        } else if (!"PUBLISHED".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "主租户模块组尚未发布");
        }
        module.setStatus("PUBLISHED");
        module.setPublishedVersion(version);
        modules.addOrUpdate(module);
        return view(module);
    }

    /** 返回已发布表单，字段顺序和占位宽度取持久化发布快照。 */
    public FormView newForm(String token, Long systemId, Long tenantId, Long moduleId) {
        var context = systems.context(token, systemId, tenantId);
        if (!context.owner() && !context.tenantAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少模块数据操作权限");
        }
        UnModule module = publishedModule(systemId, moduleId, context.tenantId());
        return new FormView(moduleId, module.getPublishedVersion(), publishedFields(moduleId).stream()
                .map(this::fieldConfig).toList());
    }

    /** 业务记录服务调用同一份发布配置。 */
    public List<UnModuleField> publishedFields(Long moduleId) {
        return fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId, moduleId)
                .eq(UnModuleField::getStatus, "PUBLISHED")
                .orderByAsc(UnModuleField::getPublishedSortNo)
                .orderByAsc(UnModuleField::getId));
    }

    /** 转成生成器基础详情所需的原配置，嵌套 layout 不改写。 */
    public FieldConfig fieldConfig(UnModuleField field) {
        Map<String, Object> configuration = new LinkedHashMap<>(readMap(field.getPublishedConfigJson()));
        configuration.put("layout", readMap(field.getPublishedLayoutJson()));
        return new FieldConfig(field.getFieldCode(), configuration);
    }

    /** 读取当前租户可使用的发布模块。 */
    public UnModule publishedModule(Long systemId, Long moduleId, Long tenantId) {
        UnModule module = modules.queryById(moduleId);
        Long rootTenantId = systemRows.queryById(systemId).getRootTenantId();
        if (!systemId.equals(module.getSystemId())
                || (!tenantId.equals(module.getTenantId()) && !rootTenantId.equals(module.getTenantId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "模块不属于当前系统租户");
        }
        if (!"PUBLISHED".equals(module.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模块尚未发布");
        }
        return module;
    }

    /** 草稿字段按设计器保存顺序读取。 */
    private List<UnModuleField> draftFields(Long moduleId) {
        return fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId, moduleId)
                .ne(UnModuleField::getStatus, "DISABLED")
                .orderByAsc(UnModuleField::getSortNo).orderByAsc(UnModuleField::getId));
    }

    /** 保存字段草稿，字段编码不能碰撞系统主数据字段。 */
    private Long saveFieldInTransaction(UnModule module, SaveFieldRequest request) {
        String fieldCode = code(request.fieldCode());
        if (!"name".equals(fieldCode) && Set.of("id", "num", "status", "moduleid", "tenantid",
                "systemid", "owneruserid", "createuserid", "deletedtime").contains(fieldCode)) {
            throw new IllegalArgumentException("字段编码与系统字段冲突");
        }
        String type = request.fieldType().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(type) || ("name".equals(fieldCode) && !"TEXT".equals(type))) {
            throw new IllegalArgumentException("字段类型暂不支持保存");
        }
        if (request.sortNo() < 0 || request.layout().isEmpty()) {
            throw new IllegalArgumentException("字段布局和顺序无效");
        }
        UnModuleField field = fields.getOne(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId, module.getId())
                .eq(UnModuleField::getTenantId, module.getTenantId())
                .eq(UnModuleField::getFieldCode, fieldCode), false);
        if (field == null) {
            field = new UnModuleField();
            field.setSystemId(module.getSystemId());
            field.setTenantId(module.getTenantId());
            field.setModuleId(module.getId());
            field.setFieldCode(fieldCode);
            field.setStatus("DRAFT");
        }
        field.setFieldName(request.fieldName().trim());
        field.setFieldType(type);
        field.setRequiredFlag(request.required());
        field.setReadonlyFlag(false);
        field.setHiddenFlag(false);
        field.setSortNo(request.sortNo());
        field.setLayoutJson(writeJson(request.layout()));
        field.setConfigJson(writeJson(request.config()));
        fields.addOrUpdate(field);
        return field.getId();
    }

    /** 配置操作要求当前系统租户有效且具备管理身份。 */
    private SystemManageService.SystemContext adminContext(String token, Long systemId, Long tenantId) {
        var context = systems.context(token, systemId, tenantId);
        if (!context.owner() && !context.tenantAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少模块配置权限");
        }
        return context;
    }

    /** 当前操作账号从系统成员关系取得，不允许请求体伪造。 */
    private Long accountId(String token, Long systemId) {
        // 系统成员映射承载当前账号；模块配置实际操作者由业务服务解析。
        return systems.accountId(token, systemId);
    }

    /** 模块须属于本系统和当前租户，阻止跨系统编码猜测。 */
    private UnModule module(Long systemId, Long moduleId, Long tenantId) {
        UnModule module = modules.queryById(moduleId);
        if (!systemId.equals(module.getSystemId()) || !tenantId.equals(module.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "模块不属于当前系统租户");
        }
        return module;
    }

    /** 系统内配置编码统一小写。 */
    private String code(String input) {
        String result = input.trim().toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z][a-z0-9_]{1,79}")) {
            throw new IllegalArgumentException("配置编码须以字母开头并只包含字母、数字和下划线");
        }
        return result;
    }

    /** 保存设计器原始配置，不变更字段布局语义。 */
    private String writeJson(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("字段配置无法保存", ex);
        }
    }

    /** 读取发布配置并保持 JSON 字段原始顺序。 */
    private Map<String, Object> readMap(String value) {
        if (value == null) {
            return Map.of();
        }
        try {
            return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("已发布字段配置损坏", ex);
        }
    }

    /** 模块管理接口只返回必要信息。 */
    private ModuleView view(UnModule module) {
        return new ModuleView(module.getId(), module.getCode(), module.getName(), module.getStatus(),
                module.getPublishedVersion() == null ? 0 : module.getPublishedVersion());
    }
}
