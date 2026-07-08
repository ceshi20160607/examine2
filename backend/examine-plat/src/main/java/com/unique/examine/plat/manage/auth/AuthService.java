package com.unique.examine.plat.manage.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.messagelog.base.entity.AuditLoginLog;
import com.unique.examine.messagelog.base.service.AuditLoginLogBaseService;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatDepartment;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatSsoBinding;
import com.unique.examine.plat.base.entity.PlatSystem;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatAccountBaseService;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatDepartmentBaseService;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatSsoBindingBaseService;
import com.unique.examine.plat.base.service.PlatSystemBaseService;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import com.unique.examine.plat.manage.auth.AuthModels.AccountProfile;
import com.unique.examine.plat.manage.auth.AuthModels.AuthActionResult;
import com.unique.examine.plat.manage.auth.AuthModels.DefaultLanding;
import com.unique.examine.plat.manage.auth.AuthModels.LoginRequest;
import com.unique.examine.plat.manage.auth.AuthModels.LoginResponse;
import com.unique.examine.plat.manage.auth.AuthModels.PasswordResetConfirmRequest;
import com.unique.examine.plat.manage.auth.AuthModels.PasswordResetRequest;
import com.unique.examine.plat.manage.auth.AuthModels.PasswordResetResponse;
import com.unique.examine.plat.manage.auth.AuthModels.RegisterWithSystemRequest;
import com.unique.examine.plat.manage.auth.AuthModels.RegisterWithSystemResponse;
import com.unique.examine.plat.manage.auth.AuthModels.SsoBindingSummary;
import com.unique.examine.plat.manage.auth.AuthModels.TokenRefreshRequest;
import com.unique.examine.plat.manage.auth.AuthTokenService.TokenPair;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Authentication service backed by platform account, system, tenant, role, and audit tables.
 */
@Service
public class AuthService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String SYSTEM_SUPER_ADMIN = "SYSTEM_SUPER_ADMIN";
    private static final String DEFAULT_TENANT_CODE = "default";
    private static final String DEFAULT_TENANT_NAME = "默认租户";
    private static final String DEFAULT_DEPARTMENT_CODE = "default_department";
    private static final String DEFAULT_DEPARTMENT_NAME = "默认部门";

    private static final String PASSWORD_RESET_PREFIX = "unexamine:auth:password-reset:";
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(15);

    private final PlatAccountBaseService accountBaseService;
    private final PlatSystemBaseService systemBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final PlatDepartmentBaseService departmentBaseService;
    private final PlatMemberBaseService memberBaseService;
    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatSsoBindingBaseService ssoBindingBaseService;
    private final AuditLoginLogBaseService auditLoginLogBaseService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final StringRedisTemplate redisTemplate;

    public AuthService(PlatAccountBaseService accountBaseService,
                       PlatSystemBaseService systemBaseService,
                       PlatTenantBaseService tenantBaseService,
                       PlatDepartmentBaseService departmentBaseService,
                       PlatMemberBaseService memberBaseService,
                       PlatAccountMemberBindingBaseService bindingBaseService,
                       PlatRoleBaseService roleBaseService,
                       PlatRoleMemberBaseService roleMemberBaseService,
                       PlatSsoBindingBaseService ssoBindingBaseService,
                       AuditLoginLogBaseService auditLoginLogBaseService,
                       PasswordEncoder passwordEncoder,
                       AuthTokenService authTokenService,
                       StringRedisTemplate redisTemplate) {
        this.accountBaseService = accountBaseService;
        this.systemBaseService = systemBaseService;
        this.tenantBaseService = tenantBaseService;
        this.departmentBaseService = departmentBaseService;
        this.memberBaseService = memberBaseService;
        this.bindingBaseService = bindingBaseService;
        this.roleBaseService = roleBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.ssoBindingBaseService = ssoBindingBaseService;
        this.auditLoginLogBaseService = auditLoginLogBaseService;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Login with account credentials and optional target system.
     *
     * @param request login request
     * @return login response
     */
    public LoginResponse login(LoginRequest request) {
        requireText(request.loginName(), AuthErrorCode.LOGIN_NAME_REQUIRED);
        requireText(request.password(), AuthErrorCode.PASSWORD_REQUIRED);

        PlatAccount account = findAccountByLoginName(request.loginName());
        if (Objects.isNull(account) || !passwordMatches(request.password(), account.getPasswordHash())) {
            writeLoginLog(null, null, null, "FAILED", AuthErrorCode.BAD_CREDENTIALS.message());
            throw new BusinessException(AuthErrorCode.BAD_CREDENTIALS);
        }
        if (!Objects.equals(account.getStatus(), ENABLED)) {
            writeLoginLog(account.getId(), null, null, "FAILED", AuthErrorCode.ACCOUNT_DISABLED.message());
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }

        BindingTarget bindingTarget = resolveBindingTarget(account.getId(), request);
        account.setLastLoginAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountBaseService.updateById(account);
        writeLoginLog(account.getId(), bindingTarget.systemId(), bindingTarget.tenantId(), "SUCCESS", null);

        List<String> platformRoles = roleCodes(findPlatformRoles(account.getId()));
        List<String> systemRoles = roleCodes(findSystemRoles(bindingTarget.binding()));
        DefaultLanding landing = defaultLanding(request.loginTarget(), platformRoles, bindingTarget);
        SsoBindingSummary ssoBindingSummary = ssoBindingSummary(account.getId(), bindingTarget);
        TokenPair tokenPair = authTokenService.issueTokens(account.getId());
        return new LoginResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                new AccountProfile(String.valueOf(account.getId()), account.getAccountName(), account.getMobile(),
                        account.getEmail(), platformRoles, systemRoles),
                landing,
                ssoBindingSummary,
                RequestContext.current().requestId(),
                RequestContext.current().traceId()
        );
    }

    /**
     * Logout the current token.
     *
     * @return action result
     */
    public AuthActionResult logout() {
        authTokenService.revokeCurrentAccessToken();
        return result("LOGOUT_SUCCESS");
    }

    /**
     * Refresh an access token.
     *
     * @param request refresh request
     * @return login response with new token ids
     */
    public LoginResponse refresh(TokenRefreshRequest request) {
        if (!StringUtils.hasText(request.refreshToken())) {
            throw new BusinessException(AuthErrorCode.BAD_CREDENTIALS, "刷新令牌不能为空");
        }
        TokenPair tokenPair = authTokenService.rotateRefreshToken(request.refreshToken());
        return new LoginResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                null,
                null,
                new SsoBindingSummary(false, List.of(), null),
                RequestContext.current().requestId(),
                RequestContext.current().traceId()
        );
    }

    /**
     * Register a platform account and bootstrap the first system.
     *
     * @param request register request
     * @return bootstrap result
     */
    @Transactional(rollbackFor = Exception.class)
    public RegisterWithSystemResponse registerWithSystem(RegisterWithSystemRequest request) {
        validateRegisterRequest(request);
        if (existsAccount(request.accountName(), request.mobile(), request.email())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_CONFLICT);
        }
        String systemCode = normalizedCode(request.systemCode(), request.accountName() + "_system");
        if (existsSystemCode(systemCode)) {
            throw new BusinessException(AuthErrorCode.SYSTEM_CODE_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        PlatAccount account = new PlatAccount();
        account.setAccountName(request.accountName());
        account.setMobile(blankToNull(request.mobile()));
        account.setEmail(blankToNull(request.email()));
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setStatus(ENABLED);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setDeleted(DELETED_NO);
        accountBaseService.saveEntity(account);

        PlatSystem system = createSystem(request, systemCode, account.getId(), now);
        PlatTenant tenant = createDefaultTenant(system.getId(), now);
        PlatDepartment department = createDefaultDepartment(system.getId(), tenant.getId(), now);
        PlatMember member = createSuperAdminMember(request, system.getId(), tenant.getId(), department.getId(),
                account.getId(), now);
        PlatRole role = createSystemSuperAdminRole(system.getId(), tenant.getId(), now);
        createRoleMember(role.getId(), account.getId(), member.getId(), system.getId(), tenant.getId(), now);
        createBinding(account.getId(), system.getId(), tenant.getId(), member.getId(), now);
        TokenPair tokenPair = authTokenService.issueTokens(account.getId());

        return new RegisterWithSystemResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                String.valueOf(account.getId()),
                String.valueOf(system.getId()),
                String.valueOf(member.getId()),
                String.valueOf(role.getId()),
                List.of("CREATE_ACCOUNT", "CREATE_SYSTEM", "CREATE_DEFAULT_TENANT", "CREATE_DEFAULT_DEPARTMENT",
                        "CREATE_SYSTEM_SUPER_ADMIN_ROLE", "BIND_ACCOUNT_MEMBER"),
                "aud_register_" + RequestContext.current().traceId()
        );
    }

    /**
     * Create a password reset ticket.
     *
     * @param request reset request
     * @return reset ticket
     */
    public PasswordResetResponse requestPasswordReset(PasswordResetRequest request) {
        requireText(request.loginName(), AuthErrorCode.LOGIN_NAME_REQUIRED);
        PlatAccount account = findAccountByLoginName(request.loginName());
        if (Objects.isNull(account) || !Objects.equals(account.getStatus(), ENABLED)) {
            throw new BusinessException(AuthErrorCode.BAD_CREDENTIALS);
        }
        String resetTicket = "reset_" + token();
        String verifyCode = verificationCode();
        redisTemplate.opsForValue().set(PASSWORD_RESET_PREFIX + resetTicket,
                account.getId() + ":" + verifyCode, PASSWORD_RESET_TTL);
        return new PasswordResetResponse(resetTicket, verifyCode,
                LocalDateTime.now().plus(PASSWORD_RESET_TTL).toString(), RequestContext.current().traceId());
    }

    /**
     * Confirm a password reset ticket.
     *
     * @param request confirm request
     * @return action result
     */
    public AuthActionResult confirmPasswordReset(PasswordResetConfirmRequest request) {
        requireText(request.resetTicket(), AuthErrorCode.REGISTER_FIELD_REQUIRED);
        requireText(request.verifyCode(), AuthErrorCode.REGISTER_FIELD_REQUIRED);
        requireText(request.newPassword(), AuthErrorCode.PASSWORD_REQUIRED);
        String key = PASSWORD_RESET_PREFIX + request.resetTicket();
        String stored = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(stored)) {
            throw new BusinessException(AuthErrorCode.BAD_CREDENTIALS, "閲嶇疆绁ㄦ嵁鏃犳晥鎴栧凡杩囨湡");
        }
        String[] parts = stored.split(":", 2);
        if (parts.length != 2 || !parts[1].equals(request.verifyCode())) {
            throw new BusinessException(AuthErrorCode.BAD_CREDENTIALS, "楠岃瘉鐮佷笉姝ｇ‘");
        }
        PlatAccount account = accountBaseService.getById(Long.valueOf(parts[0]));
        if (Objects.isNull(account) || !Objects.equals(account.getDeleted(), DELETED_NO)) {
            redisTemplate.delete(key);
            throw new BusinessException(AuthErrorCode.BAD_CREDENTIALS);
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setUpdatedAt(LocalDateTime.now());
        accountBaseService.updateById(account);
        redisTemplate.delete(key);
        return result("PASSWORD_RESET_SUCCESS");
    }

    private PlatAccount findAccountByLoginName(String loginName) {
        return accountBaseService.getOne(new LambdaQueryWrapper<PlatAccount>()
                .eq(PlatAccount::getDeleted, DELETED_NO)
                .and(wrapper -> wrapper.eq(PlatAccount::getAccountName, loginName)
                        .or().eq(PlatAccount::getMobile, loginName)
                        .or().eq(PlatAccount::getEmail, loginName))
                .last("LIMIT 1"), false);
    }

    private BindingTarget resolveBindingTarget(Long accountId, LoginRequest request) {
        PlatSystem targetSystem = findTargetSystem(request.systemCode());
        PlatTenant targetTenant = findTargetTenant(targetSystem, request.tenantCode());
        PlatAccountMemberBinding binding = findBinding(accountId, targetSystem, targetTenant);
        if (Objects.nonNull(targetSystem) && Objects.isNull(binding)) {
            writeLoginLog(accountId, targetSystem.getId(), Objects.isNull(targetTenant) ? null : targetTenant.getId(),
                    "FAILED", AuthErrorCode.SYSTEM_BINDING_NOT_FOUND.message());
            throw new BusinessException(AuthErrorCode.SYSTEM_BINDING_NOT_FOUND);
        }
        return new BindingTarget(binding,
                Objects.nonNull(targetSystem) ? targetSystem.getId() : bindingSystemId(binding),
                Objects.nonNull(targetTenant) ? targetTenant.getId() : bindingTenantId(binding));
    }

    private PlatSystem findTargetSystem(String systemCode) {
        if (!StringUtils.hasText(systemCode)) {
            return null;
        }
        PlatSystem system = systemBaseService.getOne(new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getSystemCode, systemCode)
                .eq(PlatSystem::getDeleted, DELETED_NO)
                .eq(PlatSystem::getStatus, ENABLED)
                .last("LIMIT 1"), false);
        if (Objects.isNull(system)) {
            throw new BusinessException(AuthErrorCode.SYSTEM_NOT_FOUND);
        }
        return system;
    }

    private PlatTenant findTargetTenant(PlatSystem targetSystem, String tenantCode) {
        if (Objects.isNull(targetSystem) || !StringUtils.hasText(tenantCode)) {
            return null;
        }
        return tenantBaseService.getOne(new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getSystemId, targetSystem.getId())
                .eq(PlatTenant::getTenantCode, tenantCode)
                .eq(PlatTenant::getDeleted, DELETED_NO)
                .eq(PlatTenant::getStatus, ENABLED)
                .last("LIMIT 1"), false);
    }

    private PlatAccountMemberBinding findBinding(Long accountId, PlatSystem targetSystem, PlatTenant targetTenant) {
        LambdaQueryWrapper<PlatAccountMemberBinding> wrapper = new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getAccountId, accountId)
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED);
        if (Objects.nonNull(targetSystem)) {
            wrapper.eq(PlatAccountMemberBinding::getSystemId, targetSystem.getId());
        }
        if (Objects.nonNull(targetTenant)) {
            wrapper.eq(PlatAccountMemberBinding::getTenantId, targetTenant.getId());
        }
        return bindingBaseService.getOne(wrapper.last("LIMIT 1"), false);
    }

    private List<PlatRole> findPlatformRoles(Long accountId) {
        List<PlatRoleMember> roleMembers = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getAccountId, accountId)
                .isNull(PlatRoleMember::getSystemMemberId));
        return findRoles(roleMembers, SCOPE_PLATFORM);
    }

    private List<PlatRole> findSystemRoles(PlatAccountMemberBinding binding) {
        if (Objects.isNull(binding)) {
            return List.of();
        }
        List<PlatRoleMember> roleMembers = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getSystemId, binding.getSystemId())
                .eq(PlatRoleMember::getTenantId, binding.getTenantId())
                .eq(PlatRoleMember::getSystemMemberId, binding.getSystemMemberId()));
        return findRoles(roleMembers, SCOPE_SYSTEM);
    }

    private List<PlatRole> findRoles(List<PlatRoleMember> roleMembers, String scope) {
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
        List<String> codes = new ArrayList<>();
        for (PlatRole role : roles) {
            codes.add(role.getRoleCode());
        }
        return codes;
    }

    private DefaultLanding defaultLanding(String loginTarget, List<String> platformRoles, BindingTarget bindingTarget) {
        if ("PLATFORM".equalsIgnoreCase(loginTarget) && !platformRoles.isEmpty()) {
            return new DefaultLanding("PLATFORM", null, null, "/platform");
        }
        if (Objects.nonNull(bindingTarget.binding())) {
            return new DefaultLanding("SYSTEM", String.valueOf(bindingTarget.binding().getSystemId()),
                    String.valueOf(bindingTarget.binding().getTenantId()),
                    "/systems/" + bindingTarget.binding().getSystemId() + "/dashboard");
        }
        if (!platformRoles.isEmpty()) {
            return new DefaultLanding("PLATFORM", null, null, "/platform");
        }
        return new DefaultLanding("NO_MEMBER", null, null, "/no-member");
    }

    private SsoBindingSummary ssoBindingSummary(Long accountId, BindingTarget bindingTarget) {
        LambdaQueryWrapper<PlatSsoBinding> wrapper = new LambdaQueryWrapper<PlatSsoBinding>()
                .eq(PlatSsoBinding::getAccountId, accountId)
                .eq(PlatSsoBinding::getBindingStatus, ENABLED);
        if (Objects.nonNull(bindingTarget.systemId())) {
            wrapper.and(query -> query.isNull(PlatSsoBinding::getSystemId)
                    .or().eq(PlatSsoBinding::getSystemId, bindingTarget.systemId()));
        }
        List<PlatSsoBinding> bindings = ssoBindingBaseService.list(wrapper);
        List<String> providers = bindings.stream()
                .map(PlatSsoBinding::getIdentityProvider)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        String lastProvider = providers.isEmpty() ? null : providers.get(0);
        return new SsoBindingSummary(!providers.isEmpty(), providers, lastProvider);
    }

    private void validateRegisterRequest(RegisterWithSystemRequest request) {
        requireText(request.accountName(), AuthErrorCode.REGISTER_FIELD_REQUIRED);
        requireText(request.password(), AuthErrorCode.PASSWORD_REQUIRED);
        requireText(request.systemName(), AuthErrorCode.REGISTER_FIELD_REQUIRED);
    }

    private boolean existsAccount(String accountName, String mobile, String email) {
        LambdaQueryWrapper<PlatAccount> wrapper = new LambdaQueryWrapper<PlatAccount>()
                .eq(PlatAccount::getDeleted, DELETED_NO)
                .and(query -> query.eq(PlatAccount::getAccountName, accountName));
        if (StringUtils.hasText(mobile)) {
            wrapper.or(query -> query.eq(PlatAccount::getDeleted, DELETED_NO)
                    .eq(PlatAccount::getMobile, mobile));
        }
        if (StringUtils.hasText(email)) {
            wrapper.or(query -> query.eq(PlatAccount::getDeleted, DELETED_NO)
                    .eq(PlatAccount::getEmail, email));
        }
        return accountBaseService.count(wrapper) > 0;
    }

    private boolean existsSystemCode(String systemCode) {
        return systemBaseService.count(new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getSystemCode, systemCode)
                .eq(PlatSystem::getDeleted, DELETED_NO)) > 0;
    }

    private PlatSystem createSystem(RegisterWithSystemRequest request, String systemCode, Long accountId,
                                    LocalDateTime now) {
        PlatSystem system = new PlatSystem();
        system.setSystemCode(systemCode);
        system.setSystemName(request.systemName());
        system.setTenantMode(Objects.isNull(request.tenantMode()) ? 1 : request.tenantMode());
        system.setOwnerAccountId(accountId);
        system.setStatus(ENABLED);
        system.setCreatedAt(now);
        system.setUpdatedAt(now);
        system.setDeleted(DELETED_NO);
        systemBaseService.saveEntity(system);
        return system;
    }

    private PlatTenant createDefaultTenant(Long systemId, LocalDateTime now) {
        PlatTenant tenant = new PlatTenant();
        tenant.setSystemId(systemId);
        tenant.setTenantCode(DEFAULT_TENANT_CODE);
        tenant.setTenantName(DEFAULT_TENANT_NAME);
        tenant.setStatus(ENABLED);
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenant.setDeleted(DELETED_NO);
        tenantBaseService.saveEntity(tenant);
        return tenant;
    }

    private PlatDepartment createDefaultDepartment(Long systemId, Long tenantId, LocalDateTime now) {
        PlatDepartment department = new PlatDepartment();
        department.setSystemId(systemId);
        department.setTenantId(tenantId);
        department.setParentId(null);
        department.setDeptCode(DEFAULT_DEPARTMENT_CODE);
        department.setDeptName(DEFAULT_DEPARTMENT_NAME);
        department.setSortOrder(10);
        department.setStatus(ENABLED);
        department.setCreatedAt(now);
        department.setUpdatedAt(now);
        department.setDeleted(DELETED_NO);
        departmentBaseService.saveEntity(department);
        return department;
    }

    private PlatMember createSuperAdminMember(RegisterWithSystemRequest request, Long systemId, Long tenantId,
                                             Long departmentId, Long accountId, LocalDateTime now) {
        PlatMember member = new PlatMember();
        member.setSystemId(systemId);
        member.setTenantId(tenantId);
        member.setDeptId(departmentId);
        member.setMemberName(request.accountName());
        member.setEmployeeNo("SA" + accountId);
        member.setMobile(blankToNull(request.mobile()));
        member.setEmail(blankToNull(request.email()));
        member.setStatus(ENABLED);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        member.setDeleted(DELETED_NO);
        memberBaseService.saveEntity(member);
        return member;
    }

    private PlatRole createSystemSuperAdminRole(Long systemId, Long tenantId, LocalDateTime now) {
        PlatRole role = new PlatRole();
        role.setScope(SCOPE_SYSTEM);
        role.setSystemId(systemId);
        role.setTenantId(tenantId);
        role.setRoleCode(SYSTEM_SUPER_ADMIN);
        role.setRoleName("系统超级管理员");
        role.setRoleType(SYSTEM_SUPER_ADMIN);
        role.setBuiltin(ENABLED);
        role.setStatus(ENABLED);
        role.setDescription("创建系统时自动生成，拥有系统内全部权限");
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleted(DELETED_NO);
        roleBaseService.saveEntity(role);
        return role;
    }

    private void createRoleMember(Long roleId, Long accountId, Long memberId, Long systemId, Long tenantId,
                                  LocalDateTime now) {
        PlatRoleMember roleMember = new PlatRoleMember();
        roleMember.setRoleId(roleId);
        roleMember.setAccountId(accountId);
        roleMember.setSystemMemberId(memberId);
        roleMember.setSystemId(systemId);
        roleMember.setTenantId(tenantId);
        roleMember.setCreatedAt(now);
        roleMemberBaseService.saveEntity(roleMember);
    }

    private void createBinding(Long accountId, Long systemId, Long tenantId, Long memberId, LocalDateTime now) {
        PlatAccountMemberBinding binding = new PlatAccountMemberBinding();
        binding.setAccountId(accountId);
        binding.setSystemId(systemId);
        binding.setTenantId(tenantId);
        binding.setSystemMemberId(memberId);
        binding.setBindingStatus(ENABLED);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        bindingBaseService.saveEntity(binding);
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        return StringUtils.hasText(passwordHash) && passwordEncoder.matches(rawPassword, passwordHash);
    }

    private void writeLoginLog(Long accountId, Long systemId, Long tenantId, String result, String failureReason) {
        RequestContext context = RequestContext.current();
        AuditLoginLog loginLog = new AuditLoginLog();
        loginLog.setAccountId(accountId);
        loginLog.setSystemId(systemId);
        loginLog.setTenantId(tenantId);
        loginLog.setIdentityProvider("PASSWORD");
        loginLog.setAuthMethod("PASSWORD");
        loginLog.setMfaResult("NOT_REQUIRED");
        loginLog.setAccountBindingResult(Objects.isNull(accountId) ? "NOT_FOUND" : "BOUND");
        loginLog.setMemberMappingResult(Objects.isNull(systemId) ? "NOT_TARGETED" : "MATCHED");
        loginLog.setLoginResult(result);
        loginLog.setFailureReason(failureReason);
        loginLog.setRequestId(context.requestId());
        loginLog.setTraceId(context.traceId());
        loginLog.setCreatedAt(LocalDateTime.now());
        auditLoginLogBaseService.saveEntity(loginLog);
    }

    private Long bindingSystemId(PlatAccountMemberBinding binding) {
        return Objects.isNull(binding) ? null : binding.getSystemId();
    }

    private Long bindingTenantId(PlatAccountMemberBinding binding) {
        return Objects.isNull(binding) ? null : binding.getTenantId();
    }

    private AuthActionResult result(String result) {
        RequestContext context = RequestContext.current();
        return new AuthActionResult(result, context.traceId(), "aud_" + context.traceId(), LocalDateTime.now());
    }

    private void requireText(String value, AuthErrorCode errorCode) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(errorCode);
        }
    }

    private String normalizedCode(String value, String fallback) {
        String candidate = StringUtils.hasText(value) ? value : fallback;
        return candidate.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String verificationCode() {
        long value = Math.abs((long) token().hashCode()) % 1_000_000L;
        return String.format("%06d", value);
    }

    private record BindingTarget(PlatAccountMemberBinding binding, Long systemId, Long tenantId) {
    }
}
