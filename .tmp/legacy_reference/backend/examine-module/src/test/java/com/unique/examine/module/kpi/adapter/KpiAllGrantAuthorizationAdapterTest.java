package com.unique.examine.module.kpi.adapter;

import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KpiAllGrantAuthorizationAdapterTest {
    private static final KpiActor ACTOR = new KpiActor(10, 20, 30);

    @Test
    void requiresAllGrantAndReturnsTheCurrentAuthorizationEpoch() {
        var facade = new FakeAuthorization();
        var adapter = new KpiAllGrantAuthorizationAdapter(facade);

        var result = adapter.require(ACTOR, "orders");

        assertThat(result.epoch()).isEqualTo(7);
        assertThat(facade.last.modulePermissionCode())
                .isEqualTo("module.orders.view");
        assertThat(facade.last.systemId()).isEqualTo(10);
        assertThat(facade.last.tenantId()).isEqualTo(20);
        assertThat(facade.last.memberId()).isEqualTo(30);
    }

    @Test
    void deniesNarrowOrDeniedRecordGrants() {
        var facade = new FakeAuthorization();
        var adapter = new KpiAllGrantAuthorizationAdapter(facade);

        facade.grant = new RuntimeAuthorizationFacade.RuntimeGrant(
                false, 7, false, Set.of(30L), Set.of(), List.of());
        assertThatThrownBy(() -> adapter.require(ACTOR, "orders"))
                .isInstanceOfSatisfying(KpiException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("KPI_CALCULATION_FORBIDDEN"));

        facade.grant = RuntimeAuthorizationFacade.RuntimeGrant.denied(7);
        assertThatThrownBy(() -> adapter.require(ACTOR, "orders"))
                .isInstanceOfSatisfying(KpiException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("KPI_CALCULATION_FORBIDDEN"));
    }

    @Test
    void rejectsStaleEpochBeforeAndAfterCalculation() {
        var facade = new FakeAuthorization();
        var adapter = new KpiAllGrantAuthorizationAdapter(facade);

        facade.currentEpoch = 8;
        assertThatThrownBy(() -> adapter.require(ACTOR, "orders"))
                .isInstanceOfSatisfying(KpiException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("KPI_AUTHORIZATION_CONFLICT"));

        facade.currentEpoch = 7;
        assertThatThrownBy(() ->
                adapter.requireStillAll(ACTOR, "orders", 6))
                .isInstanceOfSatisfying(KpiException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("KPI_AUTHORIZATION_CONFLICT"));
    }

    @Test
    void fieldPermissionChecksAreFailClosedAtTheSameEpoch() {
        var facade = new FakeAuthorization();
        var adapter = new KpiAllGrantAuthorizationAdapter(facade);
        facade.grant = RuntimeAuthorizationFacade.RuntimeGrant.denied(7);

        assertThat(adapter.permissionAtEpoch(
                ACTOR, "module.orders.field.amount.read", 7)).isFalse();
        facade.grant = RuntimeAuthorizationFacade.RuntimeGrant.denied(8);
        assertThatThrownBy(() -> adapter.permissionAtEpoch(
                ACTOR, "module.orders.field.amount.read", 7))
                .isInstanceOfSatisfying(KpiException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("KPI_AUTHORIZATION_CONFLICT"));
    }

    private static final class FakeAuthorization
            implements RuntimeAuthorizationFacade {
        private RuntimeAuthorizationRequest last;
        private long currentEpoch = 7;
        private RuntimeGrant grant = new RuntimeGrant(
                false, 7, true, Set.of(), Set.of(), List.of());

        @Override
        public RuntimeGrant resolve(RuntimeAuthorizationRequest request) {
            last = request;
            return grant;
        }

        @Override
        public long currentSystemEpoch(long systemId) {
            return currentEpoch;
        }
    }
}
