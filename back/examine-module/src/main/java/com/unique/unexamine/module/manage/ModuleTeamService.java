package com.unique.unexamine.module.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleTeamer;
import com.unique.unexamine.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.module.base.service.IUnModuleTeamerService;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/** 模块记录团队成员的读写服务，统一校验租户、记录和成员角色。 */
@Service
public class ModuleTeamService {
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final IUnModuleTeamerService teamers;
    private final IUnModuleRecordService records;
    private final IUnSysTenantMemberService members;

    public ModuleTeamService(ModuleSupport support, ModuleRecordAccess access,
                             IUnModuleTeamerService teamers, IUnModuleRecordService records,
                             IUnSysTenantMemberService members) {
        this.support = support;
        this.access = access;
        this.teamers = teamers;
        this.records = records;
        this.members = members;
    }

    /** 查询前先验证当前成员对记录的读取权限。 */
    public List<UnModuleTeamer> teamers(long moduleId, long recordId, long tenantId, HttpServletRequest request) {
        access.requireRead(access.scope(request, tenantId), record(tenantId, moduleId, recordId));
        return teamers.list(new LambdaQueryWrapper<UnModuleTeamer>().eq(UnModuleTeamer::getTenantId, tenantId)
                .eq(UnModuleTeamer::getModuleId, moduleId).eq(UnModuleTeamer::getRecordId, recordId));
    }

    /** 只有租户管理员能把当前租户的有效成员加入记录团队。 */
    public UnModuleTeamer addTeamer(long moduleId, long recordId, long tenantId,
                                    ModuleTeamController.TeamerForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        record(tenantId, moduleId, recordId);
        if (!Set.of("viewer", "editor", "manager").contains(body.roleCode())) {
            bad("团队成员角色无效");
        }
        if (members.count(new LambdaQueryWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, body.memberId()).eq(UnSysTenantMember::getStatus, 1)) == 0) {
            bad("成员不属于当前租户");
        }
        if (teamers.count(new LambdaQueryWrapper<UnModuleTeamer>().eq(UnModuleTeamer::getRecordId, recordId)
                .eq(UnModuleTeamer::getMemberId, body.memberId()).eq(UnModuleTeamer::getRoleCode, body.roleCode())) > 0) {
            conflict("团队成员已存在");
        }
        UnModuleTeamer row = new UnModuleTeamer();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setRecordId(recordId);
        row.setMemberId(body.memberId());
        row.setRoleCode(body.roleCode());
        teamers.save(row);
        return teamers.getById(row.getId());
    }

    /** 删除前核对团队行属于当前租户和记录。 */
    public void deleteTeamer(long moduleId, long recordId, long id, long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        record(tenantId, moduleId, recordId);
        UnModuleTeamer row = teamers.getById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getRecordId().equals(recordId)) {
            missing("团队成员不存在");
        }
        teamers.removeById(id);
    }

    /** 所有团队操作都限定到当前租户和模块的有效记录。 */
    private UnModuleRecord record(long tenantId, long moduleId, long recordId) {
        support.module(tenantId, moduleId);
        UnModuleRecord row = records.getById(recordId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || row.getRecordStatus() == 2) {
            missing("记录不存在");
        }
        return row;
    }

    private void write(HttpServletRequest request, long tenantId, long moduleId) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void missing(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
