package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModulePage;
import com.unique.unexamine.sys.module.base.entity.UnModulePageField;
import com.unique.unexamine.sys.module.base.service.IUnModuleFieldService;
import com.unique.unexamine.sys.module.base.service.IUnModulePageFieldService;
import com.unique.unexamine.sys.module.base.service.IUnModulePageService;
import com.unique.unexamine.sys.module.base.service.IUnModuleService;
import com.unique.unexamine.sys.module.manage.dto.ModulePageDto.FieldSlot;
import com.unique.unexamine.sys.module.manage.dto.ModulePageDto.PageView;
import com.unique.unexamine.sys.module.manage.dto.ModulePageDto.SaveRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块页面独立维护列表、表单、详情和关联字段布局，不改写模块字段定义。 */
@Service
public class ModulePageService {
    private static final Set<String> KINDS=Set.of("CREATE","EDIT","DETAIL","LIST","RELATION");
    private final SystemManageService systems;
    private final IUnModuleService modules;
    private final IUnModuleFieldService fields;
    private final IUnModulePageService pages;
    private final IUnModulePageFieldService pageFields;
    private final IUnSysLogOptService logs;
    private final IUnSysSystemConfigService configs;
    private final ConfigurationCache cache;
    private final ObjectMapper json;

    public ModulePageService(SystemManageService systems,IUnModuleService modules,IUnModuleFieldService fields,
            IUnModulePageService pages,IUnModulePageFieldService pageFields,IUnSysLogOptService logs,
            IUnSysSystemConfigService configs,ConfigurationCache cache,ObjectMapper json) {
        this.systems=systems; this.modules=modules; this.fields=fields; this.pages=pages;
        this.pageFields=pageFields; this.logs=logs; this.configs=configs; this.cache=cache; this.json=json;
    }

    /** 模块创建事务中只给标题字段配置默认页面位置，后续字段由管理员逐页选择。 */
    public void initialize(UnModule module,Long titleFieldId) {
        for (String kind:List.of("CREATE","EDIT","DETAIL","LIST")) {
            UnModulePage page=new UnModulePage(); page.setSystemId(module.getSystemId());
            page.setTenantId(module.getTenantId()); page.setModuleId(module.getId());
            page.setPageKind(kind); page.setRelationFieldId(0L); page.setStatus("ACTIVE"); pages.addOrUpdate(page);
            UnModulePageField slot=new UnModulePageField(); slot.setPageId(page.getId());
            slot.setFieldId(titleFieldId); slot.setSortNo(0); slot.setRowNo(0);
            slot.setColumnNo(0); slot.setWidthPercent((byte)100); slot.setStatus("ACTIVE");
            pageFields.addOrUpdate(slot);
        }
    }

    /** 配置入口按模块归属读取当前页面，关联页面必须指定来源关联字段。 */
    public PageView configuration(String token,Long systemId,Long tenantId,Long moduleId,
            String pageKind,Long relationFieldId) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        UnModule module=owned(context.systemId(),context.tenantId(),moduleId);
        if ("RELATION".equals(pageKind)) {
            targetModule(module,pageKind,relationFieldId);
            UnModulePage existing=pages.getOne(new LambdaQueryWrapper<UnModulePage>()
                    .eq(UnModulePage::getModuleId,moduleId).eq(UnModulePage::getTenantId,module.getTenantId())
                    .eq(UnModulePage::getPageKind,pageKind)
                    .eq(UnModulePage::getRelationFieldId,relationFieldId),false);
            if (existing==null) return new PageView(null,moduleId,pageKind,relationFieldId,List.of());
        }
        UnModulePage page=page(module,pageKind,relationFieldId,false);
        return view(page);
    }

    /** 整页保存字段布局；引用、唯一、占位宽度和所属模块在同一事务核对。 */
    @Transactional(rollbackFor=Exception.class)
    public PageView save(String token,Long systemId,Long tenantId,Long moduleId,
            String pageKind,Long relationFieldId,SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        UnModule module=modules.getOne(new LambdaQueryWrapper<UnModule>()
                .eq(UnModule::getId,moduleId).eq(UnModule::getSystemId,systemId).last("FOR UPDATE"),false);
        if (module==null || !context.tenantId().equals(module.getTenantId()) || !"ACTIVE".equals(module.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"模块不属于当前配置租户或已停用");
        if (request==null || request.fields()==null || request.fields().size()>200)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"页面字段配置无效或超出数量上限");
        if ("RELATION".equals(pageKind) && request.fields().isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"关联页面至少选择一个目标展示字段");
        UnModulePage page=page(module,pageKind,relationFieldId,true);
        Long displayModule=targetModule(module,pageKind,page.getRelationFieldId());
        Map<Long,UnModuleField> available=new HashMap<>();
        fields.list(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,displayModule)
                .eq(UnModuleField::getStatus,"ACTIVE")).forEach(field->available.put(field.getId(),field));
        Set<Long> seen=new HashSet<>(); Set<String> positions=new HashSet<>();
        Map<Integer,Integer> rowWidths=new HashMap<>();
        for (FieldSlot slot:request.fields()) {
            if (slot==null || slot.fieldId()==null || !seen.add(slot.fieldId())
                    || !available.containsKey(slot.fieldId()) || slot.sortNo()<0 || slot.rowNo()<0
                    || slot.columnNo()<0 || slot.widthPercent()<1 || slot.widthPercent()>100
                    || !positions.add(slot.rowNo()+":"+slot.columnNo()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"页面字段不存在、重复或位置无效");
            int width=rowWidths.merge(slot.rowNo(),slot.widthPercent(),Integer::sum);
            if (width>100) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"同一行字段宽度超过100%");
        }
        if ("CREATE".equals(pageKind) && request.fields().stream()
                .noneMatch(slot->"name".equals(available.get(slot.fieldId()).getFieldKey())))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"新建页面必须包含标题字段");
        Map<Long,UnModulePageField> existing=new HashMap<>();
        pageFields.list(new LambdaQueryWrapper<UnModulePageField>()
                .eq(UnModulePageField::getPageId,page.getId())).forEach(slot->existing.put(slot.getFieldId(),slot));
        for (UnModulePageField old:existing.values()) if (!seen.contains(old.getFieldId())) {
            old.setStatus("DISABLED"); pageFields.addOrUpdate(old);
        }
        for (FieldSlot slot:request.fields()) {
            UnModulePageField row=existing.getOrDefault(slot.fieldId(),new UnModulePageField());
            row.setPageId(page.getId()); row.setFieldId(slot.fieldId()); row.setSortNo(slot.sortNo());
            row.setRowNo(slot.rowNo()); row.setColumnNo(slot.columnNo());
            row.setWidthPercent((byte)slot.widthPercent()); row.setStatus("ACTIVE"); pageFields.addOrUpdate(row);
        }
        UnSysLogOpt log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(context.tenantId());
        log.setAccountId(systems.accountId(token,systemId)); log.setActionCode("MODULE_PAGE_SAVE");
        log.setObjectType("MODULE_PAGE"); log.setObjectId(page.getId());
        try { log.setDetailJson(json.writeValueAsString(request)); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"页面变更日志无法保存",ex); }
        logs.addOrUpdate(log);
        configs.update(new LambdaUpdateWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,systemId)
                .setSql("permission_revision=permission_revision+1"));
        cache.invalidateAfterCommit("permission:"+systemId+":"+context.tenantId());
        return view(page);
    }

    /** 运行态按页面已保存顺序读取有效字段，不从字段定义中的旧布局推导。 */
    public List<UnModulePageField> activeFields(Long moduleId,String pageKind,Long relationFieldId) {
        UnModule module=modules.getById(moduleId);
        if (module==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"模块不存在");
        UnModulePage page=page(module,pageKind,relationFieldId,false);
        return pageFields.list(new LambdaQueryWrapper<UnModulePageField>()
                .eq(UnModulePageField::getPageId,page.getId())
                .eq(UnModulePageField::getStatus,"ACTIVE")
                .orderByAsc(UnModulePageField::getSortNo).orderByAsc(UnModulePageField::getId));
    }

    /** 字段仍出现在任一页面时先调整页面，避免删除后留下失效的展示引用。 */
    public boolean hasActiveFieldReference(Long fieldId) {
        return pageFields.count(new LambdaQueryWrapper<UnModulePageField>()
                .eq(UnModulePageField::getFieldId,fieldId)
                .eq(UnModulePageField::getStatus,"ACTIVE"))>0;
    }

    private UnModulePage page(UnModule module,String pageKind,Long relationFieldId,boolean createRelation) {
        if (pageKind==null || !KINDS.contains(pageKind)
                || "RELATION".equals(pageKind)!=(relationFieldId!=null && relationFieldId>0))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"页面类型或关联字段无效");
        long relation="RELATION".equals(pageKind)?relationFieldId:0L;
        UnModulePage page=pages.getOne(new LambdaQueryWrapper<UnModulePage>()
                .eq(UnModulePage::getModuleId,module.getId()).eq(UnModulePage::getTenantId,module.getTenantId())
                .eq(UnModulePage::getPageKind,pageKind).eq(UnModulePage::getRelationFieldId,relation),false);
        if (page==null && createRelation && "RELATION".equals(pageKind)) {
            targetModule(module,pageKind,relation);
            page=new UnModulePage(); page.setSystemId(module.getSystemId()); page.setTenantId(module.getTenantId());
            page.setModuleId(module.getId()); page.setPageKind(pageKind);
            page.setRelationFieldId(relation); page.setStatus("ACTIVE"); pages.addOrUpdate(page);
        }
        if (page==null || !"ACTIVE".equals(page.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"页面配置不存在或已停用");
        return page;
    }

    private Long targetModule(UnModule module,String kind,Long relationFieldId) {
        if (!"RELATION".equals(kind)) return module.getId();
        UnModuleField relation=fields.getById(relationFieldId);
        if (relation==null || !module.getId().equals(relation.getModuleId())
                || !"ACTIVE".equals(relation.getStatus()) || relation.getFieldType()!=FieldType.RELATION.code()
                || relation.getRelationModuleId()==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"关联页面没有有效的来源关联字段");
        UnModule target=modules.getById(relation.getRelationModuleId());
        if (target==null || !module.getSystemId().equals(target.getSystemId()) || !"ACTIVE".equals(target.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"关联页面的目标模块不可用");
        return target.getId();
    }

    private UnModule owned(Long systemId,Long tenantId,Long moduleId) {
        UnModule module=modules.getById(moduleId);
        if (module==null || !systemId.equals(module.getSystemId()) || !tenantId.equals(module.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"模块不属于当前配置租户");
        return module;
    }

    private PageView view(UnModulePage page) {
        List<FieldSlot> items=new ArrayList<>();
        for (UnModulePageField row:activeFields(page.getModuleId(),page.getPageKind(),page.getRelationFieldId()))
            items.add(new FieldSlot(row.getFieldId(),row.getSortNo(),row.getRowNo(),row.getColumnNo(),
                    Byte.toUnsignedInt(row.getWidthPercent())));
        return new PageView(page.getId(),page.getModuleId(),page.getPageKind(),page.getRelationFieldId(),items);
    }
}
