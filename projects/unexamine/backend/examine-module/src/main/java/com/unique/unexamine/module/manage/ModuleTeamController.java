package com.unique.unexamine.module.manage;

import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMemberMapper;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleTeamer;
import com.unique.unexamine.module.base.mapper.UnModuleRecordMapper;
import com.unique.unexamine.module.base.mapper.UnModuleTeamerMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** 模块记录的团队成员及其访问角色。 */
@RestController
@RequestMapping("/api/modules/{moduleId}")
public class ModuleTeamController {
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final UnModuleTeamerMapper teamers;
    private final UnModuleRecordMapper records;
    private final UnSysTenantMemberMapper members;

    public ModuleTeamController(ModuleSupport support, ModuleRecordAccess access,
                                UnModuleTeamerMapper teamers, UnModuleRecordMapper records,
                                UnSysTenantMemberMapper members) {
        this.support = support;
        this.access = access;
        this.teamers = teamers;
        this.records = records;
        this.members = members;
    }

    @GetMapping("/records/{recordId}/teamers")
    public List<UnModuleTeamer> teamers(@PathVariable long moduleId, @PathVariable long recordId,
                                        @RequestParam long tenantId, HttpServletRequest request) {
        access.requireRead(access.scope(request, tenantId), record(tenantId, moduleId, recordId));
        return teamers.selectList(new LambdaQueryWrapper<UnModuleTeamer>().eq(UnModuleTeamer::getTenantId, tenantId)
                .eq(UnModuleTeamer::getModuleId, moduleId).eq(UnModuleTeamer::getRecordId, recordId));
    }

    @PostMapping("/records/{recordId}/teamers")
    public UnModuleTeamer addTeamer(@PathVariable long moduleId, @PathVariable long recordId,
                                    @RequestParam long tenantId, @Valid @RequestBody TeamerForm body,
                                    HttpServletRequest request) {
        write(request, tenantId, moduleId);
        record(tenantId, moduleId, recordId);
        if (!Set.of("viewer", "editor", "manager").contains(body.roleCode())) bad("团队成员角色无效");
        if (members.selectCount(new LambdaQueryWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, body.memberId()).eq(UnSysTenantMember::getStatus, 1)) == 0) bad("成员不属于当前租户");
        if (teamers.selectCount(new LambdaQueryWrapper<UnModuleTeamer>().eq(UnModuleTeamer::getRecordId, recordId)
                .eq(UnModuleTeamer::getMemberId, body.memberId()).eq(UnModuleTeamer::getRoleCode, body.roleCode())) > 0)
            conflict("团队成员已存在");
        UnModuleTeamer row = new UnModuleTeamer();
        row.setTenantId(tenantId);
        row.setModuleId(moduleId);
        row.setRecordId(recordId);
        row.setMemberId(body.memberId());
        row.setRoleCode(body.roleCode());
        teamers.insert(row);
        return teamers.selectById(row.getId());
    }

    @DeleteMapping("/records/{recordId}/teamers/{id}")
    public void deleteTeamer(@PathVariable long moduleId, @PathVariable long recordId, @PathVariable long id,
                             @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        record(tenantId, moduleId, recordId);
        UnModuleTeamer row = teamers.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getRecordId().equals(recordId)) missing("团队成员不存在");
        teamers.deleteById(id);
    }

    /** 绑定审批前必须验证定义属于同租户且已经启用。 */

    private UnModuleRecord record(long tenantId, long moduleId, long recordId) {
        support.module(tenantId, moduleId);
        UnModuleRecord row = records.selectById(recordId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || row.getRecordStatus() == 2) missing("记录不存在");
        return row;
    }

    private void write(HttpServletRequest request, long tenantId, long moduleId) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void missing(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    public record TeamerForm(@NotNull Long memberId, @NotBlank String roleCode) { }
}
