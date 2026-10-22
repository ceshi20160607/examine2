package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import com.unique.unexamine.sys.base.service.IUnSysTenantService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleShare;
import com.unique.unexamine.sys.module.base.entity.UnModuleTenant;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleShareService;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantService;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordFilter;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 指定记录或按来源租户负责人及字段条件划定范围的跨租户共享；写动作逐项授权。 */
@Service
public class ModuleShareService {
    private final SystemManageService systems;
    private final ModuleConfigService modules;
    private final ModuleMenuService menus;
    private final ModulePermissionService permissions;
    private final ModuleSceneService scenes;
    private final ModuleFieldService fieldRules;
    private final IUnSysSystemConfigService systemConfigs;
    private final IUnSysTenantService tenants;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnPlatAccountService accounts;
    private final IUnModuleTenantService tenantModules;
    private final IUnModuleRecordService records;
    private final IUnModuleShareService shares;
    private final IUnModuleLogService logs;
    private final ObjectMapper json;

    /** 单条记录与范围二选一；范围至少含负责人或已发布字段筛选条件。 */
    public record SaveRequest(@NotNull Long targetTenantId,Long recordId,List<Long> ownerAccountIds,
                              List<ModuleSceneService.Condition> filters,List<Long> actionMenuIds,
                              LocalDateTime effectiveTime,LocalDateTime expireTime) {}
    /** 范围配置使用稳定账号和字段主键，运行时按来源租户数据重新求值。 */
    public record RangeScope(List<Long> ownerAccountIds,List<ModuleSceneService.Condition> filters) {}
    /** 共享配置和撤销结果保留原始记录所属租户。 */
    public record ShareView(Long id,Long sourceTenantId,Long targetTenantId,Long moduleId,
                            Long recordId,RangeScope scope,List<Long> actionMenuIds,
                            String status,LocalDateTime effectiveTime,LocalDateTime expireTime) {}
    /** 来源租户后台按创建顺序查看当前及已撤销的共享配置。 */
    public record SharePage(long total,long current,long size,List<ShareView> records) {}

    /** 所有表读写均通过生成的基础 Service，业务检查集中在本层。 */
    public ModuleShareService(SystemManageService systems,ModuleConfigService modules,ModuleMenuService menus,
            ModulePermissionService permissions,ModuleSceneService scenes,ModuleFieldService fieldRules,
            IUnSysSystemConfigService systemConfigs,IUnSysTenantService tenants,
            IUnSysMemberService members,IUnSysTenantMemberService tenantMembers,IUnPlatAccountService accounts,
            IUnModuleTenantService tenantModules,IUnModuleRecordService records,
            IUnModuleShareService shares,IUnModuleLogService logs,ObjectMapper json) {
        this.systems=systems; this.modules=modules; this.menus=menus; this.permissions=permissions; this.scenes=scenes;
        this.fieldRules=fieldRules;
        this.systemConfigs=systemConfigs; this.tenants=tenants; this.tenantModules=tenantModules;
        this.members=members; this.tenantMembers=tenantMembers; this.accounts=accounts;
        this.records=records; this.shares=shares; this.logs=logs; this.json=json;
    }

    /** 来源租户管理员指定原生记录或范围，默认只授权列表和详情。 */
    @Transactional(rollbackFor=Exception.class)
    public ShareView create(String token,Long systemId,Long sourceTenantId,Long moduleId,SaveRequest request) {
        var source=systems.requireAdminContext(token,systemId,sourceTenantId);
        requireMultiTenant(systemId);
        if (request.targetTenantId()==null || request.targetTenantId()<1 || source.tenantId().equals(request.targetTenantId()))
            throw new IllegalArgumentException("目标租户无效");
        List<Long> ownerIds=request.ownerAccountIds()==null?List.of():request.ownerAccountIds();
        List<ModuleSceneService.Condition> filters=request.filters()==null?List.of():request.filters();
        boolean range=!ownerIds.isEmpty() || !filters.isEmpty();
        if ((request.recordId()==null)==!range || request.recordId()!=null && request.recordId()<1)
            throw new IllegalArgumentException("共享记录与共享范围必须且只能配置一种");
        modules.lockModule(systemId,moduleId);
        modules.publishedModule(systemId,moduleId,source.tenantId());
        UnModuleRecord record=null;
        RangeScope scope=null;
        if (request.recordId()!=null) {
            record=records.queryById(request.recordId());
            if (!systemId.equals(record.getSystemId()) || !source.tenantId().equals(record.getTenantId())
                    || !moduleId.equals(record.getModuleId()) || record.getDeletedTime()!=null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"来源租户记录不存在");
        } else {
            if (ownerIds.size()>100 || ownerIds.stream().anyMatch(id->id==null || id<1)
                    || ownerIds.stream().distinct().count()!=ownerIds.size())
                throw new IllegalArgumentException("共享负责人范围无效");
            Set<Long> activeOwners=sourceOwners(systemId,source.tenantId());
            if (!activeOwners.containsAll(ownerIds)) throw new IllegalArgumentException("共享负责人不是来源租户有效成员");
            var sourceAccess=permissions.require(token,systemId,source.tenantId(),moduleId,"LIST");
            scenes.check(systemId,source.tenantId(),moduleId,
                    new ModuleSceneService.Settings(filters,List.of(),null,true),sourceAccess);
            scope=new RangeScope(ownerIds.stream().sorted().toList(),List.copyOf(filters));
        }
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
        final UnModuleRecord direct=record;
        final String scopeJson=scope==null?null:write(scope);
        boolean duplicate=shares.list(new LambdaQueryWrapper<UnModuleShare>()
                .eq(UnModuleShare::getSystemId,systemId).eq(UnModuleShare::getSourceTenantId,source.tenantId())
                .eq(UnModuleShare::getTargetTenantId,target.getId()).eq(UnModuleShare::getModuleId,moduleId)
                .eq(UnModuleShare::getStatus,"ACTIVE"))
                .stream().anyMatch(row->java.util.Objects.equals(row.getRecordId(),direct==null?null:direct.getId())
                        && java.util.Objects.equals(row.getScopeJson(),scopeJson)
                        && (row.getExpireTime()==null || row.getExpireTime().isAfter(start))
                        && (request.expireTime()==null || row.getEffectiveTime().isBefore(request.expireTime())));
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT,"该记录或范围已共享给目标租户");
        long listMenu=menus.defaultDefinition(source,moduleId,"LIST").menu().getId();
        long detailMenu=menus.defaultDefinition(source,moduleId,"DETAIL").menu().getId();
        Set<Long> granted=new HashSet<>(Set.of(listMenu,detailMenu));
        List<Long> extras=request.actionMenuIds()==null?List.of():request.actionMenuIds();
        if (extras.size()>20 || extras.stream().anyMatch(id->id==null || id<1)
                || extras.stream().distinct().count()!=extras.size())
            throw new IllegalArgumentException("共享动作菜单无效或重复");
        for (Long id:extras) {
            var definition=menus.definition(source,moduleId,id,true);
            if (!Set.of("EDIT","DELETE","TRANSFER","UPDATE_FIELDS").contains(definition.settings().type()))
                throw new IllegalArgumentException("该动作不允许授予跨租户共享");
            granted.add(id);
        }
        UnModuleShare share=new UnModuleShare();
        share.setSystemId(systemId); share.setSourceTenantId(source.tenantId()); share.setTargetTenantId(target.getId());
        share.setModuleId(moduleId); share.setRecordId(record==null?null:record.getId()); share.setScopeJson(scopeJson);
        share.setActionMenuIdsJson(write(granted.stream().sorted().toList()));
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

    /** 授权列表走生成的基础分页服务，租户与模块边界由本层固定。 */
    public SharePage list(String token,Long systemId,Long sourceTenantId,Long moduleId,long page,long size) {
        var source=systems.requireAdminContext(token,systemId,sourceTenantId);
        modules.publishedModule(systemId,moduleId,source.tenantId());
        if (page<1 || size<1 || size>100) throw new IllegalArgumentException("分页范围无效");
        var filters=Map.<String,Object>of("systemId",systemId,"sourceTenantId",source.tenantId(),"moduleId",moduleId);
        var result=shares.queryPageList(new IUnModuleShareService.PageQuery(page,size,filters,"id",true));
        return new SharePage(result.getTotal(),result.getCurrent(),result.getSize(),
                result.getRecords().stream().map(this::view).toList());
    }

    /** 本租户负责人范围与有效共享范围在数据库条件中求并集，再由调用方分页。 */
    public boolean applyRecordScope(QueryWrapper<UnModuleRecord> where,ModulePermissionService.Access access,String scope) {
        boolean includeOwn=!"SHARED".equals(scope);
        boolean ownAll=includeOwn && access.policy().allData();
        Set<Long> ownerIds=includeOwn?access.policy().ownerAccountIds():Set.of();
        List<UnModuleShare> allowed="MINE".equals(scope) || !multiTenant(access.context().systemId())
                ?List.of():active(access);
        if (!ownAll && ownerIds.isEmpty() && allowed.isEmpty()) return false;
        where.and(combined->{
            boolean hasOwn=ownAll || !ownerIds.isEmpty();
            if (hasOwn) combined.nested(own->{
                own.eq("tenant_id",access.context().tenantId());
                if (!ownAll) own.in("owner_user_id",ownerIds);
            });
            for (UnModuleShare share:allowed) {
                if (hasOwn) combined.or();
                combined.nested(shared->applyGrant(shared,share));
                hasOwn=true;
            }
        });
        return true;
    }

    /** 无共享时基础列表可直接委托生成的同租户分页 CRUD。 */
    public boolean hasActiveShares(ModulePermissionService.Access access) {
        return multiTenant(access.context().systemId()) && !active(access).isEmpty();
    }

    /** 读取外租户记录时再查当前授权，撤销后不能靠旧缓存继续访问。 */
    public Long requireShared(ModulePermissionService.Access access,UnModuleRecord record) {
        return requireSharedGrant(access,record,false).getId();
    }

    /** 写操作锁定授权行，与撤销互斥；锁后重新检查有效状态与数据范围。 */
    public UnModuleShare requireSharedForWrite(ModulePermissionService.Access access,UnModuleRecord record) {
        return requireSharedGrant(access,record,true);
    }

    private UnModuleShare requireSharedGrant(ModulePermissionService.Access access,UnModuleRecord record,boolean lock) {
        if (!multiTenant(access.context().systemId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"业务记录不存在");
        for (UnModuleShare candidate:active(access)) {
            if (!record.getTenantId().equals(candidate.getSourceTenantId())) continue;
            UnModuleShare share=lock?shares.getOne(new LambdaQueryWrapper<UnModuleShare>()
                    .eq(UnModuleShare::getId,candidate.getId()).last("FOR UPDATE")):candidate;
            if (share==null || !"ACTIVE".equals(share.getStatus())
                    || share.getEffectiveTime().isAfter(LocalDateTime.now())
                    || share.getExpireTime()!=null && !share.getExpireTime().isAfter(LocalDateTime.now())
                    || !menuIds(share).contains(access.definition().menu().getId())) continue;
            if (records.count(shareQuery(share).eq("id",record.getId()))>0) return share;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"业务记录不存在");
    }

    /** 指定菜单、来源租户及时间窗同时有效；撤销后不读取旧缓存。 */
    private List<UnModuleShare> active(ModulePermissionService.Access access) {
        LocalDateTime now=LocalDateTime.now();
        List<UnModuleShare> candidates=shares.list(new LambdaQueryWrapper<UnModuleShare>()
                .eq(UnModuleShare::getSystemId,access.context().systemId())
                .eq(UnModuleShare::getTargetTenantId,access.context().tenantId())
                .eq(UnModuleShare::getModuleId,access.definition().module().getId())
                .eq(UnModuleShare::getStatus,"ACTIVE")
                .le(UnModuleShare::getEffectiveTime,now)
                .and(q->q.isNull(UnModuleShare::getExpireTime).or().gt(UnModuleShare::getExpireTime,now)));
        if (candidates.isEmpty()) return List.of();
        Set<Long> activeSources=tenants.list(new LambdaQueryWrapper<UnSysTenant>()
                .eq(UnSysTenant::getSystemId,access.context().systemId())
                .eq(UnSysTenant::getStatus,"ACTIVE")
                .in(UnSysTenant::getId,candidates.stream().map(UnModuleShare::getSourceTenantId).distinct().toList()))
                .stream().map(UnSysTenant::getId).collect(Collectors.toSet());
        if (activeSources.isEmpty()) return List.of();
        Set<Long> activeBindings=tenantModules.list(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getSystemId,access.context().systemId())
                .eq(UnModuleTenant::getModuleId,access.definition().module().getId())
                .eq(UnModuleTenant::getStatus,"ACTIVE")
                .in(UnModuleTenant::getTenantId,activeSources))
                .stream().map(UnModuleTenant::getTenantId).collect(Collectors.toSet());
        return candidates.stream().filter(row->activeSources.contains(row.getSourceTenantId())
                && activeBindings.contains(row.getSourceTenantId())
                && menuIds(row).contains(access.definition().menu().getId())).toList();
    }

    /** 共享范围只在来源租户数据上执行，目标租户不能借此扩大来源。 */
    private QueryWrapper<UnModuleRecord> shareQuery(UnModuleShare share) {
        QueryWrapper<UnModuleRecord> query=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",share.getSystemId())
                .eq("module_id",share.getModuleId()).isNull("deleted_time");
        applyGrant(query,share);
        return query;
    }

    /** 共享记录和条件范围使用同一条来源租户约束。 */
    private void applyGrant(QueryWrapper<UnModuleRecord> query,UnModuleShare share) {
        query.eq("tenant_id",share.getSourceTenantId());
        if (share.getRecordId()!=null) { query.eq("id",share.getRecordId()); return; }
        RangeScope scope=scope(share);
        if (scope.ownerAccountIds().isEmpty() && scope.filters().isEmpty())
            throw new IllegalStateException("共享范围配置为空");
        if (!scope.ownerAccountIds().isEmpty()) query.in("owner_user_id",scope.ownerAccountIds());
        Map<Long,UnModuleField> fields=modules.publishedFields(share.getModuleId()).stream()
                .collect(Collectors.toMap(UnModuleField::getId,row->row));
        for (var condition:scope.filters()) {
            UnModuleField field=fields.get(condition.fieldId());
            if (field==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"共享范围引用了失效字段");
            FieldDefinition definition=fieldRules.published(field.getFieldCode(),modules.fieldConfig(field).configuration());
            if ("EQ".equals(condition.operator()))
                fieldRules.validate(share.getSystemId(),share.getSourceTenantId(),definition,condition.value(),null);
            else if (!"CONTAINS".equals(condition.operator()) || definition.type().kind()!=FieldType.Kind.TEXT
                    || !(condition.value() instanceof String text) || text.isBlank() || text.length()>100)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"共享范围字段条件已失效");
            ModuleRecordFilter.apply(query,field,condition,json);
        }
    }

    private RangeScope scope(UnModuleShare share) {
        if (share.getScopeJson()==null) throw new IllegalStateException("共享范围配置缺失");
        try { return json.readValue(share.getScopeJson(),RangeScope.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("共享范围配置损坏",ex); }
    }

    /** 配置负责人范围时，只允许来源租户当前有效的系统成员。 */
    private Set<Long> sourceOwners(Long systemId,Long tenantId) {
        Set<Long> result=new HashSet<>();
        for (UnSysTenantMember link:tenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId,tenantId).eq(UnSysTenantMember::getStatus,"ACTIVE"))) {
            UnSysMember member=members.getById(link.getMemberId());
            if (member==null || !systemId.equals(member.getSystemId()) || !"ACTIVE".equals(member.getStatus())) continue;
            UnPlatAccount account=accounts.getById(member.getAccountId());
            if (account!=null && "ACTIVE".equals(account.getStatus())) result.add(account.getId());
        }
        return result;
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
        String subject=share.getRecordId()==null?"记录范围":"记录";
        String message="RECORD_SHARE".equals(action)?subject+"共享给租户 "+share.getTargetTenantId()
                :"撤销对租户 "+share.getTargetTenantId()+" 的"+subject+"共享";
        log.setDetailJson(write(new ModuleRecordService.AuditDetail(message,null,List.of(),null,null,message)));
        log.setPermissionJson(write(Map.of("sourceTenantId",share.getSourceTenantId(),
                "targetTenantId",share.getTargetTenantId(),"menuIds",menuIds(share),
                "scope",share.getScopeJson()==null?Map.of():scope(share))));
        logs.addOrUpdate(log);
    }

    private ShareView view(UnModuleShare row) {
        return new ShareView(row.getId(),row.getSourceTenantId(),row.getTargetTenantId(),row.getModuleId(),
                row.getRecordId(),row.getRecordId()==null?scope(row):null,
                menuIds(row).stream().sorted().toList(),row.getStatus(),row.getEffectiveTime(),row.getExpireTime());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("共享配置无法保存",ex); }
    }
}
