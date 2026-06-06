package com.unique.examine.plat.manage.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContext;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.service.IAccountRoleService;
import com.unique.examine.plat.base.service.IAccountService;
import com.unique.examine.plat.base.service.IPermissionService;
import com.unique.examine.plat.base.service.IRolePermissionService;
import com.unique.examine.plat.base.service.IRoleService;
import com.unique.examine.plat.base.service.ISystemService;
import com.unique.examine.plat.base.service.ITenantService;
import com.unique.examine.plat.manage.bo.AccountRoleAssignBO;
import com.unique.examine.plat.manage.bo.PlatformAccountSaveBO;
import com.unique.examine.plat.manage.bo.PlatformPermissionSaveBO;
import com.unique.examine.plat.manage.bo.PlatformRoleSaveBO;
import com.unique.examine.plat.manage.bo.PlatformStatusBO;
import com.unique.examine.plat.manage.bo.PlatformSystemSaveBO;
import com.unique.examine.plat.manage.bo.PlatformTenantSaveBO;
import com.unique.examine.plat.manage.bo.RolePermissionAssignBO;
import com.unique.examine.plat.manage.converter.PlatformManageConverter;
import com.unique.examine.plat.manage.dto.PlatformLoginDTO;
import com.unique.examine.plat.manage.enums.PlatManageErrorCode;
import com.unique.examine.plat.manage.service.PlatformManageService;
import com.unique.examine.plat.manage.vo.PlatformManageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台管理业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class PlatformManageServiceImpl implements PlatformManageService {

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";
    private static final String LOCKED = "LOCKED";
    private static final String EXPIRED = "EXPIRED";
    private static final String PERMISSION_TYPE_MENU = "MENU";
    private static final String PERMISSION_TYPE_BUTTON = "BUTTON";
    private static final String PERMISSION_TYPE_API = "API";
    private static final String PERMISSION_TYPE_FIELD = "FIELD";
    private static final String PERMISSION_TYPE_DATA_SCOPE = "DATA_SCOPE";

    private final ISystemService systemService;
    private final ITenantService tenantService;
    private final IAccountService accountService;
    private final IRoleService roleService;
    private final IPermissionService permissionService;
    private final IRolePermissionService rolePermissionService;
    private final IAccountRoleService accountRoleService;

    @Override
    public List<PlatformManageVO> listSystems() {
        return systemService.list().stream().map(PlatformManageConverter::fromSystem).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO createSystem(PlatformSystemSaveBO bo) {
        requireText(bo.getSystemCode(), "systemCode");
        requireText(bo.getSystemName(), "systemName");
        com.unique.examine.plat.base.entity.System entity = new com.unique.examine.plat.base.entity.System();
        entity.setSystemCode(bo.getSystemCode());
        entity.setSystemName(bo.getSystemName());
        entity.setDescription(bo.getDescription());
        entity.setStatus(ENABLED);
        fillAudit(entity::setCreatedBy, entity::setUpdatedBy);
        systemService.save(entity);
        return PlatformManageConverter.fromSystem(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO updateSystemStatus(Long id, PlatformStatusBO bo) {
        com.unique.examine.plat.base.entity.System entity = systemService.getById(id);
        if (ObjectUtil.isNull(entity)) {
            throwError(PlatManageErrorCode.DATA_NOT_FOUND);
        }
        validateStatus(bo.getStatus(), false);
        entity.setStatus(bo.getStatus());
        fillUpdatedBy(entity::setUpdatedBy);
        systemService.updateById(entity);
        return PlatformManageConverter.fromSystem(entity);
    }

    @Override
    public List<PlatformManageVO> listTenants() {
        return tenantService.list().stream().map(PlatformManageConverter::fromTenant).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO createTenant(PlatformTenantSaveBO bo) {
        requireText(bo.getTenantCode(), "tenantCode");
        requireText(bo.getTenantName(), "tenantName");
        Tenant tenant = new Tenant();
        tenant.setTenantCode(bo.getTenantCode());
        tenant.setTenantName(bo.getTenantName());
        tenant.setAdminAccountId(bo.getAdminAccountId());
        tenant.setExpireAt(bo.getExpireAt());
        tenant.setConfigJson(bo.getConfigJson());
        tenant.setStatus(ENABLED);
        fillAudit(tenant::setCreatedBy, tenant::setUpdatedBy);
        tenantService.save(tenant);
        return PlatformManageConverter.fromTenant(tenant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO updateTenantStatus(Long id, PlatformStatusBO bo) {
        Tenant tenant = tenantService.getById(id);
        if (ObjectUtil.isNull(tenant)) {
            throwError(PlatManageErrorCode.DATA_NOT_FOUND);
        }
        validateStatus(bo.getStatus(), true);
        tenant.setStatus(bo.getStatus());
        fillUpdatedBy(tenant::setUpdatedBy);
        tenantService.updateById(tenant);
        return PlatformManageConverter.fromTenant(tenant);
    }

    @Override
    public List<PlatformManageVO> listAccounts() {
        return accountService.list().stream().map(PlatformManageConverter::fromAccount).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO createAccount(PlatformAccountSaveBO bo) {
        requireText(bo.getUsername(), "username");
        requireText(bo.getDisplayName(), "displayName");
        Account account = new Account();
        account.setUsername(bo.getUsername());
        account.setDisplayName(bo.getDisplayName());
        account.setMobile(bo.getMobile());
        account.setEmail(bo.getEmail());
        account.setPasswordHash(StrUtil.blankToDefault(bo.getPassword(), "change-me"));
        account.setStatus(ENABLED);
        fillAudit(account::setCreatedBy, account::setUpdatedBy);
        accountService.save(account);
        return PlatformManageConverter.fromAccount(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO updateAccountStatus(Long id, PlatformStatusBO bo) {
        Account account = accountService.getById(id);
        if (ObjectUtil.isNull(account)) {
            throwError(PlatManageErrorCode.DATA_NOT_FOUND);
        }
        validateStatus(bo.getStatus(), true);
        account.setStatus(bo.getStatus());
        fillUpdatedBy(account::setUpdatedBy);
        accountService.updateById(account);
        return PlatformManageConverter.fromAccount(account);
    }

    @Override
    public List<PlatformManageVO> listRoles(Long tenantId, Long systemId) {
        return roleService.list(Wrappers.<Role>lambdaQuery()
                        .eq(ObjectUtil.isNotNull(tenantId), Role::getTenantId, tenantId)
                        .eq(ObjectUtil.isNotNull(systemId), Role::getSystemId, systemId))
                .stream().map(PlatformManageConverter::fromRole).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO createRole(PlatformRoleSaveBO bo) {
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getSystemId(), "systemId");
        requireText(bo.getRoleCode(), "roleCode");
        requireText(bo.getRoleName(), "roleName");
        Role role = new Role();
        role.setTenantId(bo.getTenantId());
        role.setSystemId(bo.getSystemId());
        role.setAppId(bo.getAppId());
        role.setRoleCode(bo.getRoleCode());
        role.setRoleName(bo.getRoleName());
        role.setRoleType(StrUtil.blankToDefault(bo.getRoleType(), "TENANT"));
        role.setStatus(ENABLED);
        fillAudit(role::setCreatedBy, role::setUpdatedBy);
        roleService.save(role);
        return PlatformManageConverter.fromRole(role);
    }

    @Override
    public List<PlatformManageVO> listPermissions() {
        return permissionService.list().stream().map(PlatformManageConverter::fromPermission).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO createPermission(PlatformPermissionSaveBO bo) {
        requireText(bo.getPermissionCode(), "permissionCode");
        requireText(bo.getPermissionName(), "permissionName");
        Permission permission = new Permission();
        permission.setTenantId(bo.getTenantId());
        permission.setSystemId(bo.getSystemId());
        permission.setAppId(bo.getAppId());
        permission.setModuleId(bo.getModuleId());
        permission.setPermissionCode(bo.getPermissionCode());
        permission.setPermissionName(bo.getPermissionName());
        permission.setPermissionType(resolvePermissionType(bo.getPermissionType()));
        permission.setResourcePath(bo.getResourcePath());
        fillAudit(permission::setCreatedBy, permission::setUpdatedBy);
        permissionService.save(permission);
        return PlatformManageConverter.fromPermission(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO assignRolePermissions(RolePermissionAssignBO bo) {
        requireId(bo.getRoleId(), "roleId");
        if (CollUtil.isEmpty(bo.getPermissionIds())) {
            throwError(PlatManageErrorCode.PARAM_REQUIRED);
        }
        rolePermissionService.remove(Wrappers.<RolePermission>lambdaQuery().eq(RolePermission::getRoleId, bo.getRoleId()));
        List<RolePermission> relations = new ArrayList<>();
        for (Long permissionId : bo.getPermissionIds()) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(bo.getRoleId());
            relation.setPermissionId(permissionId);
            fillAudit(relation::setCreatedBy, relation::setUpdatedBy);
            relations.add(relation);
        }
        rolePermissionService.saveBatch(relations);
        Role role = roleService.getById(bo.getRoleId());
        PlatformManageVO vo = PlatformManageConverter.fromRole(role);
        vo.setPermissionIds(bo.getPermissionIds());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformManageVO assignAccountRoles(AccountRoleAssignBO bo) {
        requireId(bo.getAccountId(), "accountId");
        requireId(bo.getTenantId(), "tenantId");
        requireId(bo.getSystemId(), "systemId");
        if (CollUtil.isEmpty(bo.getRoleIds())) {
            throwError(PlatManageErrorCode.PARAM_REQUIRED);
        }
        accountRoleService.remove(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getAccountId, bo.getAccountId())
                .eq(AccountRole::getTenantId, bo.getTenantId())
                .eq(AccountRole::getSystemId, bo.getSystemId()));
        List<AccountRole> relations = new ArrayList<>();
        for (Long roleId : bo.getRoleIds()) {
            AccountRole relation = new AccountRole();
            relation.setAccountId(bo.getAccountId());
            relation.setTenantId(bo.getTenantId());
            relation.setSystemId(bo.getSystemId());
            relation.setRoleId(roleId);
            fillAudit(relation::setCreatedBy, relation::setUpdatedBy);
            relations.add(relation);
        }
        accountRoleService.saveBatch(relations);
        Account account = accountService.getById(bo.getAccountId());
        PlatformManageVO vo = PlatformManageConverter.fromAccount(account);
        vo.setTenantId(bo.getTenantId());
        vo.setSystemId(bo.getSystemId());
        vo.setRoleIds(bo.getRoleIds());
        return vo;
    }

    @Override
    public PlatformManageVO login(PlatformLoginDTO dto) {
        requireText(dto.getUsername(), "username");
        Account account = accountService.getOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getUsername, dto.getUsername())
                .last("limit 1"));
        if (ObjectUtil.isNull(account) || !ENABLED.equals(account.getStatus())) {
            throwError(PlatManageErrorCode.LOGIN_FAILED);
        }
        // MVP 只校验账号可用性，生产部署时需接入正式密码摘要校验与 token 签发。
        return PlatformManageConverter.fromAccount(account);
    }

    /**
     * 校验文本必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireText(String value, String field) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(PlatManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 校验 ID 必填。
     *
     * @param value 字段值
     * @param field 字段名
     */
    private void requireId(Long value, String field) {
        if (ObjectUtil.isNull(value)) {
            throw new BusinessException(PlatManageErrorCode.PARAM_REQUIRED.getCode(), field + " is required");
        }
    }

    /**
     * 校验平台状态。
     *
     * @param status 状态值
     * @param allowExpired 是否允许 EXPIRED
     */
    private void validateStatus(String status, boolean allowExpired) {
        boolean valid = ENABLED.equals(status) || DISABLED.equals(status) || LOCKED.equals(status)
                || (allowExpired && EXPIRED.equals(status));
        if (!valid) {
            throwError(PlatManageErrorCode.STATUS_INVALID);
        }
    }

    /**
     * 解析并校验权限类型，空值沿用菜单默认值。
     *
     * @param permissionType 权限类型
     * @return 合法权限类型
     */
    private String resolvePermissionType(String permissionType) {
        if (StrUtil.isBlank(permissionType)) {
            return PERMISSION_TYPE_MENU;
        }
        boolean valid = PERMISSION_TYPE_MENU.equals(permissionType) || PERMISSION_TYPE_BUTTON.equals(permissionType)
                || PERMISSION_TYPE_API.equals(permissionType) || PERMISSION_TYPE_FIELD.equals(permissionType)
                || PERMISSION_TYPE_DATA_SCOPE.equals(permissionType);
        if (!valid) {
            throwError(PlatManageErrorCode.PERMISSION_TYPE_INVALID);
        }
        return permissionType;
    }

    /**
     * 写入创建与更新人。
     *
     * @param createdSetter 创建人写入器
     * @param updatedSetter 更新人写入器
     */
    private void fillAudit(java.util.function.Consumer<Long> createdSetter, java.util.function.Consumer<Long> updatedSetter) {
        Long accountId = currentAccountId();
        createdSetter.accept(accountId);
        updatedSetter.accept(accountId);
    }

    /**
     * 写入更新人。
     *
     * @param updatedSetter 更新人写入器
     */
    private void fillUpdatedBy(java.util.function.Consumer<Long> updatedSetter) {
        updatedSetter.accept(currentAccountId());
    }

    /**
     * 获取当前账号 ID。
     *
     * @return 当前账号 ID
     */
    private Long currentAccountId() {
        AuthContext context = AuthContextHolder.get();
        return ObjectUtil.isNull(context) ? null : context.getAccountId();
    }

    /**
     * 抛出平台管理异常。
     *
     * @param errorCode 错误码
     */
    private void throwError(PlatManageErrorCode errorCode) {
        throw new BusinessException(errorCode.getCode(), errorCode.getMessage());
    }
}
