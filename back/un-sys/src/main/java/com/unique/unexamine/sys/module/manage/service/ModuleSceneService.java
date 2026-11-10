package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleScence;
import com.unique.unexamine.sys.module.base.entity.UnModuleScenceConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleScenceConfigService;
import com.unique.unexamine.sys.module.base.service.IUnModuleScenceService;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto.Condition;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto.SceneView;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块列表场景的配置和运行态校验；保存的筛选、列及排序必须实际作用于查询。 */
@Service
public class ModuleSceneService {
    private final SystemManageService systems;
    private final ModulePermissionService permissions;
    private final ModuleConfigService modules;
    private final ModuleFieldService fieldRules;
    private final IUnModuleScenceService scenes;
    private final IUnModuleScenceConfigService configurations;
    private final ObjectMapper json;

    /** 注入同模块生成的场景和配置基础 Service。 */
    public ModuleSceneService(SystemManageService systems,ModulePermissionService permissions,
            ModuleConfigService modules,ModuleFieldService fieldRules,IUnModuleScenceService scenes,
            IUnModuleScenceConfigService configurations,ObjectMapper json) {
        this.systems=systems; this.permissions=permissions; this.modules=modules; this.fieldRules=fieldRules;
        this.scenes=scenes; this.configurations=configurations; this.json=json;
    }

    /** 保存后立即成为当前列表可用场景，字段引用和操作人范围在事务中核对。 */
    @Transactional(rollbackFor=Exception.class)
    public SceneView save(String token,Long systemId,Long tenantId,Long moduleId,SaveRequest request) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"LIST");
        var context=access.context(); modules.activeModule(systemId,moduleId,context.tenantId());
        if (request == null || request.name() == null || request.name().isBlank()
                || request.name().length() > 100 || request.id() != null && request.id() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景内容无效");
        if (!request.personal()) systems.requireAdminContext(token,systemId,tenantId);
        Settings settings=new Settings(request.filters()==null?List.of():List.copyOf(request.filters()),
                request.columnFieldIds()==null?List.of():List.copyOf(request.columnFieldIds()),
                request.sortFieldId(),request.descending());
        check(systemId,context.tenantId(),moduleId,settings,access);
        UnModuleScence scene=request.id()==null?new UnModuleScence():scenes.queryById(request.id());
        if (request.id()!=null && scene==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"场景不存在");
        if (scene.getId()!=null && (!moduleId.equals(scene.getModuleId()) || !context.tenantId().equals(scene.getTenantId())
                || !Objects.equals(scene.getOwnerAccountId(),request.personal()?access.accountId():null)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"场景不属于当前账号和模块");
        if (scene.getId()==null) {
            scene.setModuleId(moduleId); scene.setTenantId(context.tenantId());
            scene.setOwnerAccountId(request.personal()?access.accountId():null);
            scene.setCode("SCENE_"+UUID.randomUUID().toString().replace("-", ""));
            scene.setSortNo(0);
        }
        scene.setName(request.name().trim()); scene.setStatus("ACTIVE"); scenes.addOrUpdate(scene);
        UnModuleScenceConfig row=configurations.getOne(new LambdaQueryWrapper<UnModuleScenceConfig>()
                .eq(UnModuleScenceConfig::getScenceId,scene.getId()));
        if (row==null) { row=new UnModuleScenceConfig(); row.setScenceId(scene.getId()); }
        row.setFilterJson(write(settings.filters())); row.setColumnJson(write(settings.columnFieldIds()));
        row.setSortJson(write(Map.of("fieldId",settings.sortFieldId()==null?0L:settings.sortFieldId(),
                "descending",settings.descending())));
        configurations.addOrUpdate(row);
        return view(scene,settings);
    }

    /** 只列出当前账号可执行、可读取字段的公共及个人场景。 */
    public List<SceneView> list(String token,Long systemId,Long tenantId,Long moduleId) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"LIST");
        modules.activeModule(systemId,moduleId,access.context().tenantId());
        List<SceneView> result=new ArrayList<>();
        for (UnModuleScence scene:scenes.list(new LambdaQueryWrapper<UnModuleScence>()
                .eq(UnModuleScence::getModuleId,moduleId).eq(UnModuleScence::getTenantId,access.context().tenantId())
                .eq(UnModuleScence::getStatus,"ACTIVE").orderByAsc(UnModuleScence::getSortNo)
                .orderByAsc(UnModuleScence::getId))) {
            if (scene.getOwnerAccountId()!=null && !scene.getOwnerAccountId().equals(access.accountId())) continue;
            Settings settings=stored(scene.getId());
            if (permitted(moduleId,settings,access)) result.add(view(scene,visibleColumns(moduleId,settings,access)));
        }
        return result;
    }

    /** 查询时重新检查场景归属和当前字段权限，旧配置不能绕过撤销的授权。 */
    public Settings resolve(Long moduleId,Long sceneId,ModulePermissionService.Access access) {
        if (sceneId==null) return new Settings(List.of(),List.of(),null,true);
        UnModuleScence scene=scenes.queryById(sceneId);
        if (scene==null || !moduleId.equals(scene.getModuleId()) || !access.context().tenantId().equals(scene.getTenantId())
                || !"ACTIVE".equals(scene.getStatus()) || scene.getOwnerAccountId()!=null
                && !scene.getOwnerAccountId().equals(access.accountId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"场景不属于当前账号和模块");
        Settings settings=stored(sceneId);
        check(access.context().systemId(),access.context().tenantId(),moduleId,settings,access,false);
        return visibleColumns(moduleId,settings,access);
    }

    /** 条件和排序只能引用当前有权读取的已保存字段。 */
    public void check(Long systemId,Long tenantId,Long moduleId,Settings settings,ModulePermissionService.Access access) {
        check(systemId,tenantId,moduleId,settings,access,true);
    }

    /** 保存时只接受已授权列；运行时过滤后来撤销的展示列，但筛选和排序必须仍有权限。 */
    private void check(Long systemId,Long tenantId,Long moduleId,Settings settings,
            ModulePermissionService.Access access,boolean requireReadableColumns) {
        Map<Long,UnModuleField> fields=modules.currentFields(moduleId).stream()
                .collect(Collectors.toMap(UnModuleField::getId,field->field));
        if (settings.filters().size()>20 || settings.columnFieldIds().size()>100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景筛选或列数量超出范围");
        for (Condition condition:settings.filters()) {
            if (condition==null || condition.fieldId()==null || condition.operator()==null)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景筛选条件不完整");
            UnModuleField field=readable(fields,condition.fieldId(),access);
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),modules.fieldConfig(field).configuration());
            if ("EQ".equals(condition.operator()))
                fieldRules.validate(systemId,tenantId,definition,condition.value(),null);
            else if ("CONTAINS".equals(condition.operator())) {
                if (definition.type().kind()!=FieldType.Kind.TEXT || !(condition.value() instanceof String text)
                        || text.isBlank() || text.length()>100) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模糊筛选只支持非空短文本");
            } else throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景筛选操作符无效");
        }
        if (settings.columnFieldIds().stream().anyMatch(Objects::isNull)
                || settings.columnFieldIds().stream().distinct().count()!=settings.columnFieldIds().size())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景列表列重复或为空");
        var columnPolicy=requireReadableColumns?permissions.listFieldsPolicy(access):null;
        java.util.Set<Long> pageIds=modules.pageFields(moduleId,"LIST").stream()
                .map(com.unique.unexamine.sys.module.base.entity.UnModulePageField::getFieldId)
                .collect(Collectors.toSet());
        for (Long id:settings.columnFieldIds()) {
            if (!pageIds.contains(id))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"展示列不在当前模块列表页面中");
            if (requireReadableColumns) {
                UnModuleField column=fields.get(id);
                if (column==null || !columnPolicy.readable(column.getFieldKey()))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"场景引用了无权读取的展示列");
            }
            else if (!fields.containsKey(id)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景引用了未保存字段");
        }
        if (settings.sortFieldId()!=null) {
            UnModuleField field=readable(fields,settings.sortFieldId(),access);
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),modules.fieldConfig(field).configuration());
            FieldType.Kind kind=definition.type().kind();
            if (List.of(FieldType.Kind.OPTIONS,
                    FieldType.Kind.REFERENCES).contains(kind) || definition.type().valueColumn()==FieldType.ValueColumn.JSON
                    || (kind==FieldType.Kind.REFERENCE || definition.type()==FieldType.SELECT)
                    && Boolean.TRUE.equals(definition.rules().get("multiple")))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"该字段类型暂不支持列表排序");
        }
    }

    private boolean permitted(Long moduleId,Settings settings,ModulePermissionService.Access access) {
        try {
            check(access.context().systemId(),access.context().tenantId(),moduleId,settings,access,false);
            visibleColumns(moduleId,settings,access);
            return true;
        }
        catch (BaseException ex) { return false; }
    }

    private Settings visibleColumns(Long moduleId,Settings settings,ModulePermissionService.Access access) {
        Map<Long,UnModuleField> fields=modules.currentFields(moduleId).stream()
                .collect(Collectors.toMap(UnModuleField::getId,field->field));
        var listPolicy=permissions.listFieldsPolicy(access);
        java.util.Set<Long> pageIds=modules.pageFields(moduleId,"LIST").stream()
                .map(com.unique.unexamine.sys.module.base.entity.UnModulePageField::getFieldId)
                .collect(Collectors.toSet());
        List<Long> visible=settings.columnFieldIds().stream()
                .filter(id->pageIds.contains(id) && fields.containsKey(id)
                        && listPolicy.readable(fields.get(id).getFieldKey())).toList();
        if (!settings.columnFieldIds().isEmpty() && visible.isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"场景已无可读取的展示列");
        return new Settings(settings.filters(),visible,settings.sortFieldId(),settings.descending());
    }

    private UnModuleField readable(Map<Long,UnModuleField> fields,Long fieldId,ModulePermissionService.Access access) {
        UnModuleField field=fields.get(fieldId);
        if (field==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景引用了未保存字段");
        if (!access.policy().readable(field.getFieldKey()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"场景引用了无权读取的字段");
        return field;
    }

    private Settings stored(Long sceneId) {
        UnModuleScenceConfig row=configurations.getOne(new LambdaQueryWrapper<UnModuleScenceConfig>()
                .eq(UnModuleScenceConfig::getScenceId,sceneId));
        if (row==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"场景没有可用的查询配置");
        try {
            List<Condition> filters=json.readValue(row.getFilterJson(),json.getTypeFactory()
                    .constructCollectionType(List.class,Condition.class));
            List<Long> columns=json.readValue(row.getColumnJson(),json.getTypeFactory()
                    .constructCollectionType(List.class,Long.class));
            Map<?,?> sort=json.readValue(row.getSortJson(),Map.class);
            // 场景配置属于运行时输入；JSON 为 null 或缺少排序结构时统一按配置损坏处理，
            // 不能让列表入口在 settings.filters() 等处抛出未包装的空指针异常。
            if (filters==null || columns==null || sort==null
                    || !(sort.get("fieldId") instanceof Number)
                    || !(sort.get("descending") instanceof Boolean))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"场景查询配置损坏");
            Long fieldId=((Number)sort.get("fieldId")).longValue();
            return new Settings(filters,columns,fieldId==0?null:fieldId,Boolean.TRUE.equals(sort.get("descending")));
        } catch (BaseException ex) {
            throw ex;
        } catch (JsonProcessingException | ClassCastException | NullPointerException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"场景查询配置损坏",ex);
        }
    }

    private SceneView view(UnModuleScence scene,Settings settings) {
        return new SceneView(scene.getId(),scene.getName(),scene.getOwnerAccountId()!=null,settings);
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"场景配置无法保存",ex); }
    }
}
