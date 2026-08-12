package com.unique.examine.module.dashboard.runtime;

import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiAttainmentDirection;
import com.unique.examine.module.kpi.domain.KpiFieldPin;
import com.unique.examine.module.kpi.domain.KpiPeriod;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSourcePin;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import com.unique.examine.module.kpi.domain.KpiTarget;
import com.unique.examine.module.kpi.domain.KpiVersion;
import com.unique.examine.module.kpi.service.KpiService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardKpiRuntimeTest {
    private static final Instant NOW =
            Instant.parse("2026-08-03T07:08:09Z");
    private static final RuntimeSession SESSION = new RuntimeSession(
            1, 10, 31, 20L, Set.of("system.runtime.access"));

    @ParameterizedTest
    @MethodSource("currentPeriods")
    void derivesAlignedCurrentPeriodOnlyFromServerClock(
            KpiPeriodType type,
            LocalDate expectedStart
    ) {
        var kpis = new RecordingReader();
        var runtime = runtime(kpis);

        assertThat(runtime.targets(SESSION, pin(501, type))).isEmpty();

        assertThat(kpis.calls).containsExactly(new Invocation(
                new KpiActor(10, 20, 31), expectedStart));
    }

    @Test
    void returnsEveryApplicableRoleTargetForTheExactPinnedVersion() {
        var kpis = new RecordingReader();
        var definition = definition(
                501, 101, 1, KpiPeriodType.QUARTER);
        var first = target(
                801, definition, 301, "Reviewer", "12.5");
        var second = target(
                802, definition, 302, "Approver", "20");
        kpis.answer = (actor, date) -> List.of(
                        new KpiService.ApplicableTarget(
                                first, definition, null),
                        new KpiService.ApplicableTarget(
                                second, definition, null));

        var result = runtime(kpis).targets(
                SESSION, pin(501, KpiPeriodType.QUARTER));

        assertThat(result).extracting(value -> value.subjectId())
                .containsExactly("301", "302");
        assertThat(result).extracting(value -> value.targetValue())
                .containsExactly("12.5", "20");
        assertThat(result).allSatisfy(value -> {
            assertThat(value.kpiVersionId()).isEqualTo("501");
            assertThat(value.periodStart()).isEqualTo("2026-07-01");
            assertThat(value.latestCalculation()).isNull();
        });
    }

    @Test
    void filtersOtherRootsVersionsAndPeriodsWithoutChoosingOneTarget() {
        var kpis = new RecordingReader();
        var pinned = definition(
                501, 101, 1, KpiPeriodType.QUARTER);
        var newer = definition(
                502, 101, 2, KpiPeriodType.QUARTER);
        var otherRoot = definition(
                601, 102, 1, KpiPeriodType.QUARTER);
        var oldPeriodTarget = new KpiTarget(
                804, 10, 20, 101, 501, 1, KpiSubjectType.ROLE,
                304, "Old role",
                KpiPeriod.starting(KpiPeriodType.QUARTER,
                        LocalDate.parse("2026-04-01")),
                "4", 31, 31, NOW, NOW, 1);
        kpis.answer = (actor, date) -> List.of(
                        applicable(target(801, pinned, 301, "Pinned", "1"),
                                pinned),
                        applicable(target(802, newer, 302, "Newer", "2"),
                                newer),
                        applicable(target(803, otherRoot, 303, "Other", "3"),
                                otherRoot),
                        applicable(oldPeriodTarget, pinned));

        var result = runtime(kpis).targets(
                SESSION, pin(501, KpiPeriodType.QUARTER));

        assertThat(result).singleElement()
                .extracting(value -> value.subjectName())
                .isEqualTo("Pinned");
    }

    @Test
    void emptyApplicableTargetsIsASuccessfulImmutableResult() {
        var kpis = new RecordingReader();

        var result = runtime(kpis).targets(
                SESSION, pin(501, KpiPeriodType.QUARTER));

        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reevaluatesCurrentTenantAndMemberForEveryRead() {
        var kpis = new RecordingReader();
        var runtime = runtime(kpis);
        var other = new RuntimeSession(
                2, 10, 42, 21L, Set.of("system.runtime.access"));

        runtime.targets(SESSION, pin(501, KpiPeriodType.QUARTER));
        runtime.targets(other, pin(501, KpiPeriodType.QUARTER));

        assertThat(kpis.calls).containsExactly(
                new Invocation(new KpiActor(10, 20, 31),
                        LocalDate.parse("2026-07-01")),
                new Invocation(new KpiActor(10, 21, 42),
                        LocalDate.parse("2026-07-01")));
    }

    @Test
    void rejectsMissingAuthorityAndLetsExecutionFailuresReachWidgetIsolation() {
        var kpis = new RecordingReader();
        var runtime = runtime(kpis);

        assertThatThrownBy(() -> runtime.targets(
                new RuntimeSession(1, 10, 31, null, Set.of()),
                pin(501, KpiPeriodType.QUARTER)))
                .isInstanceOf(DashboardException.class)
                .satisfies(error -> assertThat(
                        ((DashboardException) error).code())
                        .isEqualTo("DASHBOARD_TENANT_REQUIRED"));

        kpis.answer = (actor, date) -> {
            throw new IllegalStateException("database detail");
        };
        assertThatThrownBy(() -> runtime.targets(
                SESSION, pin(501, KpiPeriodType.QUARTER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database detail");
    }

    private static Stream<Arguments> currentPeriods() {
        return Stream.of(
                Arguments.of(KpiPeriodType.MONTH,
                        LocalDate.parse("2026-08-01")),
                Arguments.of(KpiPeriodType.QUARTER,
                        LocalDate.parse("2026-07-01")),
                Arguments.of(KpiPeriodType.YEAR,
                        LocalDate.parse("2026-01-01")));
    }

    private static DashboardKpiRuntime runtime(DashboardKpiTargetReader kpis) {
        return new DashboardKpiRuntime(
                kpis, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DashboardKpiRuntime.PublishedKpiPin pin(
            long versionId,
            KpiPeriodType type
    ) {
        return new DashboardKpiRuntime.PublishedKpiPin(
                101, versionId, type);
    }

    private static KpiService.ApplicableTarget applicable(
            KpiTarget target,
            KpiVersion definition
    ) {
        return new KpiService.ApplicableTarget(target, definition, null);
    }

    private static KpiTarget target(
            long id,
            KpiVersion definition,
            long subjectId,
            String subjectName,
            String targetValue
    ) {
        return KpiTarget.create(
                id, new KpiActor(10, 20, 31), definition,
                subjectId, subjectName,
                KpiPeriod.starting(definition.periodType(),
                        LocalDate.parse("2026-07-01")),
                targetValue, NOW);
    }

    private static KpiVersion definition(
            long id,
            long kpiId,
            int versionNumber,
            KpiPeriodType periodType
    ) {
        var time = new KpiFieldPin(
                901, "occurredAt", "Occurred at",
                "DATETIME", "DATETIME");
        return new KpiVersion(
                id, kpiId, 10, 20, versionNumber, versionNumber,
                "reviewedRecords", "Reviewed records", null,
                KpiSubjectType.ROLE, periodType,
                StatisticsAggregation.COUNT,
                KpiAttainmentDirection.AT_LEAST, "0.8",
                new KpiSourcePin(
                        201, "work_items", 401, 1, "work",
                        "schema-1", null, time),
                "a".repeat(64), 31, NOW);
    }

    private static final class RecordingReader
            implements DashboardKpiTargetReader {
        private final List<Invocation> calls = new ArrayList<>();
        private BiFunction<KpiActor, LocalDate,
                List<KpiService.ApplicableTarget>> answer =
                (actor, date) -> List.of();

        @Override
        public List<KpiService.ApplicableTarget> applicableTargets(
                KpiActor actor,
                LocalDate date
        ) {
            calls.add(new Invocation(actor, date));
            return answer.apply(actor, date);
        }
    }

    private record Invocation(KpiActor actor, LocalDate date) {
    }
}
