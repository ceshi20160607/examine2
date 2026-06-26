package com.unique.examine.plat.manage.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.service.PlatAccountBaseService;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import com.unique.examine.plat.manage.member.MemberModels.AccountBindingRequest;
import com.unique.examine.plat.manage.member.MemberModels.AccountBindingVO;
import com.unique.examine.plat.manage.member.MemberModels.MemberQueryRequest;
import com.unique.examine.plat.manage.member.MemberModels.MemberSaveRequest;
import com.unique.examine.plat.manage.member.MemberModels.MemberVO;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * System member service backed by persisted member, binding and role-member tables.
 */
@Service
public class MemberService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;

    private final SystemMemberContextResolver contextResolver;
    private final PlatMemberBaseService memberBaseService;
    private final PlatAccountBaseService accountBaseService;
    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;

    public MemberService(SystemMemberContextResolver contextResolver,
                         PlatMemberBaseService memberBaseService,
                         PlatAccountBaseService accountBaseService,
                         PlatAccountMemberBindingBaseService bindingBaseService,
                         PlatRoleMemberBaseService roleMemberBaseService) {
        this.contextResolver = contextResolver;
        this.memberBaseService = memberBaseService;
        this.accountBaseService = accountBaseService;
        this.bindingBaseService = bindingBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
    }

    /**
     * Search members.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query request
     * @return member page
     */
    public PageResult<MemberVO> search(String systemId, PageRequest pageRequest, MemberQueryRequest query) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        int pageNo = pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        List<MemberVO> matched = memberBaseService.list(memberQuery(context, query)
                        .orderByDesc(PlatMember::getUpdatedAt)
                        .orderByAsc(PlatMember::getId))
                .stream()
                .map(member -> toVO(context, member))
                .filter(member -> matchesBinding(member, query == null ? null : query.bindingStatus()))
                .filter(member -> matchesRole(member, query == null ? null : query.roleId()))
                .toList();
        int start = Math.min((pageNo - 1) * pageSize, matched.size());
        int end = Math.min(start + pageSize, matched.size());
        return new PageResult<>(matched.subList(start, end), pageNo, pageSize, matched.size(), end < matched.size());
    }

    /**
     * Create a member.
     *
     * @param systemId system id
     * @param request save request
     * @return member
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberVO create(String systemId, MemberSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        requireText(request == null ? null : request.memberName(), "成员姓名不能为空");
        PlatMember member = new PlatMember();
        member.setSystemId(context.systemId());
        member.setTenantId(context.tenantId());
        member.setDeptId(parseNullableId(request.deptId()));
        member.setMemberName(request.memberName());
        member.setEmployeeNo(request.employeeNo());
        member.setMobile(request.mobile());
        member.setEmail(request.email());
        member.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        member.setDeleted(DELETED_NO);
        memberBaseService.saveEntity(member);
        replaceRoles(context, member.getId(), request.roleIds());
        return toVO(context, member);
    }

    /**
     * Update a member.
     *
     * @param systemId system id
     * @param systemMemberId member id
     * @param request save request
     * @return member
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberVO update(String systemId, String systemMemberId, MemberSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatMember member = requireMember(context, systemMemberId);
        if (StringUtils.hasText(request.memberName())) {
            member.setMemberName(request.memberName());
        }
        member.setDeptId(parseNullableId(request.deptId()));
        member.setEmployeeNo(request.employeeNo());
        member.setMobile(request.mobile());
        member.setEmail(request.email());
        member.setStatus(Objects.isNull(request.status()) ? member.getStatus() : request.status());
        member.setUpdatedAt(LocalDateTime.now());
        memberBaseService.updateById(member);
        if (Objects.nonNull(request.roleIds())) {
            replaceRoles(context, member.getId(), request.roleIds());
        }
        return toVO(context, member);
    }

    /**
     * Bind a system member to an account.
     *
     * @param systemId system id
     * @param systemMemberId member id
     * @param request binding request
     * @return binding result
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountBindingVO bindAccount(String systemId, String systemMemberId, AccountBindingRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatMember member = requireMember(context, systemMemberId);
        PlatAccount account = resolveAccount(request);
        PlatAccountMemberBinding binding = bindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getAccountId, account.getId())
                        .eq(PlatAccountMemberBinding::getSystemId, context.systemId())
                        .eq(PlatAccountMemberBinding::getTenantId, context.tenantId())
                        .eq(PlatAccountMemberBinding::getSystemMemberId, member.getId())
                        .last("LIMIT 1"), false);
        if (Objects.isNull(binding)) {
            binding = new PlatAccountMemberBinding();
            binding.setAccountId(account.getId());
            binding.setSystemId(context.systemId());
            binding.setTenantId(context.tenantId());
            binding.setSystemMemberId(member.getId());
            binding.setCreatedAt(LocalDateTime.now());
        }
        binding.setBindingStatus(ENABLED);
        binding.setUpdatedAt(LocalDateTime.now());
        if (Objects.isNull(binding.getId())) {
            bindingBaseService.saveEntity(binding);
        } else {
            bindingBaseService.updateById(binding);
        }
        return toBindingVO(binding);
    }

    /**
     * Return member bindings.
     *
     * @param systemId system id
     * @return bindings
     */
    public List<AccountBindingVO> bindings(String systemId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        return bindingBaseService.list(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getSystemId, context.systemId())
                        .eq(PlatAccountMemberBinding::getTenantId, context.tenantId())
                        .orderByDesc(PlatAccountMemberBinding::getUpdatedAt))
                .stream()
                .map(this::toBindingVO)
                .toList();
    }

    private LambdaQueryWrapper<PlatMember> memberQuery(SystemMemberContext context, MemberQueryRequest query) {
        LambdaQueryWrapper<PlatMember> wrapper = new LambdaQueryWrapper<PlatMember>()
                .eq(PlatMember::getSystemId, context.systemId())
                .eq(PlatMember::getTenantId, context.tenantId())
                .eq(PlatMember::getDeleted, DELETED_NO);
        if (Objects.nonNull(query)) {
            Long deptId = parseNullableId(query.departmentId());
            if (Objects.nonNull(deptId)) {
                wrapper.eq(PlatMember::getDeptId, deptId);
            }
            if (Objects.nonNull(query.status())) {
                wrapper.eq(PlatMember::getStatus, query.status());
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(PlatMember::getMemberName, query.keyword())
                        .or().like(PlatMember::getEmployeeNo, query.keyword())
                        .or().like(PlatMember::getMobile, query.keyword())
                        .or().like(PlatMember::getEmail, query.keyword()));
            }
        }
        return wrapper;
    }

    private PlatMember requireMember(SystemMemberContext context, String systemMemberId) {
        Long id = contextResolver.parseRequiredId(systemMemberId, "成员ID格式不正确");
        PlatMember member = memberBaseService.getOne(new LambdaQueryWrapper<PlatMember>()
                .eq(PlatMember::getId, id)
                .eq(PlatMember::getSystemId, context.systemId())
                .eq(PlatMember::getTenantId, context.tenantId())
                .eq(PlatMember::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(member)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "成员不存在");
        }
        return member;
    }

    private PlatAccount resolveAccount(AccountBindingRequest request) {
        Long accountId = parseNullableId(request == null ? null : request.accountId());
        LambdaQueryWrapper<PlatAccount> wrapper = new LambdaQueryWrapper<PlatAccount>()
                .eq(PlatAccount::getDeleted, DELETED_NO);
        if (Objects.nonNull(accountId)) {
            wrapper.eq(PlatAccount::getId, accountId);
        } else if (StringUtils.hasText(request == null ? null : request.loginName())) {
            String loginName = request.loginName();
            wrapper.and(value -> value.eq(PlatAccount::getAccountName, loginName)
                    .or().eq(PlatAccount::getMobile, loginName)
                    .or().eq(PlatAccount::getEmail, loginName));
        } else {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "账号ID或登录名不能为空");
        }
        PlatAccount account = accountBaseService.getOne(wrapper.last("LIMIT 1"), false);
        if (Objects.isNull(account)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "账号不存在");
        }
        return account;
    }

    private void replaceRoles(SystemMemberContext context, Long memberId, List<String> roleIds) {
        roleMemberBaseService.remove(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getSystemId, context.systemId())
                .eq(PlatRoleMember::getTenantId, context.tenantId())
                .eq(PlatRoleMember::getSystemMemberId, memberId));
        for (String roleId : safeList(roleIds)) {
            PlatRoleMember roleMember = new PlatRoleMember();
            roleMember.setRoleId(contextResolver.parseRequiredId(roleId, "角色ID格式不正确"));
            roleMember.setSystemMemberId(memberId);
            roleMember.setSystemId(context.systemId());
            roleMember.setTenantId(context.tenantId());
            roleMember.setCreatedAt(LocalDateTime.now());
            roleMemberBaseService.saveEntity(roleMember);
        }
    }

    private MemberVO toVO(SystemMemberContext context, PlatMember member) {
        return new MemberVO(String.valueOf(member.getId()), String.valueOf(member.getSystemId()),
                String.valueOf(member.getTenantId()), Objects.isNull(member.getDeptId()) ? null : String.valueOf(member.getDeptId()),
                member.getMemberName(), member.getEmployeeNo(), member.getMobile(), member.getEmail(),
                member.getStatus(), bindingStatus(context, member.getId()), roleIds(context, member.getId()),
                member.getUpdatedAt());
    }

    private String bindingStatus(SystemMemberContext context, Long memberId) {
        return bindingBaseService.count(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getSystemId, context.systemId())
                .eq(PlatAccountMemberBinding::getTenantId, context.tenantId())
                .eq(PlatAccountMemberBinding::getSystemMemberId, memberId)
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)) > 0 ? "BOUND" : "UNBOUND";
    }

    private List<String> roleIds(SystemMemberContext context, Long memberId) {
        Set<String> roleIds = new LinkedHashSet<>();
        for (PlatRoleMember roleMember : roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getSystemId, context.systemId())
                .eq(PlatRoleMember::getTenantId, context.tenantId())
                .eq(PlatRoleMember::getSystemMemberId, memberId))) {
            roleIds.add(String.valueOf(roleMember.getRoleId()));
        }
        return roleIds.stream().toList();
    }

    private boolean matchesBinding(MemberVO member, String bindingStatus) {
        return !StringUtils.hasText(bindingStatus) || bindingStatus.equals(member.bindingStatus());
    }

    private boolean matchesRole(MemberVO member, String roleId) {
        return !StringUtils.hasText(roleId) || member.roleIds().contains(roleId);
    }

    private AccountBindingVO toBindingVO(PlatAccountMemberBinding binding) {
        return new AccountBindingVO(String.valueOf(binding.getId()), String.valueOf(binding.getAccountId()),
                String.valueOf(binding.getSystemMemberId()),
                Objects.equals(binding.getBindingStatus(), ENABLED) ? "BOUND" : "DISABLED",
                Objects.equals(binding.getBindingStatus(), ENABLED) ? null : "绑定已停用", binding.getUpdatedAt());
    }

    private Long parseNullableId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> safeList(List<String> values) {
        return Objects.isNull(values) ? List.of() : values;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }
}
