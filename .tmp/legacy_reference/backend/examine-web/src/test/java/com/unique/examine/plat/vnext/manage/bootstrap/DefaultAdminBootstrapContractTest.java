package com.unique.examine.plat.vnext.manage.bootstrap;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.AccountRole;
import com.unique.examine.plat.vnext.base.entity.Credential;
import com.unique.examine.plat.vnext.base.entity.Permission;
import com.unique.examine.plat.vnext.base.entity.Role;
import com.unique.examine.plat.vnext.base.entity.RolePermission;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatCredentialService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatPermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRolePermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRoleService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAdminBootstrapContractTest {
    private final PasswordService passwordService = new PasswordService();

    @BeforeAll
    static void initializeGeneratedEntityMetadata() {
        var configuration = new MybatisConfiguration();
        for (var entityType : List.of(
                Account.class,
                Credential.class,
                Role.class,
                Permission.class,
                RolePermission.class,
                AccountRole.class
        )) {
            var assistant = new MapperBuilderAssistant(configuration, "bootstrap-contract-test");
            assistant.setCurrentNamespace(entityType.getName());
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void createsOneSecureAdministratorGraphAndNeverResetsThePassword() {
        var state = new FoundationState();
        bootstrap(state, "first-secret").run(null);

        assertEquals(1, state.accounts.size());
        assertEquals(1, state.credentials.size());
        assertEquals(1, state.roles.size());
        assertEquals(3, state.permissions.size());
        assertEquals(3, state.rolePermissions.size());
        assertEquals(1, state.accountRoles.size());
        var originalHash = state.credentials.getFirst().getPasswordHash();
        assertNotEquals("first-secret", originalHash);
        assertTrue(passwordService.matches("first-secret", originalHash));

        bootstrap(state, "replacement-must-not-be-used").run(null);

        assertEquals(1, state.accounts.size());
        assertEquals(1, state.credentials.size());
        assertEquals(1, state.roles.size());
        assertEquals(3, state.permissions.size());
        assertEquals(3, state.rolePermissions.size());
        assertEquals(1, state.accountRoles.size());
        assertEquals(originalHash, state.credentials.getFirst().getPasswordHash());
        assertTrue(passwordService.matches("first-secret", originalHash));
    }

    @Test
    void refusesToReenableAnExistingDisabledAdministrator() {
        var state = new FoundationState();
        var account = new Account();
        account.setId(7L);
        account.setUsername("admin");
        account.setUsernameNormalized("admin");
        account.setStatus("DISABLED");
        state.accounts.add(account);

        var failure = assertThrows(IllegalStateException.class, () -> bootstrap(state, "unused").run(null));

        assertTrue(failure.getMessage().startsWith("FOUNDATION_BOOTSTRAP_ADMIN_UNAVAILABLE"));
        assertEquals(0, state.credentials.size());
        assertEquals("DISABLED", account.getStatus());
    }

    @Test
    void useCaseOwnsOneTransactionAndDependsOnlyOnGeneratedServices() throws NoSuchMethodException {
        assertEquals("vnext", DefaultAdminBootstrap.class.getAnnotation(Profile.class).value()[0]);
        assertTrue(DefaultAdminBootstrap.class.getMethod("run", org.springframework.boot.ApplicationArguments.class)
                .isAnnotationPresent(Transactional.class));
        var dependencies = List.of(DefaultAdminBootstrap.class.getDeclaredFields()).stream()
                .map(field -> field.getType().getName())
                .toList();
        assertTrue(dependencies.contains(IVNextPlatAccountService.class.getName()));
        assertTrue(dependencies.contains(IVNextPlatCredentialService.class.getName()));
        assertTrue(dependencies.contains(IVNextPlatRoleService.class.getName()));
        assertTrue(dependencies.contains(IVNextPlatPermissionService.class.getName()));
        assertTrue(dependencies.contains(IVNextPlatRolePermissionService.class.getName()));
        assertTrue(dependencies.contains(IVNextPlatAccountRoleService.class.getName()));
        assertTrue(dependencies.stream().noneMatch(name -> name.contains(".base.mapper.")));
        assertTrue(dependencies.stream().noneMatch(name -> name.contains("Jdbc")));
        assertTrue(new DefaultAdminBootstrapProperties("admin", "do-not-render", "Admin")
                .toString().contains("password=<redacted>"));
        assertFalse(new DefaultAdminBootstrapProperties("admin", "do-not-render", "Admin")
                .toString().contains("do-not-render"));
    }

    private DefaultAdminBootstrap bootstrap(FoundationState state, String password) {
        return new DefaultAdminBootstrap(
                new DefaultAdminBootstrapProperties("admin", password, "Platform Administrator"),
                service(IVNextPlatAccountService.class, state.accounts, Account::getUsernameNormalized),
                service(IVNextPlatCredentialService.class, state.credentials,
                        credential -> List.of(credential.getAccountId(), credential.getCredentialType())),
                service(IVNextPlatRoleService.class, state.roles,
                        role -> List.of(role.getScopeType(), role.getScopeKey(), role.getRoleCode())),
                service(IVNextPlatPermissionService.class, state.permissions,
                        permission -> List.of(
                                permission.getScopeType(), permission.getScopeKey(), permission.getPermissionCode()
                        )),
                service(IVNextPlatRolePermissionService.class, state.rolePermissions,
                        link -> List.of(link.getRoleId(), link.getPermissionId())),
                service(IVNextPlatAccountRoleService.class, state.accountRoles,
                        link -> List.of(link.getAccountId(), link.getRoleId())),
                state.ids,
                passwordService
        );
    }

    private static <S, E> S service(
            Class<S> serviceType,
            List<E> rows,
            Function<E, Object> keyValues
    ) {
        return serviceType.cast(Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    if ("getOne".equals(method.getName())) {
                        var wrapper = (Wrapper<?>) args[0];
                        wrapper.getSqlSegment();
                        Collection<Object> parameters = ((AbstractWrapper<?, ?, ?>) wrapper)
                                .getParamNameValuePairs().values();
                        return rows.stream()
                                .filter(row -> flatten(keyValues.apply(row)).stream().allMatch(parameters::contains))
                                .findFirst()
                                .orElse(null);
                    }
                    if ("save".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        var row = (E) args[0];
                        rows.add(row);
                        return true;
                    }
                    if ("toString".equals(method.getName())) {
                        return serviceType.getSimpleName() + rows;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new AssertionError("Unexpected generated service call: " + method);
                }
        ));
    }

    private static List<Object> flatten(Object keyValues) {
        if (keyValues instanceof List<?> values) {
            return new ArrayList<>(values);
        }
        return List.of(keyValues);
    }

    private static final class FoundationState {
        private final List<Account> accounts = new ArrayList<>();
        private final List<Credential> credentials = new ArrayList<>();
        private final List<Role> roles = new ArrayList<>();
        private final List<Permission> permissions = new ArrayList<>();
        private final List<RolePermission> rolePermissions = new ArrayList<>();
        private final List<AccountRole> accountRoles = new ArrayList<>();
        private final IdService ids = new SequenceIdService();
    }

    private static final class SequenceIdService extends IdService {
        private final AtomicLong sequence = new AtomicLong(1000);

        @Override
        public long nextId() {
            return sequence.incrementAndGet();
        }
    }
}
