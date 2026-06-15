package com.unique.examine.plat.manage.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RoleOperation;
import com.unique.examine.plat.base.service.IAccountRoleService;
import com.unique.examine.plat.base.service.IRoleOperationService;
import com.unique.examine.plat.base.service.IRoleService;
import com.unique.examine.plat.manage.bo.LoginBO;
import com.unique.examine.plat.manage.bo.RefreshTokenBO;
import com.unique.examine.plat.manage.bo.RegisterBO;
import com.unique.examine.plat.manage.enums.AccountStatus;
import com.unique.examine.plat.manage.enums.AuthErrorCode;
import com.unique.examine.plat.manage.enums.PlatErrorCode;
import com.unique.examine.plat.manage.service.AuthAccountRepository;
import com.unique.examine.plat.manage.service.AuthOperationLogger;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.service.AuthTokenStore;
import com.unique.examine.plat.manage.service.AuthTokenStore.TokenSession;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.AuthTokenVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证会话服务实现。
 */
@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private static final int MAX_FAILED_LOGIN_COUNT = 5;

    private static final int LOCK_MINUTES = 30;

    private static final long ACTIVE_DELETE_TOKEN = 0L;

    private final AuthAccountRepository accountRepository;

    private final AuthOperationLogger operationLogger;

    private final PasswordEncoder passwordEncoder;

    private final AuthTokenStore tokenStore;

    private final IAccountRoleService accountRoleService;

    private final IRoleService roleService;

    private final IRoleOperationService roleOperationService;

    private final Clock clock;

    /**
     * 创建认证会话服务。
     *
     * @param accountRepository 平台账号仓储
     * @param operationLogger 认证审计日志
     * @param passwordEncoder 密码编码器
     * @param tokenStore token 存储
     * @param accountRoleService 平台账号角色服务
     * @param roleService 平台角色服务
     * @param roleOperationService 平台角色操作权限服务
     */
    @Autowired
    public AuthSessionServiceImpl(AuthAccountRepository accountRepository, AuthOperationLogger operationLogger,
            PasswordEncoder passwordEncoder, AuthTokenStore tokenStore, IAccountRoleService accountRoleService,
            IRoleService roleService, IRoleOperationService roleOperationService) {
        this(accountRepository, operationLogger, passwordEncoder, tokenStore, accountRoleService, roleService,
                roleOperationService, Clock.systemDefaultZone());
    }

    AuthSessionServiceImpl(AuthAccountRepository accountRepository, AuthOperationLogger operationLogger,
            PasswordEncoder passwordEncoder, AuthTokenStore tokenStore, Clock clock) {
        this(accountRepository, operationLogger, passwordEncoder, tokenStore, null, null, null, clock);
    }

    AuthSessionServiceImpl(AuthAccountRepository accountRepository, AuthOperationLogger operationLogger,
            PasswordEncoder passwordEncoder, AuthTokenStore tokenStore, IAccountRoleService accountRoleService,
            IRoleService roleService, IRoleOperationService roleOperationService, Clock clock) {
        this.accountRepository = accountRepository;
        this.operationLogger = operationLogger;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.accountRoleService = accountRoleService;
        this.roleService = roleService;
        this.roleOperationService = roleOperationService;
        this.clock = clock;
    }

    /**
     * 注册平台账号。
     *
     * @param registerBO 注册入参
     * @return 注册账号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthAccountVO register(RegisterBO registerBO) {
        accountRepository.findActiveByLoginName(registerBO.getLoginName())
                .ifPresent(account -> {
                    throw new BusinessException(PlatErrorCode.ACCOUNT_DUPLICATED);
                });
        LocalDateTime now = nowDateTime();
        Account account = new Account()
                .setLoginName(registerBO.getLoginName())
                .setPasswordHash(passwordEncoder.encode(registerBO.getPassword()))
                .setDisplayName(defaultDisplayName(registerBO))
                .setMobile(registerBO.getMobile())
                .setEmail(registerBO.getEmail())
                .setStatus(AccountStatus.NORMAL.getCode())
                .setFirstLoginChangePwd((byte) 0)
                .setFailedLoginCount(0)
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        accountRepository.save(account);
        operationLogger.log(account, "REGISTER", true, null, "平台注册账号");
        return toAccountVO(account);
    }

    /**
     * 平台账号登录。
     *
     * @param loginBO 登录入参
     * @return token 信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenVO login(LoginBO loginBO) {
        Account account = accountRepository.findActiveByLoginName(loginBO.getLoginName()).orElse(null);
        if (account == null) {
            operationLogger.log(null, "LOGIN", false, AuthErrorCode.INVALID_CREDENTIAL.getCode(), "登录账号不存在");
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
        }
        ensureLoginAllowed(account);
        if (!passwordEncoder.matches(loginBO.getPassword(), account.getPasswordHash())) {
            handleFailedLogin(account);
        }
        LocalDateTime now = nowDateTime();
        account.setFailedLoginCount(0)
                .setLockedUntil(null)
                .setStatus(AccountStatus.NORMAL.getCode())
                .setLastLoginAt(now)
                .setUpdatedAt(now);
        accountRepository.updateById(account);
        TokenSession session = tokenStore.issue(account);
        operationLogger.log(account, "LOGIN", true, null, "平台账号登录成功");
        return toTokenVO(session);
    }

    /**
     * 刷新 accessToken。
     *
     * @param refreshTokenBO 刷新入参
     * @return 新 token 信息
     */
    @Override
    public AuthTokenVO refresh(RefreshTokenBO refreshTokenBO) {
        TokenSession session = tokenStore.refresh(refreshTokenBO.getRefreshToken())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_INVALID));
        Account account = activeAccount(session.accountId());
        ensureLoginAllowed(account);
        operationLogger.log(account, "REFRESH", true, null, "刷新认证 token");
        return toTokenVO(session);
    }

    /**
     * 退出当前会话。
     *
     * @param accessToken accessToken
     */
    @Override
    public void logout(String accessToken) {
        tokenStore.validateAccess(accessToken)
                .ifPresent(session -> accountRepository.findActiveById(session.accountId())
                        .ifPresent(account -> operationLogger.log(account, "LOGOUT", true, null, "平台账号退出登录")));
        tokenStore.revokeAccess(accessToken);
    }

    /**
     * 查询当前登录用户。
     *
     * @param accessToken accessToken
     * @return 当前用户
     */
    @Override
    public CurrentUserVO me(String accessToken) {
        TokenSession session = tokenStore.validateAccess(accessToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TOKEN_EXPIRED));
        Account account = activeAccount(session.accountId());
        ensureLoginAllowed(account);
        List<Long> roleIds = listPlatformRoleIds(account.getId());
        return CurrentUserVO.builder()
                .account(toAccountVO(account))
                .platformRoles(listPlatformRoleCodes(roleIds))
                .platformPermissions(listPlatformPermissions(roleIds))
                .build();
    }

    /**
     * 查询当前账号绑定的平台角色 ID。
     *
     * @param accountId 平台账号 ID
     * @return 平台角色 ID 列表
     */
    private List<Long> listPlatformRoleIds(Long accountId) {
        if (accountRoleService == null) {
            return List.of();
        }
        return accountRoleService.lambdaQuery()
                .eq(AccountRole::getAccountId, accountId)
                .eq(AccountRole::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(AccountRole::getRoleId)
                .distinct()
                .toList();
    }

    /**
     * 根据账号角色关联查询平台角色编码，供前端判断平台中心导航和动作权限。
     *
     * @param roleIds 平台角色 ID
     * @return 平台角色编码
     */
    private List<String> listPlatformRoleCodes(List<Long> roleIds) {
        if (roleService == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleService.lambdaQuery()
                .in(Role::getId, roleIds)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(Role::getCode)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 根据账号平台角色查询操作权限编码，供前端执行平台级权限判断。
     *
     * @param roleIds 平台角色 ID
     * @return 平台操作权限编码
     */
    private List<String> listPlatformPermissions(List<Long> roleIds) {
        if (roleOperationService == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleOperationService.lambdaQuery()
                .in(RoleOperation::getRoleId, roleIds)
                .eq(RoleOperation::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(RoleOperation::getOperationCode)
                .distinct()
                .sorted()
                .toList();
    }

    private Account activeAccount(Long accountId) {
        return accountRepository.findActiveById(accountId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TOKEN_EXPIRED));
    }

    private void ensureLoginAllowed(Account account) {
        if (AccountStatus.DISABLED.getCode().equals(account.getStatus())) {
            operationLogger.log(account, "LOGIN", false, AuthErrorCode.ACCOUNT_DISABLED.getCode(), "账号停用");
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        if (isLocked(account)) {
            operationLogger.log(account, "LOGIN", false, AuthErrorCode.ACCOUNT_LOCKED.getCode(), "账号锁定");
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (AccountStatus.LOCKED.getCode().equals(account.getStatus())) {
            account.setStatus(AccountStatus.NORMAL.getCode())
                    .setFailedLoginCount(0)
                    .setLockedUntil(null)
                    .setUpdatedAt(nowDateTime());
            accountRepository.updateById(account);
        }
    }

    private boolean isLocked(Account account) {
        LocalDateTime lockedUntil = account.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(nowDateTime())) {
            return true;
        }
        return AccountStatus.LOCKED.getCode().equals(account.getStatus()) && lockedUntil == null;
    }

    private void handleFailedLogin(Account account) {
        int failedCount = account.getFailedLoginCount() == null ? 1 : account.getFailedLoginCount() + 1;
        account.setFailedLoginCount(failedCount)
                .setUpdatedAt(nowDateTime());
        if (failedCount >= MAX_FAILED_LOGIN_COUNT) {
            account.setStatus(AccountStatus.LOCKED.getCode())
                    .setLockedUntil(nowDateTime().plusMinutes(LOCK_MINUTES));
            accountRepository.updateById(account);
            operationLogger.log(account, "LOGIN", false, AuthErrorCode.ACCOUNT_LOCKED.getCode(), "密码错误次数过多锁定");
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        accountRepository.updateById(account);
        operationLogger.log(account, "LOGIN", false, AuthErrorCode.INVALID_CREDENTIAL.getCode(), "登录密码错误");
        throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
    }

    private LocalDateTime nowDateTime() {
        return LocalDateTime.ofInstant(Instant.now(clock), ZoneId.systemDefault());
    }

    private String defaultDisplayName(RegisterBO registerBO) {
        if (registerBO.getDisplayName() != null && !registerBO.getDisplayName().isBlank()) {
            return registerBO.getDisplayName();
        }
        return registerBO.getLoginName();
    }

    private AuthAccountVO toAccountVO(Account account) {
        return AuthAccountVO.builder()
                .accountId(String.valueOf(account.getId()))
                .loginName(account.getLoginName())
                .displayName(account.getDisplayName())
                .status(account.getStatus())
                .build();
    }

    private AuthTokenVO toTokenVO(TokenSession session) {
        return AuthTokenVO.builder()
                .accountId(String.valueOf(session.accountId()))
                .loginName(session.loginName())
                .displayName(session.displayName())
                .accessToken(session.accessToken())
                .refreshToken(session.refreshToken())
                .accessTokenExpiresAt(toOffsetDateTime(session.accessTokenExpiresAt()))
                .refreshTokenExpiresAt(toOffsetDateTime(session.refreshTokenExpiresAt()))
                .build();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
