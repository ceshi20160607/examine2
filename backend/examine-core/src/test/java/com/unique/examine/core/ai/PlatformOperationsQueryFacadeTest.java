package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformOperationsQueryFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void requestIsExactAndBoundedAndProjectionsContainNoBusinessContext() {
        assertThat(Arrays.stream(
                PlatformOperationsQueryFacade.Request.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("accountId", "authorizationEpoch", "queryKind", "limit");
        assertThatThrownBy(() -> request(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(51))
                .isInstanceOf(IllegalArgumentException.class);

        var forbidden = List.of(
                "system", "tenant", "member", "module", "field", "record",
                "secret", "host", "sql", "exception", "description");
        var projectionTypes = List.of(
                PlatformOperationsQueryFacade.PersonalTask.class,
                PlatformOperationsQueryFacade.AiQuotaResult.class,
                PlatformOperationsQueryFacade.ServiceHealth.class,
                PlatformOperationsQueryFacade.AgentActivity.class);
        projectionTypes.stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(name -> assertThat(forbidden)
                        .noneMatch(name::contains));
    }

    @Test
    void quotaArithmeticAndCoarseHealthAreStructurallyStrict() {
        var quota = new PlatformOperationsQueryFacade.AiQuotaResult(
                NOW, NOW.plusSeconds(86_400),
                100, 12, 88,
                10_000, 2_000, 500, 7_500,
                4, 1, 3);
        assertThat(quota.queryKind()).isEqualTo(
                PlatformOperationsQueryFacade.QueryKind.AI_QUOTA);
        assertThatThrownBy(() -> new PlatformOperationsQueryFacade.AiQuotaResult(
                NOW, NOW.plusSeconds(86_400),
                100, 12, 87,
                10_000, 2_000, 500, 7_500,
                4, 1, 3)).isInstanceOf(IllegalArgumentException.class);

        var services = Arrays.stream(
                PlatformOperationsQueryFacade.ServiceName.values())
                .map(service -> new PlatformOperationsQueryFacade.ServiceHealth(
                        service, PlatformOperationsQueryFacade.HealthState.UP))
                .toList();
        assertThat(new PlatformOperationsQueryFacade.ServiceHealthResult(
                NOW, services).services()).hasSize(4);
        assertThatThrownBy(() ->
                new PlatformOperationsQueryFacade.ServiceHealthResult(
                        NOW, services.subList(0, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PlatformOperationsQueryFacade.Request request(int limit) {
        return new PlatformOperationsQueryFacade.Request(
                7, 3,
                PlatformOperationsQueryFacade.QueryKind.PERSONAL_TASKS,
                limit);
    }
}
