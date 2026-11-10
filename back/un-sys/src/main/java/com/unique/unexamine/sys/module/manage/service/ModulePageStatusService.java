package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ModuleApprovalStatusReader;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModulePageStatus;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModulePageStatusService;
import com.unique.unexamine.sys.module.base.service.IUnModuleService;
import com.unique.unexamine.sys.module.manage.dto.ModulePageStatusDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePageStatusDto.View;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块页面配置审批状态字段；同组可关联多个动作审批，分组由管理员显式决定。 */
@Service
public class ModulePageStatusService {
    private final SystemManageService systems;
    private final IUnModuleService modules;
    private final IUnModulePageStatusService groups;
    private final IUnSysLogOptService logs;
    private final IUnSysSystemConfigService configs;
    private final ConfigurationCache cache;
    private final ObjectMapper json;
    private final ModuleApprovalStatusReader reader;

    public ModulePageStatusService(SystemManageService systems,IUnModuleService modules,
            IUnModulePageStatusService groups,IUnSysLogOptService logs,
            IUnSysSystemConfigService configs,ConfigurationCache cache,ObjectMapper json,
            ModuleApprovalStatusReader reader) {
        this.systems=systems; this.modules=modules; this.groups=groups;
        this.logs=logs; this.configs=configs; this.cache=cache; this.json=json; this.reader=reader;
    }

    /** 配置态列出该模块的所有状态组，包括已停用但仍有历史引用的组。 */
    public List<View> configuration(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        owned(systemId,context.tenantId(),moduleId);
        return groups.list(new LambdaQueryWrapper<UnModulePageStatus>()
                .eq(UnModulePageStatus::getModuleId,moduleId).eq(UnModulePageStatus::getTenantId,context.tenantId())
                .orderByAsc(UnModulePageStatus::getSortNo).orderByAsc(UnModulePageStatus::getId))
                .stream().map(this::view).toList();
    }

    /** 创建或修改状态组保存即生效；稳定编码不能在已有组上改写。 */
    @Transactional(rollbackFor=Exception.class)
    public View save(String token,Long systemId,Long tenantId,Long moduleId,SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        UnModule module=owned(systemId,context.tenantId(),moduleId);
        modules.getOne(new LambdaQueryWrapper<UnModule>().eq(UnModule::getId,moduleId).last("FOR UPDATE"),false);
        if (request==null || request.code()==null || request.name()==null || request.sortNo()<0
                || !request.code().matches("[a-z][a-zA-Z0-9_]{0,79}") || request.name().isBlank())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批状态组编码、名称或顺序无效");
        UnModulePageStatus row=request.id()==null?null:groups.getById(request.id());
        if (row!=null && (!moduleId.equals(row.getModuleId()) || !context.tenantId().equals(row.getTenantId())
                || !request.code().equals(row.getStatusCode())))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"状态组不属于当前模块或编码不可变更");
        if (request.id()!=null && row==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"审批状态组不存在");
        if (row==null) {
            if (groups.count(new LambdaQueryWrapper<UnModulePageStatus>()
                    .eq(UnModulePageStatus::getModuleId,moduleId)
                    .eq(UnModulePageStatus::getTenantId,context.tenantId())
                    .eq(UnModulePageStatus::getStatusCode,request.code()))>0)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"审批状态组编码已存在");
            row=new UnModulePageStatus(); row.setSystemId(systemId); row.setTenantId(context.tenantId());
            row.setModuleId(module.getId()); row.setStatusCode(request.code());
        }
        row.setStatusName(request.name().trim()); row.setSortNo(request.sortNo());
        row.setStatus(request.enabled()?"ACTIVE":"DISABLED"); groups.addOrUpdate(row);
        UnSysLogOpt log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(context.tenantId());
        log.setAccountId(systems.accountId(token,systemId)); log.setActionCode("MODULE_PAGE_STATUS_SAVE");
        log.setObjectType("MODULE_PAGE_STATUS"); log.setObjectId(row.getId());
        try { log.setDetailJson(json.writeValueAsString(request)); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"状态组日志无法保存",ex); }
        logs.addOrUpdate(log);
        configs.update(new LambdaUpdateWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,systemId)
                .setSql("permission_revision=permission_revision+1"));
        cache.invalidateAfterCommit("permission:"+systemId+":"+context.tenantId());
        return view(row);
    }

    /** 审批绑定只能选择相同模块、租户且启用的页面状态组。 */
    public void requireActive(Long systemId,Long tenantId,Long moduleId,Long groupId) {
        if (groupId==null) return;
        UnModulePageStatus group=groups.getById(groupId);
        if (group==null || !systemId.equals(group.getSystemId()) || !tenantId.equals(group.getTenantId())
                || !moduleId.equals(group.getModuleId()) || !"ACTIVE".equals(group.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批状态组不属于当前模块或已停用");
    }

    /** 运行态只返回当前启用的状态字段，按页面顺序稳定展示。 */
    public List<View> active(Long moduleId) {
        return groups.list(new LambdaQueryWrapper<UnModulePageStatus>()
                .eq(UnModulePageStatus::getModuleId,moduleId).eq(UnModulePageStatus::getStatus,"ACTIVE")
                .orderByAsc(UnModulePageStatus::getSortNo).orderByAsc(UnModulePageStatus::getId))
                .stream().map(this::view).toList();
    }

    /** 列表、详情和关联筛选共用同一组编码及审批实例口径。 */
    public Map<Long,Map<String,String>> statuses(Long systemId,Long tenantId,Long moduleId,List<Long> recordIds) {
        List<View> enabled=active(moduleId);
        if (enabled.isEmpty() || recordIds==null || recordIds.isEmpty()) return Map.of();
        Map<Long,String> codes=new LinkedHashMap<>();
        enabled.forEach(group->codes.put(group.id(),group.code()));
        Map<Long,Map<String,String>> result=new LinkedHashMap<>();
        reader.read(systemId,tenantId,moduleId,recordIds,enabled.stream().map(View::id).toList())
                .forEach((recordId,states)->{
                    Map<String,String> values=new LinkedHashMap<>();
                    states.forEach((groupId,status)->values.put(codes.get(groupId),status));
                    result.put(recordId,values);
                });
        return result;
    }

    /** 筛选条件只接受页面启用的状态组和 examine 当前有效的实例状态。 */
    public void applyFilters(QueryWrapper<UnModuleRecord> where,Long systemId,Long moduleId,
            Map<String,String> requested) {
        if (requested==null || requested.isEmpty()) return;
        if (requested.size()>10) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批状态筛选项过多");
        Map<String,View> byCode=new LinkedHashMap<>();
        active(moduleId).forEach(group->byCode.put(group.code(),group));
        for (var filter:requested.entrySet()) {
            View group=byCode.get(filter.getKey());
            if (group==null || filter.getValue()==null || !Set.of("RUNNING","COMPLETED","REJECTED","CANCELLED")
                    .contains(filter.getValue()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批状态字段或筛选值无效");
            reader.applyFilter(where,systemId,moduleId,group.id(),filter.getValue());
        }
    }

    private UnModule owned(Long systemId,Long tenantId,Long moduleId) {
        UnModule module=modules.getById(moduleId);
        if (module==null || !systemId.equals(module.getSystemId()) || !tenantId.equals(module.getTenantId())
                || !"ACTIVE".equals(module.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"模块不属于当前配置租户或已停用");
        return module;
    }

    private View view(UnModulePageStatus row) {
        return new View(row.getId(),row.getModuleId(),row.getStatusCode(),row.getStatusName(),
                row.getSortNo(),"ACTIVE".equals(row.getStatus()));
    }
}
