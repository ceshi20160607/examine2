package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import com.unique.unexamine.sys.base.service.IUnSysTenantService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleShare;
import com.unique.unexamine.sys.module.base.entity.UnModuleTenant;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleShareService;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantService;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 指定业务记录的跨租户共享；原记录归属不变，目标租户仍需菜单和字段授权。 */
@Service
public class ModuleShareService {
    private final SystemManageService systems;
    private final ModuleConfigService modules;
    private final ModuleMenuService menus;
    private final IUnSysSystemConfigService systemConfigs;
    private final IUnSysTenantService tenants;
    private final IUnModuleTenantService tenantModules;
    private final IUnModuleRecordService records;
    private final IUnModuleShareService shares;
    private final IUnModuleLogService logs;
    private final ObjectMapper json;

    /** 当前入口只开放单条记录共享；范围共享须接入同一访问判定后开放。 */
    public record SaveRequest(@NotNull Long targetTenantId,@NotNull Long recordId,
                              LocalDateTime effectiveTime,LocalDateTime expireTime) {}
    /** 共享配置和撤销结果保留原始记录所属租户。 */
    public record ShareView(Long id,Long sourceTenantId,Long targetTenantId,Long moduleId,
                            Long recordId,String status,LocalDateTime effectiveTime,LocalDateTime expireTime) {}

    /** 所有表读写均通过生成的基础 Service，业务检查集中在本层。 */
    public ModuleShareService(SystemManageService systems,ModuleConfigService modules,ModuleMenuService menus,
            IUnSysSystemConfigService systemConfigs,IUnSysTenantService tenants,
            IUnModuleTenantService tenantModules,IUnModuleRecordService records,
            IUnModuleShareService shares,IUnModuleLogService logs,ObjectMapper json) {
        this.systems=systems; this.modules=modules; this.menus=menus;
        this.systemConfigs=systemConfigs; this.tenants=tenants; this.tenantModules=tenantModules;
        this.records=records; this.shares=shares; this.logs=logs; this.json=json;
    }

    /** 来源租户管理员指定一条原生记录及目标租户，默认只授权列表和详情。 */
    @Transactional(rollbackFor=Exception.class)
    public ShareView create(String token,Long systemId,Long sourceTenantId,Long moduleId,SaveRequest request) {
        var source=systems.requireAdminContext(token,systemId,sourceTenantId);
        requireMultiTenant(systemId);
        if (request.recordId()<1 || request.targetTenantId()<1 || source.tenantId().equals(request.targetTenantId()))
            throw new IllegalArgumentException("共享记录和目标租户无效");
        modules.lockModule(systemId,moduleId);
        modules.publishedModule(systemId,moduleId,source.tenantId());
        UnModuleRecord record=records.queryById(request.recordId());
        if (!systemId.equals(record.getSystemId()) || !source.tenantId().equals(record.getTenantId())
                || !moduleId.equals(record.getModuleId()) || record.getDeletedTime()!=null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"来源租户记录不存在");
        UnSysTenant target=tenants.queryById(request.targetTenantId());
        if (!systemId.equals(target.getSystemId()) || !"ACTIVE".equals(target.getStatus()))
            throw new IllegalArgumentException("目标租户不属于当前系统或已停用");
        UnModuleTenant binding=tenantModules.getOne(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getSystemId,systemId).eq(UnModuleTenant::getTenantId,target.getId())
                .eq(UnModuleTenant::getModuleId,moduleId));
        if (binding==null || !"ACTIVE".equals(binding.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"目标租户尚未启用该模块");
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime start=request.effectiveTime()==null?now:request.effectiveTime();
        if (request.expireTime()!=null && !request.expireTime().isAfter(start))
            throw new IllegalArgumentException("共享失效时间必须晚于生效时间");
        boolean duplicate=shares.list(new LambdaQueryWrapper<UnModuleShare>()
                .eq(UnModuleShare::getSystemId,systemId).eq(UnModuleShare::getSourceTenantId,source.tenantId())
                .eq(UnModuleShare::getTargetTenantId,target.getId()).eq(UnModuleShare::getModuleId,moduleId)
                .eq(UnModuleShare::getRecordId,record.getId()).eq(UnModuleShare::getStatus,"ACTIVE"))
                .stream().anyMatch(row->(row.getExpireTime()==null || row.getExpireTime().isAfter(start))
                        && (request.expireTime()==null || row.getEffectiveTime().isBefore(request.expireTime())));
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT,"该记录已共享给目标租户");
        long listMenu=menus.defaultDefinition(source,moduleId,"LIST").menu().getId();
        long detailMenu=menus.defaultDefinition(source,moduleId,"DETAIL").menu().getId();
        UnModuleShare share=new UnModuleShare();
        share.setSystemId(systemId); share.setSourceTenantId(source.tenantId()); share.setTargetTenantId(target.getId());
        share.setModuleId(moduleId); share.setRecordId(record.getId());
        share.setActionMenuIdsJson(write(List.of(listMenu,detailMenu)));
        share.setStatus("ACTIVE"); share.setEffectiveTime(start); share.setExpireTime(request.expireTime());
        share.setCreateAccountId(systems.accountId(token,systemId)); shares.addOrUpdate(share);
        audit(share,source.tenantId(),share.getCreateAccountId(),"RECORD_SHARE");
        return view(share);
    }

    /** 撤销只改变授权关系；记录与共享操作历史继续保留。 */
    @Transactional(rollbackFor=Exception.class)
    public ShareView revoke(String token,Long systemId,Long sourceTenantId,Long moduleId,Long shareId) {
        var source=systems.requireAdminContext(token,systemId,sourceTenantId);
        UnModuleShare share=shares.getOne(new LambdaQueryWrapper<UnModuleShare>()
                .eq(UnModuleShare::getId,shareId).last("FOR UPDATE"));
        if (share==null || !systemId.equals(share.getSystemId()) || !source.tenantId().equals(share.getSourceTenantId())
                || !moduleId.equals(share.getModuleId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"共享授权不存在");
        if (!"ACTIVE".equals(share.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"共享授权已经撤销");
        Long actor=systems.accountId(token,systemId);
        share.setStatus("REVOKED"); share.setRevokeAccountId(actor); share.setRevokeTime(LocalDateTime.now());
        shares.addOrUpdate(share);
        audit(share,source.tenantId(),actor,"RECORD_SHARE_REVOKE");
        return view(share);
    }

    /** 当前菜单下有效的外租户记录由共享关系决定，不借用本租户负责人范围。 */
    public Set<Long> sharedRecordIds(ModulePermissionService.Access access) {
        if (!multiTenant(access.context().systemId())) return Set.of();
        List<UnModuleShare> allowed=active(access);
        if (allowed.isEmpty()) return Set.of();
        Map<Long,UnModuleRecord> actual=records.list(new LambdaQueryWrapper<UnModuleRecord>()
                .in(UnModuleRecord::getId,allowed.stream().map(UnModuleShare::getRecordId).distinct().toList())
                .eq(UnModuleRecord::getSystemId,access.context().systemId())
                .eq(UnModuleRecord::getModuleId,access.definition().module().getId())
                .isNull(UnModuleRecord::getDeletedTime)).stream()
                .collect(Collectors.toMap(UnModuleRecord::getId,row->row));
        return allowed.stream().filter(share->{
            var record=actual.get(share.getRecordId());
            return record!=null && share.getSourceTenantId().equals(record.getTenantId())
                    && !share.getTargetTenantId().equals(record.getTenantId());
        }).map(UnModuleShare::getRecordId).collect(Collectors.toSet());
    }

    /** 读取外租户记录时再查当前授权，撤销后不能靠旧缓存继续访问。 */
    public Long requireShared(ModulePermissionService.Access access,UnModuleRecord record) {
        if (!multiTenant(access.context().systemId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"业务记录不存在");
        for (UnModuleShare share:active(access)) if (record.getId().equals(share.getRecordId())
                && record.getTenantId().equals(share.getSourceTenantId())) return share.getId();
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"业务记录不存在");
    }

    /** 指定菜单及时间窗同时有效，且只读取单条记录共享；范围共享尚未开放配置。 */
    private List<UnModuleShare> active(ModulePermissionService.Access access) {
        LocalDateTime now=LocalDateTime.now();
        return shares.list(new LambdaQueryWrapper<UnModuleShare>()
                .eq(UnModuleShare::getSystemId,access.context().systemId())
                .eq(UnModuleShare::getTargetTenantId,access.context().tenantId())
                .eq(UnModuleShare::getModuleId,access.definition().module().getId())
                .eq(UnModuleShare::getStatus,"ACTIVE").isNotNull(UnModuleShare::getRecordId)
                .le(UnModuleShare::getEffectiveTime,now)
                .and(q->q.isNull(UnModuleShare::getExpireTime).or().gt(UnModuleShare::getExpireTime,now)))
                .stream().filter(row->menuIds(row).contains(access.definition().menu().getId())).toList();
    }

    private Set<Long> menuIds(UnModuleShare share) {
        try { return Set.copyOf(json.readValue(share.getActionMenuIdsJson(),new TypeReference<List<Long>>(){})); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("共享动作配置损坏",ex); }
    }

    private void requireMultiTenant(Long systemId) {
        if (!multiTenant(systemId)) throw new ResponseStatusException(HttpStatus.CONFLICT,"请先开启多租户模式");
    }

    private boolean multiTenant(Long systemId) {
        UnSysSystemConfig row=systemConfigs.getOne(new LambdaQueryWrapper<UnSysSystemConfig>()
                .eq(UnSysSystemConfig::getSystemId,systemId));
        return row!=null && Boolean.TRUE.equals(row.getMultiTenantEnabled());
    }

    /** 操作日志冻结来源、目标、共享主键和当时的菜单范围。 */
    private void audit(UnModuleShare share,Long actorTenantId,Long actorId,String action) {
        UnModuleLog log=new UnModuleLog(); log.setSystemId(share.getSystemId()); log.setTenantId(share.getSourceTenantId());
        log.setActorTenantId(actorTenantId); log.setModuleId(share.getModuleId()); log.setRecordId(share.getRecordId());
        log.setShareId(share.getId()); log.setOperatorAccountId(actorId); log.setActionCode(action);
        String message="RECORD_SHARE".equals(action)?"记录共享给租户 "+share.getTargetTenantId()
                :"撤销对租户 "+share.getTargetTenantId()+" 的记录共享";
        log.setDetailJson(write(new ModuleRecordService.AuditDetail(message,null,List.of(),null,null,message)));
        log.setPermissionJson(write(Map.of("sourceTenantId",share.getSourceTenantId(),
                "targetTenantId",share.getTargetTenantId(),"menuIds",menuIds(share))));
        logs.addOrUpdate(log);
    }

    private ShareView view(UnModuleShare row) {
        return new ShareView(row.getId(),row.getSourceTenantId(),row.getTargetTenantId(),row.getModuleId(),
                row.getRecordId(),row.getStatus(),row.getEffectiveTime(),row.getExpireTime());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("共享配置无法保存",ex); }
    }
}
