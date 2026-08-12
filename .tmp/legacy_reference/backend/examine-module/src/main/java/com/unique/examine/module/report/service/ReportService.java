package com.unique.examine.module.report.service;

import com.unique.examine.module.report.domain.PublishedReport;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.domain.ReportCheckReport;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import com.unique.examine.module.report.port.ReportRepository;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.unique.examine.module.report.domain.ReportCheckReport.Severity.BLOCKER;

@Transactional
public class ReportService {
    private final ReportRepository repository;
    private final ReportSourceCatalog sources;
    private final Clock clock;

    public ReportService(
            ReportRepository repository,
            ReportSourceCatalog sources,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReportDefinition create(
            ReportActor actor,
            String code,
            String name,
            String description,
            ReportDraft draft
    ) {
        requireActor(actor);
        var normalizedCode = ReportDefinition.code(code);
        if (repository.findByCode(actor.systemId(), actor.tenantId(),
                normalizedCode).isPresent()) {
            throw error("REPORT_CODE_CONFLICT",
                    "Report code already exists in this tenant");
        }
        return repository.insert(ReportDefinition.create(
                repository.nextReportId(), actor.systemId(), actor.tenantId(),
                normalizedCode, name, description, draft, clock.instant()));
    }

    public List<ReportDefinition> list(ReportActor actor) {
        requireActor(actor);
        return repository.findAll(actor.systemId(), actor.tenantId()).stream()
                .sorted(Comparator.comparing(
                                (ReportDefinition value) -> value.code())
                        .thenComparingLong(ReportDefinition::id))
                .toList();
    }

    public ReportDefinition detail(ReportActor actor, long reportId) {
        requireActor(actor);
        return root(actor, reportId);
    }

    public ReportDefinition revise(
            ReportActor actor,
            long reportId,
            long expectedDraftVersion,
            String name,
            String description,
            ReportDraft draft
    ) {
        requireActor(actor);
        var current = root(actor, reportId);
        requireDraftVersion(current, expectedDraftVersion);
        return repository.saveDraft(current, current.reviseDraft(
                name, description, draft, clock.instant()));
    }

    public ReportDefinition saveDraft(
            ReportActor actor,
            long reportId,
            long expectedDraftVersion,
            String name,
            String description,
            ReportDraft draft
    ) {
        return revise(actor, reportId, expectedDraftVersion,
                name, description, draft);
    }

    public ReportCheckReport check(ReportActor actor, long reportId) {
        requireActor(actor);
        return validate(actor, root(actor, reportId)).report();
    }

    public ReportVersion publish(
            ReportActor actor,
            long reportId,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, reportId);
        requireDraftVersion(current, expectedDraftVersion);
        var validation = validate(actor, current);
        if (!validation.report().publishable()) {
            throw error("REPORT_CHECK_BLOCKED",
                    "Report draft contains publish blockers");
        }
        var source = Objects.requireNonNull(validation.source());
        var sourcePin = sourcePin(source, validation.fields());
        var fingerprint = fingerprint(current, sourcePin);
        var active = repository.findActiveVersion(
                actor.systemId(), actor.tenantId(), current.id());
        if (current.activeVersionId() != null && active.isEmpty()) {
            throw error("REPORT_VERSION_NOT_FOUND",
                    "Active report version does not exist");
        }
        if (active.isPresent()
                && active.get().fingerprint().equals(fingerprint)) {
            return active.get();
        }

        var publishedAt = clock.instant();
        var version = new ReportVersion(
                repository.nextVersionId(), current.id(), current.systemId(),
                current.tenantId(),
                active.map(value -> value.versionNumber() + 1).orElse(1),
                current.draftVersion(), current.code(), current.name(),
                current.description(), sourcePin, fingerprint,
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

    public List<ReportVersion> versions(
            ReportActor actor,
            long reportId
    ) {
        requireActor(actor);
        root(actor, reportId);
        return repository.findVersions(
                        actor.systemId(), actor.tenantId(), reportId).stream()
                .sorted(Comparator.comparingInt(
                        ReportVersion::versionNumber).reversed())
                .toList();
    }

    public ReportVersion version(
            ReportActor actor,
            long reportId,
            int versionNumber
    ) {
        requireActor(actor);
        root(actor, reportId);
        return repository.findVersion(actor.systemId(), actor.tenantId(),
                        reportId, versionNumber)
                .orElseThrow(ReportService::versionNotFound);
    }

    /** Restores a publication as a new draft without changing runtime state. */
    public ReportDefinition restoreVersion(
            ReportActor actor,
            long reportId,
            int versionNumber,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, reportId);
        requireDraftVersion(current, expectedDraftVersion);
        var source = version(actor, reportId, versionNumber);
        var restoredDraft = new ReportDraft(
                source.source().dataSourceId(),
                source.source().fields().stream()
                        .map(ReportFieldPin::code)
                        .toList());
        return repository.saveDraft(current, current.reviseDraft(
                source.name(), source.description(), restoredDraft,
                clock.instant()));
    }

    public PublishedReport active(ReportActor actor, String code) {
        requireActor(actor);
        var normalizedCode = ReportDefinition.code(code);
        var root = repository.findByCode(
                        actor.systemId(), actor.tenantId(), normalizedCode)
                .orElseThrow(ReportService::notFound);
        return active(actor, root);
    }

    public PublishedReport active(ReportActor actor, long reportId) {
        requireActor(actor);
        return active(actor, root(actor, reportId));
    }

    public PublishedReport publication(
            ReportActor actor,
            long reportId,
            long versionId
    ) {
        requireActor(actor);
        var root = root(actor, reportId);
        if (versionId <= 0) {
            throw versionNotFound();
        }
        var version = repository.findVersionById(actor.systemId(),
                        actor.tenantId(), reportId, versionId)
                .orElseThrow(ReportService::versionNotFound);
        return new PublishedReport(root, version);
    }

    private PublishedReport active(
            ReportActor actor,
            ReportDefinition root
    ) {
        var version = repository.findActiveVersion(actor.systemId(),
                        actor.tenantId(), root.id())
                .orElseThrow(() -> error("REPORT_UNPUBLISHED",
                        "Report has no active published version"));
        return PublishedReport.active(root, version);
    }

    private Validation validate(
            ReportActor actor,
            ReportDefinition root
    ) {
        var issues = new ArrayList<ReportCheckReport.Issue>();
        var draft = root.draft();
        var source = sources.active(actor.systemId(), actor.tenantId(),
                        draft.dataSourceId())
                .filter(candidate -> exactScope(actor, draft, candidate))
                .orElse(null);
        if (source == null) {
            blocker(issues, "SOURCE_UNAVAILABLE", "dataSourceId",
                    "The selected native data source is not published");
        }
        if (draft.outputFieldCodes().isEmpty()) {
            blocker(issues, "OUTPUT_FIELDS_REQUIRED", "outputFieldCodes",
                    "At least one output field must be selected");
        }

        Map<String, ReportSourceCatalog.Field> available = source == null
                ? Map.of() : fieldsByCode(source);
        Set<String> selected = new HashSet<>();
        var pins = new ArrayList<ReportFieldPin>();
        for (int index = 0;
             index < draft.outputFieldCodes().size(); index++) {
            var code = draft.outputFieldCodes().get(index);
            var path = "outputFieldCodes[" + index + "]";
            if (!selected.add(code)) {
                blocker(issues, "OUTPUT_FIELD_DUPLICATE", path,
                        "Output field is selected more than once");
                continue;
            }
            if (source == null) {
                continue;
            }
            var field = available.get(code);
            if (field == null) {
                blocker(issues, "OUTPUT_FIELD_NOT_FOUND", path,
                        "Field is not an output of the published data source");
                continue;
            }
            if (!field.readable()) {
                blocker(issues, "OUTPUT_FIELD_UNAVAILABLE", path,
                        "Output field is unavailable to report publication");
                continue;
            }
            pins.add(pin(field));
        }

        return new Validation(new ReportCheckReport(
                root.id(), root.draftVersion(), capability(source), issues),
                source, List.copyOf(pins));
    }

    private static boolean exactScope(
            ReportActor actor,
            ReportDraft draft,
            ReportSourceCatalog.SourceVersion source
    ) {
        return source.systemId() == actor.systemId()
                && source.tenantId() == actor.tenantId()
                && source.dataSourceId() == draft.dataSourceId();
    }

    private static Map<String, ReportSourceCatalog.Field> fieldsByCode(
            ReportSourceCatalog.SourceVersion source
    ) {
        var result = new HashMap<String, ReportSourceCatalog.Field>();
        source.fields().forEach(field -> result.put(field.code(), field));
        return result;
    }

    private static ReportCheckReport.SourceCapability capability(
            ReportSourceCatalog.SourceVersion source
    ) {
        if (source == null) {
            return null;
        }
        return new ReportCheckReport.SourceCapability(
                source.dataSourceId(), source.dataSourceCode(),
                source.dataSourceName(), source.dataSourceVersionId(),
                source.dataSourceVersionNumber(), source.moduleId(),
                source.moduleCode(), source.schemaVersionId(),
                source.fields().stream().map(field ->
                        new ReportCheckReport.FieldCapability(
                                Long.toString(field.logicalFieldId()),
                                field.code(), field.name(), field.type(),
                                field.queryType(), field.readable()))
                        .toList());
    }

    private static ReportSourcePin sourcePin(
            ReportSourceCatalog.SourceVersion source,
            List<ReportFieldPin> fields
    ) {
        return new ReportSourcePin(
                source.dataSourceId(), source.dataSourceCode(),
                source.dataSourceName(), source.dataSourceVersionId(),
                source.dataSourceVersionNumber(), source.moduleId(),
                source.moduleCode(), source.schemaVersionId(), fields);
    }

    private static ReportFieldPin pin(ReportSourceCatalog.Field field) {
        return new ReportFieldPin(field.logicalFieldId(), field.code(),
                field.name(), field.type(), field.queryType());
    }

    private static String fingerprint(
            ReportDefinition root,
            ReportSourcePin source
    ) {
        var value = new StringBuilder("report-publish-v1|");
        append(value, Long.toString(root.systemId()));
        append(value, Long.toString(root.tenantId()));
        append(value, root.code());
        append(value, root.name());
        append(value, root.description());
        append(value, Long.toString(source.dataSourceId()));
        append(value, source.dataSourceCode());
        append(value, source.dataSourceName());
        append(value, Long.toString(source.dataSourceVersionId()));
        append(value, Integer.toString(source.dataSourceVersionNumber()));
        append(value, Long.toString(source.moduleId()));
        append(value, source.moduleCode());
        append(value, source.schemaVersionId());
        append(value, Integer.toString(source.fields().size()));
        source.fields().forEach(field -> {
            append(value, Long.toString(field.logicalFieldId()));
            append(value, field.code());
            append(value, field.name());
            append(value, field.type());
            append(value, field.queryType());
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append('|');
    }

    private ReportDefinition root(ReportActor actor, long reportId) {
        if (reportId <= 0) {
            throw notFound();
        }
        return repository.findById(actor.systemId(), actor.tenantId(), reportId)
                .orElseThrow(ReportService::notFound);
    }

    private static void requireDraftVersion(
            ReportDefinition root,
            long expectedDraftVersion
    ) {
        if (expectedDraftVersion <= 0
                || root.draftVersion() != expectedDraftVersion) {
            throw error("REPORT_VERSION_CONFLICT",
                    "Report draft changed; refresh before retrying");
        }
    }

    private static void requireActor(ReportActor actor) {
        Objects.requireNonNull(actor, "actor");
    }

    private static ReportException notFound() {
        return error("REPORT_NOT_FOUND", "Report does not exist");
    }

    private static ReportException versionNotFound() {
        return error("REPORT_VERSION_NOT_FOUND",
                "Report version does not exist");
    }

    private static ReportException error(String code, String message) {
        return new ReportException(code, message);
    }

    private static void blocker(
            List<ReportCheckReport.Issue> issues,
            String code,
            String path,
            String message
    ) {
        issues.add(new ReportCheckReport.Issue(
                BLOCKER, code, path, message));
    }

    private record Validation(
            ReportCheckReport report,
            ReportSourceCatalog.SourceVersion source,
            List<ReportFieldPin> fields
    ) {
    }
}
