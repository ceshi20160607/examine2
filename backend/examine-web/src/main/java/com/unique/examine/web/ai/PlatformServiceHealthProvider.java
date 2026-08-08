package com.unique.examine.web.ai;

import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coarse readiness projection; internal health details never cross the port. */
@Component
public final class PlatformServiceHealthProvider
        implements PlatformOperationsQueryFacade.ServiceHealthProvider {
    private final Function<String, PlatformOperationsQueryFacade.HealthState>
            infrastructure;
    private final Supplier<AiReadiness> ai;
    private final Clock clock;

    @Autowired
    public PlatformServiceHealthProvider(
            HealthEndpoint health,
            PlatformAiRepository repository,
            Clock clock) {
        this(component -> healthState(health.healthForPath(component)),
                () -> aiReadiness(repository), clock);
    }

    PlatformServiceHealthProvider(
            Function<String, PlatformOperationsQueryFacade.HealthState>
                    infrastructure,
            Supplier<AiReadiness> ai,
            Clock clock) {
        this.infrastructure = Objects.requireNonNull(
                infrastructure, "infrastructure");
        this.ai = Objects.requireNonNull(ai, "ai");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PlatformOperationsQueryFacade.ServiceHealthResult current() {
        var readiness = safeAiReadiness();
        return new PlatformOperationsQueryFacade.ServiceHealthResult(
                clock.instant(), List.of(
                service(PlatformOperationsQueryFacade.ServiceName.DATABASE,
                        safeInfrastructure("db")),
                service(PlatformOperationsQueryFacade.ServiceName.REDIS,
                        safeInfrastructure("redis")),
                service(PlatformOperationsQueryFacade.ServiceName
                                .PLATFORM_AI_CONFIGURATION,
                        readiness.configuration()),
                service(PlatformOperationsQueryFacade.ServiceName
                                .PLATFORM_AI_PROVIDER,
                        readiness.provider())));
    }

    private PlatformOperationsQueryFacade.HealthState safeInfrastructure(
            String component) {
        try {
            return Objects.requireNonNullElse(
                    infrastructure.apply(component), unknown());
        } catch (RuntimeException failure) {
            return unknown();
        }
    }

    private AiReadiness safeAiReadiness() {
        try {
            return Objects.requireNonNullElseGet(
                    ai.get(), AiReadiness::unknown);
        } catch (RuntimeException failure) {
            return AiReadiness.unknown();
        }
    }

    private static AiReadiness aiReadiness(PlatformAiRepository repository) {
        var active = repository.activePolicy();
        if (active.isEmpty() || !active.get().settings().enabled()) {
            return new AiReadiness(degraded(), degraded());
        }
        var policy = active.get();
        var provider = repository.provider(policy.providerId());
        var providerReady = provider.isPresent()
                && provider.get().enabled()
                && provider.get().version() == policy.providerVersion();
        return new AiReadiness(up(), providerReady ? up() : degraded());
    }

    private static PlatformOperationsQueryFacade.HealthState healthState(
            HealthComponent component) {
        if (component == null || component.getStatus() == null) return unknown();
        return switch (component.getStatus().getCode()) {
            case "UP" -> up();
            case "DOWN", "OUT_OF_SERVICE" -> degraded();
            default -> unknown();
        };
    }

    private static PlatformOperationsQueryFacade.ServiceHealth service(
            PlatformOperationsQueryFacade.ServiceName name,
            PlatformOperationsQueryFacade.HealthState state) {
        return new PlatformOperationsQueryFacade.ServiceHealth(name, state);
    }

    private static PlatformOperationsQueryFacade.HealthState up() {
        return PlatformOperationsQueryFacade.HealthState.UP;
    }

    private static PlatformOperationsQueryFacade.HealthState degraded() {
        return PlatformOperationsQueryFacade.HealthState.DEGRADED;
    }

    private static PlatformOperationsQueryFacade.HealthState unknown() {
        return PlatformOperationsQueryFacade.HealthState.UNKNOWN;
    }

    record AiReadiness(
            PlatformOperationsQueryFacade.HealthState configuration,
            PlatformOperationsQueryFacade.HealthState provider) {
        AiReadiness {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(provider, "provider");
        }

        static AiReadiness unknown() {
            return new AiReadiness(
                    PlatformOperationsQueryFacade.HealthState.UNKNOWN,
                    PlatformOperationsQueryFacade.HealthState.UNKNOWN);
        }
    }
}
