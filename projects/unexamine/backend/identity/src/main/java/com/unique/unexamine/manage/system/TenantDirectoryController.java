package com.unique.unexamine.manage.system;

import com.unique.unexamine.identity.base.entity.UnSysDepartment;
import com.unique.unexamine.identity.base.entity.UnSysMember;
import com.unique.unexamine.identity.base.entity.UnSysTenant;
import com.unique.unexamine.identity.base.entity.UnSysTenantMember;
import com.unique.unexamine.identity.base.mapper.UnSysDepartmentMapper;
import com.unique.unexamine.identity.base.mapper.UnSysMemberMapper;
import com.unique.unexamine.identity.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.identity.base.mapper.UnSysTenantMemberMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 租户管理员维护租户内部门与成员归属。 */
@RestController
@RequestMapping("/api/system/tenants/{tenantId}")
public class TenantDirectoryController {
    private final UnSysTenantMapper tenants;
    private final UnSysMemberMapper members;
    private final UnSysTenantMemberMapper tenantMembers;
    private final UnSysDepartmentMapper departments;
    private final SessionAccess access;

    public TenantDirectoryController(UnSysTenantMapper tenants, UnSysMemberMapper members,
                                     UnSysTenantMemberMapper tenantMembers, UnSysDepartmentMapper departments,
                                     SessionAccess access) {
        this.tenants = tenants;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.departments = departments;
        this.access = access;
    }

    /** 所有有效租户成员可读取所在租户的部门树。 */
    @GetMapping("/departments")
    public List<UnSysDepartment> departments(@PathVariable long tenantId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        return departments.selectList(new LambdaQueryWrapper<UnSysDepartment>()
                .eq(UnSysDepartment::getTenantId, tenantId)
                .orderByAsc(UnSysDepartment::getParentId, UnSysDepartment::getSortNo, UnSysDepartment::getId));
    }

    /** 租户部门编码只在当前租户内唯一。 */
    @PostMapping("/departments")
    public UnSysDepartment createDepartment(@PathVariable long tenantId,
                                            @Valid @RequestBody DepartmentInput input,
                                            HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        checkDepartmentCode(tenantId, input.departmentCode(), null);
        checkParent(tenantId, input.parentId(), null);
        UnSysDepartment item = new UnSysDepartment();
        item.setTenantId(tenantId);
        fillDepartment(item, input, tenantId);
        item.setStatus((byte) 1);
        departments.insert(item);
        return item;
    }

    /** 调整部门上级时，父级与负责人都必须属于当前租户。 */
    @PutMapping("/departments/{id}")
    public UnSysDepartment updateDepartment(@PathVariable long tenantId, @PathVariable long id,
                                            @Valid @RequestBody DepartmentInput input,
                                            HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysDepartment item = department(tenantId, id);
        checkDepartmentCode(tenantId, input.departmentCode(), id);
        checkParent(tenantId, input.parentId(), id);
        fillDepartment(item, input, tenantId);
        departments.updateById(item);
        return item;
    }

    /** 停用部门保留已发生的业务归属。 */
    @PutMapping("/departments/{id}/status")
    public UnSysDepartment departmentStatus(@PathVariable long tenantId, @PathVariable long id,
                                            @RequestBody StatusInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysDepartment item = department(tenantId, id);
        item.setStatus(status(input.status()));
        departments.updateById(item);
        return item;
    }

    /** 租户成员列表按范围分页，并限制可排序字段。 */
    @GetMapping("/members")
    public IPage<UnSysTenantMember> members(@PathVariable long tenantId,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(defaultValue = "createTime") String sort,
                                            @RequestParam(required = false) Long departmentId,
                                            HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        LambdaQueryWrapper<UnSysTenantMember> query = new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(departmentId != null, UnSysTenantMember::getDepartmentId, departmentId);
        switch (sort) {
            case "memberNo" -> query.orderByAsc(UnSysTenantMember::getMemberNo);
            case "joinTime" -> query.orderByDesc(UnSysTenantMember::getJoinTime);
            case "createTime" -> query.orderByDesc(UnSysTenantMember::getCreateTime);
            default -> throw bad("不支持的租户成员排序字段");
        }
        return tenantMembers.selectPage(paging(page, size), query);
    }

    /** 租户管理员只能加入本系统已有成员，不能用请求指定别的系统身份。 */
    @PostMapping("/members")
    public UnSysTenantMember addMember(@PathVariable long tenantId, @Valid @RequestBody MemberInput input,
                                       HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysTenant tenant = tenant(tenantId);
        UnSysMember member = members.selectById(input.memberId());
        if (member == null || !tenant.getSystemId().equals(member.getSystemId())) throw bad("系统成员不属于该系统");
        if (tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, input.memberId())) > 0) throw conflict("成员已加入租户");
        checkMemberFields(tenantId, input, null);
        UnSysTenantMember item = new UnSysTenantMember();
        item.setTenantId(tenantId);
        item.setMemberId(input.memberId());
        fillMember(item, input);
        item.setStatus((byte) 1);
        tenantMembers.insert(item);
        return item;
    }

    /** 修改成员租户资料，不改变其系统登录身份。 */
    @PutMapping("/members/{id}")
    public UnSysTenantMember updateMember(@PathVariable long tenantId, @PathVariable long id,
                                          @RequestBody MemberProfileInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnSysTenantMember item = tenantMember(tenantId, id);
        checkMemberFields(tenantId, new MemberInput(item.getMemberId(), input.departmentId(),
                input.memberNo(), input.positionName(), input.leaderTenantMemberId()), id);
        item.setDepartmentId(input.departmentId());
        item.setMemberNo(input.memberNo());
        item.setPositionName(input.positionName());
        item.setLeaderTenantMemberId(input.leaderTenantMemberId());
        tenantMembers.updateById(item);
        return item;
    }

    /** 管理员不能停用当前登录的租户身份。 */
    @PutMapping("/members/{id}/status")
    public UnSysTenantMember memberStatus(@PathVariable long tenantId, @PathVariable long id,
                                          @RequestBody StatusInput input, HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantAdmin(request, tenantId);
        if (actor.getId() == id && input.status() == 0) throw bad("不能停用当前租户管理员");
        UnSysTenantMember item = tenantMember(tenantId, id);
        item.setStatus(status(input.status()));
        tenantMembers.updateById(item);
        return item;
    }

    private void fillDepartment(UnSysDepartment item, DepartmentInput input, long tenantId) {
        if (input.managerTenantMemberId() != null) tenantMember(tenantId, input.managerTenantMemberId());
        item.setParentId(input.parentId() == null ? 0L : input.parentId());
        item.setDepartmentCode(input.departmentCode().trim());
        item.setName(input.name().trim());
        item.setManagerTenantMemberId(input.managerTenantMemberId());
        item.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        item.setRemark(input.remark());
    }

    private void fillMember(UnSysTenantMember item, MemberInput input) {
        item.setDepartmentId(input.departmentId());
        item.setMemberNo(input.memberNo());
        item.setPositionName(input.positionName());
        item.setLeaderTenantMemberId(input.leaderTenantMemberId());
    }

    private void checkMemberFields(long tenantId, MemberInput input, Long currentId) {
        if (input.departmentId() != null) department(tenantId, input.departmentId());
        if (input.leaderTenantMemberId() != null) {
            if (input.leaderTenantMemberId().equals(currentId)) throw bad("成员不能是自己的上级");
            tenantMember(tenantId, input.leaderTenantMemberId());
        }
        if (input.memberNo() != null && !input.memberNo().isBlank()) {
            LambdaQueryWrapper<UnSysTenantMember> query = new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getMemberNo, input.memberNo());
            if (currentId != null) query.ne(UnSysTenantMember::getId, currentId);
            if (tenantMembers.selectCount(query) > 0) throw conflict("租户成员编号已存在");
        }
    }

    private void checkDepartmentCode(long tenantId, String code, Long currentId) {
        LambdaQueryWrapper<UnSysDepartment> query = new LambdaQueryWrapper<UnSysDepartment>()
                .eq(UnSysDepartment::getTenantId, tenantId).eq(UnSysDepartment::getDepartmentCode, code.trim());
        if (currentId != null) query.ne(UnSysDepartment::getId, currentId);
        if (departments.selectCount(query) > 0) throw conflict("租户部门编码已存在");
    }

    private void checkParent(long tenantId, Long parentId, Long currentId) {
        long next = parentId == null ? 0 : parentId;
        for (int depth = 0; next != 0; depth++) {
            if (depth > 100 || currentId != null && next == currentId) throw bad("部门层级存在循环");
            UnSysDepartment parent = department(tenantId, next);
            next = parent.getParentId() == null ? 0 : parent.getParentId();
        }
    }

    private UnSysTenant tenant(long id) {
        UnSysTenant item = tenants.selectById(id);
        if (item == null) throw missing("租户不存在");
        return item;
    }

    private UnSysDepartment department(long tenantId, long id) {
        UnSysDepartment item = departments.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) throw missing("租户部门不存在");
        return item;
    }

    private UnSysTenantMember tenantMember(long tenantId, long id) {
        UnSysTenantMember item = tenantMembers.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) throw missing("租户成员不存在");
        return item;
    }

    private byte status(byte value) {
        if (value != 0 && value != 1) throw bad("状态只能是0或1");
        return value;
    }

    private <T> Page<T> paging(long page, long size) {
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        return new Page<>(page, size);
    }

    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }
    private ResponseStatusException missing(String text) { return new ResponseStatusException(HttpStatus.NOT_FOUND, text); }

    /** 部门维护请求只接收当前租户内的关联主键。 */
    public record DepartmentInput(Long parentId, @NotBlank @Size(max = 64) String departmentCode,
                                  @NotBlank @Size(max = 100) String name, Long managerTenantMemberId,
                                  Integer sortNo, String remark) {}
    public record MemberInput(long memberId, Long departmentId, String memberNo,
                              String positionName, Long leaderTenantMemberId) {}
    public record MemberProfileInput(Long departmentId, String memberNo,
                                     String positionName, Long leaderTenantMemberId) {}
    public record StatusInput(byte status) {}
}
