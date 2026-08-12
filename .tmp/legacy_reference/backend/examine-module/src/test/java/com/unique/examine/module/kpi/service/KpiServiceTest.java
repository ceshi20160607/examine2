package com.unique.examine.module.kpi.service;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiDefinition;
import com.unique.examine.module.kpi.domain.KpiDraft;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.domain.KpiFieldPin;
import com.unique.examine.module.kpi.domain.KpiAttainmentDirection;
import com.unique.examine.module.kpi.domain.KpiPeriod;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import com.unique.examine.module.kpi.domain.KpiTarget;
import com.unique.examine.module.kpi.domain.KpiVersion;
import com.unique.examine.module.kpi.domain.KpiWarningStatus;
import com.unique.examine.module.kpi.port.KpiRepository;
import com.unique.examine.module.kpi.port.KpiReminderNotifier;
import com.unique.examine.module.kpi.port.KpiSourceCatalog;
import com.unique.examine.module.kpi.port.KpiStatisticsExecutor;
import com.unique.examine.module.kpi.port.KpiSubjectDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KpiServiceTest {
    private static final KpiActor ACTOR = new KpiActor(10, 20, 30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);

    private InMemoryRepository repository;
    private MutableSourceCatalog sources;
    private MutableSubjectDirectory subjects;
    private FakeStatistics statistics;
    private KpiService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        sources = new MutableSourceCatalog();
        sources.add(10, 20, 100, 501, 1);
        subjects = new MutableSubjectDirectory();
        subjects.add(KpiSubjectType.MEMBER, 31, "Alice", List.of());
        subjects.add(KpiSubjectType.DEPARTMENT, 200, "Sales", List.of());
        subjects.add(KpiSubjectType.ROLE, 300, "Reviewer",
                List.of(35L, 33L, 35L));
        subjects.add(KpiSubjectType.ROLE, 301, "Empty role", List.of());
        statistics = new FakeStatistics();
        service = new KpiService(
                repository, sources, subjects, statistics, CLOCK);
    }

    @Test
    void publicationPinsExactSourceReplaysAndLaterDraftCannotMutateVersion() {
        var root = create("reviewed", KpiSubjectType.ROLE);

        assertThat(service.check(ACTOR, root.id()).issues()).isEmpty();
        var first = service.publish(ACTOR, root.id(), 1);
        var replay = service.publish(ACTOR, root.id(), 1);

        assertThat(replay).isEqualTo(first);
        assertThat(repository.publishCount).isEqualTo(1);
        assertThat(first.sourceDraftVersion()).isEqualTo(1);
        assertThat(first.source())
                .satisfies(source -> {
                    assertThat(source.dataSourceVersionId()).isEqualTo(501);
                    assertThat(source.schemaVersionId()).isEqualTo("schema-1");
                    assertThat(source.measureField()).isNull();
                    assertThat(source.timeField())
                            .extracting(KpiFieldPin::logicalFieldId,
                                    KpiFieldPin::code,
                                    KpiFieldPin::queryType)
                            .containsExactly(903L, "occurredAt", "DATETIME");
                });

        var revised = service.saveDraft(ACTOR, root.id(), 1,
                "Reviewed records", "new draft", new KpiDraft(
                        100, KpiSubjectType.ROLE, KpiPeriodType.QUARTER,
                        StatisticsAggregation.COUNT, null, "occurredAt",
                        KpiAttainmentDirection.AT_MOST, "0.9"));
        assertThat(first.direction()).isEqualTo(KpiAttainmentDirection.AT_LEAST);
        assertThat(first.warningThreshold()).isEqualTo("0.8");

        var second = service.publish(
                ACTOR, root.id(), revised.draftVersion());
        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(second.direction()).isEqualTo(KpiAttainmentDirection.AT_MOST);

        sources.add(10, 20, 100, 502, 2);
        var third = service.publish(
                ACTOR, root.id(), revised.draftVersion());
        assertThat(third.versionNumber()).isEqualTo(3);
        assertThat(third.source().dataSourceVersionId()).isEqualTo(502);
        assertThat(first.source().dataSourceVersionId()).isEqualTo(501);
        assertThat(service.versions(ACTOR, root.id()))
                .extracting(KpiVersion::versionNumber)
                .containsExactly(3, 2, 1);
    }

    @Test
    void restoreCreatesDraftThenExplicitPublishMovesRuntime() {
        var root = create("reviewed", KpiSubjectType.ROLE);
        var first = service.publish(ACTOR, root.id(), 1);
        var revised = service.saveDraft(ACTOR, root.id(), 1,
                "Changed", null, new KpiDraft(
                        100, KpiSubjectType.ROLE, KpiPeriodType.QUARTER,
                        StatisticsAggregation.COUNT, null, "occurredAt",
                        KpiAttainmentDirection.AT_MOST, "0.9"));
        var second = service.publish(
                ACTOR, root.id(), revised.draftVersion());

        var restored = service.restoreVersion(ACTOR, root.id(),
                first.versionNumber(), revised.draftVersion());

        assertThat(restored.draftVersion()).isEqualTo(3);
        assertThat(restored.draft().direction())
                .isEqualTo(KpiAttainmentDirection.AT_LEAST);
        assertThat(restored.draft().warningThreshold()).isEqualTo("0.8");
        assertThat(restored.activeVersionId()).isEqualTo(second.id());
        assertThat(service.active(ACTOR, root.id()).version())
                .isEqualTo(second);
        assertThat(service.check(ACTOR, root.id()).publishable()).isTrue();

        var republished = service.publish(
                ACTOR, root.id(), restored.draftVersion());
        assertThat(republished.versionNumber()).isEqualTo(3);
        assertThat(republished.direction())
                .isEqualTo(KpiAttainmentDirection.AT_LEAST);
        assertThat(service.version(ACTOR, root.id(), 1)).isEqualTo(first);
    }

    @Test
    void checkRejectsUnreadableWrongTypeAndMoneyWithoutPublishing() {
        var money = service.create(ACTOR, "money", "Money", null,
                new KpiDraft(100, KpiSubjectType.MEMBER,
                        KpiPeriodType.MONTH, StatisticsAggregation.SUM,
                        "price", "title", KpiAttainmentDirection.AT_LEAST,
                        "0.8"));

        assertThat(service.check(ACTOR, money.id()).issues())
                .extracting(issue -> issue.code())
                .containsExactly("MEASURE_MONEY_UNSUPPORTED",
                        "TIME_NOT_TEMPORAL");
        assertThatThrownBy(() -> service.publish(ACTOR, money.id(), 1))
                .isInstanceOf(KpiException.class)
                .satisfies(error -> assertThat(((KpiException) error).code())
                        .isEqualTo("KPI_CHECK_BLOCKED"));
        assertThat(repository.publishCount).isZero();
    }

    @Test
    void draftAndTargetCompareAndSwapTenantHidingAndTargetReplayAreEnforced() {
        var root = create("reviewed", KpiSubjectType.ROLE);
        var version = service.publish(ACTOR, root.id(), 1);
        var start = LocalDate.parse("2026-07-01");

        assertThatThrownBy(() -> service.saveDraft(ACTOR, root.id(), 9,
                "changed", null, root.draft()))
                .isInstanceOf(KpiException.class)
                .satisfies(error -> assertThat(((KpiException) error).code())
                        .isEqualTo("KPI_VERSION_CONFLICT"));
        assertThatThrownBy(() -> service.detail(
                new KpiActor(10, 21, 30), root.id()))
                .isInstanceOf(KpiException.class)
                .satisfies(error -> assertThat(((KpiException) error).code())
                        .isEqualTo("KPI_NOT_FOUND"));

        var target = service.createTarget(
                ACTOR, root.id(), version.id(), 300, start, "10");
        var replay = service.createTarget(
                ACTOR, root.id(), version.id(), 300, start, "10");
        assertThat(replay).isEqualTo(target);
        assertThat(repository.targetInsertCount).isEqualTo(1);
        assertThatThrownBy(() -> service.createTarget(
                ACTOR, root.id(), version.id(), 300, start, "11"))
                .isInstanceOf(KpiException.class)
                .satisfies(error -> assertThat(((KpiException) error).code())
                        .isEqualTo("KPI_TARGET_CONFLICT"));

        var revised = service.updateTarget(
                ACTOR, target.id(), target.version(), "12.5");
        assertThat(revised.targetValue()).isEqualTo("12.5");
        assertThat(revised.version()).isEqualTo(2);
        assertThatThrownBy(() -> service.updateTarget(
                ACTOR, target.id(), 1, "13"))
                .isInstanceOf(KpiException.class)
                .satisfies(error -> assertThat(((KpiException) error).code())
                        .isEqualTo("KPI_TARGET_VERSION_CONFLICT"));
    }

    @Test
    void calculationPersistsExactPinsRoleSnapshotTrendEpochAndCommandReplay() {
        var root = create("reviewed", KpiSubjectType.ROLE);
        var definition = service.publish(ACTOR, root.id(), 1);
        var target = service.createTarget(ACTOR, root.id(), definition.id(),
                300, LocalDate.parse("2026-07-01"), "10");

        var calculation = service.calculate(ACTOR, target.id(), "calc-1");
        var replay = service.calculate(ACTOR, target.id(), "calc-1");

        assertThat(replay).isEqualTo(calculation);
        assertThat(repository.calculationInsertCount).isEqualTo(1);
        assertThat(calculation.status()).isEqualTo(KpiWarningStatus.AT_RISK);
        assertThat(calculation.actualValue()).isEqualTo("9");
        assertThat(calculation.attainment()).isEqualTo("0.9");
        assertThat(calculation.authorizationEpoch()).isEqualTo(77);
        assertThat(calculation.statisticsQueryId()).isEqualTo("a".repeat(64));
        assertThat(calculation.subject().roleMemberIds())
                .containsExactly(33L, 35L);
        assertThat(calculation.trend()).hasSize(3)
                .extracting(KpiCalculation.TrendBucket::startInclusive)
                .containsExactly(LocalDate.parse("2026-07-01"),
                        LocalDate.parse("2026-08-01"),
                        LocalDate.parse("2026-09-01"));
        assertThat(statistics.requests.getFirst().ownership().memberIds())
                .containsExactly(33L, 35L);

        service.saveDraft(ACTOR, root.id(), 1, "new name", null,
                new KpiDraft(100, KpiSubjectType.ROLE,
                        KpiPeriodType.QUARTER, StatisticsAggregation.COUNT,
                        null, "occurredAt", KpiAttainmentDirection.AT_MOST,
                        "0.9"));
        sources.add(10, 20, 100, 502, 2);
        var later = service.calculate(ACTOR, target.id(), "calc-2");
        assertThat(later.definition().kpiVersionId()).isEqualTo(definition.id());
        assertThat(later.definition().source().dataSourceVersionId())
                .isEqualTo(501);
        assertThat(statistics.requests.getLast().source()
                .dataSourceVersionId()).isEqualTo(501);
    }

    @Test
    void unmetReminderUsesExactRoleSnapshotAndCalculationReplayDoesNotRedeliver() {
        var deliveries = new ArrayList<String>();
        service = new KpiService(
                repository, sources, subjects, statistics,
                (systemId, tenantId, departmentId) -> List.of(42L, 41L, 42L),
                recording(deliveries), CLOCK);
        var root = create("remindedRole", KpiSubjectType.ROLE);
        var definition = service.publish(ACTOR, root.id(), 1);
        var target = service.createTarget(
                ACTOR, root.id(), definition.id(), 300,
                LocalDate.parse("2026-07-01"), "10");

        var first = service.calculate(ACTOR, target.id(), "remind-role");
        service.calculate(ACTOR, target.id(), "remind-role");
        subjects.add(KpiSubjectType.ROLE, 300, "Reviewer", List.of(99L));

        assertThat(first.status()).isEqualTo(KpiWarningStatus.AT_RISK);
        assertThat(deliveries).containsExactly(
                first.id() + ":33", first.id() + ":35");
    }

    @Test
    void departmentRecipientsAreSortedAndAchievedOrFailedCalculationsDoNotNotify() {
        var deliveries = new ArrayList<String>();
        service = new KpiService(
                repository, sources, subjects, statistics,
                (systemId, tenantId, departmentId) ->
                        departmentId == 200 ? List.of(42L, 41L, 42L)
                                : List.of(),
                recording(deliveries), CLOCK);
        var department = published(
                "remindedDepartment", KpiSubjectType.DEPARTMENT);
        var target = service.createTarget(
                ACTOR, department.kpiId(), department.id(), 200,
                LocalDate.parse("2026-07-01"), "10");

        var atRisk = service.calculate(ACTOR, target.id(), "remind-department");
        assertThat(deliveries).containsExactly(
                atRisk.id() + ":41", atRisk.id() + ":42");

        deliveries.clear();
        statistics.nextActual = "10";
        var achieved = service.calculate(ACTOR, target.id(), "achieved");
        assertThat(achieved.status()).isEqualTo(KpiWarningStatus.ACHIEVED);
        assertThat(deliveries).isEmpty();

        statistics.failAuthorization = true;
        var failed = service.calculate(ACTOR, target.id(), "failed");
        assertThat(failed.status())
                .isEqualTo(KpiWarningStatus.CALCULATION_FAILED);
        assertThat(deliveries).isEmpty();
    }

    @Test
    void reminderFailureEscapesCalculationFallbackForTransactionRollback() {
        service = new KpiService(
                repository, sources, subjects, statistics,
                (systemId, tenantId, departmentId) -> List.of(),
                (calculation, memberId) -> {
                    throw new IllegalStateException("inbox unavailable");
                }, CLOCK);
        var member = published("reminderFailure", KpiSubjectType.MEMBER);
        var target = service.createTarget(
                ACTOR, member.kpiId(), member.id(), 31,
                LocalDate.parse("2026-07-01"), "10");

        assertThatThrownBy(() -> service.calculate(
                ACTOR, target.id(), "reminder-failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inbox unavailable");
    }

    @Test
    void emptyRoleIsAnExactZeroAndNonAllFailureIsPersistedWithoutLeakage() {
        var root = create("reviewed", KpiSubjectType.ROLE);
        var version = service.publish(ACTOR, root.id(), 1);
        var empty = service.createTarget(ACTOR, root.id(), version.id(), 301,
                LocalDate.parse("2026-07-01"), "0");

        var zero = service.calculate(ACTOR, empty.id(), "empty-role");
        assertThat(zero.status()).isEqualTo(KpiWarningStatus.ACHIEVED);
        assertThat(zero.actualValue()).isEqualTo("0");
        assertThat(zero.attainment()).isEqualTo("1");
        assertThat(zero.matchedRecordCount()).isZero();
        assertThat(zero.trend()).allSatisfy(bucket -> {
            assertThat(bucket.value()).isEqualTo("0");
            assertThat(bucket.recordCount()).isZero();
        });
        assertThat(statistics.requests.getFirst().ownership().memberIds())
                .isEmpty();

        statistics.failAuthorization = true;
        var failed = service.calculate(ACTOR, empty.id(), "no-all");
        assertThat(failed.status())
                .isEqualTo(KpiWarningStatus.CALCULATION_FAILED);
        assertThat(failed.errorCode()).isEqualTo("KPI_CALCULATION_FORBIDDEN");
        assertThat(failed.authorizationEpoch()).isNull();
        assertThat(failed.statisticsQueryId()).isNull();
        assertThat(repository.calculationInsertCount).isEqualTo(2);
    }

    @Test
    void signedNumericActualAndTrendRemainExactWhileTargetStaysNonNegative() {
        var root = service.create(ACTOR, "costDelta", "Cost delta", null,
                new KpiDraft(100, KpiSubjectType.MEMBER,
                        KpiPeriodType.MONTH, StatisticsAggregation.SUM,
                        "amount", "occurredAt",
                        KpiAttainmentDirection.AT_MOST, "0.8"));
        var version = service.publish(ACTOR, root.id(), 1);
        var target = service.createTarget(ACTOR, root.id(), version.id(), 31,
                LocalDate.parse("2026-07-01"), "0");
        statistics.nextActual = "-2.5";
        statistics.nextBucket = "-2.5";

        var calculation = service.calculate(
                ACTOR, target.id(), "signed-actual");

        assertThat(calculation.actualValue()).isEqualTo("-2.5");
        assertThat(calculation.trend().getFirst().value()).isEqualTo("-2.5");
        assertThat(calculation.attainment()).isEqualTo("1");
        assertThat(calculation.status()).isEqualTo(KpiWarningStatus.ACHIEVED);
        assertThat(statistics.requests.getFirst().source().measureField())
                .extracting(KpiFieldPin::code).isEqualTo("amount");
        assertThatThrownBy(() -> service.updateTarget(
                ACTOR, target.id(), 1, "-1"))
                .isInstanceOf(KpiException.class)
                .satisfies(error -> assertThat(((KpiException) error).code())
                        .isEqualTo("KPI_DECIMAL_INVALID"));
    }

    @Test
    void currentMemberSeesOnlyOwnDepartmentAndRoleTargetsForDate() {
        var member = published("memberKpi", KpiSubjectType.MEMBER);
        var department = published("departmentKpi", KpiSubjectType.DEPARTMENT);
        var role = published("roleKpi", KpiSubjectType.ROLE);
        var otherRole = published("otherRoleKpi", KpiSubjectType.ROLE);
        var start = LocalDate.parse("2026-07-01");
        service.createTarget(ACTOR, member.kpiId(), member.id(), 31, start, "1");
        service.createTarget(
                ACTOR, department.kpiId(), department.id(), 200, start, "1");
        service.createTarget(ACTOR, role.kpiId(), role.id(), 300, start, "1");
        service.createTarget(
                ACTOR, otherRole.kpiId(), otherRole.id(), 301, start, "1");
        subjects.membership = new KpiSubjectDirectory.CurrentMembership(
                true, List.of(200L), List.of(300L));

        assertThat(service.applicableTargets(
                new KpiActor(10, 20, 31), LocalDate.parse("2026-08-15")))
                .extracting(item -> item.definition().code())
                .containsExactly("departmentKpi", "memberKpi", "roleKpi");
        assertThat(service.applicableTargets(
                new KpiActor(10, 20, 31), LocalDate.parse("2027-01-01")))
                .isEmpty();

        subjects.membership = new KpiSubjectDirectory.CurrentMembership(
                false, List.of(200L), List.of(300L));
        assertThat(service.applicableTargets(
                new KpiActor(10, 20, 31), LocalDate.parse("2026-08-15")))
                .isEmpty();
    }

    private KpiDefinition create(String code, KpiSubjectType type) {
        return service.create(ACTOR, code, "Reviewed records", null,
                new KpiDraft(100, type, KpiPeriodType.QUARTER,
                        StatisticsAggregation.COUNT, null, "occurredAt",
                        KpiAttainmentDirection.AT_LEAST, "0.8"));
    }

    private KpiVersion published(String code, KpiSubjectType type) {
        var root = create(code, type);
        return service.publish(ACTOR, root.id(), root.draftVersion());
    }

    private static KpiReminderNotifier recording(List<String> deliveries) {
        return (calculation, recipientMemberId) -> {
            deliveries.add(calculation.id() + ":" + recipientMemberId);
            return deliveries.size();
        };
    }

    private static final class MutableSourceCatalog
            implements KpiSourceCatalog {
        private final Map<SourceKey, SourceVersion> active = new HashMap<>();
        private final Map<VersionKey, SourceVersion> versions = new HashMap<>();

        private void add(long systemId, long tenantId, long sourceId,
                         long versionId, int versionNumber) {
            var value = new SourceVersion(sourceId, systemId, tenantId,
                    "orders", versionId, versionNumber, "orders_module",
                    "schema-" + versionNumber, List.of(
                    new Field(901, "amount", "Amount", "NUMBER", "NUMBER",
                            true, true, false),
                    new Field(902, "price", "Price", "MONEY", "MONEY",
                            true, true, false),
                    new Field(903, "occurredAt", "Occurred at", "DATETIME",
                            "DATETIME", true, false, true),
                    new Field(904, "title", "Title", "TEXT", "TEXT",
                            true, false, false),
                    new Field(905, "secret", "Secret", "TEXT", "TEXT",
                            false, false, false)));
            active.put(new SourceKey(systemId, tenantId, sourceId), value);
            versions.put(new VersionKey(
                    systemId, tenantId, sourceId, versionId), value);
        }

        @Override
        public Optional<SourceVersion> active(
                long systemId, long tenantId, long dataSourceId) {
            return Optional.ofNullable(active.get(
                    new SourceKey(systemId, tenantId, dataSourceId)));
        }

        @Override
        public Optional<SourceVersion> version(long systemId, long tenantId,
                                               long dataSourceId,
                                               long dataSourceVersionId) {
            return Optional.ofNullable(versions.get(new VersionKey(
                    systemId, tenantId, dataSourceId, dataSourceVersionId)));
        }

        private record SourceKey(long systemId, long tenantId, long sourceId) {}

        private record VersionKey(long systemId, long tenantId,
                                  long sourceId, long versionId) {}
    }

    private static final class MutableSubjectDirectory
            implements KpiSubjectDirectory {
        private final Map<SubjectKey, SubjectResolution> values = new HashMap<>();
        private CurrentMembership membership =
                new CurrentMembership(true, List.of(), List.of());

        private void add(KpiSubjectType type, long id, String name,
                         List<Long> members) {
            values.put(new SubjectKey(type, id),
                    new SubjectResolution(type, id, true, name, members));
        }

        @Override
        public Optional<SubjectResolution> resolve(
                long systemId, long tenantId, KpiSubjectType subjectType,
                long subjectId) {
            if (systemId != 10 || tenantId != 20) {
                return Optional.empty();
            }
            return Optional.ofNullable(values.get(
                    new SubjectKey(subjectType, subjectId)));
        }

        @Override
        public CurrentMembership currentMembership(
                long systemId, long tenantId, long memberId) {
            return systemId == 10 && tenantId == 20
                    ? membership : new CurrentMembership(
                    false, List.of(), List.of());
        }

        private record SubjectKey(KpiSubjectType type, long id) {}
    }

    private static final class FakeStatistics
            implements KpiStatisticsExecutor {
        private final List<Request> requests = new ArrayList<>();
        private boolean failAuthorization;
        private String nextActual;
        private String nextBucket;

        @Override
        public Authorization requireAllAccess(
                KpiActor actor, String moduleCode) {
            if (failAuthorization) {
                throw new KpiException("KPI_CALCULATION_FORBIDDEN",
                        "ALL record access is required");
            }
            return new Authorization(77);
        }

        @Override
        public Result execute(KpiActor actor, Request request) {
            requests.add(request);
            var empty = request.ownership().kind()
                    == OwnershipRestriction.Kind.MEMBERS
                    && request.ownership().memberIds().isEmpty();
            var buckets = new ArrayList<KpiCalculation.TrendBucket>();
            var cursor = request.period().startInclusive();
            while (cursor.isBefore(request.period().endExclusive())) {
                buckets.add(new KpiCalculation.TrendBucket(
                        cursor, cursor.plusMonths(1), empty ? "0"
                        : nextBucket == null ? "3" : nextBucket,
                        empty ? 0 : 1));
                cursor = cursor.plusMonths(1);
            }
            var source = request.source();
            return new Result("a".repeat(64),
                    source.dataSourceId(), source.dataSourceVersionId(),
                    source.moduleCode(), source.schemaVersionId(),
                    request.aggregation(), source.measureField(),
                    source.timeField(), empty ? "0"
                    : nextActual == null ? "9" : nextActual,
                    empty ? 0 : request.period().monthCount(),
                    buckets);
        }
    }

    private static final class InMemoryRepository implements KpiRepository {
        private long rootSequence = 100;
        private long versionSequence = 500;
        private long targetSequence = 800;
        private long calculationSequence = 900;
        private int publishCount;
        private int targetInsertCount;
        private int calculationInsertCount;
        private final Map<RootKey, KpiDefinition> roots = new LinkedHashMap<>();
        private final Map<Long, List<KpiVersion>> versions = new HashMap<>();
        private final Map<TargetKey, KpiTarget> targets = new LinkedHashMap<>();
        private final Map<Long, List<KpiCalculation>> calculations =
                new HashMap<>();
        private final Map<CommandKey, KpiCalculation> commands = new HashMap<>();

        @Override public long nextKpiId() { return ++rootSequence; }
        @Override public long nextVersionId() { return ++versionSequence; }
        @Override public long nextTargetId() { return ++targetSequence; }
        @Override public long nextCalculationId() { return ++calculationSequence; }

        @Override
        public Optional<KpiDefinition> findById(
                long systemId, long tenantId, long kpiId) {
            return Optional.ofNullable(roots.get(
                    new RootKey(systemId, tenantId, kpiId)));
        }

        @Override
        public Optional<KpiDefinition> findByCode(
                long systemId, long tenantId, String code) {
            return roots.values().stream().filter(root ->
                    root.systemId() == systemId && root.tenantId() == tenantId
                            && root.code().equals(code)).findFirst();
        }

        @Override
        public List<KpiDefinition> findAll(long systemId, long tenantId) {
            return roots.values().stream().filter(root ->
                    root.systemId() == systemId
                            && root.tenantId() == tenantId).toList();
        }

        @Override
        public KpiDefinition insert(KpiDefinition root) {
            roots.put(key(root), root);
            return root;
        }

        @Override
        public KpiDefinition saveDraft(
                KpiDefinition expected, KpiDefinition revised) {
            if (!roots.replace(key(expected), expected, revised)) {
                throw conflict();
            }
            return revised;
        }

        @Override
        public KpiVersion publish(KpiDefinition expected,
                                  KpiDefinition activated,
                                  KpiVersion version) {
            if (!roots.replace(key(expected), expected, activated)) {
                throw conflict();
            }
            versions.computeIfAbsent(expected.id(), ignored ->
                    new ArrayList<>()).add(version);
            publishCount++;
            return version;
        }

        @Override
        public Optional<KpiVersion> findActiveVersion(
                long systemId, long tenantId, long kpiId) {
            return findById(systemId, tenantId, kpiId)
                    .filter(root -> root.activeVersionId() != null)
                    .flatMap(root -> findVersionById(systemId, tenantId, kpiId,
                            root.activeVersionId()));
        }

        @Override
        public Optional<KpiVersion> findVersionById(long systemId,
                                                    long tenantId, long kpiId,
                                                    long versionId) {
            if (findById(systemId, tenantId, kpiId).isEmpty()) {
                return Optional.empty();
            }
            return versions.getOrDefault(kpiId, List.of()).stream()
                    .filter(value -> value.id() == versionId).findFirst();
        }

        @Override
        public Optional<KpiVersion> findVersion(long systemId, long tenantId,
                                                long kpiId,
                                                int versionNumber) {
            if (findById(systemId, tenantId, kpiId).isEmpty()) {
                return Optional.empty();
            }
            return versions.getOrDefault(kpiId, List.of()).stream()
                    .filter(value -> value.versionNumber() == versionNumber)
                    .findFirst();
        }

        @Override
        public List<KpiVersion> findVersions(
                long systemId, long tenantId, long kpiId) {
            return findById(systemId, tenantId, kpiId).isPresent()
                    ? List.copyOf(versions.getOrDefault(kpiId, List.of()))
                    : List.of();
        }

        @Override
        public Optional<KpiTarget> findTargetById(
                long systemId, long tenantId, long targetId) {
            return targets.values().stream().filter(target ->
                    target.systemId() == systemId
                            && target.tenantId() == tenantId
                            && target.id() == targetId).findFirst();
        }

        @Override
        public Optional<KpiTarget> findTargetByBusinessKey(
                long systemId, long tenantId, long kpiVersionId,
                KpiSubjectType subjectType, long subjectId,
                KpiPeriod period) {
            return Optional.ofNullable(targets.get(new TargetKey(systemId,
                    tenantId, kpiVersionId, subjectType, subjectId, period)));
        }

        @Override
        public List<KpiTarget> findTargets(long systemId, long tenantId) {
            return targets.values().stream().filter(target ->
                    target.systemId() == systemId
                            && target.tenantId() == tenantId).toList();
        }

        @Override
        public KpiTarget insertTarget(KpiTarget target) {
            targets.put(targetKey(target), target);
            targetInsertCount++;
            return target;
        }

        @Override
        public KpiTarget saveTarget(KpiTarget expected, KpiTarget revised) {
            if (!targets.replace(targetKey(expected), expected, revised)) {
                throw conflict();
            }
            return revised;
        }

        @Override
        public Optional<KpiCalculation> findCalculationByCommandKey(
                long systemId, long tenantId, String commandKey) {
            return Optional.ofNullable(commands.get(
                    new CommandKey(systemId, tenantId, commandKey)));
        }

        @Override
        public Optional<KpiCalculation> findLatestCalculation(
                long systemId, long tenantId, long targetId) {
            var values = calculations.getOrDefault(targetId, List.of());
            return values.isEmpty() ? Optional.empty()
                    : Optional.of(values.getLast());
        }

        @Override
        public List<KpiCalculation> findCalculations(
                long systemId, long tenantId, long targetId) {
            return findTargetById(systemId, tenantId, targetId).isPresent()
                    ? List.copyOf(calculations.getOrDefault(targetId, List.of()))
                    : List.of();
        }

        @Override
        public KpiCalculation insertCalculation(KpiCalculation calculation) {
            commands.put(new CommandKey(calculation.systemId(),
                    calculation.tenantId(), calculation.commandKey()),
                    calculation);
            calculations.computeIfAbsent(calculation.target().targetId(),
                    ignored -> new ArrayList<>()).add(calculation);
            calculationInsertCount++;
            return calculation;
        }

        private static RootKey key(KpiDefinition root) {
            return new RootKey(root.systemId(), root.tenantId(), root.id());
        }

        private static TargetKey targetKey(KpiTarget target) {
            return new TargetKey(target.systemId(), target.tenantId(),
                    target.kpiVersionId(), target.subjectType(),
                    target.subjectId(), target.period());
        }

        private static KpiException conflict() {
            return new KpiException("KPI_VERSION_CONFLICT", "Stale state");
        }

        private record RootKey(long systemId, long tenantId, long id) {}
        private record TargetKey(long systemId, long tenantId,
                                 long versionId, KpiSubjectType subjectType,
                                 long subjectId, KpiPeriod period) {}
        private record CommandKey(long systemId, long tenantId, String value) {}
    }
}
