package com.unique.unexamine.manage.platform;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.base.entity.UnPlatAccount;
import com.unique.unexamine.base.entity.UnPlatConfig;
import com.unique.unexamine.base.entity.UnPlatDepartment;
import com.unique.unexamine.base.entity.UnPlatMember;
import com.unique.unexamine.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.base.mapper.UnPlatConfigMapper;
import com.unique.unexamine.base.mapper.UnPlatDepartmentMapper;
import com.unique.unexamine.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

/** 平台账号、组织成员及基础配置接口；所有写操作需要平台管理员会话。 */
@RestController
@RequestMapping("/api/platform")
public class PlatformDirectoryController {
    private final UnPlatAccountMapper accounts;
    private final UnPlatConfigMapper configs;
    private final UnPlatDepartmentMapper departments;
    private final UnPlatMemberMapper members;
    private final SessionAccess access;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PlatformDirectoryController(UnPlatAccountMapper accounts, UnPlatConfigMapper configs,
                                       UnPlatDepartmentMapper departments, UnPlatMemberMapper members,
                                       SessionAccess access) {
        this.accounts = accounts;
        this.configs = configs;
        this.departments = departments;
        this.members = members;
        this.access = access;
    }

    /** 账号列表不返回密码摘要，排序字段由接口明确允许。 */
    @GetMapping("/accounts")
    public IPage<AccountView> accounts(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(defaultValue = "createTime") String sort,
                                       @RequestParam(required = false) String keyword,
                                       HttpServletRequest request) {
        access.platformAdmin(request);
        LambdaQueryWrapper<UnPlatAccount> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            query.and(q -> q.like(UnPlatAccount::getAccount, keyword.trim())
                    .or().like(UnPlatAccount::getDisplayName, keyword.trim()));
        }
        switch (sort) {
            case "account" -> query.orderByAsc(UnPlatAccount::getAccount);
            case "displayName" -> query.orderByAsc(UnPlatAccount::getDisplayName);
            case "createTime" -> query.orderByDesc(UnPlatAccount::getCreateTime);
            default -> throw bad("不支持的账号排序字段");
        }
        return accounts.selectPage(paging(page, size), query).convert(this::accountView);
    }

    /** 新建账号时只接收登录信息，服务端生成密码摘要。 */
    @PostMapping("/accounts")
    public AccountView createAccount(@Valid @RequestBody AccountInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        if (accounts.selectCount(new LambdaQueryWrapper<UnPlatAccount>().eq(UnPlatAccount::getAccount, input.account().trim())) > 0) {
            throw conflict("登录账号已存在");
        }
        if (input.password().length() < 12) throw bad("密码至少12位");
        UnPlatAccount item = new UnPlatAccount();
        item.setAccount(input.account().trim());
        item.setPasswordHash(encoder.encode(input.password()));
        item.setDisplayName(input.displayName().trim());
        item.setStatus((byte) 1);
        accounts.insert(item);
        return accountView(item);
    }

    /** 修改账号显示名不允许修改登录名和密码摘要。 */
    @PutMapping("/accounts/{id}")
    public AccountView updateAccount(@PathVariable long id, @Valid @RequestBody NameInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatAccount item = account(id);
        item.setDisplayName(input.name().trim());
        accounts.updateById(item);
        return accountView(item);
    }

    /** 停用当前账号前检查会话，避免管理员误锁死自己的入口。 */
    @PutMapping("/accounts/{id}/status")
    public AccountView accountStatus(@PathVariable long id, @RequestBody StatusInput input, HttpServletRequest request) {
        UnPlatAccount actor = access.platformAdmin(request);
        if (id == actor.getId() && input.status() == 0) throw bad("不能停用当前登录账号");
        UnPlatAccount item = account(id);
        item.setStatus(status(input.status()));
        accounts.updateById(item);
        return accountView(item);
    }

    /** 密码仅写入摘要，响应不回传密码。 */
    @PutMapping("/accounts/{id}/password")
    public void resetPassword(@PathVariable long id, @Valid @RequestBody PasswordInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        if (input.password().length() < 12) throw bad("密码至少12位");
        UnPlatAccount item = account(id);
        item.setPasswordHash(encoder.encode(input.password()));
        accounts.updateById(item);
    }

    /** 平台部门按父级和排序号返回，树形关系由 parentId 表达。 */
    @GetMapping("/departments")
    public List<UnPlatDepartment> departments(HttpServletRequest request) {
        access.platformAccount(request);
        return departments.selectList(new LambdaQueryWrapper<UnPlatDepartment>()
                .orderByAsc(UnPlatDepartment::getParentId, UnPlatDepartment::getSortNo, UnPlatDepartment::getId));
    }

    /** 创建部门前检查上级部门存在。 */
    @PostMapping("/departments")
    public UnPlatDepartment createDepartment(@Valid @RequestBody DepartmentInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        checkParent(input.parentId(), null);
        ensureDepartmentCode(input.departmentCode(), null);
        UnPlatDepartment item = new UnPlatDepartment();
        fillDepartment(item, input);
        departments.insert(item);
        return item;
    }

    /** 调整部门上级时拒绝形成部门循环。 */
    @PutMapping("/departments/{id}")
    public UnPlatDepartment updateDepartment(@PathVariable long id, @Valid @RequestBody DepartmentInput input,
                                             HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatDepartment item = department(id);
        checkParent(input.parentId(), id);
        ensureDepartmentCode(input.departmentCode(), id);
        fillDepartment(item, input);
        departments.updateById(item);
        return item;
    }

    /** 部门停用保留历史成员归属。 */
    @PutMapping("/departments/{id}/status")
    public UnPlatDepartment departmentStatus(@PathVariable long id, @RequestBody StatusInput input,
                                             HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatDepartment item = department(id);
        item.setStatus(status(input.status()));
        departments.updateById(item);
        return item;
    }

    /** 平台成员分页查询，姓名和编号可排序。 */
    @GetMapping("/members")
    public IPage<UnPlatMember> members(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(defaultValue = "createTime") String sort,
                                      @RequestParam(required = false) Long departmentId,
                                      @RequestParam(required = false) String keyword,
                                      HttpServletRequest request) {
        access.platformAdmin(request);
        LambdaQueryWrapper<UnPlatMember> query = new LambdaQueryWrapper<>();
        query.eq(departmentId != null, UnPlatMember::getDepartmentId, departmentId);
        if (keyword != null && !keyword.isBlank()) {
            query.and(q -> q.like(UnPlatMember::getRealName, keyword.trim())
                    .or().like(UnPlatMember::getMemberNo, keyword.trim()));
        }
        switch (sort) {
            case "memberNo" -> query.orderByAsc(UnPlatMember::getMemberNo);
            case "realName" -> query.orderByAsc(UnPlatMember::getRealName);
            case "createTime" -> query.orderByDesc(UnPlatMember::getCreateTime);
            default -> throw bad("不支持的成员排序字段");
        }
        return members.selectPage(paging(page, size), query);
    }

    /** 成员资料和登录账号独立保存；同一个账号只能关联一个成员。 */
    @PostMapping("/members")
    public UnPlatMember createMember(@Valid @RequestBody MemberInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        checkMemberRefs(input, null);
        if (members.selectCount(new LambdaQueryWrapper<UnPlatMember>().eq(UnPlatMember::getMemberNo, input.memberNo())) > 0)
            throw conflict("成员编号已存在");
        UnPlatMember item = new UnPlatMember();
        fillMember(item, input);
        members.insert(item);
        return item;
    }

    /** 修改成员时重新检查账号、部门和上级归属。 */
    @PutMapping("/members/{id}")
    public UnPlatMember updateMember(@PathVariable long id, @Valid @RequestBody MemberInput input,
                                     HttpServletRequest request) {
        access.platformAdmin(request);
        UnPlatMember item = member(id);
        checkMemberRefs(input, id);
        Long duplicate = members.selectCount(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getMemberNo, input.memberNo()).ne(UnPlatMember::getId, id));
        if (duplicate > 0) throw conflict("成员编号已存在");
        fillMember(item, input);
        members.updateById(item);
        return item;
    }

    /** 停用成员保留其历史授权和业务操作记录。 */
    @PutMapping("/members/{id}/status")
    public UnPlatMember memberStatus(@PathVariable long id, @RequestBody StatusInput input,
                                     HttpServletRequest request) {
        UnPlatAccount actor = access.platformAdmin(request);
        UnPlatMember item = member(id);
        if (actor.getId().equals(item.getAccountId()) && input.status() == 0)
            throw bad("不能停用当前管理员成员");
        item.setStatus(status(input.status()));
        members.updateById(item);
        return item;
    }

    /** 平台配置以唯一键保存，避免为简单设置增加抽象层。 */
    @GetMapping("/config")
    public List<UnPlatConfig> configs(HttpServletRequest request) {
        access.platformAdmin(request);
        return configs.selectList(new LambdaQueryWrapper<UnPlatConfig>().orderByAsc(UnPlatConfig::getConfigKey));
    }

    /** 已存在的配置按键更新，不存在则创建。 */
    @PutMapping("/config/{key}")
    public UnPlatConfig saveConfig(@PathVariable String key, @RequestBody ConfigInput input,
                                   HttpServletRequest request) {
        access.platformAdmin(request);
        if (key.isBlank() || key.length() > 100) throw bad("配置键无效");
        UnPlatConfig item = configs.selectOne(new LambdaQueryWrapper<UnPlatConfig>()
                .eq(UnPlatConfig::getConfigKey, key));
        if (item == null) {
            item = new UnPlatConfig();
            item.setConfigKey(key);
            item.setConfigValue(input.value());
            item.setDescription(input.description());
            configs.insert(item);
        } else {
            item.setConfigValue(input.value());
            item.setDescription(input.description());
            configs.updateById(item);
        }
        return item;
    }

    private UnPlatAccount account(long id) {
        UnPlatAccount item = accounts.selectById(id);
        if (item == null) throw missing("平台账号不存在");
        return item;
    }

    private UnPlatDepartment department(long id) {
        UnPlatDepartment item = departments.selectById(id);
        if (item == null) throw missing("平台部门不存在");
        return item;
    }

    private UnPlatMember member(long id) {
        UnPlatMember item = members.selectById(id);
        if (item == null) throw missing("平台成员不存在");
        return item;
    }

    private void checkParent(Long parentId, Long currentId) {
        long next = parentId == null ? 0 : parentId;
        for (int depth = 0; next != 0; depth++) {
            if (depth > 100 || currentId != null && next == currentId) throw bad("部门层级存在循环");
            UnPlatDepartment parent = department(next);
            next = parent.getParentId() == null ? 0 : parent.getParentId();
        }
    }

    private void ensureDepartmentCode(String code, Long currentId) {
        LambdaQueryWrapper<UnPlatDepartment> query = new LambdaQueryWrapper<UnPlatDepartment>()
                .eq(UnPlatDepartment::getDepartmentCode, code.trim());
        if (currentId != null) query.ne(UnPlatDepartment::getId, currentId);
        if (departments.selectCount(query) > 0) throw conflict("部门编码已存在");
    }

    private void fillDepartment(UnPlatDepartment item, DepartmentInput input) {
        if (input.managerMemberId() != null) member(input.managerMemberId());
        item.setParentId(input.parentId() == null ? 0L : input.parentId());
        item.setDepartmentCode(input.departmentCode().trim());
        item.setName(input.name().trim());
        item.setManagerMemberId(input.managerMemberId());
        item.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        item.setRemark(input.remark());
    }

    private void checkMemberRefs(MemberInput input, Long currentId) {
        if (input.departmentId() != null) department(input.departmentId());
        if (input.leaderMemberId() != null) {
            if (input.leaderMemberId().equals(currentId)) throw bad("成员不能是自己的上级");
            member(input.leaderMemberId());
        }
        if (input.accountId() != null) {
            account(input.accountId());
            LambdaQueryWrapper<UnPlatMember> query = new LambdaQueryWrapper<UnPlatMember>()
                    .eq(UnPlatMember::getAccountId, input.accountId());
            if (currentId != null) query.ne(UnPlatMember::getId, currentId);
            if (members.selectCount(query) > 0) throw conflict("平台账号已关联其他成员");
        }
    }

    private void fillMember(UnPlatMember item, MemberInput input) {
        item.setAccountId(input.accountId());
        item.setMemberNo(input.memberNo().trim());
        item.setRealName(input.realName().trim());
        item.setDepartmentId(input.departmentId());
        item.setLeaderMemberId(input.leaderMemberId());
        item.setMobile(input.mobile());
        item.setEmail(input.email());
        item.setGender(input.gender() == null ? (byte) 0 : statusGender(input.gender()));
        item.setPositionName(input.positionName());
        if (item.getStatus() == null) item.setStatus((byte) 1);
    }

    private byte statusGender(byte value) {
        if (value < 0 || value > 2) throw bad("性别取值无效");
        return value;
    }

    private byte status(byte value) {
        if (value != 0 && value != 1) throw bad("状态只能是0或1");
        return value;
    }

    private <T> Page<T> paging(long page, long size) {
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        return new Page<>(page, size);
    }

    private AccountView accountView(UnPlatAccount item) {
        return new AccountView(item.getId(), item.getAccount(), item.getDisplayName(), item.getStatus());
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    /** 账号请求只允许公开字段，避免客户端写入主键和密码摘要。 */
    public record AccountInput(@NotBlank @Size(max = 64) String account,
                               @NotBlank String password,
                               @NotBlank @Size(max = 100) String displayName) {}
    public record AccountView(Long id, String account, String displayName, Byte status) {}
    public record NameInput(@NotBlank @Size(max = 100) String name) {}
    public record PasswordInput(@NotBlank String password) {}
    public record StatusInput(byte status) {}
    public record DepartmentInput(Long parentId, @NotBlank @Size(max = 64) String departmentCode,
                                  @NotBlank @Size(max = 100) String name, Long managerMemberId,
                                  Integer sortNo, String remark) {}
    public record MemberInput(Long accountId, @NotBlank @Size(max = 64) String memberNo,
                              @NotBlank @Size(max = 100) String realName, Long departmentId,
                              Long leaderMemberId, String mobile, String email, Byte gender,
                              String positionName) {}
    public record ConfigInput(String value, String description) {}
}
