package com.unique.examine.web;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.base.entity.Security;
import com.unique.examine.core.base.service.ISecurityService;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.manage.controller.AuthController;
import com.unique.examine.plat.manage.controller.EnterpriseSsoController;
import com.unique.examine.plat.manage.controller.MeController;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.AccountRole;
import com.unique.examine.plat.vnext.base.entity.ContextSession;
import com.unique.examine.plat.vnext.base.entity.Credential;
import com.unique.examine.plat.vnext.base.entity.Permission;
import com.unique.examine.plat.vnext.base.entity.RefreshToken;
import com.unique.examine.plat.vnext.base.entity.Role;
import com.unique.examine.plat.vnext.base.entity.RolePermission;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatContextSessionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatCredentialService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatPermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRefreshTokenService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRolePermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRoleService;
import com.unique.examine.plat.vnext.manage.auth.ClientRequest;
import com.unique.examine.plat.vnext.manage.auth.VNextAuthTokenService;
import com.unique.examine.plat.vnext.manage.auth.VNextLoginService;
import com.unique.examine.plat.vnext.manage.auth.VNextPlatformPermissionResolver;
import com.unique.examine.plat.vnext.manage.auth.VNextSecurityAuditService;
import com.unique.examine.plat.vnext.manage.auth.VNextSessionService;
import com.unique.examine.web.filter.AuthenticationFilter;
import com.unique.examine.web.vnext.auth.VNextAuthController;
import com.unique.examine.web.vnext.auth.VNextAuthenticationFilter;
import com.unique.examine.web.vnext.auth.VNextMeController;
import com.unique.examine.web.vnext.auth.VNextP1OperationAuditBoundary;
import com.unique.examine.web.vnext.auth.VNextSessionCookieSupport;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.LocalDateTime;
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

class P1AuthUseCaseContractTest {
    private static final ClientRequest CLIENT = new ClientRequest(
            "request-1", "trace-1", "127.0.0.1", "contract-test"
    );

    @BeforeAll
    static void initializeGeneratedEntityMetadata() {
        var configuration = new MybatisConfiguration();
        for (var entityType : List.of(
                Account.class,
                Credential.class,
                AccountRole.class,
                Role.class,
                RolePermission.class,
                Permission.class,
                ContextSession.class,
                RefreshToken.class
        )) {
            var assistant = new MapperBuilderAssistant(configuration, "p1-auth-contract-test");
            assistant.setCurrentNamespace(entityType.getName());
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void loginBuildsRelationalPlatformContextAndStoresOnlyTokenHashes() {
        var fixture = new Fixture();
        var issued = fixture.loginService.login("ADMIN", "contract-secret", CLIENT);

        assertEquals("admin", issued.accountContext().account().username());
        assertEquals("PLATFORM", issued.accountContext().context().type());
        assertEquals(List.of("PLATFORM_RUNTIME", "PLATFORM_ADMIN"), issued.accountContext().context().shells());
        assertEquals(
                java.util.Set.of(
                        "platform.runtime.access", "platform.admin.access", "platform.system.manage"
                ),
                issued.accountContext().context().permissions()
        );
        assertTrue(issued.accountContext().systems().isEmpty());
        assertEquals(1, fixture.contextSessions.size());
        assertEquals(1, fixture.refreshTokens.size());
        assertEquals(1, fixture.securityEvents.size());
        assertEquals("LOGIN", fixture.securityEvents.getFirst().getEventType());
        assertEquals("SUCCESS", fixture.securityEvents.getFirst().getResult());

        var context = fixture.contextSessions.getFirst();
        var refresh = fixture.refreshTokens.getFirst();
        assertEquals(fixture.tokenService.hash(issued.accessToken()), context.getTokenHash());
        assertEquals(fixture.tokenService.hash(issued.refreshToken()), refresh.getTokenHash());
        assertNotEquals(issued.accessToken(), context.getTokenHash());
        assertNotEquals(issued.refreshToken(), refresh.getTokenHash());
    }

    @Test
    void invalidCredentialsAuditButNeverCreateSessionOrRefreshToken() {
        var fixture = new Fixture();

        var failure = assertThrows(
                BusinessException.class,
                () -> fixture.loginService.login("admin", "incorrect-password", CLIENT)
        );

        assertEquals("AUTH_INVALID_CREDENTIALS", failure.code());
        assertTrue(fixture.contextSessions.isEmpty());
        assertTrue(fixture.refreshTokens.isEmpty());
        assertEquals(1, fixture.securityEvents.size());
        assertEquals("DENIED", fixture.securityEvents.getFirst().getResult());
        assertEquals("AUTH_INVALID_CREDENTIALS", fixture.securityEvents.getFirst().getFailureCode());
    }

    @Test
    void refreshRotatesOnceAndReplayRevokesTheFamilyAndSession() {
        var fixture = new Fixture();
        var login = fixture.loginService.login("admin", "contract-secret", CLIENT);

        var refreshed = fixture.sessionService.refresh(login.refreshToken(), CLIENT);

        assertEquals(2, fixture.refreshTokens.size());
        assertEquals("USED", fixture.refreshTokens.getFirst().getStatus());
        assertEquals(fixture.refreshTokens.get(1).getId(), fixture.refreshTokens.getFirst().getRotatedToId());
        assertEquals("ACTIVE", fixture.refreshTokens.get(1).getStatus());
        assertEquals(
                fixture.tokenService.hash(refreshed.accessToken()),
                fixture.contextSessions.getFirst().getTokenHash()
        );

        var replay = assertThrows(
                BusinessException.class,
                () -> fixture.sessionService.refresh(login.refreshToken(), CLIENT)
        );

        assertEquals("AUTH_REFRESH_REPLAY", replay.code());
        assertEquals("REVOKED", fixture.contextSessions.getFirst().getStatus());
        assertTrue(fixture.refreshTokens.stream().allMatch(token -> "REVOKED".equals(token.getStatus())));
        assertEquals("AUTH_REFRESH_REPLAY", fixture.securityEvents.getLast().getFailureCode());
    }

    @Test
    void failedAtomicRefreshClaimIsTreatedAsReplayAndRevokesFamilyAndSession() {
        var fixture = new Fixture();
        var login = fixture.loginService.login("admin", "contract-secret", CLIENT);
        fixture.rejectNextRefreshClaim = true;

        var replay = assertThrows(
                BusinessException.class,
                () -> fixture.sessionService.refresh(login.refreshToken(), CLIENT)
        );

        assertEquals("AUTH_REFRESH_REPLAY", replay.code());
        assertEquals("REVOKED", fixture.contextSessions.getFirst().getStatus());
        assertTrue(fixture.refreshTokens.stream().allMatch(token -> "REVOKED".equals(token.getStatus())));
    }

    @Test
    void accessAndRefreshCookiesAreHttpOnlySameSiteAndNeverEnterResponseData() {
        var fixture = new Fixture();
        var issued = fixture.loginService.login("admin", "contract-secret", CLIENT);
        var response = new MockHttpServletResponse();
        new VNextSessionCookieSupport(fixture.properties).write(response, issued);

        var cookies = response.getHeaders("Set-Cookie");
        var access = cookies.stream().filter(value -> value.startsWith("EXAMINE_ACCESS=")).findFirst().orElseThrow();
        var refresh = cookies.stream().filter(value -> value.startsWith("EXAMINE_REFRESH=")).findFirst().orElseThrow();
        var csrf = cookies.stream().filter(value -> value.startsWith("EXAMINE_CSRF=")).findFirst().orElseThrow();
        assertTrue(access.contains("HttpOnly"));
        assertTrue(refresh.contains("HttpOnly"));
        assertTrue(access.contains("SameSite=Lax"));
        assertTrue(refresh.contains("SameSite=Lax"));
        assertFalse(csrf.contains("HttpOnly"));
        assertFalse(issued.accountContext().toString().contains(issued.accessToken()));
        assertFalse(issued.accountContext().toString().contains(issued.refreshToken()));
    }

    @Test
    void vNextOwnsOneProfileScopedAdapterAndGeneratedServiceBoundary() throws Exception {
        assertProfile(VNextAuthController.class, "vnext");
        assertProfile(VNextMeController.class, "vnext");
        assertProfile(VNextAuthenticationFilter.class, "vnext");
        assertProfile(AuthController.class, "!vnext");
        assertProfile(MeController.class, "!vnext");
        assertProfile(EnterpriseSsoController.class, "!vnext");
        assertProfile(AuthenticationFilter.class, "!vnext");

        var permissionDependencies = List.of(VNextPlatformPermissionResolver.class.getDeclaredFields()).stream()
                .map(field -> field.getType().getName())
                .toList();
        assertTrue(permissionDependencies.contains(IVNextPlatAccountRoleService.class.getName()));
        assertTrue(permissionDependencies.contains(IVNextPlatRoleService.class.getName()));
        assertTrue(permissionDependencies.contains(IVNextPlatRolePermissionService.class.getName()));
        assertTrue(permissionDependencies.contains(IVNextPlatPermissionService.class.getName()));

        for (var type : List.of(
                VNextLoginService.class,
                VNextPlatformPermissionResolver.class,
                VNextSessionService.class,
                VNextSecurityAuditService.class
        )) {
            var dependencies = List.of(type.getDeclaredFields()).stream()
                    .map(field -> field.getType().getName())
                    .toList();
            assertTrue(dependencies.stream().noneMatch(name -> name.contains(".base.mapper.")));
            assertTrue(dependencies.stream().noneMatch(name -> name.contains("Jdbc")));
        }
        assertTrue(VNextLoginService.class
                .getMethod("login", String.class, String.class, ClientRequest.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(VNextSessionService.class
                .getMethod("refresh", String.class, ClientRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void p1OperationAuditBoundaryIsVNextPrimaryAndDoesNotReachPersistence() {
        assertTrue(OperationAuditFacade.class.isAssignableFrom(VNextP1OperationAuditBoundary.class));
        assertProfile(VNextP1OperationAuditBoundary.class, "vnext");
        assertTrue(VNextP1OperationAuditBoundary.class.isAnnotationPresent(Component.class));
        assertTrue(VNextP1OperationAuditBoundary.class.isAnnotationPresent(Primary.class));
        assertEquals(0, VNextP1OperationAuditBoundary.class.getDeclaredFields().length);

        var boundary = new VNextP1OperationAuditBoundary();
        boundary.recordSuccess(null);
        boundary.recordDenied(null);
        boundary.recordFailed(null);
    }

    private static void assertProfile(Class<?> type, String expected) {
        assertEquals(expected, type.getAnnotation(Profile.class).value()[0]);
    }

    private static final class Fixture {
        private final List<Account> accounts = new ArrayList<>();
        private final List<Credential> credentials = new ArrayList<>();
        private final List<AccountRole> accountRoles = new ArrayList<>();
        private final List<Role> roles = new ArrayList<>();
        private final List<RolePermission> rolePermissions = new ArrayList<>();
        private final List<Permission> permissions = new ArrayList<>();
        private final List<ContextSession> contextSessions = new ArrayList<>();
        private final List<RefreshToken> refreshTokens = new ArrayList<>();
        private final List<Security> securityEvents = new ArrayList<>();
        private final SequenceIdService idService = new SequenceIdService();
        private final PasswordService passwordService = new PasswordService();
        private final VNextAuthTokenService tokenService = new VNextAuthTokenService();
        private final SecurityProperties properties = new SecurityProperties(
                Duration.ofMinutes(30), Duration.ofDays(7), false, 5, Duration.ofMinutes(15)
        );
        private boolean rejectNextRefreshClaim;
        private final VNextSessionService sessionService;
        private final VNextLoginService loginService;

        private Fixture() {
            seedIdentityAndPermissions();

            var accountService = service(
                    IVNextPlatAccountService.class,
                    accounts,
                    Account::getId,
                    values -> accounts.stream().filter(account -> values.contains(account.getUsernameNormalized()))
                            .findFirst().orElse(null),
                    values -> accounts
            );
            var credentialService = service(
                    IVNextPlatCredentialService.class,
                    credentials,
                    Credential::getId,
                    values -> credentials.stream()
                            .filter(credential -> values.contains(credential.getAccountId()))
                            .filter(credential -> values.contains(credential.getCredentialType()))
                            .findFirst().orElse(null),
                    values -> credentials
            );
            var contextService = service(
                    IVNextPlatContextSessionService.class,
                    contextSessions,
                    ContextSession::getId,
                    values -> contextSessions.stream()
                            .filter(context -> values.contains(context.getTokenHash()))
                            .findFirst().orElse(null),
                    values -> contextSessions
            );
            var refreshService = service(
                    IVNextPlatRefreshTokenService.class,
                    refreshTokens,
                    RefreshToken::getId,
                    values -> refreshTokens.stream()
                            .filter(token -> values.contains(token.getTokenHash()))
                            .findFirst().orElse(null),
                    values -> refreshTokens.stream()
                            .filter(token -> values.contains(token.getTokenFamily())
                                    || values.contains(token.getContextSessionId()))
                            .toList(),
                    values -> {
                        if (values.contains("ACTIVE") && rejectNextRefreshClaim) {
                            rejectNextRefreshClaim = false;
                            return false;
                        }
                        if (values.contains("REVOKED")) {
                            refreshTokens.stream()
                                    .filter(token -> values.contains(token.getTokenFamily()))
                                    .forEach(token -> {
                                        token.setStatus("REVOKED");
                                        token.setRevokedAt(LocalDateTime.now());
                                    });
                        }
                        return true;
                    }
            );
            var permissionResolver = new VNextPlatformPermissionResolver(
                    service(IVNextPlatAccountRoleService.class, accountRoles, AccountRole::getId,
                            values -> null, values -> accountRoles),
                    service(IVNextPlatRoleService.class, roles, Role::getId,
                            values -> null, values -> roles),
                    service(IVNextPlatRolePermissionService.class, rolePermissions, RolePermission::getId,
                            values -> null, values -> rolePermissions),
                    service(IVNextPlatPermissionService.class, permissions, Permission::getId,
                            values -> null, values -> permissions)
            );
            var securityAudit = new VNextSecurityAuditService(
                    service(ISecurityService.class, securityEvents, Security::getId,
                            values -> null, values -> securityEvents),
                    idService
            );
            sessionService = new VNextSessionService(
                    accountService,
                    contextService,
                    refreshService,
                    securityAudit,
                    tokenService,
                    properties,
                    idService,
                    new ObjectMapper()
            );
            loginService = new VNextLoginService(
                    accountService,
                    credentialService,
                    passwordService,
                    tokenService,
                    permissionResolver,
                    sessionService,
                    securityAudit,
                    properties
            );
        }

        private void seedIdentityAndPermissions() {
            var account = new Account();
            account.setId(1L);
            account.setUsername("admin");
            account.setUsernameNormalized("admin");
            account.setDisplayName("Platform Administrator");
            account.setStatus("ACTIVE");
            account.setVersion(0L);
            accounts.add(account);

            var credential = new Credential();
            credential.setId(2L);
            credential.setAccountId(account.getId());
            credential.setCredentialType("PASSWORD");
            credential.setPasswordHash(passwordService.hash("contract-secret").encoded());
            credential.setFailedAttempts(0);
            credential.setVersion(0L);
            credentials.add(credential);

            var role = new Role();
            role.setId(3L);
            role.setScopeType("PLATFORM");
            role.setScopeKey(0L);
            role.setRoleCode("platform_admin");
            role.setStatus("ACTIVE");
            role.setPermissionVersion(7L);
            roles.add(role);

            var assignment = new AccountRole();
            assignment.setId(4L);
            assignment.setScopeType("PLATFORM");
            assignment.setScopeKey(0L);
            assignment.setAccountId(account.getId());
            assignment.setRoleId(role.getId());
            assignment.setValidFrom(LocalDateTime.now().minusDays(1));
            accountRoles.add(assignment);

            var permissionCodes = List.of(
                    "platform.runtime.access", "platform.admin.access", "platform.system.manage"
            );
            for (var index = 0; index < permissionCodes.size(); index++) {
                var permission = new Permission();
                permission.setId(10L + index);
                permission.setScopeType("PLATFORM");
                permission.setScopeKey(0L);
                permission.setPermissionCode(permissionCodes.get(index));
                permission.setStatus("ACTIVE");
                permissions.add(permission);

                var grant = new RolePermission();
                grant.setId(20L + index);
                grant.setScopeType("PLATFORM");
                grant.setScopeKey(0L);
                grant.setRoleId(role.getId());
                grant.setPermissionId(permission.getId());
                grant.setEffect("ALLOW");
                rolePermissions.add(grant);
            }
        }
    }

    private static <S, E> S service(
            Class<S> serviceType,
            List<E> rows,
            Function<E, Long> id,
            Function<List<Object>, E> getOne,
            Function<List<Object>, List<E>> list
    ) {
        return service(serviceType, rows, id, getOne, list, values -> true);
    }

    private static <S, E> S service(
            Class<S> serviceType,
            List<E> rows,
            Function<E, Long> id,
            Function<List<Object>, E> getOne,
            Function<List<Object>, List<E>> list,
            Function<List<Object>, Boolean> update
    ) {
        return serviceType.cast(Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOne" -> getOne.apply(parameters((Wrapper<?>) args[0]));
                    case "getById" -> rows.stream()
                            .filter(row -> id.apply(row).equals(((Number) args[0]).longValue()))
                            .findFirst().orElse(null);
                    case "list" -> list.apply(args == null || args.length == 0
                            ? List.of()
                            : parameters((Wrapper<?>) args[0]));
                    case "listByIds" -> {
                        var ids = (Collection<?>) args[0];
                        yield rows.stream().filter(row -> ids.contains(id.apply(row))).toList();
                    }
                    case "save" -> {
                        @SuppressWarnings("unchecked")
                        var row = (E) args[0];
                        rows.add(row);
                        yield true;
                    }
                    case "updateById" -> true;
                    case "update" -> update.apply(parameters((Wrapper<?>) args[0]));
                    case "toString" -> serviceType.getSimpleName() + rows;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Unexpected generated service call: " + method);
                }
        ));
    }

    private static List<Object> parameters(Wrapper<?> wrapper) {
        wrapper.getSqlSegment();
        var values = new ArrayList<Object>();
        for (var value : ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values()) {
            if (value instanceof Collection<?> collection) {
                values.addAll(collection);
            } else {
                values.add(value);
            }
        }
        return values;
    }

    private static final class SequenceIdService extends IdService {
        private final AtomicLong sequence = new AtomicLong(1000);

        @Override
        public long nextId() {
            return sequence.incrementAndGet();
        }
    }
}
