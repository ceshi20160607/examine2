package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleTeamer;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleTeamerService;
import com.unique.unexamine.sys.module.manage.dto.ModuleTeamDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleTeamDto.View;
import com.unique.unexamine.sys.module.manage.enums.TeamAccessLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护单条业务记录的查看或编辑授权，不改变主记录负责人。 */
@Service
public class ModuleTeamService {
    private final ModulePermissionService permissions;
    private final IUnModuleRecordService records;
    private final IUnModuleTeamerService teamers;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnModuleLogService logs;
    private final ObjectMapper json;

    public ModuleTeamService(ModulePermissionService permissions, IUnModuleRecordService records,
            IUnModuleTeamerService teamers, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, IUnModuleLogService logs, ObjectMapper json) {
        this.permissions = permissions; this.records = records; this.teamers = teamers;
        this.members = members; this.tenantMembers = tenantMembers; this.logs = logs; this.json = json;
    }

    /** 只展示当前记录有效的额外成员，不把主记录负责人复制进团队表。 */
    public List<View> list(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        manager(token, systemId, tenantId, moduleId, recordId);
        return teamers.list(new LambdaQueryWrapper<UnModuleTeamer>()
                .eq(UnModuleTeamer::getRecordId, recordId).eq(UnModuleTeamer::getStatus, "ACTIVE")
                .orderByAsc(UnModuleTeamer::getId)).stream().map(this::view).toList();
    }

    /** 同一记录与账号只保存一条关系；恢复撤销关系时沿用稳定主键。 */
    @Transactional(rollbackFor = Exception.class)
    public View save(String token, Long systemId, Long tenantId, Long moduleId, Long recordId, SaveRequest request) {
        var access = manager(token, systemId, tenantId, moduleId, recordId);
        if (request == null || request.accountId() == null || request.accountId() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "团队成员账号无效");
        TeamAccessLevel level = TeamAccessLevel.parse(request.accessLevel());
        member(systemId, access.context().tenantId(), request.accountId());
        UnModuleTeamer row = teamers.getOne(new LambdaQueryWrapper<UnModuleTeamer>()
                .eq(UnModuleTeamer::getRecordId, recordId)
                .eq(UnModuleTeamer::getAccountId, request.accountId()).last("FOR UPDATE"), false);
        String before = row == null || !"ACTIVE".equals(row.getStatus()) ? null : row.getAccessLevel();
        if (row == null) {
            row = new UnModuleTeamer(); row.setRecordId(recordId); row.setAccountId(request.accountId());
            row.setCreateUserId(access.accountId()); row.setCreateTime(LocalDateTime.now());
        }
        row.setAccessLevel(level.name()); row.setStatus("ACTIVE");
        row.setUpdateUserId(access.accountId()); row.setUpdateTime(LocalDateTime.now());
        teamers.addOrUpdate(row);
        audit(access, recordId, "RECORD_TEAM_SAVE", Map.of("accountId", request.accountId(),
                "before", before == null ? "" : before, "after", level.name()));
        return view(row);
    }

    /** 撤销关系后记录不再通过团队范围可见；保留同一主键供再次加入。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String token, Long systemId, Long tenantId, Long moduleId, Long recordId, Long accountId) {
        var access = manager(token, systemId, tenantId, moduleId, recordId);
        UnModuleTeamer row = teamers.getOne(new LambdaQueryWrapper<UnModuleTeamer>()
                .eq(UnModuleTeamer::getRecordId, recordId).eq(UnModuleTeamer::getAccountId, accountId)
                .eq(UnModuleTeamer::getStatus, "ACTIVE").last("FOR UPDATE"), false);
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "团队成员不存在");
        row.setStatus("REVOKED"); row.setUpdateUserId(access.accountId()); row.setUpdateTime(LocalDateTime.now());
        teamers.addOrUpdate(row);
        audit(access, recordId, "RECORD_TEAM_REVOKE", Map.of("accountId", accountId,
                "before", row.getAccessLevel()));
    }

    private ModulePermissionService.Access manager(String token, Long systemId, Long tenantId,
            Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "TEAM_MEMBERS");
        UnModuleRecord record = records.getById(recordId);
        if (record == null || !systemId.equals(record.getSystemId())
                || !moduleId.equals(record.getModuleId())
                || !access.context().tenantId().equals(record.getTenantId()) || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        access.policy().requireOwner(record.getOwnerUserId());
        return access;
    }

    private void member(Long systemId, Long tenantId, Long accountId) {
        UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId).eq(UnSysMember::getAccountId, accountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (member == null || tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, systemId)
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, member.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE")) == 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "目标账号不是当前租户有效成员");
    }

    private void audit(ModulePermissionService.Access access, Long recordId, String code, Map<String, Object> detail) {
        UnModuleLog log = new UnModuleLog();
        log.setSystemId(access.context().systemId()); log.setTenantId(access.context().tenantId());
        log.setActorTenantId(access.context().tenantId()); log.setModuleId(access.definition().module().getId());
        log.setRecordId(recordId); log.setOperatorAccountId(access.accountId()); log.setActionCode(code);
        try { log.setDetailJson(json.writeValueAsString(detail)); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "团队变更日志无法保存", ex); }
        log.setPermissionJson("{\"menuId\":" + access.definition().menu().getId() + "}");
        log.setCreateTime(LocalDateTime.now()); logs.addOrUpdate(log);
    }

    private View view(UnModuleTeamer row) {
        return new View(row.getId(), row.getRecordId(), row.getAccountId(), row.getAccessLevel(),
                row.getStatus(), row.getUpdateTime());
    }
}
