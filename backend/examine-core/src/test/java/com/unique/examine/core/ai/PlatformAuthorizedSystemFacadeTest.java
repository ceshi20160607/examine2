package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAuthorizedSystemFacadeTest {

    @Test
    void freezesPlatformOnlyRequestAndSafeDirectoryProjection() {
        assertThat(Arrays.stream(PlatformAuthorizedSystemFacade.Request.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("accountId", "authorizationEpoch", "effectivePermissions", "requestId", "traceId")
                .doesNotContain("systemId", "tenantId", "memberId", "moduleCode", "recordId");
        assertThat(Arrays.stream(PlatformAuthorizedSystemFacade.SystemAccess.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("systemId", "systemCode", "systemName", "status",
                        "membershipState", "accessState", "switchTarget")
                .doesNotContain("tenantId", "moduleCode", "recordId", "fields", "values");

        var access = new PlatformAuthorizedSystemFacade.SystemAccess(
                "41", "orders", "Orders", "ACTIVE", "ACTIVE", "AUTHORIZED",
                "/api/v1/context/systems/41:switch");
        assertThat(new PlatformAuthorizedSystemFacade.Result(List.of(access)).systems())
                .containsExactly(access);
    }

    @Test
    void rejectsInvalidActorAndForgedSwitchTargets() {
        assertThatThrownBy(() -> new PlatformAuthorizedSystemFacade.Request(
                0, 1, Set.of(), "request", "trace"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformAuthorizedSystemFacade.SystemAccess(
                "41", "orders", "Orders", "ACTIVE", "ACTIVE", "AUTHORIZED",
                "/api/v1/context/systems/99:switch"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
