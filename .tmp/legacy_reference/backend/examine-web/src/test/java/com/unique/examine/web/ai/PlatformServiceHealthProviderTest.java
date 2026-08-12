package com.unique.examine.web.ai;

import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformServiceHealthProviderTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void returnsOnlyTheFourCoarseComponents() {
        var provider = new PlatformServiceHealthProvider(
                component -> component.equals("db")
                        ? PlatformOperationsQueryFacade.HealthState.UP
                        : PlatformOperationsQueryFacade.HealthState.DEGRADED,
                () -> new PlatformServiceHealthProvider.AiReadiness(
                        PlatformOperationsQueryFacade.HealthState.UP,
                        PlatformOperationsQueryFacade.HealthState.UNKNOWN),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = provider.current();

        assertThat(result.observedAt()).isEqualTo(NOW);
        assertThat(result.services()).containsExactly(
                service(PlatformOperationsQueryFacade.ServiceName.DATABASE,
                        PlatformOperationsQueryFacade.HealthState.UP),
                service(PlatformOperationsQueryFacade.ServiceName.REDIS,
                        PlatformOperationsQueryFacade.HealthState.DEGRADED),
                service(PlatformOperationsQueryFacade.ServiceName
                                .PLATFORM_AI_CONFIGURATION,
                        PlatformOperationsQueryFacade.HealthState.UP),
                service(PlatformOperationsQueryFacade.ServiceName
                                .PLATFORM_AI_PROVIDER,
                        PlatformOperationsQueryFacade.HealthState.UNKNOWN));
    }

    @Test
    void degradesProbeFailuresToUnknownWithoutLeakingDetails() {
        var provider = new PlatformServiceHealthProvider(
                component -> { throw new IllegalStateException("secret host"); },
                () -> { throw new IllegalStateException("secret provider"); },
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(provider.current().services())
                .allMatch(value -> value.state()
                        == PlatformOperationsQueryFacade.HealthState.UNKNOWN);
    }

    private static PlatformOperationsQueryFacade.ServiceHealth service(
            PlatformOperationsQueryFacade.ServiceName name,
            PlatformOperationsQueryFacade.HealthState state) {
        return new PlatformOperationsQueryFacade.ServiceHealth(name, state);
    }
}
