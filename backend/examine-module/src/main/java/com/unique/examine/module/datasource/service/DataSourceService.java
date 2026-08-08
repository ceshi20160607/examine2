package com.unique.examine.module.datasource.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceCheckReport;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.domain.PublishedDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.unique.examine.module.datasource.domain.DataSourceCheckReport.Severity.BLOCKER;
import static com.unique.examine.module.datasource.domain.DataSourceCheckReport.Severity.WARNING;

public final class DataSourceService
        implements ExactDataSourcePublicationResolver {
    private final DataSourceRepository repository;
    private final DataSourceModuleCatalog moduleCatalog;
    private final Clock clock;
    private final DataSourcePublicationPreflight publicationPreflight;
    private final JdbcTableDataSourcePublicationPreflight
            jdbcPublicationPreflight;

    public DataSourceService(
            DataSourceRepository repository,
            DataSourceModuleCatalog moduleCatalog,
            Clock clock
    ) {
        this(repository, moduleCatalog, clock,
                unavailablePublicationPreflight(),
                unavailableJdbcPublicationPreflight());
    }

    public DataSourceService(
            DataSourceRepository repository,
            DataSourceModuleCatalog moduleCatalog,
            Clock clock,
            DataSourcePublicationPreflight publicationPreflight
    ) {
        this(repository, moduleCatalog, clock, publicationPreflight,
                unavailableJdbcPublicationPreflight());
    }

    public DataSourceService(
            DataSourceRepository repository,
            DataSourceModuleCatalog moduleCatalog,
            Clock clock,
            DataSourcePublicationPreflight publicationPreflight,
            JdbcTableDataSourcePublicationPreflight jdbcPublicationPreflight
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.moduleCatalog = Objects.requireNonNull(
                moduleCatalog, "moduleCatalog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.publicationPreflight = Objects.requireNonNull(
                publicationPreflight, "publicationPreflight");
        this.jdbcPublicationPreflight = Objects.requireNonNull(
                jdbcPublicationPreflight, "jdbcPublicationPreflight");
    }

    public ModuleDataSource create(
            DataSourceActor actor,
            String code,
            long moduleId,
            String name,
            String description,
            DataSourceDraft draft
    ) {
        requireActor(actor);
        var normalizedCode = ModuleDataSource.code(code);
        if (repository.findByCode(
                actor.systemId(), actor.tenantId(), normalizedCode).isPresent()) {
            throw error("DATA_SOURCE_CODE_CONFLICT",
                    "Data source code already exists in this tenant");
        }
        var root = ModuleDataSource.create(
                repository.nextDataSourceId(), actor.systemId(),
                actor.tenantId(), normalizedCode, moduleId, name,
                description, draft, clock.instant());
        return repository.insert(root);
    }

    public List<ModuleDataSource> list(DataSourceActor actor) {
        requireActor(actor);
        return repository.findAll(actor.systemId(), actor.tenantId())
                .stream()
                .sorted(Comparator.comparing(
                                (ModuleDataSource value) -> value.code())
                        .thenComparingLong(ModuleDataSource::id))
                .toList();
    }

    public List<DataSourceModuleCatalog.PublishedModule> modules(
            DataSourceActor actor
    ) {
        requireActor(actor);
        return moduleCatalog.publishedModules(
                        actor.systemId(), actor.tenantId())
                .stream()
                .sorted(Comparator.comparing(
                                DataSourceModuleCatalog.PublishedModule::moduleCode)
                        .thenComparingLong(
                                DataSourceModuleCatalog.PublishedModule::moduleId))
                .toList();
    }

    public ModuleDataSource detail(
            DataSourceActor actor,
            long dataSourceId
    ) {
        requireActor(actor);
        return root(actor, dataSourceId);
    }

    public ModuleDataSource revise(
            DataSourceActor actor,
            long dataSourceId,
            long expectedDraftVersion,
            String name,
            String description,
            DataSourceDraft draft
    ) {
        requireActor(actor);
        var current = root(actor, dataSourceId);
        requireDraftVersion(current, expectedDraftVersion);
        var revised = current.reviseDraft(
                name, description, draft, clock.instant());
        return repository.saveDraft(current, revised);
    }

    /** Alias retained for callers that name the operation after persistence. */
    public ModuleDataSource saveDraft(
            DataSourceActor actor,
            long dataSourceId,
            long expectedDraftVersion,
            String name,
            String description,
            DataSourceDraft draft
    ) {
        return revise(actor, dataSourceId, expectedDraftVersion,
                name, description, draft);
    }

    /** Read-only validation against the module's current published schema. */
    public DataSourceCheckReport check(
            DataSourceActor actor,
            long dataSourceId
    ) {
        requireActor(actor);
        return validate(actor, root(actor, dataSourceId)).report();
    }

    public DataSourceVersion publish(
            DataSourceActor actor,
            long dataSourceId,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, dataSourceId);
        requireDraftVersion(current, expectedDraftVersion);

        var validation = validate(actor, current);
        var kind = current.draft().sourceKind();
        var http = kind == DataSourceDraft.SourceKind.HTTP_JSON;
        var jdbc = kind == DataSourceDraft.SourceKind.JDBC_TABLE;
        if (hasPublicationBlocker(validation.report(), http)) {
            throw error("DATA_SOURCE_CHECK_BLOCKED",
                    "Data source draft contains publish blockers");
        }
        var module = Objects.requireNonNull(validation.module());
        var normalizedDraft = Objects.requireNonNull(
                validation.normalizedDraft());
        if (http && containsRichTextProjection(normalizedDraft, module)) {
            throw error("DATA_SOURCE_HTTP_RICH_TEXT_BLOCKED",
                    "HTTP string projections cannot be published to rich text fields");
        }
        if (jdbc && containsJdbcRichTextProjection(
                normalizedDraft, module)) {
            throw error("DATA_SOURCE_JDBC_RICH_TEXT_BLOCKED",
                    "JDBC string projections cannot be published to rich text fields");
        }
        var fingerprint = fingerprint(current, module, normalizedDraft);

        var active = repository.findActiveVersion(
                actor.systemId(), actor.tenantId(), current.id());
        if (current.activeVersionId() != null && active.isEmpty()) {
            throw error("DATA_SOURCE_VERSION_NOT_FOUND",
                    "Active data source version does not exist");
        }
        if (active.isPresent()
                && active.get().fingerprint().equals(fingerprint)) {
            return active.get();
        }

        if (http) {
            publicationPreflight.verify(actor, normalizedDraft);
        } else if (jdbc) {
            jdbcPublicationPreflight.verify(actor, normalizedDraft);
        }

        var publishedAt = clock.instant();
        var version = new DataSourceVersion(
                repository.nextVersionId(), current.id(),
                current.systemId(), current.tenantId(),
                active.map(value -> value.versionNumber() + 1).orElse(1),
                current.code(), current.moduleId(), module.moduleCode(),
                module.schemaVersionId(), current.name(),
                current.description(), normalizedDraft, fingerprint,
                actor.memberId(), publishedAt);
        var activated = current.activate(version, publishedAt);
        return repository.publish(current, activated, version);
    }

    public List<DataSourceVersion> versions(
            DataSourceActor actor,
            long dataSourceId
    ) {
        requireActor(actor);
        root(actor, dataSourceId);
        return repository.findVersions(
                        actor.systemId(), actor.tenantId(), dataSourceId)
                .stream()
                .sorted(Comparator.comparingInt(
                        DataSourceVersion::versionNumber).reversed())
                .toList();
    }

    public DataSourceVersion version(
            DataSourceActor actor,
            long dataSourceId,
            int versionNumber
    ) {
        requireActor(actor);
        root(actor, dataSourceId);
        return repository.findVersion(
                        actor.systemId(), actor.tenantId(), dataSourceId,
                        versionNumber)
                .orElseThrow(() -> error(
                        "DATA_SOURCE_VERSION_NOT_FOUND",
                        "Data source version does not exist"));
    }

    /**
     * Copies an immutable publication back into the editable draft. The
     * historical publication and active runtime pointer are deliberately left
     * untouched; callers must validate and publish the new draft explicitly.
     */
    public ModuleDataSource restoreVersion(
            DataSourceActor actor,
            long dataSourceId,
            int versionNumber,
            long expectedDraftVersion
    ) {
        requireActor(actor);
        var current = root(actor, dataSourceId);
        requireDraftVersion(current, expectedDraftVersion);
        var source = version(actor, dataSourceId, versionNumber);
        var restored = current.reviseDraft(
                source.name(), source.description(), source.snapshot(),
                clock.instant());
        return repository.saveDraft(current, restored);
    }

    @Override
    public DataSourcePublication publication(
            DataSourceActor actor,
            long dataSourceId,
            long versionId
    ) {
        requireActor(actor);
        var root = root(actor, dataSourceId);
        if (versionId <= 0) {
            throw error("DATA_SOURCE_VERSION_NOT_FOUND",
                    "Data source version does not exist");
        }
        var version = repository.findVersionById(
                        actor.systemId(), actor.tenantId(), dataSourceId,
                        versionId)
                .orElseThrow(() -> error(
                        "DATA_SOURCE_VERSION_NOT_FOUND",
                        "Data source version does not exist"));
        return new DataSourcePublication(root, version);
    }

    public PublishedDataSource active(
            DataSourceActor actor,
            String code
    ) {
        requireActor(actor);
        var normalizedCode = ModuleDataSource.code(code);
        var root = repository.findByCode(
                        actor.systemId(), actor.tenantId(), normalizedCode)
                .orElseThrow(DataSourceService::notFound);
        var version = repository.findActiveVersion(
                        actor.systemId(), actor.tenantId(), root.id())
                .orElseThrow(() -> error(
                        "DATA_SOURCE_UNPUBLISHED",
                        "Data source has no active published version"));
        return new PublishedDataSource(root, version);
    }

    public PublishedDataSource active(
            DataSourceActor actor,
            long dataSourceId
    ) {
        requireActor(actor);
        var root = root(actor, dataSourceId);
        var version = repository.findActiveVersion(
                        actor.systemId(), actor.tenantId(), root.id())
                .orElseThrow(() -> error(
                        "DATA_SOURCE_UNPUBLISHED",
                        "Data source has no active published version"));
        return new PublishedDataSource(root, version);
    }

    private Validation validate(
            DataSourceActor actor,
            ModuleDataSource root
    ) {
        var issues = new ArrayList<DataSourceCheckReport.Issue>();
        var module = moduleCatalog.publishedModule(
                actor.systemId(), actor.tenantId(), root.moduleId())
                .orElse(null);
        var draft = root.draft();

        if (draft.sourceKind() == DataSourceDraft.SourceKind.HTTP_JSON) {
            blocker(issues, "SOURCE_RUNTIME_UNAVAILABLE", "sourceKind",
                    "HTTP JSON data sources cannot be published until their "
                            + "runtime is available");
        }
        if (module == null) {
            blocker(issues, "MODULE_UNAVAILABLE", "moduleId",
                    "The bound module has no published schema");
        }
        if (draft.sourceKind() == DataSourceDraft.SourceKind.HTTP_JSON) {
            validateHttpDraft(draft, module, issues);
            var report = new DataSourceCheckReport(
                    root.id(), root.draftVersion(),
                    module == null ? null : module.schemaVersionId(), issues);
            return new Validation(
                    report, module, module == null ? null : draft);
        }
        if (draft.sourceKind() == DataSourceDraft.SourceKind.JDBC_TABLE) {
            validateJdbcDraft(draft, module, issues);
            var report = new DataSourceCheckReport(
                    root.id(), root.draftVersion(),
                    module == null ? null : module.schemaVersionId(), issues);
            return new Validation(
                    report, module, module == null ? null : draft);
        }
        if (draft.sourceKind()
                == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
            var normalized = validateMultiModuleJoin(
                    actor, root, module, draft, issues);
            var report = new DataSourceCheckReport(
                    root.id(), root.draftVersion(),
                    module == null ? null : module.schemaVersionId(), issues);
            return new Validation(report, module,
                    module == null ? null : normalized);
        }
        if (draft.outputFields().isEmpty()) {
            blocker(issues, "OUTPUT_FIELDS_REQUIRED", "outputFields",
                    "At least one output field must be selected");
        }

        Map<String, DataSourceModuleCatalog.FieldCapability> fields =
                module == null ? Map.of() : fieldsByCode(module);
        Set<String> selected = new HashSet<>();
        for (int index = 0; index < draft.outputFields().size(); index++) {
            var fieldCode = draft.outputFields().get(index).fieldCode();
            var path = "outputFields[" + index + "].fieldCode";
            if (!selected.add(fieldCode)) {
                blocker(issues, "OUTPUT_FIELD_DUPLICATE", path,
                        "Output field is selected more than once");
                continue;
            }
            if (module != null) {
                var field = fields.get(fieldCode);
                if (field == null) {
                    blocker(issues, "OUTPUT_FIELD_NOT_FOUND", path,
                            "Output field is absent from the published schema");
                } else if (!field.available()) {
                    blocker(issues, "OUTPUT_FIELD_UNAVAILABLE", path,
                            "Output field is unavailable or deferred");
                }
            }
        }

        var normalizedFilters = new ArrayList<DataSourceDraft.FixedFilter>();
        for (int index = 0; index < draft.fixedFilters().size(); index++) {
            var filter = draft.fixedFilters().get(index);
            var path = "fixedFilters[" + index + "]";
            var field = fields.get(filter.fieldCode());
            if (module == null) {
                normalizedFilters.add(filter);
                continue;
            }
            if (field == null) {
                blocker(issues, "FILTER_FIELD_NOT_FOUND", path + ".fieldCode",
                        "Filter field is absent from the published schema");
                normalizedFilters.add(filter);
                continue;
            }
            if (!field.available()) {
                blocker(issues, "FILTER_FIELD_UNAVAILABLE", path + ".fieldCode",
                        "Filter field is unavailable or deferred");
                normalizedFilters.add(filter);
                continue;
            }
            if (!field.operators().contains(filter.operator())) {
                blocker(issues, "FILTER_OPERATOR_UNSUPPORTED", path + ".operator",
                        "Filter operator is not supported by the native field");
                normalizedFilters.add(filter);
                continue;
            }
            var canonical = moduleCatalog.canonicalizeFilterValue(
                    actor.systemId(), actor.tenantId(), root.moduleId(),
                    field, filter.operator(), filter.canonicalValue());
            if (canonical == null || !canonical.valid()) {
                blocker(issues, "FILTER_VALUE_INVALID", path + ".canonicalValue",
                        canonical == null
                                ? "Filter value cannot be normalized"
                                : canonical.errorMessage());
                normalizedFilters.add(filter);
                continue;
            }
            try {
                normalizedFilters.add(new DataSourceDraft.FixedFilter(
                        filter.fieldCode(), filter.operator(),
                        canonical.canonicalValue()));
            } catch (RuntimeException invalidCanonicalValue) {
                blocker(issues, "FILTER_VALUE_INVALID", path + ".canonicalValue",
                        "Filter value cannot be normalized");
                normalizedFilters.add(filter);
            }
        }

        validateSort(draft, module, fields, selected, issues);
        validateTimeField(draft, module, fields, selected, issues);

        var report = new DataSourceCheckReport(
                root.id(), root.draftVersion(),
                module == null ? null : module.schemaVersionId(), issues);
        var normalized = module == null ? null : new DataSourceDraft(
                draft.outputFields(), normalizedFilters,
                draft.defaultSort(), draft.defaultTimeFieldCode(),
                draft.sourceKind(), draft.httpConnection(),
                draft.httpFieldProjections(), draft.jdbcTableConnection(),
                draft.jdbcFieldProjections(), null);
        return new Validation(report, module, normalized);
    }

    private DataSourceDraft validateMultiModuleJoin(
            DataSourceActor actor,
            ModuleDataSource root,
            DataSourceModuleCatalog.PublishedModule resultModule,
            DataSourceDraft draft,
            List<DataSourceCheckReport.Issue> issues
    ) {
        var join = draft.multiModuleJoin();
        if (join == null) {
            blocker(issues, "JOIN_PLAN_REQUIRED", "multiModuleJoin",
                    "A multi-module join plan is required");
            return draft;
        }
        var capabilitiesByAlias = new LinkedHashMap<String,
                Map<String, DataSourceModuleCatalog.FieldCapability>>();
        var modulesByAlias = new LinkedHashMap<String,
                DataSourceModuleCatalog.PublishedModule>();
        for (int index = 0; index < join.inputs().size(); index++) {
            var input = join.inputs().get(index);
            var path = "multiModuleJoin.inputs[" + index + "]";
            if (input.dataSourceId() == root.id()) {
                blocker(issues, "JOIN_SOURCE_SELF_REFERENCE", path,
                        "A join source cannot reference itself");
                continue;
            }
            var sourceRoot = repository.findById(
                    actor.systemId(), actor.tenantId(), input.dataSourceId())
                    .orElse(null);
            var version = repository.findVersionById(
                    actor.systemId(), actor.tenantId(), input.dataSourceId(),
                    input.dataSourceVersionId()).orElse(null);
            if (sourceRoot == null || version == null
                    || version.dataSourceId() != input.dataSourceId()) {
                blocker(issues, "JOIN_SOURCE_VERSION_NOT_FOUND", path,
                        "The exact joined data-source publication does not exist");
                continue;
            }
            if (version.snapshot().sourceKind()
                    == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
                blocker(issues, "JOIN_SOURCE_NESTED_UNSUPPORTED", path,
                        "Nested multi-module join sources are not supported");
                continue;
            }
            var sourceModule = moduleCatalog.publishedModule(
                    actor.systemId(), actor.tenantId(), version.moduleId())
                    .orElse(null);
            if (sourceModule == null || !sourceModule.schemaVersionId()
                    .equals(version.schemaVersionId())) {
                blocker(issues, "JOIN_SOURCE_SCHEMA_UNAVAILABLE", path,
                        "The exact joined source schema is unavailable");
                continue;
            }
            if (index == 0 && (resultModule == null
                    || resultModule.moduleId() != sourceModule.moduleId()
                    || !resultModule.schemaVersionId()
                    .equals(sourceModule.schemaVersionId()))) {
                blocker(issues, "JOIN_ANCHOR_MODULE_MISMATCH", path,
                        "The first join input must match the bound anchor module");
            }
            var sourceFields = new LinkedHashMap<String,
                    DataSourceModuleCatalog.FieldCapability>();
            var available = fieldsByCode(sourceModule);
            for (var code : sourceFieldCodes(version.snapshot())) {
                var field = available.get(code);
                if (field != null && field.available()) {
                    sourceFields.put(code, field);
                }
            }
            capabilitiesByAlias.put(input.alias(), Map.copyOf(sourceFields));
            modulesByAlias.put(input.alias(), sourceModule);
        }
        if (modulesByAlias.values().stream()
                .map(DataSourceModuleCatalog.PublishedModule::moduleId)
                .distinct().count() < 2) {
            blocker(issues, "JOIN_MODULES_REQUIRED", "multiModuleJoin.inputs",
                    "A multi-module join must reference at least two modules");
        }

        for (int index = 0; index < join.edges().size(); index++) {
            var edge = join.edges().get(index);
            var path = "multiModuleJoin.edges[" + index + "]";
            var left = capabilitiesByAlias.getOrDefault(
                    edge.leftAlias(), Map.of()).get(edge.leftFieldCode());
            var right = capabilitiesByAlias.getOrDefault(
                    edge.rightAlias(), Map.of()).get(edge.rightFieldCode());
            if (left == null || right == null) {
                blocker(issues, "JOIN_KEY_UNAVAILABLE", path,
                        "A join key is absent or unreadable in its exact publication");
            } else if (!compatibleJoinTypes(
                    left.queryType(), right.queryType())) {
                blocker(issues, "JOIN_KEY_TYPE_MISMATCH", path,
                        "Join key query types are incompatible");
            }
        }

        var pinned = new ArrayList<DataSourceDraft.JoinProjection>();
        var selected = new HashSet<String>();
        var logicalIds = new HashSet<Long>();
        for (int index = 0; index < join.projections().size(); index++) {
            var projection = join.projections().get(index);
            var path = "multiModuleJoin.projections[" + index + "]";
            var identity = projection.sourceAlias() + "\u0000"
                    + projection.sourceFieldCode();
            var field = capabilitiesByAlias.getOrDefault(
                            projection.sourceAlias(), Map.of())
                    .get(projection.sourceFieldCode());
            if (!selected.add(identity)) {
                blocker(issues, "JOIN_PROJECTION_DUPLICATE", path,
                        "A joined source field is projected more than once");
            }
            if (field == null) {
                blocker(issues, "JOIN_PROJECTION_UNAVAILABLE", path,
                        "A joined projection is absent or unreadable");
                pinned.add(projection);
                continue;
            }
            if (!logicalIds.add(field.logicalFieldId())) {
                blocker(issues, "JOIN_LOGICAL_FIELD_COLLISION", path,
                        "Joined projection logical identities collide");
            }
            var queryType = field.queryType();
            pinned.add(new DataSourceDraft.JoinProjection(
                    projection.sourceAlias(), projection.sourceFieldCode(),
                    projection.fieldCode(), field.logicalFieldId(),
                    field.fieldName(), field.type(), queryType,
                    NUMERIC_JOIN_TYPES.contains(queryType),
                    TEMPORAL_JOIN_TYPES.contains(queryType),
                    GROUPABLE_JOIN_TYPES.contains(queryType)));
        }
        var normalizedJoin = new DataSourceDraft.MultiModuleJoin(
                join.inputs(), join.edges(), pinned, join.failureMode(),
                join.timeoutSeconds(), join.rowLimit());
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.MULTI_MODULE_JOIN,
                null, List.of(), null, List.of(), normalizedJoin);
    }

    private static final Set<String> NUMERIC_JOIN_TYPES = Set.of(
            "NUMBER", "PERCENT", "MONEY", "RATING", "PROGRESS");
    private static final Set<String> TEMPORAL_JOIN_TYPES = Set.of(
            "DATE", "DATETIME");
    private static final Set<String> GROUPABLE_JOIN_TYPES = Set.of(
            "TEXT", "NUMBER", "DATE", "DATETIME", "RADIO", "MEMBER",
            "DEPARTMENT", "PERCENT", "MONEY", "TIME", "SWITCH",
            "RATING", "PROGRESS", "STATUS");

    private static boolean compatibleJoinTypes(String left, String right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        return NUMERIC_JOIN_TYPES.contains(left)
                && NUMERIC_JOIN_TYPES.contains(right);
    }

    private static List<String> sourceFieldCodes(DataSourceDraft snapshot) {
        return switch (snapshot.sourceKind()) {
            case NATIVE_MODULE -> snapshot.outputFields().stream()
                    .map(DataSourceDraft.OutputField::fieldCode).toList();
            case HTTP_JSON -> snapshot.httpFieldProjections().stream()
                    .map(DataSourceDraft.HttpJsonFieldProjection::fieldCode)
                    .toList();
            case JDBC_TABLE -> snapshot.jdbcFieldProjections().stream()
                    .map(DataSourceDraft.JdbcTableFieldProjection::fieldCode)
                    .toList();
            case MULTI_MODULE_JOIN -> snapshot.multiModuleJoin().projections()
                    .stream().map(DataSourceDraft.JoinProjection::fieldCode)
                    .toList();
        };
    }

    private static boolean hasPublicationBlocker(
            DataSourceCheckReport report,
            boolean http
    ) {
        return report.issues().stream().anyMatch(issue ->
                issue.severity() == BLOCKER
                        && !(http && "SOURCE_RUNTIME_UNAVAILABLE"
                        .equals(issue.code())));
    }

    private static boolean containsRichTextProjection(
            DataSourceDraft draft,
            DataSourceModuleCatalog.PublishedModule module
    ) {
        var fields = fieldsByCode(module);
        return draft.httpFieldProjections().stream().anyMatch(projection ->
                projection.sourceType()
                        == DataSourceDraft.HttpJsonSourceType.STRING
                        && "RICH_TEXT".equals(
                        fields.get(projection.fieldCode()).type()));
    }

    private static boolean containsJdbcRichTextProjection(
            DataSourceDraft draft,
            DataSourceModuleCatalog.PublishedModule module
    ) {
        var fields = fieldsByCode(module);
        return draft.jdbcFieldProjections().stream().anyMatch(projection ->
                projection.sourceType()
                        == DataSourceDraft.JdbcTableSourceType.STRING
                        && "RICH_TEXT".equals(
                        fields.get(projection.fieldCode()).type()));
    }

    private static void validateHttpDraft(
            DataSourceDraft draft,
            DataSourceModuleCatalog.PublishedModule module,
            List<DataSourceCheckReport.Issue> issues
    ) {
        if (!draft.outputFields().isEmpty()) {
            blocker(issues, "HTTP_NATIVE_OUTPUT_FIELDS_FORBIDDEN",
                    "outputFields",
                    "HTTP JSON drafts cannot use Native output fields");
        }
        if (!draft.fixedFilters().isEmpty()) {
            blocker(issues, "HTTP_NATIVE_FILTERS_FORBIDDEN", "fixedFilters",
                    "HTTP JSON drafts cannot use Native fixed filters");
        }
        if (draft.defaultSort() != null) {
            blocker(issues, "HTTP_NATIVE_SORT_FORBIDDEN", "defaultSort",
                    "HTTP JSON drafts cannot use a Native default sort");
        }
        if (draft.defaultTimeFieldCode() != null) {
            blocker(issues, "HTTP_NATIVE_TIME_FIELD_FORBIDDEN",
                    "defaultTimeFieldCode",
                    "HTTP JSON drafts cannot use a Native default time field");
        }
        if (draft.httpFieldProjections().isEmpty()) {
            blocker(issues, "HTTP_PROJECTION_REQUIRED",
                    "httpFieldProjections",
                    "At least one HTTP field projection is required");
            return;
        }
        if (module == null) {
            return;
        }
        var fields = fieldsByCode(module);
        for (int index = 0;
             index < draft.httpFieldProjections().size(); index++) {
            var projection = draft.httpFieldProjections().get(index);
            var path = "httpFieldProjections[" + index + "]";
            var field = fields.get(projection.fieldCode());
            if (field == null) {
                blocker(issues, "HTTP_PROJECTION_FIELD_NOT_FOUND",
                        path + ".fieldCode",
                        "Projection target is absent from the anchor schema");
            } else if (!field.available()) {
                blocker(issues, "HTTP_PROJECTION_FIELD_UNAVAILABLE",
                        path + ".fieldCode",
                        "Projection target is unavailable or deferred");
            } else if (!compatible(projection.sourceType(), field.type())) {
                blocker(issues, "HTTP_PROJECTION_TYPE_INCOMPATIBLE",
                        path + ".sourceType",
                        "Projection source type is incompatible with its "
                                + "anchor field");
            }
        }
    }

    private static boolean compatible(
            DataSourceDraft.HttpJsonSourceType sourceType,
            String anchorType
    ) {
        return switch (sourceType) {
            case STRING -> Set.of("TEXT", "TEXTAREA", "RICH_TEXT")
                    .contains(anchorType);
            case INTEGER, DECIMAL -> "NUMBER".equals(anchorType);
            case BOOLEAN -> "SWITCH".equals(anchorType);
        };
    }

    private static void validateJdbcDraft(
            DataSourceDraft draft,
            DataSourceModuleCatalog.PublishedModule module,
            List<DataSourceCheckReport.Issue> issues
    ) {
        if (!draft.outputFields().isEmpty()) {
            blocker(issues, "JDBC_NATIVE_OUTPUT_FIELDS_FORBIDDEN",
                    "outputFields",
                    "JDBC table drafts cannot use Native output fields");
        }
        if (!draft.fixedFilters().isEmpty()) {
            blocker(issues, "JDBC_NATIVE_FILTERS_FORBIDDEN", "fixedFilters",
                    "JDBC table drafts cannot use Native fixed filters");
        }
        if (draft.defaultSort() != null) {
            blocker(issues, "JDBC_NATIVE_SORT_FORBIDDEN", "defaultSort",
                    "JDBC table drafts cannot use a Native default sort");
        }
        if (draft.defaultTimeFieldCode() != null) {
            blocker(issues, "JDBC_NATIVE_TIME_FIELD_FORBIDDEN",
                    "defaultTimeFieldCode",
                    "JDBC table drafts cannot use a Native default time field");
        }
        if (draft.jdbcFieldProjections().isEmpty()) {
            blocker(issues, "JDBC_PROJECTION_REQUIRED",
                    "jdbcFieldProjections",
                    "At least one JDBC field projection is required");
            return;
        }
        if (module == null) {
            return;
        }
        var fields = fieldsByCode(module);
        for (int index = 0;
             index < draft.jdbcFieldProjections().size(); index++) {
            var projection = draft.jdbcFieldProjections().get(index);
            var path = "jdbcFieldProjections[" + index + "]";
            var field = fields.get(projection.fieldCode());
            if (field == null) {
                blocker(issues, "JDBC_PROJECTION_FIELD_NOT_FOUND",
                        path + ".fieldCode",
                        "Projection target is absent from the anchor schema");
            } else if (!field.available()) {
                blocker(issues, "JDBC_PROJECTION_FIELD_UNAVAILABLE",
                        path + ".fieldCode",
                        "Projection target is unavailable or deferred");
            } else if (!compatible(projection.sourceType(), field.type())) {
                blocker(issues, "JDBC_PROJECTION_TYPE_INCOMPATIBLE",
                        path + ".sourceType",
                        "Projection source type is incompatible with its "
                                + "anchor field");
            }
        }
    }

    private static boolean compatible(
            DataSourceDraft.JdbcTableSourceType sourceType,
            String anchorType
    ) {
        return switch (sourceType) {
            case STRING -> Set.of("TEXT", "TEXTAREA", "RICH_TEXT")
                    .contains(anchorType);
            case INTEGER, DECIMAL -> "NUMBER".equals(anchorType);
            case BOOLEAN -> "SWITCH".equals(anchorType);
            case DATE -> "DATE".equals(anchorType);
            case TIME -> "TIME".equals(anchorType);
            case DATETIME -> "DATETIME".equals(anchorType);
        };
    }

    private static void validateSort(
            DataSourceDraft draft,
            DataSourceModuleCatalog.PublishedModule module,
            Map<String, DataSourceModuleCatalog.FieldCapability> fields,
            Set<String> selected,
            List<DataSourceCheckReport.Issue> issues
    ) {
        var sort = draft.defaultSort();
        if (sort == null) {
            warning(issues, "DEFAULT_SORT_MISSING", "defaultSort",
                    "No default sort is configured");
            return;
        }
        if (!selected.contains(sort.fieldCode())) {
            blocker(issues, "DEFAULT_SORT_FIELD_NOT_SELECTED",
                    "defaultSort.fieldCode",
                    "Default sort field must be selected for output");
        }
        if (module == null) {
            return;
        }
        var field = fields.get(sort.fieldCode());
        if (field == null) {
            blocker(issues, "DEFAULT_SORT_FIELD_NOT_FOUND",
                    "defaultSort.fieldCode",
                    "Default sort field is absent from the published schema");
        } else if (!field.available()) {
            blocker(issues, "DEFAULT_SORT_FIELD_UNAVAILABLE",
                    "defaultSort.fieldCode",
                    "Default sort field is unavailable or deferred");
        } else if (!field.sortable()) {
            blocker(issues, "DEFAULT_SORT_FIELD_UNSORTABLE",
                    "defaultSort.fieldCode",
                    "Default sort field is not sortable");
        }
    }

    private static void validateTimeField(
            DataSourceDraft draft,
            DataSourceModuleCatalog.PublishedModule module,
            Map<String, DataSourceModuleCatalog.FieldCapability> fields,
            Set<String> selected,
            List<DataSourceCheckReport.Issue> issues
    ) {
        var timeField = draft.defaultTimeFieldCode();
        if (timeField == null) {
            warning(issues, "DEFAULT_TIME_FIELD_MISSING",
                    "defaultTimeFieldCode",
                    "No default time field is configured");
            return;
        }
        if (!selected.contains(timeField)) {
            blocker(issues, "DEFAULT_TIME_FIELD_NOT_SELECTED",
                    "defaultTimeFieldCode",
                    "Default time field must be selected for output");
        }
        if (module == null) {
            return;
        }
        var field = fields.get(timeField);
        if (field == null) {
            blocker(issues, "DEFAULT_TIME_FIELD_NOT_FOUND",
                    "defaultTimeFieldCode",
                    "Default time field is absent from the published schema");
        } else if (!field.available()) {
            blocker(issues, "DEFAULT_TIME_FIELD_UNAVAILABLE",
                    "defaultTimeFieldCode",
                    "Default time field is unavailable or deferred");
        } else if (!field.temporal()) {
            blocker(issues, "DEFAULT_TIME_FIELD_NOT_TEMPORAL",
                    "defaultTimeFieldCode",
                    "Default time field is not temporal");
        }
    }

    private static Map<String, DataSourceModuleCatalog.FieldCapability>
    fieldsByCode(DataSourceModuleCatalog.PublishedModule module) {
        var result = new HashMap<String,
                DataSourceModuleCatalog.FieldCapability>();
        module.fields().forEach(field -> result.put(field.code(), field));
        return result;
    }

    private static String fingerprint(
            ModuleDataSource root,
            DataSourceModuleCatalog.PublishedModule module,
            DataSourceDraft normalizedDraft
    ) {
        var value = new StringBuilder("datasource-publish-v1|");
        append(value, Long.toString(root.systemId()));
        append(value, Long.toString(root.tenantId()));
        append(value, root.code());
        append(value, Long.toString(root.moduleId()));
        append(value, module.moduleCode());
        append(value, module.schemaVersionId());
        append(value, root.name());
        append(value, root.description());
        append(value, normalizedDraft.canonicalForm());
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(
                    value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
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

    private ModuleDataSource root(
            DataSourceActor actor,
            long dataSourceId
    ) {
        if (dataSourceId <= 0) {
            throw notFound();
        }
        return repository.findById(
                        actor.systemId(), actor.tenantId(), dataSourceId)
                .orElseThrow(DataSourceService::notFound);
    }

    private static void requireDraftVersion(
            ModuleDataSource root,
            long expectedDraftVersion
    ) {
        if (expectedDraftVersion <= 0
                || root.draftVersion() != expectedDraftVersion) {
            throw error("DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft changed; refresh before retrying");
        }
    }

    private static void requireActor(DataSourceActor actor) {
        Objects.requireNonNull(actor, "actor");
    }

    private static DataSourceException notFound() {
        return error("DATA_SOURCE_NOT_FOUND", "Data source does not exist");
    }

    private static DataSourceException error(String code, String message) {
        return new DataSourceException(code, message);
    }

    private static DataSourcePublicationPreflight
    unavailablePublicationPreflight() {
        return (actor, normalizedHttpDraft) -> {
            throw error(
                    "DATA_SOURCE_PUBLICATION_PREFLIGHT_UNAVAILABLE",
                    "HTTP data source publication preflight is unavailable");
        };
    }

    private static JdbcTableDataSourcePublicationPreflight
    unavailableJdbcPublicationPreflight() {
        return (actor, normalizedJdbcDraft) -> {
            throw error(
                    "DATA_SOURCE_JDBC_PUBLICATION_PREFLIGHT_UNAVAILABLE",
                    "JDBC table publication preflight is unavailable");
        };
    }

    private static void blocker(
            List<DataSourceCheckReport.Issue> issues,
            String code,
            String path,
            String message
    ) {
        issues.add(new DataSourceCheckReport.Issue(
                BLOCKER, code, path, message));
    }

    private static void warning(
            List<DataSourceCheckReport.Issue> issues,
            String code,
            String path,
            String message
    ) {
        issues.add(new DataSourceCheckReport.Issue(
                WARNING, code, path, message));
    }

    private record Validation(
            DataSourceCheckReport report,
            DataSourceModuleCatalog.PublishedModule module,
            DataSourceDraft normalizedDraft
    ) {
    }
}
