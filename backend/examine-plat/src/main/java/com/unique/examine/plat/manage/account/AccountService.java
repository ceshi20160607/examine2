package com.unique.examine.plat.manage.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.messagelog.base.entity.AuditLoginLog;
import com.unique.examine.messagelog.base.service.AuditLoginLogBaseService;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatSystem;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatAccountBaseService;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatSystemBaseService;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import com.unique.examine.plat.manage.account.AccountModels.AccountActionResult;
import com.unique.examine.plat.manage.account.AccountModels.AccountProfileVO;
import com.unique.examine.plat.manage.account.AccountModels.LoginLogVO;
import com.unique.examine.plat.manage.account.AccountModels.PasswordUpdateRequest;
import com.unique.examine.plat.manage.account.AccountModels.ProfileUpdateRequest;
import com.unique.examine.plat.manage.account.AccountModels.SystemAccessSummary;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Account profile service backed by persisted platform identity tables.
 */
@Service
public class AccountService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_SYSTEM = "SYSTEM";

    private final CurrentAccountProvider currentAccountProvider;
    private final PlatAccountBaseService accountBaseService;
    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final PlatSystemBaseService systemBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final AuditLoginLogBaseService auditLoginLogBaseService;
    private final PasswordEncoder passwordEncoder;

    public AccountService(CurrentAccountProvider currentAccountProvider,
                          PlatAccountBaseService accountBaseService,
                          PlatAccountMemberBindingBaseService bindingBaseService,
                          PlatSystemBaseService systemBaseService,
                          PlatTenantBaseService tenantBaseService,
                          PlatRoleMemberBaseService roleMemberBaseService,
                          PlatRoleBaseService roleBaseService,
                          AuditLoginLogBaseService auditLoginLogBaseService,
                          PasswordEncoder passwordEncoder) {
        this.currentAccountProvider = currentAccountProvider;
        this.accountBaseService = accountBaseService;
        this.bindingBaseService = bindingBaseService;
        this.systemBaseService = systemBaseService;
        this.tenantBaseService = tenantBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.roleBaseService = roleBaseService;
        this.auditLoginLogBaseService = auditLoginLogBaseService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Return the current account profile and authorized systems.
     *
     * @return account profile
     */
    public AccountProfileVO currentProfile() {
        PlatAccount account = currentAccountProvider.currentAccount();
        return new AccountProfileVO(
                String.valueOf(account.getId()),
                account.getAccountName(),
                account.getMobile(),
                account.getEmail(),
                "NORMAL",
                systemAccessSummaries(account.getId()),
                roleCodes(platformRoles(account.getId()))
        );
    }

    /**
     * Update account profile fields.
     *
     * @param request update request
     * @return action result
     */
    public AccountActionResult updateProfile(ProfileUpdateRequest request) {
        PlatAccount account = currentAccountProvider.currentAccount();
        if (StringUtils.hasText(request.accountName())) {
            account.setAccountName(request.accountName());
        }
        if (StringUtils.hasText(request.mobile())) {
            account.setMobile(request.mobile());
        }
        if (StringUtils.hasText(request.email())) {
            account.setEmail(request.email());
        }
        account.setUpdatedAt(LocalDateTime.now());
        accountBaseService.updateById(account);
        return actionResult("PROFILE_UPDATED");
    }

    /**
     * Update account password.
     *
     * @param request password request
     * @return action result
     */
    public AccountActionResult updatePassword(PasswordUpdateRequest request) {
        PlatAccount account = currentAccountProvider.currentAccount();
        if (!StringUtils.hasText(request.oldPassword()) || !StringUtils.hasText(request.newPassword())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "旧密码和新密码不能为空");
        }
        if (!passwordEncoder.matches(request.oldPassword(), account.getPasswordHash())) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED, "旧密码不正确");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setUpdatedAt(LocalDateTime.now());
        accountBaseService.updateById(account);
        return actionResult("PASSWORD_UPDATED");
    }

    /**
     * Query personal login logs.
     *
     * @param request page request
     * @return login log page
     */
    public PageResult<LoginLogVO> loginLogs(PageRequest request) {
        PlatAccount account = currentAccountProvider.currentAccount();
        int pageNo = request == null || request.pageNo() <= 0 ? 1 : request.pageNo();
        int pageSize = request == null || request.pageSize() <= 0 ? 20 : request.pageSize();
        int offset = (pageNo - 1) * pageSize;
        long total = auditLoginLogBaseService.count(new LambdaQueryWrapper<AuditLoginLog>()
                .eq(AuditLoginLog::getAccountId, account.getId()));
        List<AuditLoginLog> records = auditLoginLogBaseService.list(new LambdaQueryWrapper<AuditLoginLog>()
                .eq(AuditLoginLog::getAccountId, account.getId())
                .orderByDesc(AuditLoginLog::getCreatedAt)
                .last("LIMIT " + offset + "," + pageSize));
        List<LoginLogVO> vos = records.stream()
                .map(log -> new LoginLogVO(String.valueOf(log.getId()), log.getIdentityProvider(),
                        log.getAuthMethod(), log.getLoginResult(), log.getFailureReason(), log.getIp(),
                        log.getDevice(), log.getRequestId(), log.getTraceId(), log.getCreatedAt()))
                .toList();
        return new PageResult<>(vos, pageNo, pageSize, total, offset + records.size() < total);
    }

    private List<SystemAccessSummary> systemAccessSummaries(Long accountId) {
        List<PlatAccountMemberBinding> bindings = bindingBaseService.list(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getAccountId, accountId)
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED));
        Map<Long, PlatSystem> systems = systems(bindings);
        Map<Long, PlatTenant> tenants = tenants(bindings);
        List<SystemAccessSummary> summaries = new ArrayList<>();
        for (PlatAccountMemberBinding binding : bindings) {
            PlatSystem system = systems.get(binding.getSystemId());
            PlatTenant tenant = tenants.get(binding.getTenantId());
            if (Objects.isNull(system) || Objects.isNull(tenant)) {
                continue;
            }
            summaries.add(new SystemAccessSummary(
                    String.valueOf(system.getId()),
                    system.getSystemName(),
                    String.valueOf(tenant.getId()),
                    String.valueOf(binding.getSystemMemberId()),
                    roleCodes(systemRoles(binding)),
                    true,
                    null
            ));
        }
        return summaries;
    }

    private Map<Long, PlatSystem> systems(List<PlatAccountMemberBinding> bindings) {
        Set<Long> ids = new LinkedHashSet<>();
        for (PlatAccountMemberBinding binding : bindings) {
            ids.add(binding.getSystemId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PlatSystem> map = new LinkedHashMap<>();
        for (PlatSystem system : systemBaseService.list(new LambdaQueryWrapper<PlatSystem>()
                .in(PlatSystem::getId, ids)
                .eq(PlatSystem::getDeleted, DELETED_NO))) {
            map.put(system.getId(), system);
        }
        return map;
    }

    private Map<Long, PlatTenant> tenants(List<PlatAccountMemberBinding> bindings) {
        Set<Long> ids = new LinkedHashSet<>();
        for (PlatAccountMemberBinding binding : bindings) {
            ids.add(binding.getTenantId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, PlatTenant> map = new LinkedHashMap<>();
        for (PlatTenant tenant : tenantBaseService.list(new LambdaQueryWrapper<PlatTenant>()
                .in(PlatTenant::getId, ids)
                .eq(PlatTenant::getDeleted, DELETED_NO))) {
            map.put(tenant.getId(), tenant);
        }
        return map;
    }

    private List<PlatRole> platformRoles(Long accountId) {
        List<PlatRoleMember> roleMembers = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getAccountId, accountId)
                .isNull(PlatRoleMember::getSystemMemberId));
        return roles(roleMembers, SCOPE_PLATFORM);
    }

    private List<PlatRole> systemRoles(PlatAccountMemberBinding binding) {
        List<PlatRoleMember> roleMembers = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getSystemId, binding.getSystemId())
                .eq(PlatRoleMember::getTenantId, binding.getTenantId())
                .eq(PlatRoleMember::getSystemMemberId, binding.getSystemMemberId()));
        return roles(roleMembers, SCOPE_SYSTEM);
    }

    private List<PlatRole> roles(List<PlatRoleMember> roleMembers, String scope) {
        Set<Long> roleIds = new LinkedHashSet<>();
        for (PlatRoleMember roleMember : roleMembers) {
            if (Objects.nonNull(roleMember.getRoleId())) {
                roleIds.add(roleMember.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleBaseService.list(new LambdaQueryWrapper<PlatRole>()
                .in(PlatRole::getId, roleIds)
                .eq(PlatRole::getScope, scope)
                .eq(PlatRole::getStatus, ENABLED)
                .eq(PlatRole::getDeleted, DELETED_NO));
    }

    private List<String> roleCodes(List<PlatRole> roles) {
        return roles.stream().map(PlatRole::getRoleCode).toList();
    }

    private AccountActionResult actionResult(String result) {
        RequestContext context = RequestContext.current();
        return new AccountActionResult(result, context.traceId(), "aud_" + context.traceId(), LocalDateTime.now());
    }
}
