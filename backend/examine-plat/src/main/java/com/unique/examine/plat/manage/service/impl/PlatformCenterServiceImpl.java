package com.unique.examine.plat.manage.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.Config;
import com.unique.examine.plat.base.entity.Menu;
import com.unique.examine.plat.base.entity.Operation;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RoleMenu;
import com.unique.examine.plat.base.entity.RoleOperation;
import com.unique.examine.plat.base.entity.System;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.service.IAccountRoleService;
import com.unique.examine.plat.base.service.IAccountService;
import com.unique.examine.plat.base.service.IConfigService;
import com.unique.examine.plat.base.service.IMenuService;
import com.unique.examine.plat.base.service.IOperationService;
import com.unique.examine.plat.base.service.IRoleMenuService;
import com.unique.examine.plat.base.service.IRoleOperationService;
import com.unique.examine.plat.base.service.IRoleService;
import com.unique.examine.plat.base.service.ISystemService;
import com.unique.examine.plat.base.service.ITenantService;
import com.unique.examine.plat.manage.bo.PlatformAccountResetPasswordBO;
import com.unique.examine.plat.manage.bo.PlatformAccountRoleAssignBO;
import com.unique.examine.plat.manage.bo.PlatformAccountSaveBO;
import com.unique.examine.plat.manage.bo.PlatformAccountUpdateBO;
import com.unique.examine.plat.manage.bo.PlatformConfigUpdateBO;
import com.unique.examine.plat.manage.bo.PlatformRolePermissionBO;
import com.unique.examine.plat.manage.bo.PlatformRoleSaveBO;
import com.unique.examine.plat.manage.bo.PlatformStatusBO;
import com.unique.examine.plat.manage.bo.PlatformSystemSaveBO;
import com.unique.examine.plat.manage.enums.AccountStatus;
import com.unique.examine.plat.manage.enums.EnableStatus;
import com.unique.examine.plat.manage.enums.PlatErrorCode;
import com.unique.examine.plat.manage.enums.SystemStatus;
import com.unique.examine.plat.manage.enums.TenantMode;
import com.unique.examine.plat.manage.service.PlatformCenterService;
import com.unique.examine.plat.manage.service.PlatformModuleInitializationWriter;
import com.unique.examine.plat.manage.service.PlatformModuleInitializationWriter.InitializationCommand;
import com.unique.examine.plat.manage.service.PlatformModuleInitializationWriter.InitializationResult;
import com.unique.examine.plat.manage.vo.InitializedObjectVO;
import com.unique.examine.plat.manage.vo.PlatformAccountVO;
import com.unique.examine.plat.manage.vo.PlatformConfigVO;
import com.unique.examine.plat.manage.vo.PlatformMenuTreeVO;
import com.unique.examine.plat.manage.vo.PlatformPermissionCatalogVO;
import com.unique.examine.plat.manage.vo.PlatformRoleVO;
import com.unique.examine.plat.manage.vo.PlatformSystemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 平台中心服务实现。
 */
@Service
@RequiredArgsConstructor
public class PlatformCenterServiceImpl implements PlatformCenterService {

    private static final long ACTIVE_DELETE_TOKEN = 0L;

    private static final String DEFAULT_TENANT_CODE = "default";

    private static final String MASKED_VALUE = "******";

    private final IAccountService accountService;

    private final ISystemService systemService;

    private final ITenantService tenantService;

    private final IRoleService roleService;

    private final IMenuService menuService;

    private final IOperationService operationService;

    private final IAccountRoleService accountRoleService;

    private final IRoleMenuService roleMenuService;

    private final IRoleOperationService roleOperationService;

    private final IConfigService configService;

    private final PlatformModuleInitializationWriter moduleInitializationWriter;

    private final PasswordEncoder passwordEncoder;

    /**
     * 查询当前账号的系统列表。
     *
     * @param accountId 平台账号 ID
     * @return 系统列表
     */
    @Override
    public List<PlatformSystemVO> mySystems(Long accountId) {
        return systemService.lambdaQuery()
                .eq(System::getOwnerAccountId, accountId)
                .eq(System::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(system -> toSystemVO(system, List.of()))
                .toList();
    }

    /**
     * 创建自定义系统，并在同一事务内初始化默认租户、创建人成员、系统角色和默认应用。
     *
     * @param accountId 创建人平台账号 ID
     * @param saveBO 创建入参
     * @return 系统信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformSystemVO createSystem(Long accountId, PlatformSystemSaveBO saveBO) {
        if (!TenantMode.isValid(saveBO.getTenantMode())) {
            throw new BusinessException(PlatErrorCode.SYSTEM_STATUS_INVALID, "租户模式不正确");
        }
        ensureSystemCodeAvailable(saveBO.getCode(), null);
        Account owner = activeAccount(accountId);
        LocalDateTime now = LocalDateTime.now();
        System system = new System()
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setTenantMode(saveBO.getTenantMode())
                .setOwnerAccountId(accountId)
                .setStatus(SystemStatus.ENABLED.getCode())
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        systemService.save(system);

        Tenant tenant = new Tenant()
                .setSystemId(system.getId())
                .setCode(DEFAULT_TENANT_CODE)
                .setName("默认租户")
                .setStatus(EnableStatus.ENABLED.getCode())
                .setDescription("系统创建时初始化的默认租户")
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        tenantService.save(tenant);

        InitializationResult initializationResult = moduleInitializationWriter.initialize(new InitializationCommand(
                system.getId(), tenant.getId(), accountId, displayName(owner), now));
        system.setDefaultTenantId(tenant.getId())
                .setOwnerMemberId(initializationResult.ownerMemberId())
                .setUpdatedAt(now);
        systemService.updateById(system);

        List<InitializedObjectVO> initializedObjects = new ArrayList<>();
        initializedObjects.add(initialized("SYSTEM", system.getCode(), system.getId(), system.getStatus()));
        initializedObjects.add(initialized("TENANT", tenant.getCode(), tenant.getId(), tenant.getStatus()));
        initializedObjects.addAll(initializationResult.initializedObjects());
        return toSystemVO(system, initializedObjects);
    }

    /**
     * 查询平台系统列表。
     *
     * @return 系统列表
     */
    @Override
    public List<PlatformSystemVO> listSystems() {
        return systemService.lambdaQuery()
                .eq(System::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(system -> toSystemVO(system, List.of()))
                .toList();
    }

    /**
     * 查询平台系统详情。
     *
     * @param systemId 系统 ID
     * @return 系统详情
     */
    @Override
    public PlatformSystemVO getSystem(Long systemId) {
        return toSystemVO(activeSystem(systemId), List.of());
    }

    /**
     * 变更系统状态。
     *
     * @param systemId 系统 ID
     * @param statusBO 状态入参
     * @return 系统详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformSystemVO changeSystemStatus(Long systemId, PlatformStatusBO statusBO) {
        if (!SystemStatus.isValid(statusBO.getStatus())) {
            throw new BusinessException(PlatErrorCode.SYSTEM_STATUS_INVALID);
        }
        System system = activeSystem(systemId);
        system.setStatus(statusBO.getStatus())
                .setUpdatedAt(LocalDateTime.now());
        systemService.updateById(system);
        return toSystemVO(system, List.of());
    }

    /**
     * 查询平台账号列表。
     *
     * @return 平台账号列表
     */
    @Override
    public List<PlatformAccountVO> listAccounts() {
        return accountService.lambdaQuery()
                .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(this::toAccountVO)
                .toList();
    }

    /**
     * 创建平台账号。
     *
     * @param saveBO 创建入参
     * @return 平台账号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformAccountVO createAccount(PlatformAccountSaveBO saveBO) {
        ensureAccountLoginNameAvailable(saveBO.getLoginName(), null);
        LocalDateTime now = LocalDateTime.now();
        Account account = new Account()
                .setLoginName(saveBO.getLoginName())
                .setPasswordHash(passwordEncoder.encode(saveBO.getInitialPassword()))
                .setDisplayName(defaultDisplayName(saveBO.getDisplayName(), saveBO.getLoginName()))
                .setMobile(saveBO.getMobile())
                .setEmail(saveBO.getEmail())
                .setStatus(AccountStatus.NORMAL.getCode())
                .setFirstLoginChangePwd((byte) 1)
                .setFailedLoginCount(0)
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        accountService.save(account);
        return toAccountVO(account);
    }

    /**
     * 查询平台账号详情。
     *
     * @param accountId 平台账号 ID
     * @return 平台账号
     */
    @Override
    public PlatformAccountVO getAccount(Long accountId) {
        return toAccountVO(activeAccount(accountId));
    }

    /**
     * 更新平台账号。
     *
     * @param accountId 平台账号 ID
     * @param updateBO 更新入参
     * @return 平台账号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformAccountVO updateAccount(Long accountId, PlatformAccountUpdateBO updateBO) {
        Account account = activeAccount(accountId);
        if (StringUtils.hasText(updateBO.getDisplayName())) {
            account.setDisplayName(updateBO.getDisplayName());
        }
        account.setMobile(updateBO.getMobile())
                .setEmail(updateBO.getEmail())
                .setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
        return toAccountVO(account);
    }

    /**
     * 变更账号状态。
     *
     * @param accountId 平台账号 ID
     * @param statusBO 状态入参
     * @return 平台账号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformAccountVO changeAccountStatus(Long accountId, PlatformStatusBO statusBO) {
        if (!accountStatusValid(statusBO.getStatus())) {
            throw new BusinessException(PlatErrorCode.ACCOUNT_STATUS_INVALID);
        }
        Account account = activeAccount(accountId);
        account.setStatus(statusBO.getStatus())
                .setUpdatedAt(LocalDateTime.now());
        if (!AccountStatus.LOCKED.getCode().equals(statusBO.getStatus())) {
            account.setLockedUntil(null);
        }
        accountService.updateById(account);
        return toAccountVO(account);
    }

    /**
     * 重置账号密码。
     *
     * @param accountId 平台账号 ID
     * @param resetPasswordBO 重置入参
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long accountId, PlatformAccountResetPasswordBO resetPasswordBO) {
        Account account = activeAccount(accountId);
        account.setPasswordHash(passwordEncoder.encode(resetPasswordBO.getNewPassword()))
                .setFirstLoginChangePwd((byte) 1)
                .setFailedLoginCount(0)
                .setLockedUntil(null)
                .setUpdatedAt(LocalDateTime.now());
        accountService.updateById(account);
    }

    /**
     * 分配平台账号角色。
     *
     * @param accountId 平台账号 ID
     * @param assignBO 分配入参
     * @return 平台账号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformAccountVO assignAccountRoles(Long accountId, PlatformAccountRoleAssignBO assignBO) {
        Account account = activeAccount(accountId);
        ensureRolesExist(assignBO.getRoleIds());
        accountRoleService.lambdaUpdate()
                .eq(AccountRole::getAccountId, accountId)
                .remove();
        LocalDateTime now = LocalDateTime.now();
        List<AccountRole> accountRoles = assignBO.getRoleIds().stream()
                .distinct()
                .map(roleId -> new AccountRole()
                        .setAccountId(accountId)
                        .setRoleId(roleId)
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList();
        if (!accountRoles.isEmpty()) {
            accountRoleService.saveBatch(accountRoles);
        }
        return toAccountVO(account);
    }

    /**
     * 查询平台角色列表。
     *
     * @return 平台角色列表
     */
    @Override
    public List<PlatformRoleVO> listRoles() {
        return roleService.lambdaQuery()
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(this::toRoleVO)
                .toList();
    }

    /**
     * 创建平台角色。
     *
     * @param saveBO 保存入参
     * @return 平台角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformRoleVO createRole(PlatformRoleSaveBO saveBO) {
        ensureRoleCodeAvailable(saveBO.getCode(), null);
        LocalDateTime now = LocalDateTime.now();
        Role role = new Role()
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setStatus(EnableStatus.ENABLED.getCode())
                .setProtectedFlag((byte) 0)
                .setSortOrder(100)
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        roleService.save(role);
        return toRoleVO(role);
    }

    /**
     * 更新平台角色。
     *
     * @param roleId 角色 ID
     * @param saveBO 保存入参
     * @return 平台角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformRoleVO updateRole(Long roleId, PlatformRoleSaveBO saveBO) {
        Role role = activeRole(roleId);
        ensureRoleCodeAvailable(saveBO.getCode(), roleId);
        role.setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setUpdatedAt(LocalDateTime.now());
        roleService.updateById(role);
        return toRoleVO(role);
    }

    /**
     * 变更平台角色状态。
     *
     * @param roleId 角色 ID
     * @param statusBO 状态入参
     * @return 平台角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformRoleVO changeRoleStatus(Long roleId, PlatformStatusBO statusBO) {
        if (!EnableStatus.isValid(statusBO.getStatus())) {
            throw new BusinessException(PlatErrorCode.ROLE_STATUS_INVALID);
        }
        Role role = activeRole(roleId);
        role.setStatus(statusBO.getStatus())
                .setUpdatedAt(LocalDateTime.now());
        roleService.updateById(role);
        return toRoleVO(role);
    }

    /**
     * 保存平台角色权限。
     *
     * @param roleId 角色 ID
     * @param permissionBO 权限入参
     * @return 平台角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformRoleVO saveRolePermissions(Long roleId, PlatformRolePermissionBO permissionBO) {
        Role role = activeRole(roleId);
        List<Menu> menus = menusByIds(permissionBO.getMenuIds());
        List<Operation> operations = operationsByCodes(permissionBO.getOperationCodes());
        roleMenuService.lambdaUpdate()
                .eq(RoleMenu::getRoleId, roleId)
                .remove();
        roleOperationService.lambdaUpdate()
                .eq(RoleOperation::getRoleId, roleId)
                .remove();
        LocalDateTime now = LocalDateTime.now();
        List<RoleMenu> roleMenus = menus.stream()
                .map(menu -> new RoleMenu()
                        .setRoleId(roleId)
                        .setMenuId(menu.getId())
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList();
        if (!roleMenus.isEmpty()) {
            roleMenuService.saveBatch(roleMenus);
        }
        List<RoleOperation> roleOperations = operations.stream()
                .map(operation -> new RoleOperation()
                        .setRoleId(roleId)
                        .setOperationId(operation.getId())
                        .setOperationCode(operation.getCode())
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList();
        if (!roleOperations.isEmpty()) {
            roleOperationService.saveBatch(roleOperations);
        }
        return toRoleVO(role);
    }

    /**
     * 查询平台配置列表。
     *
     * @return 配置列表
     */
    @Override
    public List<PlatformConfigVO> listConfigs() {
        return configService.lambdaQuery()
                .eq(Config::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(this::toConfigVO)
                .toList();
    }

    /**
     * 更新平台配置。
     *
     * @param configKey 配置 key
     * @param updateBO 更新入参
     * @return 配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformConfigVO updateConfig(String configKey, PlatformConfigUpdateBO updateBO) {
        Config config = activeConfig(configKey);
        if (isSensitive(config) && MASKED_VALUE.equals(updateBO.getValue())) {
            throw new BusinessException(PlatErrorCode.CONFIG_SENSITIVE_VALUE_FORBIDDEN);
        }
        config.setConfigValue(updateBO.getValue())
                .setRemark(updateBO.getRemark())
                .setUpdatedAt(LocalDateTime.now());
        configService.updateById(config);
        return toConfigVO(config);
    }

    /**
     * 查询平台权限目录。
     *
     * @return 权限目录
     */
    @Override
    public PlatformPermissionCatalogVO permissionCatalog() {
        List<Menu> menus = menuService.lambdaQuery()
                .eq(Menu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .sorted(Comparator.comparing(Menu::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<String> operationCodes = operationService.lambdaQuery()
                .eq(Operation::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(Operation::getCode)
                .sorted()
                .toList();
        return PlatformPermissionCatalogVO.builder()
                .menus(toMenuTree(menus))
                .operationCodes(operationCodes)
                .build();
    }

    private Account activeAccount(Long accountId) {
        Account account = accountService.lambdaQuery()
                .eq(Account::getId, accountId)
                .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (account == null) {
            throw new BusinessException(PlatErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private System activeSystem(Long systemId) {
        System system = systemService.lambdaQuery()
                .eq(System::getId, systemId)
                .eq(System::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (system == null) {
            throw new BusinessException(PlatErrorCode.SYSTEM_NOT_FOUND);
        }
        return system;
    }

    private Role activeRole(Long roleId) {
        Role role = roleService.lambdaQuery()
                .eq(Role::getId, roleId)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (role == null) {
            throw new BusinessException(PlatErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private Config activeConfig(String configKey) {
        Config config = configService.lambdaQuery()
                .eq(Config::getConfigKey, configKey)
                .eq(Config::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (config == null) {
            throw new BusinessException(PlatErrorCode.CONFIG_NOT_FOUND);
        }
        return config;
    }

    private void ensureSystemCodeAvailable(String code, Long excludeSystemId) {
        System system = systemService.lambdaQuery()
                .eq(System::getCode, code)
                .eq(System::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (system != null && !system.getId().equals(excludeSystemId)) {
            throw new BusinessException(PlatErrorCode.SYSTEM_CODE_DUPLICATED);
        }
    }

    private void ensureAccountLoginNameAvailable(String loginName, Long excludeAccountId) {
        Account account = accountService.lambdaQuery()
                .eq(Account::getLoginName, loginName)
                .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (account != null && !account.getId().equals(excludeAccountId)) {
            throw new BusinessException(PlatErrorCode.ACCOUNT_DUPLICATED);
        }
    }

    private void ensureRoleCodeAvailable(String code, Long excludeRoleId) {
        Role role = roleService.lambdaQuery()
                .eq(Role::getCode, code)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (role != null && !role.getId().equals(excludeRoleId)) {
            throw new BusinessException(PlatErrorCode.ROLE_CODE_DUPLICATED);
        }
    }

    private void ensureRolesExist(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> expectedRoleIds = new HashSet<>(roleIds);
        Set<Long> existingRoleIds = roleService.lambdaQuery()
                .in(Role::getId, expectedRoleIds)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(Role::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!existingRoleIds.containsAll(expectedRoleIds)) {
            throw new BusinessException(PlatErrorCode.ROLE_NOT_FOUND);
        }
    }

    private List<Menu> menusByIds(Collection<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        Set<Long> expectedMenuIds = new HashSet<>(menuIds);
        List<Menu> menus = menuService.lambdaQuery()
                .in(Menu::getId, expectedMenuIds)
                .eq(Menu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        if (menus.size() != expectedMenuIds.size()) {
            throw new BusinessException(PlatErrorCode.ROLE_NOT_FOUND, "平台菜单不存在");
        }
        return menus;
    }

    private List<Operation> operationsByCodes(Collection<String> operationCodes) {
        if (operationCodes == null || operationCodes.isEmpty()) {
            return List.of();
        }
        Set<String> expectedCodes = new HashSet<>(operationCodes);
        List<Operation> operations = operationService.lambdaQuery()
                .in(Operation::getCode, expectedCodes)
                .eq(Operation::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        if (operations.size() != expectedCodes.size()) {
            throw new BusinessException(PlatErrorCode.ROLE_NOT_FOUND, "平台操作权限不存在");
        }
        return operations;
    }

    private PlatformSystemVO toSystemVO(System system, List<InitializedObjectVO> initializedObjects) {
        return PlatformSystemVO.builder()
                .systemId(toId(system.getId()))
                .systemCode(system.getCode())
                .systemName(system.getName())
                .description(system.getDescription())
                .tenantMode(system.getTenantMode())
                .defaultTenantId(toId(system.getDefaultTenantId()))
                .ownerAccountId(toId(system.getOwnerAccountId()))
                .ownerMemberId(toId(system.getOwnerMemberId()))
                .status(system.getStatus())
                .initializedObjects(initializedObjects)
                .build();
    }

    private PlatformAccountVO toAccountVO(Account account) {
        return PlatformAccountVO.builder()
                .accountId(toId(account.getId()))
                .loginName(account.getLoginName())
                .displayName(account.getDisplayName())
                .mobile(account.getMobile())
                .email(account.getEmail())
                .status(account.getStatus())
                .roleIds(roleIdsByAccount(account.getId()))
                .build();
    }

    private PlatformRoleVO toRoleVO(Role role) {
        return PlatformRoleVO.builder()
                .roleId(toId(role.getId()))
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .status(role.getStatus())
                .protectedFlag(role.getProtectedFlag() != null && role.getProtectedFlag() == 1)
                .menuIds(menuIdsByRole(role.getId()))
                .operationCodes(operationCodesByRole(role.getId()))
                .build();
    }

    private PlatformConfigVO toConfigVO(Config config) {
        return PlatformConfigVO.builder()
                .configKey(config.getConfigKey())
                .configName(config.getConfigName())
                .value(isSensitive(config) ? MASKED_VALUE : config.getConfigValue())
                .sensitive(isSensitive(config))
                .status(config.getStatus())
                .remark(config.getRemark())
                .build();
    }

    private List<String> roleIdsByAccount(Long accountId) {
        return accountRoleService.lambdaQuery()
                .eq(AccountRole::getAccountId, accountId)
                .list()
                .stream()
                .map(AccountRole::getRoleId)
                .map(this::toId)
                .toList();
    }

    private List<String> menuIdsByRole(Long roleId) {
        return roleMenuService.lambdaQuery()
                .eq(RoleMenu::getRoleId, roleId)
                .list()
                .stream()
                .map(RoleMenu::getMenuId)
                .map(this::toId)
                .toList();
    }

    private List<String> operationCodesByRole(Long roleId) {
        return roleOperationService.lambdaQuery()
                .eq(RoleOperation::getRoleId, roleId)
                .list()
                .stream()
                .map(RoleOperation::getOperationCode)
                .toList();
    }

    private List<PlatformMenuTreeVO> toMenuTree(List<Menu> menus) {
        Map<Long, List<Menu>> children = new LinkedHashMap<>();
        for (Menu menu : menus) {
            children.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
        }
        return toMenuTree(children, 0L);
    }

    private List<PlatformMenuTreeVO> toMenuTree(Map<Long, List<Menu>> children, Long parentId) {
        return children.getOrDefault(parentId, List.of())
                .stream()
                .map(menu -> PlatformMenuTreeVO.builder()
                        .menuId(toId(menu.getId()))
                        .parentId(toId(menu.getParentId()))
                        .code(menu.getCode())
                        .name(menu.getName())
                        .path(menu.getPath())
                        .icon(menu.getIcon())
                        .status(menu.getStatus())
                        .children(toMenuTree(children, menu.getId()))
                        .build())
                .toList();
    }

    private boolean isSensitive(Config config) {
        return config.getSensitiveFlag() != null && config.getSensitiveFlag() == 1;
    }

    private boolean accountStatusValid(String status) {
        for (AccountStatus accountStatus : AccountStatus.values()) {
            if (accountStatus.getCode().equals(status)) {
                return true;
            }
        }
        return false;
    }

    private String defaultDisplayName(String displayName, String loginName) {
        return StringUtils.hasText(displayName) ? displayName : loginName;
    }

    private String displayName(Account account) {
        return StringUtils.hasText(account.getDisplayName()) ? account.getDisplayName() : account.getLoginName();
    }

    private InitializedObjectVO initialized(String objectType, String code, Long id, String status) {
        return InitializedObjectVO.builder()
                .objectType(objectType)
                .code(code)
                .id(toId(id))
                .status(status)
                .build();
    }

    private String toId(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
