package com.unique.examine.module.kpi.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiAttainment;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiCheckReport;
import com.unique.examine.module.kpi.domain.KpiDefinition;
import com.unique.examine.module.kpi.domain.KpiDecimal;
import com.unique.examine.module.kpi.domain.KpiDraft;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.domain.KpiFieldPin;
import com.unique.examine.module.kpi.domain.KpiPeriod;
import com.unique.examine.module.kpi.domain.KpiPeriodType;
import com.unique.examine.module.kpi.domain.KpiSourcePin;
import com.unique.examine.module.kpi.domain.KpiSubjectSnapshot;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import com.unique.examine.module.kpi.domain.KpiTarget;
import com.unique.examine.module.kpi.domain.KpiVersion;
import com.unique.examine.module.kpi.domain.KpiWarningStatus;
import com.unique.examine.module.kpi.domain.PublishedKpi;
import com.unique.examine.module.kpi.port.KpiRepository;
import com.unique.examine.module.kpi.port.KpiReminderNotifier;
import com.unique.examine.module.kpi.port.KpiReminderRecipientDirectory;
import com.unique.examine.module.kpi.port.KpiSourceCatalog;
import com.unique.examine.module.kpi.port.KpiStatisticsExecutor;
import com.unique.examine.module.kpi.port.KpiSubjectDirectory;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.unique.examine.module.kpi.domain.KpiCheckReport.Severity.BLOCKER;

@Transactional
public class KpiService {
    private final KpiRepository repository;
    private final KpiSourceCatalog sources;
    private final KpiSubjectDirectory subjects;
    private final KpiStatisticsExecutor statistics;
    private final KpiReminderRecipientDirectory reminderRecipients;
    private final KpiReminderNotifier reminders;
    private final Clock clock;

    public KpiService(
            KpiRepository repository,
            KpiSourceCatalog sources,
            KpiSubjectDirectory subjects,
            KpiStatisticsExecutor statistics,
            Clock clock
    ) {
        this(repository, sources, subjects, statistics,
                (systemId, tenantId, departmentId) -> List.of(),
                (calculation, recipientMemberId) -> 0L, clock);
    }

    public KpiService(
            KpiRepository repository,
            KpiSourceCatalog sources,
            KpiSubjectDirectory subjects,
            KpiStatisticsExecutor statistics,
            KpiReminderRecipientDirectory reminderRecipients,
            KpiReminderNotifier reminders,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.subjects = Objects.requireNonNull(subjects, "subjects");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.reminderRecipients = Objects.requireNonNull(
                reminderRecipients, "reminderRecipients");
        this.reminders = Objects.requireNonNull(reminders, "reminders");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public KpiDefinition create(
            KpiActor actor,
            String code,
            String name,
            String description,
            KpiDraft draft
    ) {
        requireActor(actor);
        var normalizedCode = KpiDefinition.code(code);
        if (repository.findByCode(actor.systemId(), actor.tenantId(),
                normalizedCode).isPresent()) {
            throw error("KPI_CODE_CONFLICT",
                    "KPI code already exists in this tenant");
        }
        return repository.insert(KpiDefinition.create(
                repository.nextKpiId(), actor.systemId(), actor.tenantId(),
                normalizedCode, name, description, draft, clock.instant()));
    }

    public List<KpiDefinition> list(KpiActor actor) {
        requireActor(actor);
        return repository.findAll(actor.systemId(), actor.tenantId()).stream()
                .sorted(Comparator.comparing(
                                (KpiDefinition definition) -> definition.code())
                        .thenComparingLong(KpiDefinition::id))
                .toList();
    }

    public KpiDefinition detail(KpiActor actor, long kpiId) {
        requireActor(actor);
        return root(actor, kpiId);
    }

    public KpiDefinition saveDraft(
            KpiActor actor,
            long kpiId,
            long expectedDraftVersion,
            String name,
            String description,
            KpiDraft draft
    ) {
        requireActor(actor);
        var current = root(actor, kpiId);
        requireDraftVersion(current, expectedDraftVersion);
        return repository.saveDraft(current, current.reviseDraft(
                name, description, draft, clock.instant()));
    }

    public KpiCheckReport check(KpiActor actor, long kpiId) {
        requireActor(actor);
        return validate(actor, root(actor, kpiId)).report();
    }

    public KpiVersion publish(
            KpiActor actor,
            long kpiId,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, kpiId);
        requireDraftVersion(current, expectedDraftVersion);
        var validation = validate(actor, current);
        if (!validation.report().publishable()) {
            throw error("KPI_CHECK_BLOCKED",
                    "KPI draft contains publish blockers");
        }
        var source = Objects.requireNonNull(validation.source());
        var sourcePin = sourcePin(current.draft(), source);
        var fingerprint = fingerprint(current, sourcePin);
        var active = repository.findActiveVersion(
                actor.systemId(), actor.tenantId(), current.id());
        if (current.activeVersionId() != null && active.isEmpty()) {
            throw error("KPI_VERSION_NOT_FOUND",
                    "Active KPI version does not exist");
        }
        if (active.isPresent()
                && active.get().fingerprint().equals(fingerprint)) {
            return active.get();
        }
        var draft = current.draft();
        var publishedAt = clock.instant();
        var version = new KpiVersion(
                repository.nextVersionId(), current.id(), current.systemId(),
                current.tenantId(),
                active.map(value -> value.versionNumber() + 1).orElse(1),
                current.draftVersion(), current.code(), current.name(),
                current.description(), draft.subjectType(),
                draft.periodType(), draft.aggregation(), draft.direction(),
                draft.warningThreshold(), sourcePin, fingerprint,
                actor.memberId(), publishedAt);
        var activated = current.activate(version, publishedAt);
        try {
            return repository.publish(current, activated, version);
        } catch (RuntimeException conflict) {
            var replay = repository.findActiveVersion(
                    actor.systemId(), actor.tenantId(), current.id());
            if (replay.isPresent()
                    && replay.get().fingerprint().equals(fingerprint)) {
                return replay.get();
            }
            throw conflict;
        }
    }

    public List<KpiVersion> versions(KpiActor actor, long kpiId) {
        requireActor(actor);
        root(actor, kpiId);
        return repository.findVersions(actor.systemId(), actor.tenantId(), kpiId)
                .stream().sorted(Comparator.comparingInt(
                        KpiVersion::versionNumber).reversed()).toList();
    }

    public KpiVersion version(
            KpiActor actor,
            long kpiId,
            int versionNumber
    ) {
        requireActor(actor);
        root(actor, kpiId);
        return repository.findVersion(actor.systemId(), actor.tenantId(),
                        kpiId, versionNumber)
                .orElseThrow(KpiService::versionNotFound);
    }

    /** Restores a publication as a new draft without changing runtime state. */
    public KpiDefinition restoreVersion(
            KpiActor actor,
            long kpiId,
            int versionNumber,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, kpiId);
        requireDraftVersion(current, expectedDraftVersion);
        var source = version(actor, kpiId, versionNumber);
        var pin = source.source();
        var restoredDraft = new KpiDraft(
                pin.dataSourceId(), source.subjectType(), source.periodType(),
                source.aggregation(),
                pin.measureField() == null ? null : pin.measureField().code(),
                pin.timeField().code(), source.direction(),
                source.warningThreshold());
        return repository.saveDraft(current, current.reviseDraft(
                source.name(), source.description(), restoredDraft,
                clock.instant()));
    }

    public PublishedKpi active(KpiActor actor, long kpiId) {
        requireActor(actor);
        var root = root(actor, kpiId);
        var version = repository.findActiveVersion(
                        actor.systemId(), actor.tenantId(), kpiId)
                .orElseThrow(() -> error("KPI_UNPUBLISHED",
                        "KPI has no active published version"));
        return new PublishedKpi(root, version);
    }

    public KpiTarget createTarget(
            KpiActor actor,
            long kpiId,
            long subjectId,
            LocalDate startInclusive,
            String targetValue
    ) {
        return createTarget(actor, active(actor, kpiId).version(), subjectId,
                startInclusive, targetValue);
    }

    public KpiTarget createTarget(
            KpiActor actor,
            long kpiId,
            long kpiVersionId,
            long subjectId,
            LocalDate startInclusive,
            String targetValue
    ) {
        requireActor(actor);
        root(actor, kpiId);
        var version = repository.findVersionById(actor.systemId(),
                        actor.tenantId(), kpiId, kpiVersionId)
                .orElseThrow(KpiService::versionNotFound);
        return createTarget(actor, version, subjectId, startInclusive,
                targetValue);
    }

    private KpiTarget createTarget(
            KpiActor actor,
            KpiVersion definition,
            long subjectId,
            LocalDate startInclusive,
            String targetValue
    ) {
        var period = KpiPeriod.starting(definition.periodType(), startInclusive);
        var subject = activeSubject(actor, definition.subjectType(), subjectId);
        var existing = repository.findTargetByBusinessKey(
                actor.systemId(), actor.tenantId(), definition.id(),
                definition.subjectType(), subjectId, period);
        if (existing.isPresent()) {
            if (existing.get().sameCreation(
                    definition, subjectId, period, targetValue)) {
                return existing.get();
            }
            throw error("KPI_TARGET_CONFLICT",
                    "A different target already uses this KPI, subject and period");
        }
        var candidate = KpiTarget.create(
                repository.nextTargetId(), actor, definition, subjectId,
                subject.displayName(), period, targetValue, clock.instant());
        try {
            return repository.insertTarget(candidate);
        } catch (RuntimeException conflict) {
            var replay = repository.findTargetByBusinessKey(
                    actor.systemId(), actor.tenantId(), definition.id(),
                    definition.subjectType(), subjectId, period);
            if (replay.isPresent() && replay.get().sameCreation(
                    definition, subjectId, period, targetValue)) {
                return replay.get();
            }
            throw conflict;
        }
    }

    public KpiTarget updateTarget(
            KpiActor actor,
            long targetId,
            long expectedVersion,
            String targetValue
    ) {
        requireActor(actor);
        var current = target(actor, targetId);
        if (expectedVersion <= 0 || current.version() != expectedVersion) {
            throw error("KPI_TARGET_VERSION_CONFLICT",
                    "KPI target changed; refresh before retrying");
        }
        activeSubject(actor, current.subjectType(), current.subjectId());
        return repository.saveTarget(current, current.reviseValue(
                targetValue, actor.memberId(), clock.instant()));
    }

    public KpiTarget target(KpiActor actor, long targetId) {
        requireActor(actor);
        if (targetId <= 0) {
            throw targetNotFound();
        }
        return repository.findTargetById(
                        actor.systemId(), actor.tenantId(), targetId)
                .orElseThrow(KpiService::targetNotFound);
    }

    public List<KpiTarget> targets(KpiActor actor, long kpiId) {
        requireActor(actor);
        root(actor, kpiId);
        return repository.findTargets(actor.systemId(), actor.tenantId()).stream()
                .filter(target -> target.kpiId() == kpiId)
                .sorted(targetOrder()).toList();
    }

    public KpiCalculation calculate(
            KpiActor actor,
            long targetId,
            String commandKey
    ) {
        requireActor(actor);
        var target = target(actor, targetId);
        commandKey = KpiCalculation.commandKey(commandKey);
        var replay = repository.findCalculationByCommandKey(
                actor.systemId(), actor.tenantId(), commandKey);
        if (replay.isPresent()) {
            if (replay.get().target().targetId() != targetId) {
                throw error("KPI_CALCULATION_COMMAND_CONFLICT",
                        "Calculation command key was used for another target");
            }
            return replay.get();
        }
        var definition = repository.findVersionById(actor.systemId(),
                        actor.tenantId(), target.kpiId(), target.kpiVersionId())
                .orElseThrow(KpiService::versionNotFound);
        var startedAt = clock.instant();
        var subject = fallbackSubject(target);
        Long authorizationEpoch = null;
        KpiCalculation candidate;
        try {
            requireExactDefinition(target, definition);
            requireExactSource(actor, definition);
            var resolved = activeSubject(
                    actor, target.subjectType(), target.subjectId());
            subject = snapshot(resolved);
            var authorization = statistics.requireAllAccess(
                    actor, definition.source().moduleCode());
            authorizationEpoch = authorization.epoch();
            var restriction = ownership(target, subject);
            var result = statistics.execute(actor,
                    new KpiStatisticsExecutor.Request(
                            definition.source(), definition.aggregation(),
                            target.period(), restriction, authorization));
            requireExactResult(definition, target.period(), result);
            var actual = result.value() == null
                    ? "0" : KpiDecimal.require(result.value());
            var attainment = KpiAttainment.evaluate(definition.direction(),
                    target.targetValue(), actual,
                    definition.warningThreshold());
            candidate = KpiCalculation.success(
                    repository.nextCalculationId(), actor, commandKey,
                    definition, target, subject, actual, attainment,
                    result.queryId(), result.matchedRecordCount(),
                    result.trend(), authorizationEpoch, startedAt,
                    clock.instant());
        } catch (RuntimeException failure) {
            candidate = KpiCalculation.failed(
                    repository.nextCalculationId(), actor, commandKey,
                    definition, target, subject, authorizationEpoch,
                    startedAt, clock.instant(), stableError(failure));
        }
        var inserted = insertCalculation(candidate);
        if (inserted.inserted()) {
            deliverReminders(inserted.calculation());
        }
        return inserted.calculation();
    }

    private InsertResult insertCalculation(KpiCalculation candidate) {
        try {
            return new InsertResult(repository.insertCalculation(candidate),
                    true);
        } catch (RuntimeException conflict) {
            var replay = repository.findCalculationByCommandKey(
                    candidate.systemId(), candidate.tenantId(),
                    candidate.commandKey());
            if (replay.isPresent()
                    && replay.get().target().targetId()
                    == candidate.target().targetId()) {
                return new InsertResult(replay.get(), false);
            }
            throw conflict;
        }
    }

    private void deliverReminders(KpiCalculation calculation) {
        if (calculation.status() != KpiWarningStatus.AT_RISK
                && calculation.status() != KpiWarningStatus.MISSED) {
            return;
        }
        List<Long> recipients = switch (calculation.subject().type()) {
            case MEMBER -> List.of(calculation.subject().id());
            case DEPARTMENT -> reminderRecipients.activeDepartmentMemberIds(
                    calculation.systemId(), calculation.tenantId(),
                    calculation.subject().id());
            case ROLE -> calculation.subject().roleMemberIds();
        };
        recipients.stream().sorted().distinct()
                .forEach(memberId -> reminders.deliver(
                        calculation, memberId));
    }

    private record InsertResult(
            KpiCalculation calculation,
            boolean inserted
    ) {
    }

    public List<KpiCalculation> calculationHistory(
            KpiActor actor,
            long targetId
    ) {
        requireActor(actor);
        target(actor, targetId);
        return repository.findCalculations(
                        actor.systemId(), actor.tenantId(), targetId).stream()
                .sorted(Comparator.comparing(KpiCalculation::startedAt)
                        .reversed().thenComparing(
                                KpiCalculation::id, Comparator.reverseOrder()))
                .toList();
    }

    public List<ApplicableTarget> applicableTargets(
            KpiActor actor,
            LocalDate date
    ) {
        requireActor(actor);
        Objects.requireNonNull(date, "date");
        var membership = subjects.currentMembership(
                actor.systemId(), actor.tenantId(), actor.memberId());
        if (!membership.memberActive()) {
            return List.of();
        }
        Set<Long> departments = new HashSet<>(membership.departmentIds());
        Set<Long> roles = new HashSet<>(membership.roleIds());
        var result = new ArrayList<ApplicableTarget>();
        for (var target : repository.findTargets(
                actor.systemId(), actor.tenantId())) {
            if (!applies(actor, target, departments, roles)
                    || date.isBefore(target.period().startInclusive())
                    || !date.isBefore(target.period().endExclusive())) {
                continue;
            }
            var definition = repository.findVersionById(
                    actor.systemId(), actor.tenantId(), target.kpiId(),
                    target.kpiVersionId()).orElse(null);
            if (definition != null) {
                result.add(new ApplicableTarget(target, definition,
                        repository.findLatestCalculation(actor.systemId(),
                                actor.tenantId(), target.id()).orElse(null)));
            }
        }
        return result.stream().sorted(Comparator.comparing(
                        (ApplicableTarget item) -> item.target().period()
                                .startInclusive()).reversed()
                .thenComparing(item -> item.definition().code())
                .thenComparingLong(item -> item.target().id())).toList();
    }

    public List<ApplicableTarget> applicableTargets(KpiActor actor) {
        return applicableTargets(actor, LocalDate.now(clock));
    }

    private Validation validate(KpiActor actor, KpiDefinition root) {
        var issues = new ArrayList<KpiCheckReport.Issue>();
        var source = sources.active(actor.systemId(), actor.tenantId(),
                        root.draft().dataSourceId())
                .filter(candidate -> candidate.systemId() == actor.systemId()
                        && candidate.tenantId() == actor.tenantId()
                        && candidate.dataSourceId()
                        == root.draft().dataSourceId())
                .orElse(null);
        if (source == null) {
            blocker(issues, "SOURCE_NOT_FOUND", "dataSourceId",
                    "Published KPI data source does not exist in this tenant");
            return new Validation(new KpiCheckReport(
                    root.id(), root.draftVersion(), issues), null);
        }
        var draft = root.draft();
        if (draft.measureFieldCode() != null) {
            var measure = field(source, draft.measureFieldCode());
            if (measure == null || !measure.readable()) {
                blocker(issues, "MEASURE_UNREADABLE", "measureFieldCode",
                        "KPI measure must be a readable published field");
            } else if (!measure.numeric()) {
                blocker(issues, "MEASURE_NOT_NUMERIC", "measureFieldCode",
                        "KPI measure must be numeric");
            } else if (measure.money()) {
                blocker(issues, "MEASURE_MONEY_UNSUPPORTED", "measureFieldCode",
                        "MONEY KPI measures require currency-aware aggregation");
            }
        }
        var time = field(source, draft.timeFieldCode());
        if (time == null || !time.readable()) {
            blocker(issues, "TIME_UNREADABLE", "timeFieldCode",
                    "KPI time field must be a readable published field");
        } else if (!time.temporal()) {
            blocker(issues, "TIME_NOT_TEMPORAL", "timeFieldCode",
                    "KPI time field must be temporal");
        }
        return new Validation(new KpiCheckReport(
                root.id(), root.draftVersion(), issues), source);
    }

    private static KpiSourcePin sourcePin(
            KpiDraft draft,
            KpiSourceCatalog.SourceVersion source
    ) {
        return new KpiSourcePin(source.dataSourceId(), source.dataSourceCode(),
                source.versionId(), source.versionNumber(), source.moduleCode(),
                source.schemaVersionId(), pin(field(source,
                draft.measureFieldCode())), pin(field(source,
                draft.timeFieldCode())));
    }

    private void requireExactSource(KpiActor actor, KpiVersion definition) {
        var expected = definition.source();
        var current = sources.version(actor.systemId(), actor.tenantId(),
                        expected.dataSourceId(), expected.dataSourceVersionId())
                .orElseThrow(() -> error("KPI_SOURCE_NOT_FOUND",
                        "Exact published KPI data source is unavailable"));
        if (current.systemId() != actor.systemId()
                || current.tenantId() != actor.tenantId()
                || !sourcePin(new KpiDraft(expected.dataSourceId(),
                        definition.subjectType(), definition.periodType(),
                        definition.aggregation(),
                        expected.measureField() == null ? null
                                : expected.measureField().code(),
                        expected.timeField().code(), definition.direction(),
                        definition.warningThreshold()), current).equals(expected)) {
            throw error("KPI_SOURCE_NOT_FOUND",
                    "Exact published KPI source pins are unavailable");
        }
    }

    private static void requireExactDefinition(
            KpiTarget target,
            KpiVersion definition
    ) {
        if (target.kpiId() != definition.kpiId()
                || target.kpiVersionId() != definition.id()
                || target.kpiVersionNumber() != definition.versionNumber()
                || target.subjectType() != definition.subjectType()
                || target.period().type() != definition.periodType()) {
            throw error("KPI_VERSION_NOT_FOUND",
                    "KPI target definition snapshot is inconsistent");
        }
    }

    private static void requireExactResult(
            KpiVersion definition,
            KpiPeriod period,
            KpiStatisticsExecutor.Result result
    ) {
        var source = definition.source();
        if (result == null || result.dataSourceId() != source.dataSourceId()
                || result.dataSourceVersionId() != source.dataSourceVersionId()
                || !result.moduleCode().equals(source.moduleCode())
                || !result.schemaVersionId().equals(source.schemaVersionId())
                || result.aggregation() != definition.aggregation()
                || !Objects.equals(result.measureField(), source.measureField())
                || !result.timeField().equals(source.timeField())
                || result.trend().size() != period.monthCount()) {
            throw error("KPI_STATISTICS_INVALID",
                    "Exact KPI statistics result does not match published pins");
        }
    }

    private KpiSubjectDirectory.SubjectResolution activeSubject(
            KpiActor actor,
            KpiSubjectType type,
            long id
    ) {
        if (id <= 0) {
            throw error("KPI_SUBJECT_NOT_FOUND",
                    "KPI subject does not exist");
        }
        return subjects.resolve(actor.systemId(), actor.tenantId(), type, id)
                .filter(subject -> subject.subjectType() == type
                        && subject.subjectId() == id && subject.active())
                .orElseThrow(() -> error("KPI_SUBJECT_NOT_FOUND",
                        "KPI subject does not exist"));
    }

    private static KpiSubjectSnapshot snapshot(
            KpiSubjectDirectory.SubjectResolution subject
    ) {
        return new KpiSubjectSnapshot(subject.subjectType(),
                subject.subjectId(), subject.displayName(),
                subject.subjectType() == KpiSubjectType.ROLE
                        ? subject.activeMemberIds() : List.of());
    }

    private static KpiSubjectSnapshot fallbackSubject(KpiTarget target) {
        return new KpiSubjectSnapshot(target.subjectType(), target.subjectId(),
                target.subjectDisplayName(), List.of());
    }

    private static KpiStatisticsExecutor.OwnershipRestriction ownership(
            KpiTarget target,
            KpiSubjectSnapshot subject
    ) {
        return switch (target.subjectType()) {
            case MEMBER -> KpiStatisticsExecutor.OwnershipRestriction.members(
                    List.of(target.subjectId()));
            case DEPARTMENT ->
                    KpiStatisticsExecutor.OwnershipRestriction.department(
                            target.subjectId());
            case ROLE -> KpiStatisticsExecutor.OwnershipRestriction.members(
                    subject.roleMemberIds());
        };
    }

    private static boolean applies(
            KpiActor actor,
            KpiTarget target,
            Set<Long> departments,
            Set<Long> roles
    ) {
        return switch (target.subjectType()) {
            case MEMBER -> target.subjectId() == actor.memberId();
            case DEPARTMENT -> departments.contains(target.subjectId());
            case ROLE -> roles.contains(target.subjectId());
        };
    }

    private KpiDefinition root(KpiActor actor, long kpiId) {
        if (kpiId <= 0) {
            throw notFound();
        }
        return repository.findById(actor.systemId(), actor.tenantId(), kpiId)
                .orElseThrow(KpiService::notFound);
    }

    private static void requireDraftVersion(
            KpiDefinition root,
            long expectedDraftVersion
    ) {
        if (expectedDraftVersion <= 0
                || root.draftVersion() != expectedDraftVersion) {
            throw error("KPI_VERSION_CONFLICT",
                    "KPI draft changed; refresh before retrying");
        }
    }

    private static KpiSourceCatalog.Field field(
            KpiSourceCatalog.SourceVersion source,
            String code
    ) {
        if (code == null) {
            return null;
        }
        return source.fields().stream()
                .filter(field -> field.code().equals(code))
                .findFirst().orElse(null);
    }

    private static KpiFieldPin pin(KpiSourceCatalog.Field field) {
        return field == null ? null : new KpiFieldPin(
                field.logicalFieldId(), field.code(), field.name(),
                field.type(), field.queryType());
    }

    private static Comparator<KpiTarget> targetOrder() {
        return Comparator.comparing((KpiTarget target) ->
                        target.period().startInclusive()).reversed()
                .thenComparing(target -> target.subjectType().name())
                .thenComparingLong(KpiTarget::subjectId)
                .thenComparingLong(KpiTarget::id);
    }

    private static String fingerprint(
            KpiDefinition root,
            KpiSourcePin source
    ) {
        var value = new StringBuilder("kpi-publish-v1|");
        append(value, Long.toString(root.systemId()));
        append(value, Long.toString(root.tenantId()));
        append(value, root.code());
        append(value, root.name());
        append(value, root.description());
        var draft = root.draft();
        append(value, draft.subjectType().name());
        append(value, draft.periodType().name());
        append(value, draft.aggregation().name());
        append(value, draft.direction().name());
        append(value, draft.warningThreshold());
        append(value, Long.toString(source.dataSourceId()));
        append(value, source.dataSourceCode());
        append(value, Long.toString(source.dataSourceVersionId()));
        append(value, Integer.toString(source.dataSourceVersionNumber()));
        append(value, source.moduleCode());
        append(value, source.schemaVersionId());
        appendField(value, source.measureField());
        appendField(value, source.timeField());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void appendField(StringBuilder value, KpiFieldPin field) {
        if (field == null) {
            append(value, null);
            return;
        }
        append(value, Long.toString(field.logicalFieldId()));
        append(value, field.code());
        append(value, field.name());
        append(value, field.type());
        append(value, field.queryType());
    }

    private static void append(StringBuilder value, String item) {
        if (item == null) {
            value.append("-1:");
        } else {
            value.append(item.length()).append(':').append(item);
        }
        value.append('|');
    }

    private static String stableError(RuntimeException failure) {
        var code = failure instanceof BusinessException business
                ? business.code() : null;
        if (code == null) {
            return "KPI_CALCULATION_FAILED";
        }
        if (code.contains("FORBIDDEN") || code.contains("SCOPE")
                || code.contains("GRANT")) {
            return "KPI_CALCULATION_FORBIDDEN";
        }
        if (code.contains("SUBJECT")) {
            return "KPI_SUBJECT_UNAVAILABLE";
        }
        if (code.contains("SOURCE") || code.contains("VERSION")
                || code.contains("FIELD")) {
            return "KPI_SOURCE_UNAVAILABLE";
        }
        if (code.contains("STATISTICS")) {
            return "KPI_STATISTICS_UNAVAILABLE";
        }
        return "KPI_CALCULATION_FAILED";
    }

    private static void blocker(
            List<KpiCheckReport.Issue> issues,
            String code,
            String path,
            String message
    ) {
        issues.add(new KpiCheckReport.Issue(BLOCKER, code, path, message));
    }

    private static void requireActor(KpiActor actor) {
        Objects.requireNonNull(actor, "actor");
    }

    private static KpiException notFound() {
        return error("KPI_NOT_FOUND", "KPI does not exist");
    }

    private static KpiException versionNotFound() {
        return error("KPI_VERSION_NOT_FOUND", "KPI version does not exist");
    }

    private static KpiException targetNotFound() {
        return error("KPI_TARGET_NOT_FOUND", "KPI target does not exist");
    }

    private static KpiException error(String code, String message) {
        return new KpiException(code, message);
    }

    public record ApplicableTarget(
            KpiTarget target,
            KpiVersion definition,
            KpiCalculation latestCalculation
    ) {
        public ApplicableTarget {
            if (target == null || definition == null
                    || target.kpiId() != definition.kpiId()
                    || target.kpiVersionId() != definition.id()) {
                throw new IllegalArgumentException(
                        "Applicable KPI target is inconsistent");
            }
        }
    }

    private record Validation(
            KpiCheckReport report,
            KpiSourceCatalog.SourceVersion source
    ) {
    }
}
