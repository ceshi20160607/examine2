package com.unique.examine.plat.ai;

import com.unique.examine.core.ai.PlatformAuthorizedSystemFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAuthorizedSystemAdapterTest {
    private static final Set<String> PERMISSIONS = Set.of(
            "platform.runtime.access", "platform.ai.agent.use", "platform.audit.view");

    @Test
    void projectsOnlyCurrentAuthorizedSystemLabelsAndSwitchTargets() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);

        var result = adapter.authorizedSystems(request(5, PERMISSIONS));

        assertThat(owner.lastAccountId).isEqualTo(17);
        assertThat(result.systems()).singleElement().satisfies(system -> {
            assertThat(system.systemId()).isEqualTo("41");
            assertThat(system.systemCode()).isEqualTo("orders");
            assertThat(system.systemName()).isEqualTo("Orders");
            assertThat(system.status()).isEqualTo("ACTIVE");
            assertThat(system.membershipState()).isEqualTo("ACTIVE");
            assertThat(system.accessState()).isEqualTo("AUTHORIZED");
            assertThat(system.switchTarget()).isEqualTo("/api/v1/context/systems/41:switch");
        });
        assertThat(result.toString()).doesNotContain("tenant", "module", "record", "business");
    }

    @Test
    void staleEpochOrForgedPermissionSnapshotIsRejectedBeforeDirectoryRead() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);

        assertCode("AI_PLATFORM_AUTHORIZATION_STALE", () ->
                adapter.authorizedSystems(request(4, PERMISSIONS)));
        assertCode("AI_PLATFORM_AUTHORIZATION_STALE", () ->
                adapter.authorizedSystems(request(5, Set.of(
                        "platform.runtime.access", "platform.ai.agent.use"))));
        assertThat(owner.directoryCalls).isZero();
    }

    @Test
    void livePlatformPermissionRevocationIsRejected() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);
        owner.authorization = new PlatformAuthorizedSystemAdapter.LiveAuthorization(
                6, Set.of("platform.runtime.access"));

        assertCode("PERMISSION_DENIED", () -> adapter.authorizedSystems(
                request(6, Set.of("platform.runtime.access"))));
        assertThat(owner.directoryCalls).isZero();
    }

    @Test
    void liveMembershipOrSystemRevocationRemovesTheEntry() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);
        assertThat(adapter.authorizedSystems(request(5, PERMISSIONS)).systems()).hasSize(1);

        owner.systems = List.of();
        assertThat(adapter.authorizedSystems(request(5, PERMISSIONS)).systems()).isEmpty();
        assertThat(owner.directoryCalls).isEqualTo(2);
    }

    @Test
    void inactiveAccountFailureFromSharedSwitchOwnerIsNotHidden() {
        var owner = new FakeOwner();
        var adapter = adapter(owner);
        owner.directoryFailure = new BusinessException(
                "ACCOUNT_UNAVAILABLE", "inactive", org.springframework.http.HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> adapter.authorizedSystems(request(5, PERMISSIONS)))
                .isSameAs(owner.directoryFailure);
    }

    @Test
    void publicMethodRemainsSpringProxyableAndReadOnly() throws Exception {
        assertThat(java.lang.reflect.Modifier.isFinal(
                PlatformAuthorizedSystemAdapter.class.getModifiers())).isFalse();
        assertThat(PlatformAuthorizedSystemAdapter.class.getMethod(
                        "authorizedSystems", PlatformAuthorizedSystemFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    private static PlatformAuthorizedSystemAdapter adapter(FakeOwner owner) {
        return new PlatformAuthorizedSystemAdapter(
                accountId -> owner.authorization,
                accountId -> owner.directory(accountId));
    }

    private static PlatformAuthorizedSystemFacade.Request request(long epoch, Set<String> permissions) {
        return new PlatformAuthorizedSystemFacade.Request(
                17, epoch, permissions, "request-1", "trace-1");
    }

    private static void assertCode(String code, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code()).isEqualTo(code);
    }

    private static final class FakeOwner {
        private PlatformAuthorizedSystemAdapter.LiveAuthorization authorization =
                new PlatformAuthorizedSystemAdapter.LiveAuthorization(5, PERMISSIONS);
        private List<PlatformAuthorizedSystemAdapter.Projection> systems = List.of(
                new PlatformAuthorizedSystemAdapter.Projection(
                        41, "orders", "Orders", "ACTIVE", "ACTIVE", "AUTHORIZED",
                        "/api/v1/context/systems/41:switch"));
        private int directoryCalls;
        private long lastAccountId;
        private BusinessException directoryFailure;

        private List<PlatformAuthorizedSystemAdapter.Projection> directory(long accountId) {
            directoryCalls++;
            lastAccountId = accountId;
            if (directoryFailure != null) throw directoryFailure;
            return systems;
        }
    }
}
