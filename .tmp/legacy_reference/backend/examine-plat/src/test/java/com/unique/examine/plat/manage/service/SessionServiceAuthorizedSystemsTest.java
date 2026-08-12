package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceAuthorizedSystemsTest {

    @Test
    void reusesLiveSwitchRulesForAccountMembershipSystemAndTenantState() {
        var account = account(17, "ACTIVE");
        var members = new AtomicReference<>(List.of(
                member(101, 17, 41, 501, "ACTIVE"),
                member(102, 17, 42, 502, "DISABLED"),
                member(103, 17, 43, 503, "ACTIVE"),
                member(104, 17, 44, 504, "ACTIVE")));
        var valid = system(41, "orders", "Orders", "ACTIVE");
        var inactiveMemberSystem = system(42, "finance", "Finance", "ACTIVE");
        var unavailableSystem = system(43, "archive", "Archive", "DISABLED");
        var unavailableTenantSystem = system(44, "support", "Support", "INITIALIZING");
        var systems = List.of(valid, inactiveMemberSystem, unavailableSystem, unavailableTenantSystem);
        var selectedMembers = new ArrayDeque<>(List.of(
                members.get().get(0), members.get().get(1), members.get().get(3)));
        var authorization = new FakeAuthorizationService();

        var service = service(
                account,
                members,
                systems,
                selectedMembers,
                List.of(tenant(501, 41, "ACTIVE"), tenant(504, 44, "DISABLED")),
                authorization);

        var result = service.listAuthorizedSystems(17);

        assertThat(result).singleElement().satisfies(system -> {
            assertThat(system.systemId()).isEqualTo(41);
            assertThat(system.systemCode()).isEqualTo("orders");
            assertThat(system.systemName()).isEqualTo("Orders");
            assertThat(system.status()).isEqualTo("ACTIVE");
            assertThat(system.membershipState()).isEqualTo("ACTIVE");
            assertThat(system.accessState()).isEqualTo("AUTHORIZED");
            assertThat(system.switchTarget()).isEqualTo("/api/v1/context/systems/41:switch");
        });
        assertThat(authorization.calls).isOne();

        members.set(List.of());
        assertThat(service.listAuthorizedSystems(17)).isEmpty();
    }

    @Test
    void inactiveAccountIsRejectedBeforeMembershipReads() {
        var service = service(
                account(17, "DISABLED"), new AtomicReference<>(List.of()), List.of(),
                new ArrayDeque<>(), List.of(), new FakeAuthorizationService());

        assertThatThrownBy(() -> service.listAuthorizedSystems(17))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("ACCOUNT_UNAVAILABLE");
    }

    private static SessionService service(
            Account account,
            AtomicReference<List<Member>> members,
            List<com.unique.examine.plat.base.entity.System> systems,
            ArrayDeque<Member> selectedMembers,
            List<Tenant> tenants,
            AuthorizationService authorization
    ) {
        var accountMapper = proxy(PlatAccountMapper.class, (method, arguments) -> switch (method) {
            case "selectById" -> account;
            default -> unexpected(method);
        });
        var memberMapper = proxy(PlatMemberMapper.class, (method, arguments) -> switch (method) {
            case "selectList" -> members.get();
            case "selectOne" -> selectedMembers.removeFirst();
            default -> unexpected(method);
        });
        var systemMapper = proxy(PlatSystemMapper.class, (method, arguments) -> switch (method) {
            case "selectByIds" -> systems;
            case "selectById" -> systems.stream()
                    .filter(system -> system.getId().equals(arguments[0])).findFirst().orElse(null);
            default -> unexpected(method);
        });
        var tenantMapper = proxy(PlatTenantMapper.class, (method, arguments) -> switch (method) {
            case "selectById" -> tenants.stream()
                    .filter(tenant -> tenant.getId().equals(arguments[0])).findFirst().orElse(null);
            default -> unexpected(method);
        });
        return new SessionService(
                accountMapper, null, null, systemMapper, tenantMapper, memberMapper, null,
                authorization, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> invocation.call(method.getName(), arguments));
    }

    private static Object unexpected(String method) {
        throw new AssertionError("Unexpected mapper method: " + method);
    }

    private static Account account(long id, String status) {
        var value = new Account();
        value.setId(id);
        value.setStatus(status);
        return value;
    }

    private static Member member(
            long id, long accountId, long systemId, long defaultTenantId, String status
    ) {
        var value = new Member();
        value.setId(id);
        value.setAccountId(accountId);
        value.setSystemId(systemId);
        value.setDefaultTenantId(defaultTenantId);
        value.setStatus(status);
        return value;
    }

    private static com.unique.examine.plat.base.entity.System system(
            long id, String code, String name, String status
    ) {
        var value = new com.unique.examine.plat.base.entity.System();
        value.setId(id);
        value.setSystemCode(code);
        value.setName(name);
        value.setStatus(status);
        return value;
    }

    private static Tenant tenant(long id, long systemId, String status) {
        var value = new Tenant();
        value.setId(id);
        value.setSystemId(systemId);
        value.setStatus(status);
        return value;
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private static final class FakeAuthorizationService extends AuthorizationService {
        private int calls;

        private FakeAuthorizationService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public AuthorizationSnapshot system(long systemId, long tenantId, long memberId) {
            calls++;
            return new AuthorizationSnapshot(3, Set.of("system.runtime.access"), List.of(), List.of());
        }
    }
}
