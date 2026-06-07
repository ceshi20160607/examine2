package com.unique.examine.plat.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.Menu;
import com.unique.examine.plat.base.entity.Operation;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RoleMenu;
import com.unique.examine.plat.base.entity.RoleOperation;
import com.unique.examine.plat.base.entity.System;
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
import com.unique.examine.plat.manage.bo.PlatformRolePermissionBO;
import com.unique.examine.plat.manage.bo.PlatformStatusBO;
import com.unique.examine.plat.manage.bo.PlatformSystemSaveBO;
import com.unique.examine.plat.manage.enums.AccountStatus;
import com.unique.examine.plat.manage.enums.PlatErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatformCenterServiceImplTest {

    private IAccountService accountService;

    private ISystemService systemService;

    private ITenantService tenantService;

    private IRoleService roleService;

    private IMenuService menuService;

    private IOperationService operationService;

    private IAccountRoleService accountRoleService;

    private IRoleMenuService roleMenuService;

    private IRoleOperationService roleOperationService;

    private IConfigService configService;

    private PlatformCenterServiceImpl service;

    @BeforeEach
    void setUp() {
        accountService = mock(IAccountService.class);
        systemService = mock(ISystemService.class);
        tenantService = mock(ITenantService.class);
        roleService = mock(IRoleService.class);
        menuService = mock(IMenuService.class);
        operationService = mock(IOperationService.class);
        accountRoleService = mock(IAccountRoleService.class);
        roleMenuService = mock(IRoleMenuService.class);
        roleOperationService = mock(IRoleOperationService.class);
        configService = mock(IConfigService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        service = new PlatformCenterServiceImpl(accountService, systemService, tenantService, roleService, menuService,
                operationService, accountRoleService, roleMenuService, roleOperationService, configService,
                command -> null, passwordEncoder);
    }

    @Test
    void shouldRejectDuplicatedSystemCodeBeforeSaving() {
        System existedSystem = new System()
                .setId(1L)
                .setCode("demo")
                .setDeleteToken(0L);
        when(systemService.lambdaQuery()).thenReturn(queryReturningOne(existedSystem));

        PlatformSystemSaveBO saveBO = new PlatformSystemSaveBO();
        saveBO.setCode("demo");
        saveBO.setName("demo");
        saveBO.setTenantMode("SINGLE");

        assertThatThrownBy(() -> service.createSystem(100L, saveBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlatErrorCode.SYSTEM_CODE_DUPLICATED);

        verify(systemService, never()).save(any(System.class));
        verify(tenantService, never()).save(any());
    }

    @Test
    void shouldChangeAccountStatusAndClearLockedUntilWhenNotLocked() {
        Account account = new Account()
                .setId(100L)
                .setLoginName("admin")
                .setDisplayName("admin")
                .setStatus(AccountStatus.LOCKED.getCode())
                .setLockedUntil(LocalDateTime.parse("2026-06-06T12:00:00"))
                .setDeleteToken(0L);
        when(accountService.lambdaQuery()).thenReturn(queryReturningOne(account));
        when(accountRoleService.lambdaQuery()).thenReturn(queryReturningList(List.of()));

        PlatformStatusBO statusBO = new PlatformStatusBO();
        statusBO.setStatus(AccountStatus.DISABLED.getCode());

        service.changeAccountStatus(100L, statusBO);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.DISABLED.getCode());
        assertThat(account.getLockedUntil()).isNull();
        verify(accountService).updateById(account);
    }

    @Test
    void shouldReplaceRoleMenusAndOperations() {
        Role role = new Role()
                .setId(10L)
                .setCode("PLATFORM_ADMIN")
                .setName("Platform admin")
                .setStatus("ENABLED")
                .setProtectedFlag((byte) 0)
                .setDeleteToken(0L);
        Menu menu = new Menu()
                .setId(20L)
                .setCode("SYSTEM")
                .setDeleteToken(0L);
        Operation operation = new Operation()
                .setId(30L)
                .setCode("platform:system:update")
                .setDeleteToken(0L);

        when(roleService.lambdaQuery()).thenReturn(queryReturningOne(role));
        when(menuService.lambdaQuery()).thenReturn(queryReturningList(List.of(menu)));
        when(operationService.lambdaQuery()).thenReturn(queryReturningList(List.of(operation)));
        when(roleMenuService.lambdaUpdate()).thenReturn(updateRemoving());
        when(roleOperationService.lambdaUpdate()).thenReturn(updateRemoving());
        when(roleMenuService.lambdaQuery()).thenReturn(queryReturningList(List.of()));
        when(roleOperationService.lambdaQuery()).thenReturn(queryReturningList(List.of()));

        PlatformRolePermissionBO permissionBO = new PlatformRolePermissionBO();
        permissionBO.setMenuIds(List.of(20L));
        permissionBO.setOperationCodes(List.of("platform:system:update"));

        service.saveRolePermissions(10L, permissionBO);

        ArgumentCaptor<Collection<RoleMenu>> roleMenuCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<RoleOperation>> roleOperationCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(roleMenuService).saveBatch(roleMenuCaptor.capture());
        verify(roleOperationService).saveBatch(roleOperationCaptor.capture());
        assertThat(roleMenuCaptor.getValue()).extracting(RoleMenu::getMenuId).containsExactly(20L);
        assertThat(roleOperationCaptor.getValue()).extracting(RoleOperation::getOperationCode)
                .containsExactly("platform:system:update");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> queryReturningOne(T entity) {
        return mock(LambdaQueryChainWrapper.class, invocation -> {
            if ("one".equals(invocation.getMethod().getName())) {
                return entity;
            }
            if ("list".equals(invocation.getMethod().getName())) {
                return entity == null ? List.of() : List.of(entity);
            }
            return invocation.getMock();
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> queryReturningList(List<T> values) {
        return mock(LambdaQueryChainWrapper.class, invocation -> {
            if ("one".equals(invocation.getMethod().getName())) {
                return values.isEmpty() ? null : values.get(0);
            }
            if ("list".equals(invocation.getMethod().getName())) {
                return values;
            }
            return invocation.getMock();
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaUpdateChainWrapper<T> updateRemoving() {
        return mock(LambdaUpdateChainWrapper.class, invocation -> {
            if ("remove".equals(invocation.getMethod().getName())) {
                return true;
            }
            return invocation.getMock();
        });
    }
}
