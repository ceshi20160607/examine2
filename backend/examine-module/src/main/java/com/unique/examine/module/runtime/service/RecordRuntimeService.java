package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.api.ApiError;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import com.unique.examine.core.api.RuntimeRecordTeamOwnershipFacade;
import com.unique.examine.core.api.RuntimeReferenceFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowTriggerFacade;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.filefield.RecordFileFieldBindingService;
import com.unique.examine.module.runtime.query.GeoHashCodec;
import com.unique.examine.module.runtime.query.NativeRecordAggregatePlan;
import com.unique.examine.module.runtime.query.QuerySnapshotTokenService;
import com.unique.examine.module.runtime.query.RecordNeighborQueryBuilder;
import com.unique.examine.module.runtime.query.RecordQueryCompiler;
import com.unique.examine.module.runtime.query.RecordQueryModels;
import com.unique.examine.module.runtime.query.RecordQueryParser;
import com.unique.examine.module.runtime.query.RecordSearchTokenizer;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.security.SensitiveCryptoService;
import com.unique.examine.module.systemfield.SystemFieldDefinition;
import com.unique.examine.module.systemfield.SystemFieldValueService;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecordRuntimeService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "DATE", "DATETIME", "RADIO", "MEMBER", "DEPARTMENT",
            "PERCENT", "MONEY", "DATE_RANGE", "TIME", "TIME_RANGE", "MULTI_SELECT", "CASCADE",
            "SWITCH", "RATING", "PROGRESS", "TAG", "PHONE", "EMAIL", "URL", "IDENTITY",
            "ADDRESS", "GEO", "BARCODE", "RICH_TEXT", "JSON", "SECRET", "STATUS",
            "ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE",
            "RELATION", "REFERENCE", "SUBTABLE", "FORMULA", "SUMMARY", "CALCULATED", "LOOKUP", "AGGREGATE",
            "AI_FILL",
            "TENANT", "AUTO_NUMBER", "CREATED_BY", "CREATED_AT", "UPDATED_BY", "UPDATED_AT");
    private static final Set<String> P4_C2_TYPES = Set.of(
            "PHONE", "EMAIL", "URL", "IDENTITY", "ADDRESS", "GEO", "BARCODE", "RICH_TEXT",
            "JSON", "SECRET", "STATUS");
    private static final Set<String> SENSITIVE_TYPES = Set.of("IDENTITY", "SECRET");
    private static final Set<String> FILE_FIELD_TYPES = RecordFileFieldBindingService.TYPES;
    private static final Set<String> P4_C3_COMPOSITION_TYPES = Set.of("RELATION", "REFERENCE", "SUBTABLE");
    private static final Set<String> P4_C4_DERIVED_TYPES = Set.of(
            "FORMULA", "SUMMARY", "CALCULATED", "LOOKUP", "AGGREGATE", "AI_FILL");
    private static final Set<String> P4_C4_CROSS_TYPES = Set.of("SUMMARY", "LOOKUP", "AGGREGATE");
    private static final Set<String> SYSTEM_COMPUTED_TYPES = Set.of(
            "TENANT", "AUTO_NUMBER", "CREATED_BY", "CREATED_AT", "UPDATED_BY", "UPDATED_AT");
    private static final String SENSITIVE_KEY_UNAVAILABLE_REASON = "Sensitive field key service is unavailable";
    private static final Set<String> LEGACY_FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "DATE", "DATETIME", "RADIO", "MEMBER", "DEPARTMENT");
    private static final Set<String> RECORD_STATUSES = Set.of("ACTIVE", "ARCHIVED");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "recordNo", "r.record_no",
            "title", "r.title",
            "createdAt", "r.created_at",
            "updatedAt", "r.updated_at");

    static boolean supportsFieldType(String type) {
        return SUPPORTED_TYPES.contains(type);
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RuntimeAuthorizationFacade authorizationFacade;
    private final RuntimeReferenceFacade referenceFacade;
    private final RuntimeActiveMemberFacade activeMemberFacade;
    private final RuntimeRecordTeamOwnershipFacade teamOwnershipFacade;
    private final IdService idService;
    private final RecordMutationSupport mutations;
    private final RecordQueryParser queryParser;
    private final RecordQueryCompiler queryCompiler;
    private final RecordNeighborQueryBuilder neighborQueries;
    private final QuerySnapshotTokenService querySnapshotTokens;
    private final SavedViewRepository savedViews;
    private final CanonicalFieldValueCodec fieldValueCodec;
    private final SensitiveCryptoService sensitiveCrypto;
    private final RecordCompositionService compositions;
    private final ReferenceMaterializationService referenceMaterialization;
    private final DerivedMaterializationService derivedMaterialization;
    private final SystemFieldValueService systemFields;
    private final RecordFlowEventPublisher flowTriggers;
    private final PublishedPageRuleRuntime pageRuleRuntime;
    private final RecordFileFieldBindingService fileFields;

    public RecordRuntimeService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            RuntimeAuthorizationFacade authorizationFacade,
            RuntimeReferenceFacade referenceFacade,
            RuntimeActiveMemberFacade activeMemberFacade,
            RuntimeRecordTeamOwnershipFacade teamOwnershipFacade,
            IdService idService,
            RecordMutationSupport mutations,
            RecordQueryParser queryParser,
            RecordQueryCompiler queryCompiler,
            RecordNeighborQueryBuilder neighborQueries,
            QuerySnapshotTokenService querySnapshotTokens,
            SavedViewRepository savedViews,
            CanonicalFieldValueCodec fieldValueCodec,
            SensitiveCryptoService sensitiveCrypto,
            RecordCompositionService compositions,
            ReferenceMaterializationService referenceMaterialization,
            DerivedMaterializationService derivedMaterialization,
            SystemFieldValueService systemFields,
            RecordFlowEventPublisher flowTriggers,
            RecordFileFieldBindingService fileFields
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.authorizationFacade = authorizationFacade;
        this.referenceFacade = referenceFacade;
        this.activeMemberFacade = activeMemberFacade;
        this.teamOwnershipFacade = teamOwnershipFacade;
        this.idService = idService;
        this.mutations = mutations;
        this.queryParser = queryParser;
        this.queryCompiler = queryCompiler;
        this.neighborQueries = neighborQueries;
        this.querySnapshotTokens = querySnapshotTokens;
        this.savedViews = savedViews;
        this.fieldValueCodec = fieldValueCodec;
        this.sensitiveCrypto = sensitiveCrypto;
        this.compositions = compositions;
        this.referenceMaterialization = referenceMaterialization;
        this.derivedMaterialization = derivedMaterialization;
        this.systemFields = systemFields;
        this.flowTriggers = flowTriggers;
        this.pageRuleRuntime = new PublishedPageRuleRuntime();
        this.fileFields = fileFields;
    }

    public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
        var catalog = catalog(session, moduleCode);
        var references = referenceFacade.resolve(session.systemId(), requiredTenant(session), session.memberId());
        var actions = new ArrayList<String>();
        if (allowed(session, moduleCode, "create")) {
            actions.add("CREATE");
        }
        if (allowed(session, moduleCode, "archive.view")) {
            actions.add("VIEW_ARCHIVE");
        }
        if (allowed(session, moduleCode, "trash.view")) {
            actions.add("VIEW_TRASH");
        }
        var effectiveActions = applyRuleActions(actions, ruleDecision(catalog, List.of()));
        return new RecordRuntimeViews.RecordSchema(
                catalog.versionId(), catalog.moduleSnapshotId(), catalog.moduleId(), catalog.checksum(),
                catalog.unavailableReason() == null ? "READY" : "UNAVAILABLE", catalog.unavailableReason(),
                catalog.grant().authzEpoch(),
                catalog.fields().stream().map(field -> field.capability(references)).toList(),
                effectiveActions, new RecordRuntimeViews.QueryLimits(50, 200, 3), catalog.publishedRuntime());
    }

    /**
     * Resolves one immutable published schema version while applying the
     * caller's current module grant and field permissions.
     */
    public RecordRuntimeViews.RecordSchema schema(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId
    ) {
        var historical = historicalCatalog(
                session, moduleCode, schemaVersionId, "view");
        var catalog = historical.catalog();
        var references = referenceFacade.resolve(
                session.systemId(), requiredTenant(session),
                session.memberId());
        return new RecordRuntimeViews.RecordSchema(
                catalog.versionId(),
                Long.toString(historical.identity().moduleSnapshotId()),
                Long.toString(historical.identity().logicalModuleId()),
                catalog.checksum(),
                catalog.unavailableReason() == null
                        ? "READY" : "UNAVAILABLE",
                catalog.unavailableReason(), catalog.grant().authzEpoch(),
                catalog.fields().stream()
                        .map(field -> field.capability(references)).toList(),
                List.of(), new RecordRuntimeViews.QueryLimits(50, 200, 3), catalog.publishedRuntime());
    }

    public RecordRuntimeViews.RecordSchema importSchema(RuntimeSession session, String moduleCode) {
        var catalog = availableCatalog(session, moduleCode, "import");
        var references = referenceFacade.resolve(session.systemId(), requiredTenant(session), session.memberId());
        return new RecordRuntimeViews.RecordSchema(
                catalog.versionId(), catalog.moduleSnapshotId(), catalog.moduleId(), catalog.checksum(), "READY", null,
                catalog.grant().authzEpoch(),
                catalog.fields().stream().map(field -> field.capability(references)).toList(),
                List.of("IMPORT"), new RecordRuntimeViews.QueryLimits(50, 200, 3), catalog.publishedRuntime());
    }

    public RecordRuntimeViews.RecordSchema exportSchema(RuntimeSession session, String moduleCode) {
        var catalog = availableCatalog(session, moduleCode, "export");
        var references = referenceFacade.resolve(session.systemId(), requiredTenant(session), session.memberId());
        return new RecordRuntimeViews.RecordSchema(
                catalog.versionId(), catalog.moduleSnapshotId(), catalog.moduleId(), catalog.checksum(), "READY", null,
                catalog.grant().authzEpoch(),
                catalog.fields().stream().map(field -> field.capability(references)).toList(),
                List.of("EXPORT"), new RecordRuntimeViews.QueryLimits(50, 200, 3), catalog.publishedRuntime());
    }

    public RecordRuntimeViews.RecordSchema printSchema(RuntimeSession session, String moduleCode) {
        var catalog = availableCatalog(session, moduleCode, "print");
        var references = referenceFacade.resolve(session.systemId(), requiredTenant(session), session.memberId());
        return new RecordRuntimeViews.RecordSchema(
                catalog.versionId(), catalog.moduleSnapshotId(), catalog.moduleId(), catalog.checksum(), "READY", null,
                catalog.grant().authzEpoch(),
                catalog.fields().stream().map(field -> field.capability(references)).toList(),
                List.of("PRINT"), new RecordRuntimeViews.QueryLimits(50, 200, 3), catalog.publishedRuntime());
    }

    public ImportRowProbe probeImportRow(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId,
            String mode,
            String matchFieldCode,
            Map<String, JsonNode> supplied
    ) {
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "import");
        requireCurrentSchema(schemaVersionId, catalog.versionId());
        if (supplied == null || supplied.isEmpty()) {
            throw new BusinessException("IMPORT_ROW_EMPTY", "Import row must contain at least one field",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var fieldsByCode = new LinkedHashMap<String, FieldDescriptor>();
        catalog.fields().forEach(field -> fieldsByCode.put(field.code(), field));
        for (var code : supplied.keySet()) {
            var field = fieldsByCode.get(code);
            if (field == null || !importSafe(field)) {
                throw fieldError("IMPORT_FIELD_FORBIDDEN", code,
                        "The field is unavailable, readonly, sensitive, derived, system or composition-owned",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        var references = referenceFacade.resolve(session.systemId(), tenantId, session.memberId());
        var normalized = normalizeValues(session, catalog, references, supplied);
        var fingerprints = uniqueFingerprints(session, tenantId, catalog, normalized);

        Long targetRecordId = null;
        long targetVersion = -1;
        if ("UPSERT".equals(mode)) {
            var matchField = fieldsByCode.get(matchFieldCode);
            if (matchField == null || !importSafe(matchField) || !"UNIQUE".equals(matchField.indexMode())) {
                throw new BusinessException("IMPORT_MATCH_FIELD_INVALID",
                        "UPSERT requires one writable published UNIQUE match field", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            var match = normalized.stream().filter(value -> value.field().code().equals(matchFieldCode))
                    .findFirst().orElseThrow(() -> new BusinessException("IMPORT_MATCH_VALUE_REQUIRED",
                            "UPSERT match value is required", HttpStatus.UNPROCESSABLE_ENTITY));
            var keys = uniqueValues(session, tenantId, catalog, matchField, match.value());
            if (keys.size() != 1) {
                throw new BusinessException("IMPORT_MATCH_VALUE_AMBIGUOUS",
                        "UPSERT match field must normalize to exactly one unique value",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            var matches = conflictingRecords(session, tenantId, catalog, matchField, keys.getFirst());
            if (matches.size() > 1) {
                throw new BusinessException("IMPORT_MATCH_AMBIGUOUS", "UPSERT match resolves to multiple records",
                        HttpStatus.CONFLICT);
            }
            if (!matches.isEmpty()) {
                targetRecordId = matches.getFirst().recordId();
                targetVersion = matches.getFirst().version();
            }
        } else if (!"NEW".equals(mode)) {
            throw new BusinessException("IMPORT_MODE_INVALID", "Import mode must be NEW or UPSERT",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        var action = targetRecordId == null ? "NEW" : "UPDATE";
        if ("NEW".equals(action)) {
            validateStatusChanges(List.of(), normalized, true);
            requireActivationValues(catalog, normalized);
        } else {
            var before = persistedValues(session, catalog, targetRecordId);
            var editPlan = RecordBatchEditPlan.from(new RecordRuntimeViews.BatchEditRequest(
                    List.of(new RecordRuntimeViews.BatchRecordRef(Long.toString(targetRecordId), targetVersion)),
                    importChanges(supplied)));
            var edits = batchEditFields(session, tenantId, catalog, editPlan.changes());
            validateBatchEditRecord(catalog, before, patchedValues(catalog, before, edits));
        }
        var expectedTarget = targetRecordId;
        for (var fingerprint : fingerprints) {
            var field = fieldsByCode.get(fingerprint.fieldCode());
            var key = new UniqueValue(fingerprint.currency(), fingerprint.hash(), fingerprint.hashKeyVersion());
            var conflicts = conflictingRecords(session, tenantId, catalog, field, key);
            if (conflicts.stream().anyMatch(record -> expectedTarget == null || record.recordId() != expectedTarget)) {
                throw fieldError("FIELD_UNIQUE_CONFLICT", field.code(), field.name() + " must be unique",
                        HttpStatus.CONFLICT);
            }
        }
        return new ImportRowProbe(action, targetRecordId, targetVersion, List.copyOf(fingerprints));
    }

    public ImportRecordSnapshot importSnapshot(
            RuntimeSession session, String moduleCode, long recordId, Set<String> fieldCodes) {
        var catalog = availableCatalog(session, moduleCode, "import");
        var rows = commandRows(session, catalog, recordId, true);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().status())) throw notFound();
        var values = new LinkedHashMap<String, JsonNode>();
        persistedValues(session, catalog, recordId).stream()
                .filter(value -> fieldCodes.contains(value.field().code()))
                .forEach(value -> values.put(value.field().code(), objectMapper.valueToTree(value.value())));
        return new ImportRecordSnapshot(recordId, rows.getFirst().version(), rows.getFirst().status(),
                Map.copyOf(values));
    }

    public void publishImportCompleted(
            RuntimeSession session, String moduleCode, long recordId, long expectedVersion) {
        var catalog = availableCatalog(session, moduleCode, "import");
        var rows = commandRows(session, catalog, recordId, true);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().status())
                || rows.getFirst().version() != expectedVersion) {
            throw new BusinessException("IMPORT_COMMIT_STALE",
                    "Imported record changed before completion event dispatch", HttpStatus.CONFLICT);
        }
        var record = rows.getFirst();
        publishRecordEvent(session, moduleCode, recordId, record.version(), record.recordNo(),
                RuntimeRecordFlowTriggerFacade.TriggerEvent.IMPORT_COMPLETED,
                persistedValues(session, catalog, recordId));
    }

    private static boolean importSafe(FieldDescriptor field) {
        return field.writable() && !SENSITIVE_TYPES.contains(field.type()) && !FILE_FIELD_TYPES.contains(field.type())
                && !P4_C3_COMPOSITION_TYPES.contains(field.type())
                && !P4_C4_DERIVED_TYPES.contains(field.type())
                && !SYSTEM_COMPUTED_TYPES.contains(field.type());
    }

    private static List<RecordRuntimeViews.BatchFieldChange> importChanges(Map<String, JsonNode> supplied) {
        return supplied.entrySet().stream().map(entry -> {
            var value = entry.getValue();
            var clear = value == null || value.isNull()
                    || value.isTextual() && value.asText().isBlank()
                    || value.isArray() && value.isEmpty();
            return new RecordRuntimeViews.BatchFieldChange(entry.getKey(), clear ? "CLEAR" : "SET",
                    clear ? null : value);
        }).toList();
    }

    private List<ImportUniqueFingerprint> uniqueFingerprints(
            RuntimeSession session, long tenantId, Catalog catalog, List<NormalizedValue> normalized) {
        var result = new ArrayList<ImportUniqueFingerprint>();
        for (var value : normalized) {
            if (!"UNIQUE".equals(value.field().indexMode())) continue;
            uniqueValues(session, tenantId, catalog, value.field(), value.value()).forEach(unique -> result.add(
                    new ImportUniqueFingerprint(value.field().code(), unique.currency(), unique.hash(),
                            unique.hashKeyVersion())));
        }
        return List.copyOf(result);
    }

    private List<ImportConflictRecord> conflictingRecords(
            RuntimeSession session, long tenantId, Catalog catalog, FieldDescriptor field, UniqueValue unique) {
        return jdbc.query("SELECT r.record_id,r.version FROM un_module_record_unique u "
                        + "JOIN un_module_record r ON r.system_id=u.system_id AND r.tenant_id=u.tenant_id "
                        + "AND r.record_id=u.record_id WHERE u.system_id=? AND u.tenant_id=? "
                        + "AND u.logical_module_id=? AND u.logical_field_id=? "
                        + "AND u.normalization_generation_id=1 AND u.normalized_hash=? AND u.currency_key=? "
                        + "AND r.status='ACTIVE' ORDER BY r.record_id",
                (result, row) -> new ImportConflictRecord(result.getLong("record_id"), result.getLong("version")),
                session.systemId(), tenantId, Long.parseLong(catalog.moduleId()), field.id(), unique.hash(),
                unique.currency() == null ? "---" : unique.currency());
    }

    public record ImportUniqueFingerprint(String fieldCode, String currency, String hash, String hashKeyVersion) { }
    public record ImportRowProbe(String action, Long targetRecordId, long targetVersion,
                                 List<ImportUniqueFingerprint> fingerprints) { }
    public record ImportRecordSnapshot(long recordId, long version, String status, Map<String, JsonNode> values) { }
    private record ImportConflictRecord(long recordId, long version) { }

    public RecordRuntimeViews.RecordPage list(
            RuntimeSession session,
            String moduleCode,
            int page,
            int size,
            String status,
            String sort,
            String filter
    ) {
        var catalog = availableCatalog(session, moduleCode);
        validateQuery(page, size, status, sort, filter);
        var scope = scope(session, catalog);
        var order = order(sort);
        var offset = Math.multiplyExact(page - 1, size);
        var baseArguments = new ArrayList<Object>();
        baseArguments.add(session.systemId());
        baseArguments.add(requiredTenant(session));
        baseArguments.add(Long.parseLong(catalog.moduleId()));
        baseArguments.add(Long.parseLong(catalog.versionId()));
        baseArguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        baseArguments.add(status);
        baseArguments.addAll(scope.arguments());
        var where = "r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? "
                + "AND r.schema_version_id=? AND r.module_snapshot_id=? AND r.status=? AND " + scope.sql();
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_record r WHERE " + where,
                Long.class, baseArguments.toArray());
        var pageArguments = new ArrayList<>(baseArguments);
        pageArguments.add(size);
        pageArguments.add(offset);
        var records = jdbc.query("SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id "
                        + "FROM un_module_record r WHERE " + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (result, row) -> recordRow(result), pageArguments.toArray());
        var values = values(session, catalog, records.stream().map(RecordRow::recordId).toList(),
                catalog.listFields());
        var rows = records.stream().map(record -> new RecordRuntimeViews.RecordSummary(
                Long.toString(record.recordId()), record.recordNo(), record.version(), record.status(), record.title(),
                values.getOrDefault(record.recordId(), List.of()))).toList();
        return new RecordRuntimeViews.RecordPage(rows, page, size, total == null ? 0 : total);
    }

    public RecordRuntimeViews.RecordPage myDraftsQuery(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.MyDraftsQueryRequest request
    ) {
        var plan = RecordMyDraftsQueryPlan.from(request);
        var catalog = availableCatalog(session, moduleCode, "update");
        var scope = scope(session, catalog);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(catalog.moduleId()));
        arguments.add(Long.parseLong(catalog.versionId()));
        arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        arguments.add(session.memberId());
        arguments.addAll(scope.arguments());
        if (plan.query() != null) {
            arguments.add(plan.likePattern());
            arguments.add(plan.likePattern());
        }
        var where = plan.whereSql(scope.sql());
        var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_module_record r WHERE " + where,
                Long.class,
                arguments.toArray());
        var pageArguments = new ArrayList<>(arguments);
        pageArguments.add(plan.size());
        pageArguments.add(plan.offset());
        var records = jdbc.query(
                "SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id "
                        + "FROM un_module_record r WHERE " + where
                        + " ORDER BY " + RecordMyDraftsQueryPlan.ORDER_BY + " LIMIT ? OFFSET ?",
                (result, row) -> recordRow(result),
                pageArguments.toArray());
        var values = values(
                session,
                catalog,
                records.stream().map(RecordRow::recordId).toList(),
                catalog.listFields());
        var rows = records.stream().map(record -> new RecordRuntimeViews.RecordSummary(
                Long.toString(record.recordId()),
                record.recordNo(),
                record.version(),
                record.status(),
                record.title(),
                values.getOrDefault(record.recordId(), List.of()))).toList();
        return new RecordRuntimeViews.RecordPage(
                rows, plan.page(), plan.size(), total == null ? 0 : total);
    }

    public RecordRuntimeViews.RecordPage query(RuntimeSession session, String moduleCode, String body) {
        var catalog = availableCatalog(session, moduleCode);
        var supplied = queryParser.parse(body);
        var queryFields = queryFields(catalog);
        var invalidNodes = new ArrayList<String>();
        var query = supplied.viewId() == null ? supplied
                : applySavedView(session, catalog, supplied, queryFields, invalidNodes);
        var execution = prepareQuery(session, moduleCode, catalog, query, queryFields);
        var directPlan = directIndexedSortPlan(query, queryFields, execution);
        final Long total;
        final List<QueryRecord> records;
        if (directPlan != null) {
            total = jdbc.queryForObject(
                    "SELECT COUNT(*) " + directPlan.countFromSql()
                            + " WHERE " + directPlan.countWhereSql(),
                    Long.class,
                    execution.whereArguments().toArray());
            var pageArguments = new ArrayList<Object>(execution.whereArguments());
            pageArguments.add(query.size());
            pageArguments.add(Math.multiplyExact(query.page() - 1, query.size()));
            records = jdbc.query(
                    "SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id,"
                            + directPlan.anchorSql() + " AS sort_anchor_0 "
                            + directPlan.fromSql() + " WHERE " + directPlan.whereSql()
                            + " ORDER BY " + directPlan.orderSql() + " LIMIT ? OFFSET ?",
                    (result, row) -> queryRecordRow(result, execution.compiled().effectiveSorts()),
                    pageArguments.toArray());
        } else {
            var selectHint = execution.materializeSinglePredicate()
                    ? " /*+ SEMIJOIN(@record_filter MATERIALIZATION) */"
                    : "";
            total = jdbc.queryForObject(
                    "SELECT" + selectHint + " COUNT(*) FROM un_module_record r WHERE " + execution.whereSql(),
                    Long.class,
                    execution.whereArguments().toArray());
            var anchors = neighborQueries.anchors(execution.compiled().effectiveSorts());
            var pageArguments = new ArrayList<Object>();
            pageArguments.addAll(anchors.arguments());
            pageArguments.addAll(execution.whereArguments());
            pageArguments.addAll(execution.compiled().orderArguments());
            pageArguments.add(query.size());
            pageArguments.add(Math.multiplyExact(query.page() - 1, query.size()));
            records = jdbc.query("SELECT" + selectHint
                            + " r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id"
                            + anchors.selectSql()
                            + " FROM un_module_record r WHERE " + execution.whereSql()
                            + " ORDER BY " + execution.compiled().orderSql()
                            + " LIMIT ? OFFSET ?",
                    (result, row) -> queryRecordRow(result, execution.compiled().effectiveSorts()),
                    pageArguments.toArray());
        }
        var values = values(
                session,
                catalog,
                records.stream().map(record -> record.record().recordId()).toList(),
                execution.selectedFields());
        var rows = records.stream().map(record -> summary(
                record,
                values.getOrDefault(record.record().recordId(), List.of()))).toList();
        var queryHash = queryParser.sha256(query.canonicalJson());
        var snapshotToken = querySnapshotTokens.issue(
                session.systemId(),
                requiredTenant(session),
                session.memberId(),
                moduleCode,
                catalog.moduleId(),
                catalog.versionId(),
                query.canonicalJson());
        return new RecordRuntimeViews.RecordPage(rows, query.page(), query.size(), total == null ? 0 : total,
                queryHash, snapshotToken, invalidNodes);
    }

    private DirectIndexedSortPlan directIndexedSortPlan(
            RecordQueryModels.RecordQuery query,
            List<RecordQueryModels.QueryField> fields,
            QueryExecution execution
    ) {
        if (!(query.filter() instanceof RecordQueryModels.Predicate predicate)
                || query.q() != null
                || query.sort().size() != 1
                || !predicate.fieldCode().equals(query.sort().getFirst().fieldCode())
                || "EMPTY".equals(predicate.operator())) {
            return null;
        }
        var field = fields.stream().filter(candidate -> predicate.fieldCode().equals(candidate.code()))
                .findFirst().orElse(null);
        if (field == null) {
            return null;
        }
        var route = switch (field.type()) {
            case "TEXT", "TEXTAREA", "BARCODE", "STATUS" -> new IndexedSortRoute("idx_ri_string", "string_value");
            case "NUMBER", "PERCENT", "RATING", "PROGRESS" ->
                    new IndexedSortRoute("idx_ri_decimal", "decimal_value");
            case "DATE" -> new IndexedSortRoute("idx_ri_date", "date_value");
            default -> null;
        };
        if (route == null) {
            return null;
        }
        var compiledPredicate = execution.compiled().predicateSql();
        var marker = " AND i.record_status=r.status AND ";
        var conditionAt = compiledPredicate.indexOf(marker);
        if (!compiledPredicate.startsWith("EXISTS (SELECT /*+ QB_NAME(record_filter) */ 1 ")
                || conditionAt < 0
                || !compiledPredicate.endsWith(")")) {
            return null;
        }
        var condition = compiledPredicate.substring(conditionAt + marker.length(), compiledPredicate.length() - 1)
                .replace("i.", "fi.");
        var suffix = " AND " + compiledPredicate;
        if (!execution.whereSql().endsWith(suffix)) {
            return null;
        }
        var baseWhere = execution.whereSql().substring(0, execution.whereSql().length() - suffix.length());
        var join = "FROM un_module_record_index fi FORCE INDEX (" + route.indexName() + ") "
                + "STRAIGHT_JOIN un_module_record r ON fi.system_id=r.system_id "
                + "AND fi.tenant_id=r.tenant_id AND fi.record_id=r.record_id "
                + "AND fi.schema_version_id=r.schema_version_id "
                + "AND fi.module_snapshot_id=r.module_snapshot_id "
                + "AND fi.logical_module_id=r.logical_module_id "
                + "AND fi.logical_field_id=" + field.id() + " AND fi.index_generation_id=1 "
                + "AND fi.record_status=r.status";
        var sort = query.sort().getFirst();
        var column = "fi." + route.columnName();
        // A non-EMPTY indexed predicate guarantees a non-null sort value. Using
        // the projection's record id preserves the public stable tie-break and
        // lets MySQL consume the typed index order before joining record rows.
        var order = column + " " + sort.direction() + ",fi.record_id ASC";
        var indexOnly = baseWhere.endsWith(" AND 1=1");
        var countFrom = indexOnly
                ? "FROM un_module_record_index fi FORCE INDEX (" + route.indexName() + ")"
                : join;
        var countWhere = indexOnly
                ? baseWhere.replace("r.status", "fi.record_status").replace("r.", "fi.") + " AND " + condition
                : baseWhere + " AND " + condition;
        return new DirectIndexedSortPlan(
                join, baseWhere + " AND " + condition, column, order, countFrom, countWhere);
    }

    /**
     * Prepares an ACTIVE-only aggregate predicate through the exact ordinary
     * query path, including module view permission, field-read projection,
     * data scope, fixed filters, relation validation and GEO candidate bounds.
     * No record page is loaded by this operation.
     */
    public NativeRecordAggregatePlan prepareActiveAggregate(
            RuntimeSession session,
            String moduleCode,
            String body
    ) {
        var catalog = availableCatalog(session, moduleCode);
        var identity = aggregateIdentity(session, catalog, moduleCode);
        return prepareActiveAggregate(
                session, moduleCode, body, catalog, identity, null);
    }

    /**
     * Prepares an aggregate over one immutable historical schema version while
     * retaining the caller's current authorization and data-scope grant.
     */
    public NativeRecordAggregatePlan prepareActiveAggregate(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId,
            String body
    ) {
        var historical = availableHistoricalCatalog(
                session, moduleCode, schemaVersionId, "view");
        return prepareActiveAggregate(
                session, moduleCode, body, historical.catalog(),
                historical.identity(), historical.version());
    }

    private NativeRecordAggregatePlan prepareActiveAggregate(
            RuntimeSession session,
            String moduleCode,
            String body,
            Catalog catalog,
            ModuleIdentity identity,
            Active historicalVersion
    ) {
        var query = queryParser.parse(body);
        if (!"active".equals(query.recordScope())
                || query.viewId() != null || query.q() != null
                || !query.sort().isEmpty()) {
            throw invalidQuery(
                    "Native statistics requires an active fixed-filter query");
        }
        var queryFields = catalog.fields().stream().map(field ->
                new RecordQueryModels.QueryField(
                        field.id(), field.code(), queryType(field),
                        field.searchable(), field.filterable(), field.sortable(),
                        field.sensitiveQueryable(), field.schema()))
                .toList();
        var execution = prepareQuery(
                session, moduleCode, catalog, query, queryFields, identity,
                historicalVersion);
        return new NativeRecordAggregatePlan(
                queryParser.sha256(query.canonicalJson()), moduleCode,
                session.systemId(), requiredTenant(session),
                Long.parseLong(catalog.versionId()),
                identity.moduleSnapshotId(),
                identity.logicalModuleId(),
                execution.whereSql(), execution.whereArguments(),
                queryFields.stream().map(field ->
                        new NativeRecordAggregatePlan.Field(
                                field.id(), field.code(), field.type()))
                        .toList());
    }

    public RecordRuntimeViews.NeighborResponse neighbor(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            RecordRuntimeViews.NeighborRequest request,
            String correlationId
    ) {
        if (recordId <= 0) {
            throw notFound();
        }
        if (request == null) {
            throw invalidQuery("Neighbor request is required");
        }
        var catalog = availableCatalog(session, moduleCode);
        var snapshot = querySnapshotTokens.verify(
                request.querySnapshotToken(),
                session.systemId(),
                requiredTenant(session),
                session.memberId(),
                moduleCode,
                catalog.moduleId(),
                catalog.versionId());
        var query = queryParser.parse(snapshot.canonicalQuery());
        var queryFields = catalog.fields().stream().map(field -> new RecordQueryModels.QueryField(
                field.id(), field.code(), queryType(field), field.searchable(), field.filterable(), field.sortable(),
                field.sensitiveQueryable(), field.schema()))
                .toList();
        var execution = prepareQuery(session, moduleCode, catalog, query, queryFields);
        validateNeighborAnchor(request, recordId, execution.compiled().effectiveSorts().size());
        var locate = neighborQueries.locate(
                execution.whereSql(),
                execution.whereArguments(),
                execution.compiled(),
                request.direction(),
                recordId);
        var pointers = jdbc.query(
                locate.sql(),
                (row, number) -> {
                    var neighborId = row.getLong("neighbor_id");
                    return row.wasNull() ? null : neighborId;
                },
                locate.arguments().toArray());
        if (pointers.isEmpty()) {
            throw notFound();
        }
        var neighborId = pointers.getFirst();
        if (neighborId == null) {
            return new RecordRuntimeViews.NeighborResponse(
                    null,
                    true,
                    request.querySnapshotToken(),
                    correlationId);
        }
        var neighbor = queryRecord(
                execution,
                neighborId);
        if (neighbor == null) {
            return new RecordRuntimeViews.NeighborResponse(
                    null,
                    true,
                    request.querySnapshotToken(),
                    correlationId);
        }
        var projected = values(
                session,
                catalog,
                List.of(neighborId),
                execution.selectedFields());
        return new RecordRuntimeViews.NeighborResponse(
                summary(neighbor, projected.getOrDefault(neighborId, List.of())),
                false,
                request.querySnapshotToken(),
                correlationId);
    }

    private QueryExecution prepareQuery(
            RuntimeSession session,
            String moduleCode,
            Catalog catalog,
            RecordQueryModels.RecordQuery query,
            List<RecordQueryModels.QueryField> queryFields
    ) {
        return prepareQuery(session, moduleCode, catalog, query, queryFields,
                new ModuleIdentity(Long.parseLong(catalog.moduleSnapshotId()),
                        Long.parseLong(catalog.moduleId())), null);
    }

    private QueryExecution prepareQuery(
            RuntimeSession session,
            String moduleCode,
            Catalog catalog,
            RecordQueryModels.RecordQuery query,
            List<RecordQueryModels.QueryField> queryFields,
            ModuleIdentity identity,
            Active historicalVersion
    ) {
        requireCurrentSchema(query.schemaVersionId(), catalog.versionId());
        var status = switch (query.recordScope()) {
            case "active" -> "ACTIVE";
            case "archived" -> {
                requireScopePermission(session, moduleCode, "archive.view");
                yield "ARCHIVED";
            }
            case "trash" -> {
                requireScopePermission(session, moduleCode, "trash.view");
                yield "TRASHED";
            }
            default -> throw invalidQuery("recordScope is unsupported");
        };
        var compiled = queryCompiler.compile(query, queryFields,
                (field, value) -> sensitiveQueryHashes(session, catalog, field, value));
        var materializeSinglePredicate = query.q() == null
                && query.filter() instanceof RecordQueryModels.Predicate
                && compiled.predicateSql().startsWith("EXISTS (SELECT 1 ");
        if (materializeSinglePredicate) {
            compiled = new RecordQueryModels.CompiledQuery(
                    compiled.predicateSql().replaceFirst(
                            "EXISTS \\(SELECT 1 ",
                            "EXISTS (SELECT /*+ QB_NAME(record_filter) */ 1 "),
                    compiled.arguments(),
                    compiled.orderSql(),
                    compiled.orderArguments(),
                    compiled.effectiveSorts());
        }
        validateRelationQueryTargets(
                session, catalog, query.filter(), historicalVersion);
        var selectedFields = selectedFields(catalog, query.columns());
        var scope = scope(session, catalog);
        enforceGeoCandidateLimit(session, catalog, identity, status, query, queryFields, scope);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(identity.logicalModuleId());
        arguments.add(Long.parseLong(catalog.versionId()));
        arguments.add(identity.moduleSnapshotId());
        arguments.add(status);
        arguments.addAll(scope.arguments());
        arguments.addAll(compiled.arguments());
        var where = "r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? "
                + "AND r.schema_version_id=? AND r.module_snapshot_id=? AND r.status=? AND "
                + scope.sql() + " AND " + compiled.predicateSql();
        return new QueryExecution(compiled, selectedFields, where, arguments, materializeSinglePredicate);
    }

    private QueryRecord queryRecord(QueryExecution execution, long recordId) {
        var anchors = neighborQueries.anchors(execution.compiled().effectiveSorts());
        var arguments = new ArrayList<Object>();
        arguments.addAll(anchors.arguments());
        arguments.addAll(execution.whereArguments());
        arguments.add(recordId);
        var rows = jdbc.query(
                "SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id"
                        + anchors.selectSql()
                        + " FROM un_module_record r WHERE " + execution.whereSql() + " AND r.record_id=?",
                (result, row) -> queryRecordRow(result, execution.compiled().effectiveSorts()),
                arguments.toArray());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static void validateNeighborAnchor(
            RecordRuntimeViews.NeighborRequest request,
            long recordId,
            int sortCount
    ) {
        if (!Set.of("PREVIOUS", "NEXT").contains(request.direction())
                || request.sortAnchor() == null
                || request.sortAnchor().values().size() != sortCount
                || request.sortAnchor().recordId() == null
                || !request.sortAnchor().recordId().matches("^[1-9][0-9]{0,18}$")
                || request.sortAnchor().values().stream()
                .anyMatch(value -> value == null || !value.isValueNode())) {
            throw invalidQuery("Neighbor direction or sort anchor is invalid");
        }
        try {
            if (Long.parseLong(request.sortAnchor().recordId()) != recordId) {
                throw notFound();
            }
        } catch (NumberFormatException exception) {
            throw invalidQuery("Neighbor sort anchor recordId is invalid");
        }
    }

    public RecordRuntimeViews.RelationCandidatePage relationCandidates(
            RuntimeSession session,
            String sourceModuleCode,
            String fieldCode,
            String q,
            int page,
            int size,
            String correlationId
    ) {
        validateCompositionPage(page, size);
        var normalizedQuery = q == null ? "" : RecordSearchTokenizer.normalize(q);
        if (!normalizedQuery.isEmpty() && (normalizedQuery.length() < 2 || normalizedQuery.length() > 100)) {
            throw new BusinessException("QUERY_INVALID", "Relation candidate query must contain 2..100 characters",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var source = availableCatalog(session, sourceModuleCode);
        var relation = compositionField(source, fieldCode, "RELATION", true);
        var targetModuleId = relation.schema().path("targetModuleId").asText("");
        var targetModuleCode = targetModuleCode(session.systemId(), Long.parseLong(source.versionId()),
                targetModuleId);
        var target = availableCatalog(session, targetModuleCode);
        var displayField = relationDisplayField(relation, target);
        if (relation.schema().hasNonNull("displayFieldId") && displayField == null) {
            return new RecordRuntimeViews.RelationCandidatePage(List.of(), page, size, 0, correlationId);
        }
        var targetFilter = compileRelationTargetFilter(session, relation, target);
        var scope = scope(session, target);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(target.moduleId()));
        arguments.add(Long.parseLong(target.versionId()));
        arguments.add(Long.parseLong(target.moduleId()));
        arguments.addAll(scope.arguments());
        arguments.addAll(targetFilter.arguments());
        var display = relationDisplaySql(displayField);
        var search = "";
        if (!normalizedQuery.isEmpty()) {
            search = " AND LOCATE(?,LOWER(" + display + "))>0";
            arguments.add(normalizedQuery);
        }
        var where = "r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? "
                + "AND r.schema_version_id=? AND r.module_snapshot_id=? AND r.status='ACTIVE' AND "
                + scope.sql() + " AND " + targetFilter.predicateSql() + search;
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_record r WHERE " + where,
                Long.class, arguments.toArray());
        var pageArguments = new ArrayList<>(arguments);
        pageArguments.add(size);
        pageArguments.add(Math.multiplyExact(page - 1, size));
        var offset = Math.multiplyExact(page - 1, size);
        var items = jdbc.query("SELECT r.record_id,r.version," + display + " AS relation_title "
                        + "FROM un_module_record r WHERE " + where
                        + " ORDER BY relation_title,r.record_id LIMIT ? OFFSET ?",
                (row, number) -> new RecordRuntimeViews.RelationItem(
                        Long.toString(row.getLong("record_id")), row.getLong("version"),
                        offset + number, row.getString("relation_title")), pageArguments.toArray());
        return new RecordRuntimeViews.RelationCandidatePage(items, page, size, total == null ? 0 : total,
                correlationId);
    }

    private void enforceGeoCandidateLimit(
            RuntimeSession session,
            Catalog catalog,
            ModuleIdentity identity,
            String recordStatus,
            RecordQueryModels.RecordQuery query,
            List<RecordQueryModels.QueryField> fields,
            ScopeSql scope
    ) {
        for (var window : queryCompiler.geoCandidateWindows(query, fields)) {
            var longitude = window.wrapsAntimeridian()
                    ? "(i.geo_lng>=? OR i.geo_lng<=?)" : "i.geo_lng BETWEEN ? AND ?";
            var sql = "SELECT COUNT(*) FROM (SELECT 1 AS candidate FROM un_module_record r "
                    + "JOIN un_module_record_index i ON i.system_id=r.system_id AND i.tenant_id=r.tenant_id "
                    + "AND i.record_id=r.record_id AND i.schema_version_id=r.schema_version_id "
                    + "AND i.module_snapshot_id=r.module_snapshot_id AND i.logical_module_id=r.logical_module_id "
                    + "WHERE r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? AND r.schema_version_id=? "
                    + "AND r.module_snapshot_id=? AND r.status=? AND " + scope.sql()
                    + " AND i.logical_field_id=? AND i.index_generation_id=1 AND i.record_status=r.status "
                    + "AND i.value_kind='GEO' AND i.geo_lat BETWEEN ? AND ? AND " + longitude
                    + " LIMIT 2001) p4c2_geo_candidates";
            var arguments = new ArrayList<Object>();
            arguments.add(session.systemId());
            arguments.add(requiredTenant(session));
            arguments.add(identity.logicalModuleId());
            arguments.add(Long.parseLong(catalog.versionId()));
            arguments.add(identity.moduleSnapshotId());
            arguments.add(recordStatus);
            arguments.addAll(scope.arguments());
            arguments.add(window.fieldId());
            arguments.add(window.south());
            arguments.add(window.north());
            arguments.add(window.west());
            arguments.add(window.east());
            var candidates = jdbc.queryForObject(sql, Long.class, arguments.toArray());
            if (candidates != null && candidates > 2000) {
                throw new BusinessException("QUERY_TOO_BROAD",
                        "GEO query exceeds the 2000-record candidate limit", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
    }

    private ModuleIdentity aggregateIdentity(
            RuntimeSession session,
            Catalog catalog,
            String moduleCode
    ) {
        var identities = jdbc.query(
                "SELECT module_snapshot_id,logical_module_id "
                        + "FROM un_module_runtime_schema_module "
                        + "WHERE system_id=? AND schema_version_id=? AND module_code=?",
                (result, row) -> new ModuleIdentity(
                        result.getLong("module_snapshot_id"),
                        result.getLong("logical_module_id")),
                session.systemId(), Long.parseLong(catalog.versionId()), moduleCode);
        if (identities.size() != 1
                || identities.getFirst().logicalModuleId()
                != Long.parseLong(catalog.moduleId())) {
            throw new BusinessException(
                    "FIELD_RUNTIME_UNAVAILABLE",
                    "Published module identity is absent from the runtime schema",
                    HttpStatus.CONFLICT);
        }
        return identities.getFirst();
    }

    private ModuleIdentity requireHistoricalProjection(
            RuntimeSession session,
            Catalog catalog,
            String moduleCode
    ) {
        var identity = aggregateIdentity(session, catalog, moduleCode);
        var projected = jdbc.query(
                "SELECT logical_field_id,field_code,field_type "
                        + "FROM un_module_runtime_schema_field "
                        + "WHERE system_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND logical_module_id=? "
                        + "AND field_scope='RECORD'",
                (result, row) -> new ProjectedField(
                        result.getLong("logical_field_id"),
                        result.getString("field_code"),
                        result.getString("field_type")),
                session.systemId(), Long.parseLong(catalog.versionId()),
                identity.moduleSnapshotId(), identity.logicalModuleId());
        var byId = new HashMap<Long, ProjectedField>();
        var codes = new HashSet<String>();
        var invalid = false;
        for (var field : projected) {
            invalid |= field.logicalFieldId() <= 0
                    || field.code() == null || field.code().isBlank()
                    || field.type() == null || field.type().isBlank();
            invalid |= byId.put(field.logicalFieldId(), field) != null;
            invalid |= !codes.add(field.code());
        }
        for (var field : catalog.fields()) {
            var persisted = byId.get(field.id());
            invalid |= persisted == null
                    || !Objects.equals(persisted.code(), field.code())
                    || !Objects.equals(persisted.type(), field.type());
        }
        if (invalid) {
            throw historicalUnavailable(
                    "Historical runtime field projection is inconsistent");
        }
        return identity;
    }

    public void validateQuery(RuntimeSession session, String moduleCode, String body) {
        var catalog = availableCatalog(session, moduleCode);
        var query = queryParser.parse(body);
        if (query.viewId() != null) {
            throw new BusinessException("SAVED_VIEW_INVALID",
                    "A saved definition cannot reference another saved view", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        requireCurrentSchema(query.schemaVersionId(), catalog.versionId());
        var fields = catalog.fields().stream().map(field -> new RecordQueryModels.QueryField(
                field.id(), field.code(), queryType(field), field.searchable(), field.filterable(), field.sortable(),
                field.sensitiveQueryable(), field.schema()))
                .toList();
        queryCompiler.compile(query, fields,
                (field, value) -> sensitiveQueryHashes(session, catalog, field, value));
        selectedFields(catalog, query.columns());
    }

    public RecordRuntimeViews.RecordDetail detail(
            RuntimeSession session,
            String moduleCode,
            long recordId
    ) {
        if (recordId <= 0) {
            throw notFound();
        }
        var viewCatalog = availableCatalog(session, moduleCode);
        var rows = detailRows(session, viewCatalog, recordId, "r.status IN ('ACTIVE','ARCHIVED')");
        if (!rows.isEmpty()) {
            return detailResponse(session, moduleCode, viewCatalog, rows.getFirst());
        }
        if (allowed(session, moduleCode, "update")) {
            var updateCatalog = availableCatalog(session, moduleCode, "update");
            var drafts = detailRows(session, updateCatalog, recordId, "r.status='DRAFT'");
            if (!drafts.isEmpty()) {
                return detailResponse(session, moduleCode, updateCatalog, drafts.getFirst());
            }
        }
        if (allowed(session, moduleCode, "action.restore_trash")) {
            var restoreCatalog = availableCatalog(session, moduleCode, "action.restore_trash");
            var trashed = detailRows(session, restoreCatalog, recordId, "r.status='TRASHED'");
            if (!trashed.isEmpty()) {
                return detailResponse(session, moduleCode, restoreCatalog, trashed.getFirst());
            }
        }
        if (allowed(session, moduleCode, "action.recover_draft")) {
            var recoverCatalog = availableCatalog(session, moduleCode, "action.recover_draft");
            var expired = detailRows(session, recoverCatalog, recordId, "r.status='EXPIRED'");
            if (!expired.isEmpty()) {
                return detailResponse(session, moduleCode, recoverCatalog, expired.getFirst());
            }
        }
        throw notFound();
    }

    public RecordRuntimeViews.RelationPage relations(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            int page,
            int size,
            String correlationId
    ) {
        validateCompositionPage(page, size);
        var source = availableCatalog(session, moduleCode);
        var field = compositionField(source, fieldCode, "RELATION", false);
        requireVisibleRecord(session, moduleCode, source, recordId);
        var targetModuleId = field.schema().path("targetModuleId").asText("");
        var targetModuleCode = targetModuleCode(session.systemId(), Long.parseLong(source.versionId()),
                targetModuleId);
        var target = availableCatalog(session, targetModuleCode);
        var targetScope = scope(session, target);
        var slice = compositions.relationPage(session, requiredTenant(session), recordId, field.id(),
                Long.parseLong(targetModuleId), targetScope.sql(), targetScope.arguments(), page, size);
        var displayField = relationDisplayField(field, target);
        if (field.schema().hasNonNull("displayFieldId") && displayField == null) {
            return new RecordRuntimeViews.RelationPage(List.of(), page, size, 0,
                    compositionCapabilities(session, moduleCode, field), correlationId);
        }
        var items = slice.items();
        if (displayField != null && !items.isEmpty()) {
            var displayValues = values(session, target, items.stream()
                    .map(item -> Long.parseLong(item.targetRecordId())).toList(), List.of(displayField));
            items = items.stream().map(item -> {
                var values = displayValues.getOrDefault(Long.parseLong(item.targetRecordId()), List.of());
                var value = values.isEmpty() ? null : values.getFirst();
                var title = value == null ? item.title()
                        : value.displayValue() != null ? value.displayValue() : String.valueOf(value.value());
                return new RecordRuntimeViews.RelationItem(item.targetRecordId(), item.targetVersion(),
                        item.ordinal(), title);
            }).toList();
        }
        return new RecordRuntimeViews.RelationPage(items, page, size, slice.total(),
                compositionCapabilities(session, moduleCode, field), correlationId);
    }

    public RecordRuntimeViews.SubtablePage subtable(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            int page,
            int size,
            String correlationId
    ) {
        validateCompositionPage(page, size);
        var catalog = availableCatalog(session, moduleCode);
        var field = compositionField(catalog, fieldCode, "SUBTABLE", false);
        requireVisibleRecord(session, moduleCode, catalog, recordId);
        var slice = compositions.subtablePage(session, requiredTenant(session), recordId, field.id(), page, size);
        return new RecordRuntimeViews.SubtablePage(slice.items(), page, size, slice.total(),
                compositionCapabilities(session, moduleCode, field), correlationId);
    }

    @Transactional
    public RecordRuntimeViews.RecordMutationResponse mutateRelation(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            RecordRuntimeViews.RelationMutationRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + recordId + ":relation:" + fieldCode,
                idempotencyKey, request, RecordRuntimeViews.RecordMutationResponse.class, 200,
                () -> mutateRelationNow(
                        session, moduleCode, recordId, fieldCode,
                        request, "WEB", requestId, traceId));
    }

    /**
     * OpenAPI relation mutation. The application is part of the idempotency
     * scope so applications sharing one service member cannot replay or
     * conflict with each other.
     */
    @Transactional
    public RecordRuntimeViews.RecordMutationResponse mutateRelation(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            long applicationId,
            RecordRuntimeViews.RelationMutationRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        requireOpenApiCompositionMutationAccess(
                session, moduleCode, recordId, fieldCode, "RELATION");
        return mutations.idempotent(
                openApiCompositionMutationScope(
                        session, moduleCode, recordId, applicationId,
                        "relation", fieldCode),
                idempotencyKey, request,
                RecordRuntimeViews.RecordMutationResponse.class, 200,
                () -> mutateRelationNow(
                        session, moduleCode, recordId, fieldCode,
                        request, "OPENAPI", requestId, traceId));
    }

    private RecordRuntimeViews.RecordMutationResponse mutateRelationNow(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            RecordRuntimeViews.RelationMutationRequest request,
            String auditSource,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "update");
        var field = compositionField(catalog, fieldCode, "RELATION", true);
        var record = mutableCompositionRecord(session, moduleCode, catalog, recordId, request.expectedVersion());
        var current = compositions.relationState(session.systemId(), tenantId, recordId, field.id());
        var targets = new LinkedHashMap<String, RecordRuntimeViews.RelationTargetInput>();
        current.forEach(target -> targets.put(target.targetRecordId(), target));

        var removeIds = new HashSet<String>();
        for (var targetId : request.remove()) {
            if (targetId == null || !removeIds.add(targetId) || targets.remove(targetId) == null) {
                throw compositionInvalid("relations." + fieldCode, "remove contains an unavailable target");
            }
        }
        validateRelationAdds(session, catalog, field, request.add());
        for (var target : request.add()) {
            if (targets.putIfAbsent(target.targetRecordId(), target) != null) {
                throw compositionInvalid("relations." + fieldCode, "add contains a duplicate target");
            }
        }
        var replacement = orderedRelationTargets(targets, request.order(), fieldCode);
        var before = List.copyOf(current);
        var now = LocalDateTime.now();
        compositions.replaceRelation(session, tenantId, record.schemaVersionId(), record.moduleSnapshotId(),
                recordId, fieldCode, replacement, now);
        referenceMaterialization.recomputeParent(session.systemId(), tenantId, record.schemaVersionId(),
                record.moduleSnapshotId(), recordId, now);
        incrementCompositionVersion(session, tenantId, record, recordId, now);
        recomputeLocalOrThrow(session.systemId(), tenantId, record.schemaVersionId(),
                record.moduleSnapshotId(), recordId, now);
        referenceMaterialization.queueTargetUpdate(
                session.systemId(), tenantId, recordId, record.version() + 1, now);
        mutations.fieldChanged(
                session,
                recordId,
                record.version() + 1,
                "RECORD_RELATION_MUTATED",
                fieldCode,
                before,
                replacement,
                auditSource,
                requestId,
                traceId);
        return compositionMutationResponse(recordId, record, requestId, traceId);
    }

    @Transactional
    public RecordRuntimeViews.RecordMutationResponse mutateSubtable(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            RecordRuntimeViews.SubtableMutationRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + recordId + ":subtable:" + fieldCode,
                idempotencyKey, request, RecordRuntimeViews.RecordMutationResponse.class, 200,
                () -> mutateSubtableNow(
                        session, moduleCode, recordId, fieldCode,
                        request, "WEB", requestId, traceId));
    }

    /**
     * OpenAPI subtable mutation with an application-isolated idempotency scope.
     */
    @Transactional
    public RecordRuntimeViews.RecordMutationResponse mutateSubtable(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            long applicationId,
            RecordRuntimeViews.SubtableMutationRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        requireOpenApiCompositionMutationAccess(
                session, moduleCode, recordId, fieldCode, "SUBTABLE");
        return mutations.idempotent(
                openApiCompositionMutationScope(
                        session, moduleCode, recordId, applicationId,
                        "subtable", fieldCode),
                idempotencyKey, request,
                RecordRuntimeViews.RecordMutationResponse.class, 200,
                () -> mutateSubtableNow(
                        session, moduleCode, recordId, fieldCode,
                        request, "OPENAPI", requestId, traceId));
    }

    private RecordRuntimeViews.RecordMutationResponse mutateSubtableNow(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            RecordRuntimeViews.SubtableMutationRequest request,
            String auditSource,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "update");
        var field = compositionField(catalog, fieldCode, "SUBTABLE", true);
        requireSubtableOperations(field, request);
        var record = mutableCompositionRecord(session, moduleCode, catalog, recordId, request.expectedVersion());
        var current = compositions.subtableState(session, tenantId, recordId, field.id());
        var rows = new LinkedHashMap<String, RecordRuntimeViews.SubRowInput>();
        current.forEach(row -> rows.put(row.rowId(), row));

        var removed = new HashSet<String>();
        for (var reference : request.remove()) {
            var row = reference == null ? null : rows.get(reference.rowId());
            if (row == null || !removed.add(reference.rowId())
                    || row.expectedVersion() == null || row.expectedVersion() != reference.expectedVersion()) {
                throw compositionInvalid("subtables." + fieldCode, "remove contains an unavailable or stale row");
            }
            rows.remove(reference.rowId());
        }
        var updated = new HashSet<String>();
        for (var change : request.update()) {
            var row = change == null ? null : rows.get(change.rowId());
            if (row == null || !updated.add(change.rowId())
                    || row.expectedVersion() == null || row.expectedVersion() != change.expectedVersion()) {
                throw compositionInvalid("subtables." + fieldCode, "update contains an unavailable or stale row");
            }
            rows.put(change.rowId(), new RecordRuntimeViews.SubRowInput(null, change.rowId(),
                    change.expectedVersion(), change.ordinal(), change.values()));
        }
        var replacement = new ArrayList<>(rows.values());
        for (var added : request.add()) {
            replacement.add(new RecordRuntimeViews.SubRowInput(added.clientRowKey(), null, null,
                    added.ordinal(), added.values()));
        }
        replacement = orderedSubtableRows(replacement, request.order(), fieldCode);
        var before = List.copyOf(current);
        var now = LocalDateTime.now();
        compositions.replaceSubtable(session, tenantId, record.schemaVersionId(), record.moduleSnapshotId(),
                recordId, fieldCode, replacement, now);
        incrementCompositionVersion(session, tenantId, record, recordId, now);
        recomputeLocalOrThrow(session.systemId(), tenantId, record.schemaVersionId(),
                record.moduleSnapshotId(), recordId, now);
        referenceMaterialization.queueTargetUpdate(
                session.systemId(), tenantId, recordId, record.version() + 1, now);
        mutations.fieldChanged(
                session,
                recordId,
                record.version() + 1,
                "RECORD_SUBTABLE_MUTATED",
                fieldCode,
                before,
                replacement,
                auditSource,
                requestId,
                traceId);
        return compositionMutationResponse(recordId, record, requestId, traceId);
    }

    @Transactional
    public RecordRuntimeViews.ReferenceRetryResponse retryReference(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            RecordRuntimeViews.ReferenceRetryRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + recordId + ":reference:" + fieldCode + ":retry",
                idempotencyKey, request, RecordRuntimeViews.ReferenceRetryResponse.class, 200,
                () -> retryReferenceNow(session, moduleCode, recordId, fieldCode, request, requestId, traceId));
    }

    private RecordRuntimeViews.ReferenceRetryResponse retryReferenceNow(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            RecordRuntimeViews.ReferenceRetryRequest request,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "update");
        var field = catalog.fields().stream().filter(candidate -> candidate.code().equals(fieldCode)
                        && ("REFERENCE".equals(candidate.type()) || P4_C4_CROSS_TYPES.contains(candidate.type())))
                .findFirst().orElseThrow(RecordRuntimeService::notFound);
        var record = mutableCompositionRecord(session, moduleCode, catalog, recordId, request.expectedVersion());
        var now = LocalDateTime.now();
        long sourceRecordId;
        long sourceRecordVersion;
        String correlationId;
        if ("REFERENCE".equals(field.type())) {
            var retry = referenceMaterialization.retryFailed(session.systemId(), tenantId, recordId,
                    record.schemaVersionId(), record.moduleSnapshotId(), field.id(), now);
            if (retry == null) {
                throw new BusinessException("RECALCULATION_NOT_RETRYABLE",
                        "Recalculation is not in a retryable failed state", HttpStatus.CONFLICT);
            }
            sourceRecordId = retry.sourceRecordId();
            sourceRecordVersion = retry.sourceRecordVersion();
            correlationId = retry.correlationId();
        } else {
            var retry = derivedMaterialization.retryFailed(session.systemId(), tenantId, recordId,
                    record.schemaVersionId(), record.moduleSnapshotId(), field.id(), now);
            if (retry == null) {
                throw new BusinessException("RECALCULATION_NOT_RETRYABLE",
                        "Recalculation is not in a retryable failed state", HttpStatus.CONFLICT);
            }
            sourceRecordId = retry.sourceRecordId();
            sourceRecordVersion = retry.sourceRecordVersion();
            correlationId = retry.correlationId();
        }
        if (correlationId == null) {
            throw new BusinessException("RECALCULATION_NOT_RETRYABLE",
                    "Recalculation is not in a retryable failed state", HttpStatus.CONFLICT);
        }
        mutations.referenceRetryRequested(session, recordId, record.version(), sourceRecordId,
                sourceRecordVersion, correlationId, requestId, traceId);
        return new RecordRuntimeViews.ReferenceRetryResponse(Long.toString(recordId), record.version(), fieldCode,
                "PENDING", correlationId);
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail create(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.CreateRecordRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + session.memberId() + ":" + moduleCode + ":create",
                idempotencyKey, request, RecordRuntimeViews.RecordDetail.class, 201,
                () -> createNow(session, moduleCode, request, "DRAFT", "WEB", true, requestId, traceId)
        );
    }

    /**
     * Creates a record for a previously authenticated OpenAPI application.
     *
     * <p>The application id is deliberately part of the idempotency scope so
     * two applications represented by the same service member cannot collide.
     * Tenant, owner and audit values continue to come exclusively from the
     * authoritative runtime session and system-field services.</p>
     */
    @Transactional
    public RecordRuntimeViews.RecordDetail createOpenApi(
            RuntimeSession session,
            String moduleCode,
            long applicationId,
            String lifecycleState,
            Map<String, JsonNode> values,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        var state = requireOpenApiLifecycleState(lifecycleState);
        var request = new RecordRuntimeViews.CreateRecordRequest(
                null, null, values, List.of(), List.of());
        var idempotencyRequest = new OpenApiCreateIdentity(state, new java.util.TreeMap<>(request.values()));
        return mutations.idempotent(
                openApiCreateIdempotencyScope(session, moduleCode, applicationId),
                idempotencyKey, idempotencyRequest, RecordRuntimeViews.RecordDetail.class, 201,
                () -> createNow(session, moduleCode, request, state, "OPENAPI", false, requestId, traceId)
        );
    }

    static String openApiCreateIdempotencyScope(
            RuntimeSession session,
            String moduleCode,
            long applicationId
    ) {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        return session.systemId() + ":" + requiredTenant(session) + ":" + session.memberId() + ":"
                + applicationId + ":" + moduleCode + ":openapi:create";
    }

    static String openApiRecordMutationScope(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            long applicationId
    ) {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        if (recordId <= 0) {
            throw notFound();
        }
        return session.systemId() + ":" + requiredTenant(session) + ":" + session.memberId() + ":"
                + applicationId + ":" + moduleCode + ":" + recordId + ":openapi:mutation";
    }

    static String openApiCompositionMutationScope(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            long applicationId,
            String compositionKind,
            String fieldCode
    ) {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        if (recordId <= 0) {
            throw notFound();
        }
        requireModuleCode(moduleCode);
        if (!Set.of("relation", "subtable").contains(compositionKind)
                || fieldCode == null
                || !fieldCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw notFound();
        }
        var canonical = session.systemId() + ":" + requiredTenant(session) + ":" + session.memberId() + ":"
                + applicationId + ":" + moduleCode + ":" + recordId
                + ":openapi:composition:" + compositionKind + ":" + fieldCode;
        // un_sys_idempotency.scope_key is VARCHAR(128). Hash the complete,
        // validated identity so maximum-length codes and Snowflake ids stay
        // losslessly isolated without relying on database truncation.
        return "oac:" + sha256(canonical);
    }

    private void requireOpenApiCompositionMutationAccess(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String fieldCode,
            String fieldType
    ) {
        requireOpenApiMutationAccess(session, moduleCode, recordId, "update");
        compositionField(
                availableCatalog(session, moduleCode, "update"),
                fieldCode,
                fieldType,
                true
        );
    }

    private void requireOpenApiMutationAccess(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            String permissionVerb
    ) {
        if (recordId <= 0) {
            throw notFound();
        }
        var catalog = availableCatalog(session, moduleCode, permissionVerb);
        if (commandRows(session, catalog, recordId, false).isEmpty()) {
            throw notFound();
        }
    }

    private static String requireOpenApiMutationAction(String action) {
        if (action == null || !Set.of(
                "activate", "archive", "unarchive", "trash", "restore-from-trash").contains(action)) {
            throw new BusinessException("OPENAPI_RECORD_ACTION_INVALID",
                    "Unsupported external record lifecycle action", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return action;
    }

    private static LifecycleCommand openApiLifecycleCommand(String action) {
        return switch (action) {
            case "archive" -> LifecycleCommand.ARCHIVE;
            case "unarchive" -> LifecycleCommand.UNARCHIVE;
            case "trash" -> LifecycleCommand.TRASH;
            case "restore-from-trash" -> LifecycleCommand.RESTORE_FROM_TRASH;
            default -> throw new IllegalArgumentException("Action is not a lifecycle command: " + action);
        };
    }

    private RecordRuntimeViews.RecordDetail createNow(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.CreateRecordRequest request,
            String lifecycleState,
            String auditSource,
            boolean requireSuppliedSchema,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "create");
        if (requireSuppliedSchema) {
            requireCurrentSchema(request.schemaVersionId(), catalog.versionId());
        }
        var now = LocalDateTime.now();
        assertSystemClientValuesAllowed(session, catalog, request.values(), now);
        var references = referenceFacade.resolve(session.systemId(), tenantId, session.memberId());
        var normalizedClientValues = normalizeValues(session, catalog, references, request.values());
        enforceMutationRules(catalog, normalizedClientValues, request.values().keySet(), "CREATE",
                "ACTIVE".equals(lifecycleState));
        validateStatusChanges(List.of(), normalizedClientValues, true);
        validateAggregateRelations(session, catalog, request.relations());
        var recordId = idService.nextId();
        var recordNo = "R-" + recordId;
        var title = title(request.title(), normalizedClientValues, recordNo);
        var generatedSystemValues = systemFields.onCreate(systemScope(session, catalog), systemDefinitions(catalog),
                clientValueMarkers(request.values()), new SystemFieldValueService.ActorContext(session.memberId(), now));
        var normalized = new ArrayList<>(normalizedClientValues);
        normalized.addAll(systemNormalizedValues(catalog, generatedSystemValues));
        if ("ACTIVE".equals(lifecycleState)) {
            requireActivationValues(catalog, normalized);
        }
        jdbc.update("INSERT INTO un_module_record "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "record_no,title,status,prior_status,owner_member_id,owner_department_id,draft_expires_at,"
                        + "deleted_at,deleted_by,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,NULL,?,?,?,NULL,NULL,?,?,?,?,0)",
                recordId, session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), Long.parseLong(catalog.moduleId()), recordNo, title,
                lifecycleState, session.memberId(), references.primaryDepartmentId(),
                "DRAFT".equals(lifecycleState) ? now.plusDays(30) : null, now, session.memberId(),
                now, session.memberId());
        for (var value : normalized) {
            insertValue(session, tenantId, catalog, recordId, value, lifecycleState, now);
        }
        synchronizeFileFields(session, recordId, catalog, normalized, request.values(), now);
        compositions.replace(session, tenantId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), recordId, request.relations(), request.subtables(), now);
        referenceMaterialization.recomputeParent(session.systemId(), tenantId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), recordId, now);
        recomputeLocalOrThrow(session.systemId(), tenantId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), recordId, now);
        referenceMaterialization.queueTargetUpdate(session.systemId(), tenantId, recordId, 0, now);
        var persisted = persistedValues(session, catalog, recordId);
        if ("ACTIVE".equals(lifecycleState)) {
            replaceUniqueReservations(session, tenantId, catalog, recordId, lifecycleState, persisted, now);
        }
        var response = response(recordId, recordNo, 0, lifecycleState, title, catalog.versionId(), persisted,
                ruleActions(session, moduleCode, lifecycleState, catalog, persisted));
        var auditAction = "ACTIVE".equals(lifecycleState) ? "RECORD_CREATED" : "RECORD_DRAFT_CREATED";
        mutations.changed(session, recordId, 0, auditAction, null,
                redactSensitive(response), requestId, traceId, Set.of(), auditSource);
        publishRecordEvent(
                session,
                moduleCode,
                recordId,
                0,
                recordNo,
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_CREATED,
                persisted);
        return response;
    }

    private static String requireOpenApiLifecycleState(String lifecycleState) {
        if (lifecycleState == null || !Set.of("DRAFT", "ACTIVE").contains(lifecycleState.trim())) {
            throw new BusinessException("OPENAPI_LIFECYCLE_INVALID",
                    "lifecycleState must be DRAFT or ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return lifecycleState.trim();
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail update(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            RecordRuntimeViews.UpdateRecordRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        var input = new WriteInput(request.schemaVersionId(), request.title(), request.expectedVersion(),
                request.values(), request.relations(), request.subtables());
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + session.memberId() + ":" + moduleCode + ":"
                        + recordId + ":update",
                idempotencyKey, request, RecordRuntimeViews.RecordDetail.class, 200,
                () -> writeNow(session, moduleCode, recordId, input, false, true, "WEB", requestId, traceId)
                        .record()
        );
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail updateOpenApi(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            long applicationId,
            long expectedVersion,
            Map<String, JsonNode> values,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        requireOpenApiMutationAccess(session, moduleCode, recordId, "update");
        var supplied = values == null ? Map.<String, JsonNode>of() : values;
        var identity = new OpenApiMutationIdentity(
                "update", expectedVersion, new java.util.TreeMap<>(supplied));
        var input = new WriteInput(null, null, expectedVersion, supplied, List.of(), List.of());
        return mutations.idempotent(
                openApiRecordMutationScope(session, moduleCode, recordId, applicationId),
                idempotencyKey, identity, RecordRuntimeViews.RecordDetail.class, 200,
                () -> writeNow(session, moduleCode, recordId, input, false, false, "OPENAPI", requestId, traceId)
                        .record()
        );
    }

    @Transactional
    public RecordRuntimeViews.AutosaveRecordResponse autosave(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            RecordRuntimeViews.AutosaveRecordRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        var input = new WriteInput(request.schemaVersionId(), request.title(), request.expectedVersion(),
                request.values(), List.of(), List.of());
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + session.memberId() + ":" + moduleCode + ":"
                        + recordId + ":autosave",
                idempotencyKey, request, RecordRuntimeViews.AutosaveRecordResponse.class, 200,
                () -> {
                    var result = writeNow(session, moduleCode, recordId, input, true, true, "WEB",
                            requestId, traceId);
                    return new RecordRuntimeViews.AutosaveRecordResponse(
                            result.record(), result.savedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            result.expiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
        );
    }

    private WriteResult writeNow(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            WriteInput input,
            boolean autosave,
            boolean requireSuppliedSchema,
            String auditSource,
            String requestId,
            String traceId
    ) {
        if (recordId <= 0) {
            throw notFound();
        }
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "update");
        if (requireSuppliedSchema) {
            requireCurrentSchema(input.schemaVersionId(), catalog.versionId());
        }
        assertSystemClientValuesAllowed(session, catalog, input.values(), LocalDateTime.now());
        var records = commandRows(session, catalog, recordId, true);
        if (records.isEmpty()) {
            throw notFound();
        }
        var record = records.getFirst();
        if (record.schemaVersionId() != Long.parseLong(catalog.versionId())
                || record.moduleSnapshotId() != Long.parseLong(catalog.moduleSnapshotId())) {
            throw new BusinessException("RECORD_SCHEMA_STALE", "记录使用的发布版本已经不是当前版本",
                    HttpStatus.CONFLICT);
        }
        if (autosave && !"DRAFT".equals(record.status())) {
            throw new BusinessException("RECORD_STATE_INVALID", "只有草稿记录可以自动保存", HttpStatus.CONFLICT);
        }
        if (!autosave && !Set.of("DRAFT", "ACTIVE").contains(record.status())) {
            throw new BusinessException("RECORD_STATE_INVALID", "当前记录状态不允许编辑", HttpStatus.CONFLICT);
        }

        var beforeValues = persistedValues(session, catalog, recordId);
        var before = response(recordId, record.recordNo(), record.version(), record.status(), record.title(),
                catalog.versionId(), beforeValues,
                ruleActions(session, moduleCode, record.status(), catalog, beforeValues));
        if (input.expectedVersion() < 0 || record.version() != input.expectedVersion()) {
            throw versionConflict(input, before);
        }

        var references = referenceFacade.resolve(session.systemId(), tenantId, session.memberId());
        var normalized = normalizeValues(session, catalog, references, input.values());
        var ruleState = patchedRuleValues(beforeValues, normalized, input.values().keySet());
        enforceMutationRules(catalog, ruleState, input.values().keySet(), "UPDATE",
                !autosave && "ACTIVE".equals(record.status()));
        validateStatusChanges(beforeValues, normalized, false);
        if (!autosave) {
            validateAggregateRelations(session, catalog, input.relations());
        }
        var nextTitle = input.title() == null ? record.title()
                : title(input.title(), normalized, record.recordNo());
        var now = LocalDateTime.now();
        var expiresAt = autosave ? now.plusDays(30) : record.draftExpiresAt();
        final int updated;
        if (autosave) {
            updated = jdbc.update("UPDATE un_module_record SET title=?,draft_expires_at=?,updated_at=?,updated_by=?,"
                            + "version=version+1 WHERE system_id=? AND tenant_id=? AND record_id=? "
                            + "AND schema_version_id=? AND module_snapshot_id=? AND status='DRAFT' AND version=?",
                    nextTitle, expiresAt, now, session.memberId(), session.systemId(), tenantId, recordId,
                    record.schemaVersionId(), record.moduleSnapshotId(), input.expectedVersion());
        } else {
            updated = jdbc.update("UPDATE un_module_record SET title=?,updated_at=?,updated_by=?,version=version+1 "
                            + "WHERE system_id=? AND tenant_id=? AND record_id=? AND schema_version_id=? "
                            + "AND module_snapshot_id=? AND status=? AND version=?",
                    nextTitle, now, session.memberId(), session.systemId(), tenantId, recordId,
                    record.schemaVersionId(), record.moduleSnapshotId(), record.status(), input.expectedVersion());
        }
        if (updated != 1) {
            throw versionConflict(input, before);
        }

        replaceWritableValues(session, tenantId, catalog, recordId, record.status(), normalized, input.values(), now);
        synchronizeFileFields(session, recordId, catalog, normalized, input.values(), now);
        var generatedSystemValues = systemFields.onUpdate(systemScope(session, catalog), systemDefinitions(catalog),
                clientValueMarkers(input.values()), new SystemFieldValueService.ActorContext(session.memberId(), now));
        replaceSystemValues(session, tenantId, catalog, recordId, record.status(),
                systemNormalizedValues(catalog, generatedSystemValues), now);
        if (!autosave) {
            compositions.replace(session, tenantId, record.schemaVersionId(), record.moduleSnapshotId(), recordId,
                    input.relations(), input.subtables(), now);
            referenceMaterialization.recomputeParent(session.systemId(), tenantId, record.schemaVersionId(),
                    record.moduleSnapshotId(), recordId, now);
        }
        recomputeLocalOrThrow(session.systemId(), tenantId, record.schemaVersionId(),
                record.moduleSnapshotId(), recordId, now);
        if (!autosave) {
            referenceMaterialization.queueTargetUpdate(
                    session.systemId(), tenantId, recordId, record.version() + 1, now);
        }
        var currentValues = persistedValues(session, catalog, recordId);
        if ("ACTIVE".equals(record.status())) {
            replaceUniqueReservations(session, tenantId, catalog, recordId, record.status(), currentValues, now);
        }
        var current = response(recordId, record.recordNo(), record.version() + 1, record.status(), nextTitle,
                catalog.versionId(), currentValues,
                ruleActions(session, moduleCode, record.status(), catalog, currentValues));
        var forcedMaskedChanges = Set.copyOf(catalog.fields().stream()
                .filter(field -> SENSITIVE_TYPES.contains(field.type()))
                .filter(field -> input.values().containsKey(field.code()))
                .map(FieldDescriptor::code)
                .toList());
        mutations.changed(session, recordId, record.version() + 1,
                autosave ? "RECORD_DRAFT_AUTOSAVED" : "RECORD_UPDATED",
                redactSensitive(before), redactSensitive(current), requestId, traceId,
                forcedMaskedChanges, auditSource);
        if (!autosave) {
            publishRecordEvent(
                    session,
                    moduleCode,
                    recordId,
                    record.version() + 1,
                    record.recordNo(),
                    RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_UPDATED,
                    currentValues);
        }
        return new WriteResult(current, now, expiresAt);
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail activate(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            RecordRuntimeViews.VersionCommandRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + recordId + ":activate",
                idempotencyKey, request, RecordRuntimeViews.RecordDetail.class, 200,
                () -> activateNow(session, moduleCode, recordId, request, "WEB", requestId, traceId)
        );
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail lifecycleOpenApi(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            long applicationId,
            String action,
            long expectedVersion,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var normalizedAction = requireOpenApiMutationAction(action);
        var lifecycleCommand = "activate".equals(normalizedAction)
                ? null : openApiLifecycleCommand(normalizedAction);
        var permissionVerb = lifecycleCommand == null ? "update" : lifecycleCommand.permissionVerb();
        requireOpenApiMutationAccess(session, moduleCode, recordId, permissionVerb);
        var identity = new OpenApiMutationIdentity(normalizedAction, expectedVersion, Map.of());
        var request = new RecordRuntimeViews.VersionCommandRequest(expectedVersion);
        return mutations.idempotent(
                openApiRecordMutationScope(session, moduleCode, recordId, applicationId),
                idempotencyKey, identity, RecordRuntimeViews.RecordDetail.class, 200,
                () -> lifecycleCommand == null
                        ? activateNow(session, moduleCode, recordId, request, "OPENAPI", requestId, traceId)
                        : lifecycleNow(session, moduleCode, recordId, request, lifecycleCommand, "OPENAPI",
                                requestId, traceId)
        );
    }

    private RecordRuntimeViews.RecordDetail activateNow(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            RecordRuntimeViews.VersionCommandRequest request,
            String auditSource,
            String requestId,
            String traceId
    ) {
        if (recordId <= 0) {
            throw notFound();
        }
        if (request.expectedVersion() < 0) {
            throw versionConflict();
        }
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "update");
        var records = commandRows(session, catalog, recordId, true);
        if (records.isEmpty()) {
            throw notFound();
        }
        var record = records.getFirst();
        if (record.schemaVersionId() != Long.parseLong(catalog.versionId())
                || record.moduleSnapshotId() != Long.parseLong(catalog.moduleSnapshotId())) {
            throw new BusinessException("RECORD_SCHEMA_STALE", "草稿使用的发布版本已经不是当前版本",
                    HttpStatus.CONFLICT);
        }
        if (!"DRAFT".equals(record.status())) {
            throw new BusinessException("RECORD_STATE_INVALID", "只有草稿记录可以激活", HttpStatus.CONFLICT);
        }
        if (record.version() != request.expectedVersion()) {
            throw versionConflict();
        }
        var normalized = persistedValues(session, catalog, recordId);
        enforceMutationRules(catalog, normalized, Set.of(), "ACTIVATE", true);
        requireActivationValues(catalog, normalized);
        var now = LocalDateTime.now();
        var updated = jdbc.update("UPDATE un_module_record SET status='ACTIVE',draft_expires_at=NULL,updated_at=?,"
                        + "updated_by=?,version=version+1 WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND status='DRAFT' AND version=?",
                now, session.memberId(), session.systemId(), tenantId, recordId, record.schemaVersionId(),
                record.moduleSnapshotId(), request.expectedVersion());
        if (updated != 1) {
            throw versionConflict();
        }
        jdbc.update("UPDATE un_module_record_index SET record_status='ACTIVE',updated_at=? WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=?",
                now, session.systemId(), tenantId, recordId, record.schemaVersionId(), record.moduleSnapshotId());
        jdbc.update("UPDATE un_module_record_search SET record_status='ACTIVE' WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=?",
                session.systemId(), tenantId, recordId, record.schemaVersionId(), record.moduleSnapshotId());
        replaceUniqueReservations(session, tenantId, catalog, recordId, "ACTIVE", normalized, now);
        referenceMaterialization.queueTargetUpdate(
                session.systemId(), tenantId, recordId, record.version() + 1, now);
        var response = response(recordId, record.recordNo(), record.version() + 1, "ACTIVE", record.title(),
                catalog.versionId(), normalized,
                ruleActions(session, moduleCode, "ACTIVE", catalog, normalized));
        mutations.changed(session, recordId, record.version() + 1, "RECORD_ACTIVATED",
                Map.of("status", record.status(), "version", record.version()),
                redactSensitive(response), requestId, traceId, Set.of(), auditSource);
        publishRecordEvent(
                session,
                moduleCode,
                recordId,
                record.version() + 1,
                record.recordNo(),
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                normalized);
        return response;
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail archive(
            RuntimeSession session, String moduleCode, long recordId,
            RecordRuntimeViews.VersionCommandRequest request, String idempotencyKey,
            String requestId, String traceId
    ) {
        return lifecycle(session, moduleCode, recordId, request, idempotencyKey,
                LifecycleCommand.ARCHIVE, requestId, traceId);
    }

    @Transactional
    public RecordRuntimeViews.BatchMutationResponse batchArchive(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchCommandRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return batchLifecycle(
                session,
                moduleCode,
                request,
                idempotencyKey,
                LifecycleCommand.ARCHIVE,
                1,
                requestId,
                traceId);
    }

    @Transactional
    public RecordRuntimeViews.BatchMutationResponse batchTrash(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchCommandRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        return batchLifecycle(
                session,
                moduleCode,
                request,
                idempotencyKey,
                LifecycleCommand.TRASH,
                0,
                requestId,
                traceId);
    }

    @Transactional
    public RecordRuntimeViews.BatchMutationResponse batchTransfer(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchTransferRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + session.memberId() + ":" + moduleCode
                        + ":batch-transfer",
                idempotencyKey,
                request,
                RecordRuntimeViews.BatchMutationResponse.class,
                200,
                () -> batchTransferNow(session, moduleCode, request, requestId, traceId));
    }

    private RecordRuntimeViews.BatchMutationResponse batchTransferNow(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchTransferRequest request,
            String requestId,
            String traceId
    ) {
        var plan = RecordBatchTransferPlan.from(request);
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "action.transfer");
        var target = activeMemberFacade.lockActiveMember(
                        session.systemId(),
                        tenantId,
                        plan.targetMemberId())
                .orElseThrow(RecordRuntimeService::invalidTransferTarget);
        var records = batchTransferRows(session, catalog, plan.lockOrder());
        var teams = teamOwnershipFacade.lockExistingTeams(
                session.systemId(),
                tenantId,
                records.stream().map(RecordBatchTransferPlan.RecordState::recordId).toList(),
                target.memberId());
        var prepared = plan.preflight(records, teams);
        var recordsById = new HashMap<Long, RecordBatchTransferPlan.RecordState>();
        records.forEach(record -> recordsById.put(record.recordId(), record));
        var now = LocalDateTime.now();
        return prepared.apply(recordId -> {
            var record = recordsById.get(recordId);
            var updated = jdbc.update(
                    RecordBatchTransferPlan.UPDATE_RECORD_SQL,
                    target.memberId(),
                    target.primaryDepartmentId(),
                    now,
                    session.memberId(),
                    session.systemId(),
                    tenantId,
                    Long.parseLong(catalog.moduleId()),
                    Long.parseLong(catalog.versionId()),
                    Long.parseLong(catalog.moduleSnapshotId()),
                    recordId,
                    record.ownerMemberId(),
                    record.version());
            if (updated != 1) {
                throw versionConflict();
            }
            teamOwnershipFacade.transferOwnership(
                    session.systemId(),
                    tenantId,
                    recordId,
                    record.ownerMemberId(),
                    target.memberId(),
                    session.memberId());
            var nextVersion = record.version() + 1;
            mutations.changed(
                    session,
                    recordId,
                    nextVersion,
                    "RECORD_TRANSFERRED",
                    ownershipSnapshot(record.ownerMemberId(), record.ownerDepartmentId()),
                    ownershipSnapshot(target.memberId(), target.primaryDepartmentId()),
                    requestId,
                    traceId);
            return nextVersion;
        });
    }

    @Transactional
    public RecordRuntimeViews.BatchMutationResponse batchEdit(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchEditRequest request,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + session.memberId() + ":" + moduleCode
                        + ":batch-edit",
                idempotencyKey,
                request,
                RecordRuntimeViews.BatchMutationResponse.class,
                200,
                () -> batchEditNow(session, moduleCode, request, requestId, traceId));
    }

    private RecordRuntimeViews.BatchMutationResponse batchEditNow(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchEditRequest request,
            String requestId,
            String traceId
    ) {
        var plan = RecordBatchEditPlan.from(request);
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, "update");
        var edits = batchEditFields(session, tenantId, catalog, plan.changes());
        var records = batchEditRows(session, catalog, plan.lockOrder());
        var prepared = plan.preflight(records.stream()
                .map(record -> new RecordBatchEditPlan.RecordState(
                        record.recordId(),
                        record.version(),
                        record.status(),
                        record.schemaVersionId() == Long.parseLong(catalog.versionId())
                                && record.moduleSnapshotId() == Long.parseLong(catalog.moduleSnapshotId())))
                .toList());

        var recordsById = new HashMap<Long, CommandRecord>();
        var beforeValuesById = new HashMap<Long, List<NormalizedValue>>();
        for (var record : records) {
            recordsById.put(record.recordId(), record);
            var beforeValues = persistedValues(session, catalog, record.recordId());
            var afterValues = patchedValues(catalog, beforeValues, edits);
            enforceMutationRules(catalog, afterValues,
                    edits.stream().map(edit -> edit.field().code())
                            .collect(java.util.stream.Collectors.toSet()),
                    "UPDATE", "ACTIVE".equals(record.status()));
            validateBatchEditRecord(catalog, beforeValues, afterValues);
            beforeValuesById.put(record.recordId(), beforeValues);
        }
        validateBatchEditUniqueValues(session, tenantId, catalog, plan.lockOrder(), edits);

        var touchedUniqueFieldIds = edits.stream()
                .map(BatchEditField::field)
                .filter(field -> "UNIQUE".equals(field.indexMode()))
                .map(FieldDescriptor::id)
                .toList();
        deleteTouchedUniqueReservations(
                session, tenantId, catalog, plan.lockOrder(), touchedUniqueFieldIds);

        var now = LocalDateTime.now();
        return prepared.apply(recordId -> {
            var record = recordsById.get(recordId);
            var beforeValues = beforeValuesById.get(recordId);
            var before = response(
                    recordId, record.recordNo(), record.version(), record.status(), record.title(),
                    catalog.versionId(), beforeValues,
                    ruleActions(session, moduleCode, record.status(), catalog, beforeValues));
            var updated = jdbc.update(
                    RecordBatchEditPlan.UPDATE_RECORD_SQL,
                    now,
                    session.memberId(),
                    session.systemId(),
                    tenantId,
                    Long.parseLong(catalog.moduleId()),
                    Long.parseLong(catalog.versionId()),
                    Long.parseLong(catalog.moduleSnapshotId()),
                    recordId,
                    record.version());
            if (updated != 1) {
                throw versionConflict();
            }
            replaceTouchedValues(
                    session, tenantId, catalog, recordId, record.status(), edits, now);
            var generatedSystemValues = systemFields.onUpdate(
                    systemScope(session, catalog),
                    systemDefinitions(catalog),
                    Map.of(),
                    new SystemFieldValueService.ActorContext(session.memberId(), now));
            replaceSystemValues(
                    session, tenantId, catalog, recordId, record.status(),
                    systemNormalizedValues(catalog, generatedSystemValues), now);
            try {
                referenceMaterialization.recomputeParent(
                        session.systemId(), tenantId, record.schemaVersionId(),
                        record.moduleSnapshotId(), recordId, now);
                recomputeLocalOrThrow(
                        session.systemId(), tenantId, record.schemaVersionId(),
                        record.moduleSnapshotId(), recordId, now);
            } catch (BusinessException exception) {
                throw batchEditValidation(exception);
            }
            referenceMaterialization.queueTargetUpdate(
                    session.systemId(), tenantId, recordId, record.version() + 1, now);
            var currentValues = persistedValues(session, catalog, recordId);
            try {
                insertUniqueReservations(
                        session, tenantId, catalog, recordId, record.status(),
                        currentValues.stream()
                                .filter(value -> touchedUniqueFieldIds.contains(value.field().id()))
                                .toList(),
                        now);
            } catch (BusinessException exception) {
                throw batchEditValidation(exception);
            }
            var current = response(
                    recordId, record.recordNo(), record.version() + 1, record.status(), record.title(),
                    catalog.versionId(), currentValues,
                    ruleActions(session, moduleCode, record.status(), catalog, currentValues));
            mutations.changed(
                    session, recordId, record.version() + 1, "RECORD_BATCH_EDITED",
                    redactSensitive(before), redactSensitive(current), requestId, traceId);
            publishRecordEvent(
                    session,
                    moduleCode,
                    recordId,
                    record.version() + 1,
                    record.recordNo(),
                    RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_UPDATED,
                    currentValues);
            return record.version() + 1;
        });
    }

    private RecordRuntimeViews.BatchMutationResponse batchLifecycle(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchCommandRequest request,
            String idempotencyKey,
            LifecycleCommand command,
            long minimumVersion,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + session.memberId() + ":" + moduleCode
                        + ":batch-" + command.key(),
                idempotencyKey,
                request,
                RecordRuntimeViews.BatchMutationResponse.class,
                200,
                () -> batchLifecycleNow(
                        session,
                        moduleCode,
                        request,
                        command,
                        minimumVersion,
                        requestId,
                        traceId));
    }

    private RecordRuntimeViews.BatchMutationResponse batchLifecycleNow(
            RuntimeSession session,
            String moduleCode,
            RecordRuntimeViews.BatchCommandRequest request,
            LifecycleCommand command,
            long minimumVersion,
            String requestId,
            String traceId
    ) {
        var plan = RecordBatchMutationPlan.from(request, minimumVersion, command.key());
        var catalog = availableCatalog(session, moduleCode, command.permissionVerb());
        var lockedRecords = batchCommandRows(session, catalog, plan.lockOrder());
        var prepared = plan.preflight(lockedRecords.stream()
                .map(record -> new RecordBatchMutationPlan.RecordState(
                        record.recordId(), record.version(), record.status()))
                .toList(), command.sources());

        var selectionsById = new HashMap<Long, RecordBatchMutationPlan.Selection>();
        var recordsById = new HashMap<Long, CommandRecord>();
        for (var selection : plan.requestOrder()) {
            selectionsById.put(selection.recordId(), selection);
        }
        var valuesById = new HashMap<Long, List<NormalizedValue>>();
        for (var record : lockedRecords) {
            requireCurrentRecordSchema(record, catalog);
            var currentValues = persistedValues(session, catalog, record.recordId());
            if (command == LifecycleCommand.ARCHIVE) {
                requireActivationValues(catalog, currentValues);
            }
            enforceLifecycleRule(catalog, currentValues, command);
            valuesById.put(record.recordId(), currentValues);
            recordsById.put(record.recordId(), record);
        }

        return prepared.apply(batchTargetStatus(command), recordId -> {
            var record = recordsById.get(recordId);
            var selection = selectionsById.get(recordId);
            var current = applyLifecycleTransition(
                    session,
                    moduleCode,
                    catalog,
                    record,
                    valuesById.get(record.recordId()),
                    selection.expectedVersion(),
                    command,
                    "WEB",
                    requestId,
                    traceId);
            return current.version();
        });
    }

    private static String batchTargetStatus(LifecycleCommand command) {
        return switch (command) {
            case ARCHIVE -> "ARCHIVED";
            case TRASH -> "TRASHED";
            default -> throw new IllegalArgumentException("Lifecycle command does not support batch mutation");
        };
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail unarchive(
            RuntimeSession session, String moduleCode, long recordId,
            RecordRuntimeViews.VersionCommandRequest request, String idempotencyKey,
            String requestId, String traceId
    ) {
        return lifecycle(session, moduleCode, recordId, request, idempotencyKey,
                LifecycleCommand.UNARCHIVE, requestId, traceId);
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail trash(
            RuntimeSession session, String moduleCode, long recordId,
            RecordRuntimeViews.VersionCommandRequest request, String idempotencyKey,
            String requestId, String traceId
    ) {
        return lifecycle(session, moduleCode, recordId, request, idempotencyKey,
                LifecycleCommand.TRASH, requestId, traceId);
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail restoreFromTrash(
            RuntimeSession session, String moduleCode, long recordId,
            RecordRuntimeViews.VersionCommandRequest request, String idempotencyKey,
            String requestId, String traceId
    ) {
        return lifecycle(session, moduleCode, recordId, request, idempotencyKey,
                LifecycleCommand.RESTORE_FROM_TRASH, requestId, traceId);
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail discard(
            RuntimeSession session, String moduleCode, long recordId,
            RecordRuntimeViews.VersionCommandRequest request, String idempotencyKey,
            String requestId, String traceId
    ) {
        return lifecycle(session, moduleCode, recordId, request, idempotencyKey,
                LifecycleCommand.DISCARD, requestId, traceId);
    }

    @Transactional
    public RecordRuntimeViews.RecordDetail recover(
            RuntimeSession session, String moduleCode, long recordId,
            RecordRuntimeViews.VersionCommandRequest request, String idempotencyKey,
            String requestId, String traceId
    ) {
        return lifecycle(session, moduleCode, recordId, request, idempotencyKey,
                LifecycleCommand.RECOVER, requestId, traceId);
    }

    private RecordRuntimeViews.RecordDetail lifecycle(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            RecordRuntimeViews.VersionCommandRequest request,
            String idempotencyKey,
            LifecycleCommand command,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        return mutations.idempotent(
                session.systemId() + ":" + tenantId + ":" + session.memberId() + ":" + moduleCode + ":"
                        + recordId + ":" + command.key(),
                idempotencyKey, request, RecordRuntimeViews.RecordDetail.class, 200,
                () -> lifecycleNow(session, moduleCode, recordId, request, command, "WEB", requestId, traceId)
        );
    }

    private RecordRuntimeViews.RecordDetail lifecycleNow(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            RecordRuntimeViews.VersionCommandRequest request,
            LifecycleCommand command,
            String auditSource,
            String requestId,
            String traceId
    ) {
        if (recordId <= 0) {
            throw notFound();
        }
        var tenantId = requiredTenant(session);
        var catalog = availableCatalog(session, moduleCode, command.permissionVerb());
        var records = commandRows(session, catalog, recordId, true);
        if (records.isEmpty()) {
            throw notFound();
        }
        var record = records.getFirst();
        requireCurrentRecordSchema(record, catalog);
        var currentValues = persistedValues(session, catalog, recordId);
        enforceLifecycleRule(catalog, currentValues, command);
        var before = response(recordId, record.recordNo(), record.version(), record.status(), record.title(),
                catalog.versionId(), currentValues,
                ruleActions(session, moduleCode, record.status(), catalog, currentValues));
        if (request.expectedVersion() < 0 || record.version() != request.expectedVersion()) {
            throw versionConflict(before);
        }
        if (!command.sources().contains(record.status())) {
            throw new BusinessException("RECORD_STATE_INVALID",
                    "The record state does not allow this lifecycle action", HttpStatus.CONFLICT);
        }

        return applyLifecycleTransition(
                session,
                moduleCode,
                catalog,
                record,
                currentValues,
                request.expectedVersion(),
                command,
                auditSource,
                requestId,
                traceId);
    }

    private RecordRuntimeViews.RecordDetail applyLifecycleTransition(
            RuntimeSession session,
            String moduleCode,
            Catalog catalog,
            CommandRecord record,
            List<NormalizedValue> currentValues,
            long expectedVersion,
            LifecycleCommand command,
            String auditSource,
            String requestId,
            String traceId
    ) {
        var tenantId = requiredTenant(session);
        var recordId = record.recordId();
        var before = response(recordId, record.recordNo(), record.version(), record.status(), record.title(),
                catalog.versionId(), currentValues,
                ruleActions(session, moduleCode, record.status(), catalog, currentValues));
        var targetStatus = command.targetStatus(record);
        if (Set.of("ACTIVE", "ARCHIVED").contains(targetStatus)) {
            requireActivationValues(catalog, currentValues);
        }
        var now = LocalDateTime.now();
        var nextPriorStatus = "TRASHED".equals(targetStatus) ? record.status() : null;
        var nextExpiry = "DRAFT".equals(targetStatus) ? now.plusDays(30) : null;
        var deletedAt = "TRASHED".equals(targetStatus) ? now : null;
        var deletedBy = "TRASHED".equals(targetStatus) ? session.memberId() : null;
        var updated = jdbc.update("UPDATE un_module_record SET status=?,prior_status=?,draft_expires_at=?,"
                        + "deleted_at=?,deleted_by=?,updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND status=? AND version=?",
                targetStatus, nextPriorStatus, nextExpiry, deletedAt, deletedBy, now, session.memberId(),
                session.systemId(), tenantId, recordId, record.schemaVersionId(), record.moduleSnapshotId(),
                record.status(), expectedVersion);
        if (updated != 1) {
            throw versionConflict(before);
        }
        jdbc.update("UPDATE un_module_record_index SET record_status=?,updated_at=? WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=?",
                targetStatus, now, session.systemId(), tenantId, recordId, record.schemaVersionId(),
                record.moduleSnapshotId());
        jdbc.update("UPDATE un_module_record_search SET record_status=? WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=?",
                targetStatus, session.systemId(), tenantId, recordId, record.schemaVersionId(),
                record.moduleSnapshotId());
        replaceUniqueReservations(session, tenantId, catalog, recordId, targetStatus, currentValues, now);
        referenceMaterialization.queueTargetUpdate(
                session.systemId(), tenantId, recordId, record.version() + 1, now);
        var current = response(recordId, record.recordNo(), record.version() + 1, targetStatus, record.title(),
                catalog.versionId(), currentValues,
                ruleActions(session, moduleCode, targetStatus, catalog, currentValues));
        mutations.changed(session, recordId, record.version() + 1, command.eventType(),
                redactSensitive(before), redactSensitive(current), requestId, traceId, Set.of(), auditSource);
        publishRecordEvent(
                session,
                moduleCode,
                recordId,
                record.version() + 1,
                record.recordNo(),
                command.triggerEvent(),
                currentValues);
        return current;
    }

    private void publishRecordEvent(
            RuntimeSession session,
            String moduleCode,
            long recordId,
            long recordVersion,
            String businessKey,
            RuntimeRecordFlowTriggerFacade.TriggerEvent event,
            List<NormalizedValue> values
    ) {
        flowTriggers.publish(
                session,
                moduleCode,
                recordId,
                recordVersion,
                businessKey,
                event,
                RecordFlowEventValueSnapshot.from(
                        objectMapper,
                        values.stream()
                                .map(value -> new RecordFlowEventValueSnapshot.Value(
                                        value.field().code(),
                                        value.field().type(),
                                        value.value()))
                                .toList()));
    }

    private static void requireCurrentRecordSchema(CommandRecord record, Catalog catalog) {
        if (record.schemaVersionId() != Long.parseLong(catalog.versionId())
                || record.moduleSnapshotId() != Long.parseLong(catalog.moduleSnapshotId())) {
            throw new BusinessException("RECORD_SCHEMA_STALE",
                    "The record uses a configuration version that is no longer current", HttpStatus.CONFLICT);
        }
    }

    private void recomputeLocalOrThrow(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            LocalDateTime now
    ) {
        try {
            derivedMaterialization.recomputeLocal(systemId, tenantId, schemaVersionId,
                    moduleSnapshotId, recordId, now);
        } catch (IllegalStateException exception) {
            throw new BusinessException("DERIVED_EVALUATION_FAILED",
                    "A derived field could not be evaluated safely", HttpStatus.UNPROCESSABLE_ENTITY,
                    List.of(new ApiError("DERIVED_EVALUATION_FAILED", "derivedFields",
                            "Repair the source values or the published derived declaration")));
        }
    }

    private Catalog availableCatalog(RuntimeSession session, String moduleCode) {
        return availableCatalog(session, moduleCode, "view");
    }

    private Catalog availableCatalog(RuntimeSession session, String moduleCode, String permissionVerb) {
        return requireAvailable(catalog(session, moduleCode, permissionVerb));
    }

    private HistoricalCatalog availableHistoricalCatalog(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId,
            String permissionVerb
    ) {
        var historical = historicalCatalog(
                session, moduleCode, schemaVersionId, permissionVerb);
        requireAvailable(historical.catalog());
        return historical;
    }

    private Catalog requireAvailable(Catalog catalog) {
        if (catalog.unavailableReason() != null) {
            if (SENSITIVE_KEY_UNAVAILABLE_REASON.equals(catalog.unavailableReason())) {
                throw new BusinessException("SENSITIVE_KEY_UNAVAILABLE", catalog.unavailableReason(),
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
            throw new BusinessException("FIELD_RUNTIME_UNAVAILABLE", catalog.unavailableReason(), HttpStatus.CONFLICT);
        }
        return catalog;
    }

    private Catalog catalog(RuntimeSession session, String moduleCode) {
        return catalog(session, moduleCode, "view");
    }

    private Catalog catalog(RuntimeSession session, String moduleCode, String permissionVerb) {
        requireModuleCode(moduleCode);
        var activeRows = jdbc.query("SELECT v.id,v.snapshot_checksum,v.snapshot_json "
                        + "FROM un_module_config_root r JOIN un_module_config_version v "
                        + "ON v.system_id=r.system_id AND v.id=r.active_version_id WHERE r.system_id=?",
                (result, row) -> new Active(result.getLong("id"), result.getString("snapshot_checksum"),
                        parse(result.getString("snapshot_json"))), session.systemId());
        if (activeRows.isEmpty()) {
            throw new BusinessException("MODULE_NOT_PUBLISHED", "系统尚未发布模块配置", HttpStatus.NOT_FOUND);
        }
        return catalog(session, moduleCode, permissionVerb,
                activeRows.getFirst());
    }

    private HistoricalCatalog historicalCatalog(
            RuntimeSession session,
            String moduleCode,
            String schemaVersionId,
            String permissionVerb
    ) {
        requireModuleCode(moduleCode);
        var versionId = positiveSchemaVersionId(schemaVersionId);
        var versions = jdbc.query(
                "SELECT id,snapshot_checksum,snapshot_json "
                        + "FROM un_module_config_version "
                        + "WHERE system_id=? AND id=? "
                        + "AND source_type IN ('PUBLISH','ROLLBACK')",
                (result, row) -> new Active(
                        result.getLong("id"),
                        result.getString("snapshot_checksum"),
                        parse(result.getString("snapshot_json"))),
                session.systemId(), versionId);
        if (versions.size() != 1) {
            throw historicalUnavailable(
                    "Exact published module schema is unavailable");
        }
        var version = versions.getFirst();
        if (version.checksum() == null
                || !version.checksum().matches("^[0-9a-f]{64}$")
                || !version.snapshot().isObject()
                || !version.snapshot().path("modules").isArray()
                || !version.snapshot().path("fields").isArray()) {
            throw historicalUnavailable(
                    "Exact published module schema is invalid");
        }
        return historicalCatalog(
                session, moduleCode, permissionVerb, version);
    }

    private HistoricalCatalog historicalCatalog(
            RuntimeSession session,
            String moduleCode,
            String permissionVerb,
            Active version
    ) {
        var catalog = catalog(session, moduleCode, permissionVerb, version);
        var identity = requireHistoricalProjection(
                session, catalog, moduleCode);
        return new HistoricalCatalog(catalog, identity, version);
    }

    private Catalog catalog(
            RuntimeSession session,
            String moduleCode,
            String permissionVerb,
            Active active
    ) {
        JsonNode module = null;
        for (var candidate : active.snapshot().path("modules")) {
            if (enabled(candidate) && moduleCode.equals(candidate.path("module_code").asText())) {
                module = candidate;
                break;
            }
        }
        if (module == null) {
            throw new BusinessException("MODULE_NOT_PUBLISHED", "模块未发布或已停用", HttpStatus.NOT_FOUND);
        }
        var permissionCode = "module." + moduleCode + "." + permissionVerb;
        var grant = authorizationFacade.resolve(new RuntimeAuthorizationFacade.RuntimeAuthorizationRequest(
                session.systemId(), requiredTenant(session), session.memberId(), permissionCode));
        if (grant.denied() || !session.permissions().contains(permissionCode)) {
            throw new BusinessException("PERMISSION_DENIED", "当前成员没有执行此记录操作的权限", HttpStatus.FORBIDDEN);
        }
        var moduleId = module.path("id").asText();
        var fieldPermissions = permissionsByResource(active.snapshot(), "FIELD");
        var fields = new ArrayList<FieldDescriptor>();
        String unavailableReason = null;
        for (var field : sorted(active.snapshot().path("fields"))) {
            if (!moduleId.equals(field.path("module_id").asText()) || !enabled(field) || flag(field, "is_hidden")) {
                continue;
            }
            var fieldType = field.path("field_type").asText();
            if (!SUPPORTED_TYPES.contains(fieldType)) {
                if (flag(field, "is_required")) {
                    unavailableReason = "必填字段 " + field.path("field_name").asText() + " 尚未进入当前运行切片";
                }
                continue;
            }
            if (SENSITIVE_TYPES.contains(fieldType) && !sensitiveCrypto.available()) {
                unavailableReason = SENSITIVE_KEY_UNAVAILABLE_REASON;
            }
            if ("REFERENCE".equals(fieldType)) {
                var target = referenceTarget(field, active.snapshot());
                if (!session.permissions().contains("module." + target.moduleCode() + ".view")) {
                    continue;
                }
            }
            var derivedTarget = P4_C4_CROSS_TYPES.contains(fieldType)
                    ? derivedTarget(field, active.snapshot()) : null;
            var derivedQueryAllowed = true;
            if (derivedTarget != null) {
                var targetView = "module." + derivedTarget.moduleCode() + ".view";
                if (!session.permissions().contains(targetView)) continue;
                var targetRead = "module." + derivedTarget.moduleCode() + ".field."
                        + derivedTarget.fieldCode() + ".read";
                var targetPermissions = fieldPermissions.getOrDefault(derivedTarget.fieldId(), Set.of());
                if (targetPermissions.contains(targetRead) && !session.permissions().contains(targetRead)) continue;
                var targetGrant = authorizationFacade.resolve(new RuntimeAuthorizationFacade.RuntimeAuthorizationRequest(
                        session.systemId(), requiredTenant(session), session.memberId(), targetView));
                if (targetGrant.denied()) continue;
                derivedQueryAllowed = targetGrant.allRecords();
            }
            var fieldId = field.path("id").asText();
            var fieldCode = field.path("field_code").asText();
            var declaredPermissions = fieldPermissions.getOrDefault(fieldId, Set.of());
            var readPermission = "module." + moduleCode + ".field." + fieldCode + ".read";
            if (declaredPermissions.contains(readPermission) && !session.permissions().contains(readPermission)) {
                continue;
            }
            var writePermission = "module." + moduleCode + ".field." + fieldCode + ".write";
            var writable = !SYSTEM_COMPUTED_TYPES.contains(fieldType) && !flag(field, "is_readonly")
                    && (!declaredPermissions.contains(writePermission) || session.permissions().contains(writePermission));
            var sensitiveReadable = "IDENTITY".equals(fieldType) && session.permissions().contains(
                    "module." + moduleCode + ".field." + fieldCode + ".sensitive.read");
            var sensitiveQueryable = SENSITIVE_TYPES.contains(fieldType) && session.permissions().contains(
                    "module." + moduleCode + ".field." + fieldCode + ".sensitive.query");
            fields.add(descriptor(field, active.snapshot(), writable, sensitiveReadable, sensitiveQueryable,
                    derivedQueryAllowed));
        }
        var readableFieldCodesById = new LinkedHashMap<String, String>();
        fields.forEach(field -> readableFieldCodesById.put(Long.toString(field.id()), field.code()));
        var publishedRuntime = pageRuleRuntime.compile(
                Long.toString(active.versionId()), active.snapshot(), moduleId, readableFieldCodesById);
        var identities = jdbc.query(
                "SELECT module_snapshot_id,logical_module_id FROM un_module_runtime_schema_module "
                        + "WHERE system_id=? AND schema_version_id=? AND module_code=?",
                (result, row) -> new ModuleIdentity(
                        result.getLong("module_snapshot_id"), result.getLong("logical_module_id")),
                session.systemId(), active.versionId(), moduleCode);
        if (identities.size() != 1
                || identities.getFirst().logicalModuleId() != Long.parseLong(moduleId)) {
            throw new BusinessException("FIELD_RUNTIME_UNAVAILABLE",
                    "Published module identity is absent from the runtime schema", HttpStatus.CONFLICT);
        }
        return new Catalog(Long.toString(active.versionId()), active.checksum(), moduleId,
                Long.toString(identities.getFirst().moduleSnapshotId()),
                List.copyOf(fields), unavailableReason, grant, publishedRuntime);
    }

    private FieldDescriptor descriptor(
            JsonNode field,
            JsonNode snapshot,
            boolean writable,
            boolean sensitiveReadable,
            boolean sensitiveQueryable,
            boolean derivedQueryAllowed
    ) {
        var type = field.path("field_type").asText();
        var indexMode = field.path("index_mode").asText("NONE");
        var effectiveQueryType = "REFERENCE".equals(type) ? referenceQueryType(field, snapshot)
                : P4_C4_DERIVED_TYPES.contains(type) ? derivedQueryType(field)
                : systemQueryType(type);
        var indexActive = !"NONE".equals(indexMode) || "REFERENCE".equals(type)
                || P4_C4_DERIVED_TYPES.contains(type) || SYSTEM_COMPUTED_TYPES.contains(type);
        var declaredSubtableQuery = "SUBTABLE".equals(type)
                && field.path("property_json").path("aggregates").isArray()
                && !field.path("property_json").path("aggregates").isEmpty();
        var searchable = flag(field, "is_searchable") && indexActive
                && Set.of("TEXT", "TEXTAREA", "RICH_TEXT").contains(type);
        var filterable = derivedQueryAllowed && ("RELATION".equals(type) || declaredSubtableQuery
                || (flag(field, "is_filterable") || "REFERENCE".equals(type)) && indexActive)
                && (!SENSITIVE_TYPES.contains(type) || sensitiveQueryable);
        var sortable = derivedQueryAllowed
                && (Set.of("SORT", "UNIQUE", "STATISTIC").contains(indexMode) || "REFERENCE".equals(type))
                && (!P4_C2_TYPES.contains(type) || Set.of("BARCODE", "STATUS").contains(type))
                && !"LOOKUP".equals(type);
        var property = field.path("property_json");
        var schema = property.isObject() ? ((ObjectNode) property).deepCopy() : objectMapper.createObjectNode();
        if (field.hasNonNull("target_module_id")) {
            schema.put("targetModuleId", field.path("target_module_id").asText());
        }
        if ("REFERENCE".equals(type)) {
            schema.put("referenceResultType", effectiveQueryType);
            var target = referenceTarget(field, snapshot);
            schema.put("referenceTargetModuleId", target.moduleId());
            schema.put("referenceTargetModuleCode", target.moduleCode());
        }
        if (P4_C4_DERIVED_TYPES.contains(type)) {
            schema.put("derivedQueryType", effectiveQueryType);
            schema.put("evaluatorVersion", 1);
            var target = P4_C4_CROSS_TYPES.contains(type) ? derivedTarget(field, snapshot) : null;
            if (target != null) {
                schema.put("derivedTargetModuleId", target.moduleId());
                schema.put("derivedTargetModuleCode", target.moduleCode());
                schema.put("derivedTargetFieldId", target.fieldId());
                schema.put("derivedTargetFieldCode", target.fieldCode());
                schema.put("derivedQueryScope", derivedQueryAllowed ? "ALL" : "READ_ONLY_SCOPED");
            }
        }
        schema.put("required", flag(field, "is_required"));
        schema.put("readonly", !writable);
        schema.put("unique", "UNIQUE".equals(indexMode));
        var options = Set.of("RADIO", "MULTI_SELECT", "CASCADE", "STATUS").contains(type)
                ? dictionaryOptions(snapshot, field.path("dictionary_id").asText())
                : List.<CanonicalFieldValueCodec.ValueOption>of();
        return new FieldDescriptor(
                Long.parseLong(field.path("id").asText()), field.path("field_code").asText(),
                field.path("field_name").asText(), type, flag(field, "show_in_list"),
                flag(field, "show_in_detail"), flag(field, "is_required"), writable,
                sensitiveReadable, sensitiveQueryable, searchable, filterable, sortable, indexMode,
                filterable ? operators(effectiveQueryType) : List.of(), options, schema);
    }

    private static String queryType(FieldDescriptor field) {
        if ("REFERENCE".equals(field.type())) {
            return field.schema().path("referenceResultType").asText("TEXT");
        }
        return P4_C4_DERIVED_TYPES.contains(field.type())
                ? field.schema().path("derivedQueryType").asText("TEXT") : systemQueryType(field.type());
    }

    private static String systemQueryType(String type) {
        return switch (type) {
            case "AUTO_NUMBER" -> "TEXT";
            case "TENANT", "CREATED_BY", "UPDATED_BY" -> "MEMBER";
            case "CREATED_AT", "UPDATED_AT" -> "DATETIME";
            default -> type;
        };
    }

    private static String derivedQueryType(JsonNode field) {
        return switch (field.path("property_json").path("resultSchema").asText()) {
            case "STRING" -> "TEXT";
            case "DECIMAL", "INTEGER" -> "NUMBER";
            case "DATE" -> "DATE";
            case "DATETIME" -> "DATETIME";
            case "BOOLEAN" -> "SWITCH";
            default -> throw new IllegalStateException("Published derived result schema is unsupported");
        };
    }

    private static String referenceQueryType(JsonNode field, JsonNode snapshot) {
        var targetFieldId = field.path("property_json").path("targetFieldId").asText("");
        for (var target : snapshot.path("fields")) {
            if (!targetFieldId.equals(target.path("id").asText())) {
                continue;
            }
            return switch (target.path("field_type").asText()) {
                case "TEXT" -> "TEXT";
                case "NUMBER", "RATING" -> "NUMBER";
                case "DATE" -> "DATE";
                case "DATETIME" -> "DATETIME";
                case "SWITCH" -> "SWITCH";
                default -> throw new IllegalStateException("Published REFERENCE result type is unsupported");
            };
        }
        throw new IllegalStateException("Published REFERENCE target field is unavailable");
    }

    private static ReferenceTarget referenceTarget(JsonNode field, JsonNode snapshot) {
        var sourceFieldId = field.path("property_json").path("sourceFieldId").asText("");
        String targetModuleId = null;
        for (var source : snapshot.path("fields")) {
            if (sourceFieldId.equals(source.path("id").asText())) {
                targetModuleId = source.path("target_module_id").asText("");
                break;
            }
        }
        for (var module : snapshot.path("modules")) {
            if (module.path("id").asText().equals(targetModuleId)) {
                return new ReferenceTarget(targetModuleId, module.path("module_code").asText());
            }
        }
        throw new IllegalStateException("Published REFERENCE target module is unavailable");
    }

    private static DerivedTarget derivedTarget(JsonNode field, JsonNode snapshot) {
        if ("AGGREGATE".equals(field.path("field_type").asText())) return null;
        var relationFieldId = field.path("property_json").path("relationFieldId").asText("");
        String targetModuleId = null;
        for (var relation : snapshot.path("fields")) {
            if (relationFieldId.equals(relation.path("id").asText())
                    && "RELATION".equals(relation.path("field_type").asText())) {
                targetModuleId = relation.path("target_module_id").asText("");
                break;
            }
        }
        String targetModuleCode = null;
        for (var module : snapshot.path("modules")) {
            if (module.path("id").asText().equals(targetModuleId)) {
                targetModuleCode = module.path("module_code").asText();
                break;
            }
        }
        if (targetModuleCode == null) {
            throw new IllegalStateException("Published derived target module is unavailable");
        }
        var targetFieldId = field.path("property_json").path("targetFieldId").asText("0");
        var targetFieldCode = "";
        if (!"0".equals(targetFieldId)) {
            for (var target : snapshot.path("fields")) {
                if (targetFieldId.equals(target.path("id").asText())) {
                    targetFieldCode = target.path("field_code").asText();
                    break;
                }
            }
            if (targetFieldCode.isEmpty()) {
                throw new IllegalStateException("Published derived target field is unavailable");
            }
        }
        return new DerivedTarget(targetModuleId, targetModuleCode, targetFieldId, targetFieldCode);
    }

    private List<RecordQueryModels.SensitiveHash> sensitiveQueryHashes(
            RuntimeSession session,
            Catalog catalog,
            RecordQueryModels.QueryField queryField,
            JsonNode supplied
    ) {
        var field = catalog.fields().stream().filter(candidate -> candidate.id() == queryField.id())
                .findFirst().orElseThrow(() -> invalidQuery("Sensitive field is unavailable"));
        if (!SENSITIVE_TYPES.contains(field.type()) || !field.sensitiveQueryable()) {
            throw new BusinessException("QUERY_FIELD_UNAVAILABLE",
                    "Sensitive query permission is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        final CanonicalFieldValueCodec.CanonicalValue canonical;
        try {
            canonical = fieldValueCodec.normalize(new CanonicalFieldValueCodec.FieldContract(
                    field.code(), field.name(), field.type(), field.schema(), field.options()), supplied);
        } catch (BusinessException exception) {
            if (!"FIELD_VALUE_INVALID".equals(exception.code())) {
                throw exception;
            }
            throw invalidQuery("Sensitive equality value is invalid");
        }
        var context = new SensitiveCryptoService.BlindIndexContext(
                session.systemId(), requiredTenant(session), Long.parseLong(catalog.moduleId()),
                field.id(), field.type());
        return sensitiveCrypto.equalityHashes((String) canonical.value(), context).stream()
                .map(hash -> new RecordQueryModels.SensitiveHash(hash.hashKeyVersion(), hash.valueHash()))
                .toList();
    }

    private List<RecordRow> detailRows(
            RuntimeSession session,
            Catalog catalog,
            long recordId,
            String statusPredicate
    ) {
        var scope = scope(session, catalog);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(catalog.moduleId()));
        arguments.add(Long.parseLong(catalog.versionId()));
        arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        arguments.add(recordId);
        arguments.addAll(scope.arguments());
        return jdbc.query("SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id "
                        + "FROM un_module_record r WHERE r.system_id=? AND r.tenant_id=? "
                        + "AND r.logical_module_id=? AND r.schema_version_id=? AND r.module_snapshot_id=? "
                        + "AND r.record_id=? AND " + statusPredicate + " AND " + scope.sql(),
                (result, row) -> recordRow(result), arguments.toArray());
    }

    private List<CommandRecord> commandRows(
            RuntimeSession session,
            Catalog catalog,
            long recordId,
            boolean lock
    ) {
        var scope = scope(session, catalog);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(catalog.moduleId()));
        arguments.add(recordId);
        arguments.addAll(scope.arguments());
        return jdbc.query("SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id,"
                        + "r.module_snapshot_id,r.draft_expires_at,r.prior_status FROM un_module_record r "
                        + "WHERE r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? AND r.record_id=? AND "
                        + scope.sql() + (lock ? " FOR UPDATE" : ""),
                (result, row) -> commandRecord(result), arguments.toArray());
    }

    private List<CommandRecord> batchCommandRows(
            RuntimeSession session,
            Catalog catalog,
            List<Long> recordIds
    ) {
        var scope = scope(session, catalog);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(catalog.moduleId()));
        arguments.add(Long.parseLong(catalog.versionId()));
        arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        arguments.addAll(recordIds);
        arguments.addAll(scope.arguments());
        return jdbc.query("SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id,"
                        + "r.module_snapshot_id,r.draft_expires_at,r.prior_status FROM un_module_record r "
                        + "WHERE r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? "
                        + "AND r.schema_version_id=? AND r.module_snapshot_id=? AND r.record_id IN ("
                        + placeholders(recordIds.size()) + ") AND " + scope.sql()
                        + " ORDER BY r.record_id ASC FOR UPDATE",
                (result, row) -> commandRecord(result), arguments.toArray());
    }

    private List<RecordBatchTransferPlan.RecordState> batchTransferRows(
            RuntimeSession session,
            Catalog catalog,
            List<Long> recordIds
    ) {
        var scope = scope(session, catalog);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(catalog.moduleId()));
        arguments.add(Long.parseLong(catalog.versionId()));
        arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        arguments.addAll(recordIds);
        arguments.addAll(scope.arguments());
        return jdbc.query(
                "SELECT r.record_id,r.version,r.status,r.owner_member_id,r.owner_department_id "
                        + "FROM un_module_record r WHERE r.system_id=? AND r.tenant_id=? "
                        + "AND r.logical_module_id=? AND r.schema_version_id=? AND r.module_snapshot_id=? "
                        + "AND r.record_id IN (" + placeholders(recordIds.size()) + ") AND " + scope.sql()
                        + " ORDER BY r.record_id ASC FOR UPDATE",
                (result, row) -> new RecordBatchTransferPlan.RecordState(
                        result.getLong("record_id"),
                        result.getLong("version"),
                        result.getString("status"),
                        result.getLong("owner_member_id"),
                        result.getObject("owner_department_id") == null
                                ? null
                                : result.getLong("owner_department_id")),
                arguments.toArray());
    }

    private List<CommandRecord> batchEditRows(
            RuntimeSession session,
            Catalog catalog,
            List<Long> recordIds
    ) {
        var scope = scope(session, catalog);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(catalog.moduleId()));
        arguments.addAll(recordIds);
        arguments.addAll(scope.arguments());
        return jdbc.query(
                "SELECT r.record_id,r.record_no,r.version,r.status,r.title,r.schema_version_id,"
                        + "r.module_snapshot_id,r.draft_expires_at,r.prior_status FROM un_module_record r "
                        + "WHERE r.system_id=? AND r.tenant_id=? AND r.logical_module_id=? "
                        + "AND r.record_id IN (" + placeholders(recordIds.size()) + ") AND " + scope.sql()
                        + " ORDER BY r.record_id ASC FOR UPDATE",
                (result, row) -> commandRecord(result),
                arguments.toArray());
    }

    private RecordRuntimeViews.RecordDetail detailResponse(
            RuntimeSession session,
            String moduleCode,
            Catalog catalog,
            RecordRow record
    ) {
        var recordValues = values(session, catalog, List.of(record.recordId()), catalog.fields())
                .getOrDefault(record.recordId(), List.of());
        var decision = ruleDecisionFromView(catalog, recordValues);
        var visibleValues = recordValues.stream()
                .filter(value -> !decision.hiddenFields().contains(value.fieldCode()))
                .toList();
        return new RecordRuntimeViews.RecordDetail(
                Long.toString(record.recordId()), record.recordNo(), record.version(), record.status(), record.title(),
                Long.toString(record.schemaVersionId()), visibleValues,
                applyRuleActions(actions(session, moduleCode, record.status()), decision));
    }

    private List<String> actions(RuntimeSession session, String moduleCode, String status) {
        var result = new ArrayList<String>();
        if ("DRAFT".equals(status) && allowed(session, moduleCode, "update")) {
            result.addAll(List.of("UPDATE", "AUTOSAVE", "ACTIVATE"));
        } else if ("ACTIVE".equals(status) && allowed(session, moduleCode, "update")) {
            result.add("UPDATE");
        }
        if ("ACTIVE".equals(status) && allowed(session, moduleCode, "action.archive")) {
            result.add("ARCHIVE");
        }
        if ("ARCHIVED".equals(status) && allowed(session, moduleCode, "action.unarchive")) {
            result.add("UNARCHIVE");
        }
        if (Set.of("DRAFT", "ACTIVE", "ARCHIVED", "EXPIRED").contains(status)
                && allowed(session, moduleCode, "delete")) {
            result.add("DRAFT".equals(status) ? "DISCARD" : "TRASH");
        }
        if ("TRASHED".equals(status) && allowed(session, moduleCode, "action.restore_trash")) {
            result.add("RESTORE_TRASH");
        }
        if ("EXPIRED".equals(status) && allowed(session, moduleCode, "action.recover_draft")) {
            result.add("RECOVER_DRAFT");
        }
        if (Set.of("ACTIVE", "ARCHIVED").contains(status) && allowed(session, moduleCode, "print")) {
            result.add("PRINT");
        }
        return List.copyOf(result);
    }

    private List<String> ruleActions(
            RuntimeSession session,
            String moduleCode,
            String status,
            Catalog catalog,
            List<NormalizedValue> values
    ) {
        return applyRuleActions(actions(session, moduleCode, status), ruleDecision(catalog, values));
    }

    private static List<String> applyRuleActions(
            List<String> base,
            RecordRuntimeViews.RuntimeRuleDecision decision
    ) {
        var result = new ArrayList<>(base);
        var disabled = decision.disabledActions();
        result.removeIf(action -> disabled.contains(action)
                || "UPDATE".equals(action) && disabled.contains("UPDATE")
                || Set.of("TRASH", "DISCARD").contains(action) && disabled.contains("DELETE")
                || "PRINT".equals(action) && disabled.contains("PRINT"));
        if (!decision.deleteAllowed()) {
            result.removeIf(action -> Set.of("TRASH", "DISCARD").contains(action));
        }
        if (decision.approvalRequired() && !disabled.contains("APPROVAL")) {
            result.add("START_APPROVAL");
        }
        return List.copyOf(new LinkedHashSet<>(result));
    }

    private RecordRuntimeViews.RuntimeRuleDecision ruleDecision(
            Catalog catalog,
            List<NormalizedValue> values
    ) {
        var state = new LinkedHashMap<String, JsonNode>();
        values.forEach(value -> state.put(value.field().code(), objectMapper.valueToTree(value.value())));
        return pageRuleRuntime.evaluate(catalog.publishedRuntime(), state,
                catalog.fields().stream().filter(FieldDescriptor::required)
                        .map(FieldDescriptor::code).collect(java.util.stream.Collectors.toSet()),
                catalog.fields().stream().filter(field -> !field.writable())
                        .map(FieldDescriptor::code).collect(java.util.stream.Collectors.toSet()));
    }

    private RecordRuntimeViews.RuntimeRuleDecision ruleDecisionFromView(
            Catalog catalog,
            List<RecordRuntimeViews.FieldValue> values
    ) {
        var state = new LinkedHashMap<String, JsonNode>();
        values.forEach(value -> state.put(value.fieldCode(), objectMapper.valueToTree(value.value())));
        return pageRuleRuntime.evaluate(catalog.publishedRuntime(), state,
                catalog.fields().stream().filter(FieldDescriptor::required)
                        .map(FieldDescriptor::code).collect(java.util.stream.Collectors.toSet()),
                catalog.fields().stream().filter(field -> !field.writable())
                        .map(FieldDescriptor::code).collect(java.util.stream.Collectors.toSet()));
    }

    private static List<NormalizedValue> patchedRuleValues(
            List<NormalizedValue> before,
            List<NormalizedValue> supplied,
            Set<String> suppliedCodes
    ) {
        var result = new LinkedHashMap<String, NormalizedValue>();
        before.forEach(value -> result.put(value.field().code(), value));
        suppliedCodes.forEach(result::remove);
        supplied.forEach(value -> result.put(value.field().code(), value));
        return List.copyOf(result.values());
    }

    private void enforceMutationRules(
            Catalog catalog,
            List<NormalizedValue> state,
            Set<String> suppliedCodes,
            String action,
            boolean requireRequired
    ) {
        var decision = ruleDecision(catalog, state);
        if (decision.disabledActions().contains(action)) {
            throw new BusinessException("RUNTIME_RULE_DENIED",
                    "Published business rules disable this action", HttpStatus.FORBIDDEN);
        }
        for (var fieldCode : suppliedCodes) {
            if (decision.hiddenFields().contains(fieldCode)) {
                throw fieldError("RECORD_FIELD_FORBIDDEN", fieldCode,
                        "Published rules hide this field for the current value state", HttpStatus.FORBIDDEN);
            }
            if (decision.readonlyFields().contains(fieldCode)) {
                throw fieldError("RECORD_FIELD_FORBIDDEN", fieldCode,
                        "Published rules make this field readonly for the current value state", HttpStatus.FORBIDDEN);
            }
        }
        if (!requireRequired) return;
        var present = state.stream().map(value -> value.field().code())
                .collect(java.util.stream.Collectors.toSet());
        var missing = decision.requiredFields().stream()
                .filter(code -> !decision.hiddenFields().contains(code))
                .filter(code -> catalog.fields().stream().anyMatch(field -> field.code().equals(code)
                        && !P4_C3_COMPOSITION_TYPES.contains(field.type())))
                .filter(code -> !present.contains(code))
                .findFirst();
        if (missing.isPresent()) {
            var field = catalog.fields().stream().filter(candidate -> candidate.code().equals(missing.get()))
                    .findFirst().orElseThrow();
            throw fieldError("RECORD_VALIDATION_FAILED", field.code(),
                    field.name() + " is required by the published rule", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void enforceLifecycleRule(
            Catalog catalog,
            List<NormalizedValue> state,
            LifecycleCommand command
    ) {
        var decision = ruleDecision(catalog, state);
        if (Set.of(LifecycleCommand.TRASH, LifecycleCommand.DISCARD).contains(command)
                && (!decision.deleteAllowed() || decision.disabledActions().contains("DELETE"))) {
            throw new BusinessException("RUNTIME_RULE_DENIED",
                    "Published business rules do not allow deleting this record", HttpStatus.FORBIDDEN);
        }
    }

    private Map<Long, List<RecordRuntimeViews.FieldValue>> values(
            RuntimeSession session,
            Catalog catalog,
            List<Long> recordIds,
            List<FieldDescriptor> fields
    ) {
        if (recordIds.isEmpty() || fields.isEmpty()) {
            return Map.of();
        }
        var scalarFields = fields.stream().filter(field -> !P4_C3_COMPOSITION_TYPES.contains(field.type())).toList();
        var fieldById = new HashMap<Long, FieldDescriptor>();
        scalarFields.forEach(field -> fieldById.put(field.id(), field));
        var grouped = new LinkedHashMap<Long, LinkedHashMap<Long, List<PersistedCell>>>();
        if (!scalarFields.isEmpty()) {
            var sql = "SELECT record_id,field_snapshot_id,logical_field_id,field_type,ordinal,string_value,text_value,"
                    + "decimal_value,date_value,datetime_value,time_value,boolean_value,currency_code,reference_value,"
                    + "encrypted_value,encryption_key_version,value_hash,hash_key_version,display_value,result_schema,"
                    + "dependency_version_json,evaluator_version,recalculation_state,failure_correlation_id "
                    + "FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND logical_module_id=? "
                    + "AND schema_version_id=? AND module_snapshot_id=? AND record_id IN ("
                    + placeholders(recordIds.size()) + ") AND field_snapshot_id IN ("
                    + placeholders(scalarFields.size()) + ") ORDER BY record_id,field_snapshot_id,ordinal";
            var arguments = new ArrayList<Object>();
            arguments.add(session.systemId());
            arguments.add(requiredTenant(session));
            arguments.add(Long.parseLong(catalog.moduleId()));
            arguments.add(Long.parseLong(catalog.versionId()));
            arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
            arguments.addAll(recordIds);
            arguments.addAll(scalarFields.stream().map(FieldDescriptor::id).toList());
            jdbc.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) row -> grouped
                    .computeIfAbsent(row.getLong("record_id"), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getLong("field_snapshot_id"), ignored -> new ArrayList<>())
                    .add(persistedCell(row, session, catalog,
                            fieldById.get(row.getLong("field_snapshot_id")))), arguments.toArray());
        }
        var referenceValues = referenceValues(session, catalog, recordIds, fields.stream()
                .filter(field -> "REFERENCE".equals(field.type())).toList());
        var relationValues = relationValues(session, catalog, recordIds, fields.stream()
                .filter(field -> "RELATION".equals(field.type())).toList());
        var result = new LinkedHashMap<Long, List<RecordRuntimeViews.FieldValue>>();
        for (var recordId : recordIds) {
            var byField = grouped.get(recordId);
            var recordValues = new ArrayList<RecordRuntimeViews.FieldValue>();
            for (var field : fields) {
                if ("REFERENCE".equals(field.type())) {
                    var reference = referenceValues.getOrDefault(recordId, Map.of()).get(field.id());
                    if (reference != null) {
                        recordValues.add(reference);
                    }
                    continue;
                }
                if ("RELATION".equals(field.type())) {
                    var relation = relationValues.getOrDefault(recordId, Map.of()).get(field.id());
                    if (relation != null) {
                        recordValues.add(relation);
                    }
                    continue;
                }
                if (P4_C3_COMPOSITION_TYPES.contains(field.type()) || byField == null) {
                    continue;
                }
                var cells = byField.get(field.id());
                if (cells == null || cells.isEmpty()) {
                    continue;
                }
                cells = authorizedDerivedCells(session, field, cells);
                if (cells.isEmpty()) {
                    continue;
                }
                recordValues.add(new RecordRuntimeViews.FieldValue(
                        field.code(), field.name(), field.type(), persistedValue(field.type(), cells),
                        persistedDisplay(field.type(), cells), cells.getFirst().failureCorrelationId()));
            }
            result.put(recordId, List.copyOf(recordValues));
        }
        return result;
    }

    private Map<Long, Map<Long, RecordRuntimeViews.FieldValue>> relationValues(
            RuntimeSession session,
            Catalog source,
            List<Long> recordIds,
            List<FieldDescriptor> fields
    ) {
        if (recordIds.isEmpty() || fields.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<Long, Map<Long, RecordRuntimeViews.FieldValue>>();
        for (var field : fields) {
            var targetModuleId = field.schema().path("targetModuleId").asText("");
            if (!targetModuleId.matches("^[1-9][0-9]{0,18}$")) {
                continue;
            }
            var targetCode = targetModuleCode(
                    session.systemId(), Long.parseLong(source.versionId()), targetModuleId);
            var target = availableCatalog(session, targetCode);
            var targetScope = scope(session, target);
            var arguments = new ArrayList<Object>();
            arguments.add(session.systemId());
            arguments.add(requiredTenant(session));
            arguments.add(Long.parseLong(source.versionId()));
            arguments.add(Long.parseLong(source.moduleSnapshotId()));
            arguments.addAll(recordIds);
            arguments.add(field.id());
            arguments.add(Long.parseLong(target.moduleId()));
            arguments.addAll(targetScope.arguments());
            var rows = jdbc.query(
                    "SELECT rel.source_record_id,rel.target_record_id,r.version,rel.ordinal,"
                            + "COALESCE(NULLIF(r.title,''),r.record_no) AS target_title "
                            + "FROM un_module_record_relation rel JOIN un_module_record r "
                            + "ON r.system_id=rel.system_id AND r.tenant_id=rel.tenant_id "
                            + "AND r.record_id=rel.target_record_id "
                            + "AND r.schema_version_id=rel.target_schema_version_id "
                            + "AND r.module_snapshot_id=rel.target_module_snapshot_id "
                            + "WHERE rel.system_id=? AND rel.tenant_id=? "
                            + "AND rel.source_schema_version_id=? AND rel.source_module_snapshot_id=? "
                            + "AND rel.source_record_id IN (" + placeholders(recordIds.size()) + ") "
                            + "AND rel.source_field_snapshot_id=? AND rel.target_logical_module_id=? "
                            + "AND r.status IN ('ACTIVE','ARCHIVED') AND " + targetScope.sql()
                            + " ORDER BY rel.source_record_id,rel.ordinal",
                    (row, number) -> new RelationProjection(
                            row.getLong("source_record_id"),
                            row.getLong("target_record_id"),
                            row.getLong("version"),
                            row.getInt("ordinal"),
                            row.getString("target_title")),
                    arguments.toArray());
            if (rows.isEmpty()) {
                continue;
            }
            var displayField = relationDisplayField(field, target);
            if (field.schema().hasNonNull("displayFieldId") && displayField == null) {
                continue;
            }
            var displayByTarget = new HashMap<Long, String>();
            if (displayField != null) {
                var targetIds = rows.stream().map(RelationProjection::targetRecordId).distinct().toList();
                var projected = values(session, target, targetIds, List.of(displayField));
                for (var targetId : targetIds) {
                    var values = projected.getOrDefault(targetId, List.of());
                    if (values.isEmpty()) {
                        continue;
                    }
                    var value = values.getFirst();
                    displayByTarget.put(targetId, value.displayValue() == null
                            ? String.valueOf(value.value()) : value.displayValue());
                }
            }
            var bySource = rows.stream().collect(Collectors.groupingBy(
                    RelationProjection::sourceRecordId,
                    LinkedHashMap::new,
                    Collectors.toList()));
            for (var entry : bySource.entrySet()) {
                var targets = entry.getValue();
                var ids = targets.stream().map(row -> Long.toString(row.targetRecordId())).toList();
                var display = targets.stream().map(row ->
                        displayByTarget.getOrDefault(row.targetRecordId(), row.title())).toList();
                Object value = field.schema().path("multiple").asBoolean(false)
                        ? ids : ids.getFirst();
                result.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>()).put(
                        field.id(),
                        new RecordRuntimeViews.FieldValue(
                                field.code(), field.name(), field.type(), value,
                                String.join(", ", display), null));
            }
        }
        return result;
    }

    private List<PersistedCell> authorizedDerivedCells(
            RuntimeSession session,
            FieldDescriptor field,
            List<PersistedCell> cells
    ) {
        if (!Set.of("SUMMARY", "LOOKUP").contains(field.type())) {
            return cells;
        }
        var targetCode = field.schema().path("derivedTargetModuleCode").asText("");
        if (targetCode.isEmpty()) {
            return List.of();
        }
        var target = availableCatalog(session, targetCode);
        if (target.grant().allRecords()) {
            return cells;
        }
        var sourceIdsByCell = new LinkedHashMap<PersistedCell, Set<Long>>();
        var sourceIds = new LinkedHashSet<Long>();
        for (var cell : cells) {
            var cellSources = derivedSourceRecordIds(cell);
            sourceIdsByCell.put(cell, cellSources);
            sourceIds.addAll(cellSources);
        }
        if (sourceIds.isEmpty()) {
            return cells;
        }
        var targetScope = scope(session, target);
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(target.moduleId()));
        arguments.addAll(sourceIds);
        arguments.addAll(targetScope.arguments());
        var visible = new LinkedHashSet<Long>();
        jdbc.query("SELECT r.record_id FROM un_module_record r WHERE r.system_id=? AND r.tenant_id=? "
                        + "AND r.logical_module_id=? AND r.record_id IN (" + placeholders(sourceIds.size()) + ") "
                        + "AND r.status IN ('ACTIVE','ARCHIVED') AND " + targetScope.sql(),
                (org.springframework.jdbc.core.RowCallbackHandler) row -> visible.add(row.getLong("record_id")),
                arguments.toArray());
        return filterDerivedDisclosure(field.type(), cells, sourceIdsByCell, visible);
    }

    static <T> List<T> filterDerivedDisclosure(
            String type,
            List<T> values,
            Map<T, Set<Long>> sourceIdsByValue,
            Set<Long> visibleSourceIds
    ) {
        if ("SUMMARY".equals(type)) {
            var allSources = new LinkedHashSet<Long>();
            sourceIdsByValue.values().forEach(allSources::addAll);
            return visibleSourceIds.containsAll(allSources) ? List.copyOf(values) : List.of();
        }
        if (!"LOOKUP".equals(type)) {
            return List.copyOf(values);
        }
        return values.stream().filter(value -> {
            var dependencies = sourceIdsByValue.getOrDefault(value, Set.of());
            return dependencies.isEmpty() || visibleSourceIds.containsAll(dependencies);
        }).toList();
    }

    private Set<Long> derivedSourceRecordIds(PersistedCell cell) {
        if (cell.dependencyVersionJson() == null || cell.dependencyVersionJson().isBlank()) {
            return Set.of();
        }
        var dependencies = readJsonValue(cell.dependencyVersionJson());
        if (!dependencies.isArray()) {
            throw new IllegalStateException("Derived dependency versions are invalid");
        }
        var result = new LinkedHashSet<Long>();
        for (var dependency : dependencies) {
            var source = dependency.path("sourceRecordId");
            if (!source.isTextual() || !source.asText().matches("^[1-9][0-9]{0,18}$")) continue;
            result.add(Long.parseLong(source.asText()));
        }
        return Set.copyOf(result);
    }

    private Map<Long, Map<Long, RecordRuntimeViews.FieldValue>> referenceValues(
            RuntimeSession session,
            Catalog catalog,
            List<Long> recordIds,
            List<FieldDescriptor> fields
    ) {
        if (fields.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<Long, Map<Long, RecordRuntimeViews.FieldValue>>();
        for (var field : fields) {
            var targetCode = field.schema().path("referenceTargetModuleCode").asText("");
            var target = availableCatalog(session, targetCode);
            var targetScope = scope(session, target);
            var arguments = new ArrayList<Object>();
            arguments.add(session.systemId());
            arguments.add(requiredTenant(session));
            arguments.add(Long.parseLong(catalog.versionId()));
            arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
            arguments.addAll(recordIds);
            arguments.add(field.id());
            arguments.add(Long.parseLong(target.moduleId()));
            arguments.addAll(targetScope.arguments());
            jdbc.query("SELECT s.record_id,s.field_snapshot_id,s.source_record_version,s.recalculation_state,"
                            + "s.failure_correlation_id,"
                            + "v.string_value,v.decimal_value,v.date_value,v.datetime_value,v.boolean_value,"
                            + "v.display_value FROM un_module_reference_state s LEFT JOIN un_module_record_value v "
                            + "ON v.system_id=s.system_id AND v.tenant_id=s.tenant_id AND v.record_id=s.record_id "
                            + "AND v.schema_version_id=s.schema_version_id "
                            + "AND v.module_snapshot_id=s.module_snapshot_id "
                            + "AND v.field_snapshot_id=s.field_snapshot_id LEFT JOIN un_module_record r "
                            + "ON r.system_id=s.system_id AND r.tenant_id=s.tenant_id "
                            + "AND r.record_id=s.source_record_id WHERE s.system_id=? AND s.tenant_id=? "
                            + "AND s.schema_version_id=? AND s.module_snapshot_id=? AND s.record_id IN ("
                            + placeholders(recordIds.size()) + ") AND s.field_snapshot_id=? "
                            + "AND (s.source_record_id IS NULL OR (r.logical_module_id=? "
                            + "AND r.status IN ('ACTIVE','ARCHIVED') AND " + targetScope.sql() + ")) "
                            + "ORDER BY s.record_id",
                    (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                        var envelope = new LinkedHashMap<String, Object>();
                        envelope.put("result", referenceResult(row));
                        envelope.put("recalculationState", row.getString("recalculation_state"));
                        envelope.put("sourceVersion", row.getObject("source_record_version", Long.class));
                        result.computeIfAbsent(row.getLong("record_id"), ignored -> new LinkedHashMap<>())
                                .put(field.id(), new RecordRuntimeViews.FieldValue(
                                        field.code(), field.name(), field.type(), envelope,
                                        row.getString("display_value"),
                                        row.getString("failure_correlation_id")));
                    }, arguments.toArray());
        }
        return result;
    }

    private static Object referenceResult(ResultSet row) throws SQLException {
        if (row.getString("string_value") != null) {
            return row.getString("string_value");
        }
        if (row.getBigDecimal("decimal_value") != null) {
            return row.getBigDecimal("decimal_value");
        }
        if (row.getDate("date_value") != null) {
            return row.getDate("date_value").toLocalDate().toString();
        }
        if (row.getTimestamp("datetime_value") != null) {
            return row.getTimestamp("datetime_value").toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        return row.getObject("boolean_value", Boolean.class);
    }

    private PersistedCell persistedCell(
            ResultSet row,
            RuntimeSession session,
            Catalog catalog,
            FieldDescriptor field
    ) throws SQLException {
        var type = row.getString("field_type");
        Object value = P4_C4_DERIVED_TYPES.contains(type) ? derivedStoredValue(row) : switch (type) {
            case "TEXT", "TAG", "PHONE", "EMAIL", "URL", "AUTO_NUMBER" -> row.getString("string_value");
            case "TEXTAREA", "RICH_TEXT" -> row.getString("text_value");
            case "ADDRESS", "GEO", "BARCODE", "JSON" -> readJsonValue(row.getString("text_value"));
            case "NUMBER", "PERCENT", "MONEY", "RATING", "PROGRESS" -> row.getBigDecimal("decimal_value");
            case "DATE", "DATE_RANGE" -> row.getDate("date_value").toLocalDate().toString();
            case "DATETIME", "CREATED_AT", "UPDATED_AT" -> row.getTimestamp("datetime_value").toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "TIME", "TIME_RANGE" -> row.getTime("time_value").toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            case "RADIO", "MEMBER", "DEPARTMENT", "MULTI_SELECT", "CASCADE", "STATUS",
                    "ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE",
                    "TENANT", "CREATED_BY", "UPDATED_BY" ->
                    Long.toString(row.getLong("reference_value"));
            case "SWITCH" -> row.getBoolean("boolean_value");
            case "IDENTITY" -> field.sensitiveReadable()
                    ? sensitiveCrypto.decrypt(row.getBytes("encrypted_value"),
                    row.getString("encryption_key_version"), sensitiveContext(
                            session, catalog, row.getLong("record_id"), row.getLong("logical_field_id"),
                            row.getLong("field_snapshot_id"), row.getInt("ordinal"), type))
                    : null;
            case "SECRET" -> null;
            default -> throw new IllegalStateException("Unsupported persisted field type " + type);
        };
        return new PersistedCell(type, row.getInt("ordinal"), value, row.getString("display_value"),
                row.getString("currency_code"), row.getString("dependency_version_json"),
                row.getObject("evaluator_version", Integer.class), row.getString("recalculation_state"),
                row.getString("failure_correlation_id"));
    }

    private static Object derivedStoredValue(ResultSet row) throws SQLException {
        return switch (row.getString("result_schema")) {
            case "STRING" -> row.getString("string_value");
            case "DECIMAL" -> row.getBigDecimal("decimal_value");
            case "INTEGER" -> row.getBigDecimal("decimal_value") == null ? null
                    : row.getBigDecimal("decimal_value").toBigIntegerExact();
            case "DATE" -> row.getDate("date_value") == null ? null
                    : row.getDate("date_value").toLocalDate().toString();
            case "DATETIME" -> row.getTimestamp("datetime_value") == null ? null
                    : row.getTimestamp("datetime_value").toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "BOOLEAN" -> row.getObject("boolean_value", Boolean.class);
            default -> throw new IllegalStateException("Derived result schema is unavailable");
        };
    }

    private Object persistedValue(String type, List<PersistedCell> cells) {
        if (P4_C4_DERIVED_TYPES.contains(type)) {
            var envelope = new LinkedHashMap<String, Object>();
            envelope.put("result", "LOOKUP".equals(type)
                    ? cells.stream().map(PersistedCell::value).filter(Objects::nonNull).toList()
                    : cells.getFirst().value());
            envelope.put("recalculationState", cells.getFirst().recalculationState());
            envelope.put("evaluatorVersion", cells.getFirst().evaluatorVersion());
            envelope.put("dependencyVersions", derivedDependencyVersions(cells));
            envelope.put("failureCorrelationId", cells.getFirst().failureCorrelationId());
            return envelope;
        }
        if (Set.of("DATE_RANGE", "TIME_RANGE", "MULTI_SELECT", "CASCADE", "TAG", "PHONE", "EMAIL",
                "ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE")
                .contains(type)) {
            return cells.stream().map(PersistedCell::value).toList();
        }
        var first = cells.getFirst();
        if ("MONEY".equals(type)) {
            var amount = (BigDecimal) first.value();
            var scale = Currency.getInstance(first.currencyCode()).getDefaultFractionDigits();
            return new CanonicalFieldValueCodec.MoneyValue(
                    amount.setScale(scale).toPlainString(), first.currencyCode());
        }
        if ("RATING".equals(type)) {
            return ((BigDecimal) first.value()).intValueExact();
        }
        return first.value();
    }

    private JsonNode derivedDependencyVersions(List<PersistedCell> cells) {
        if (cells.size() == 1) {
            return cells.getFirst().dependencyVersionJson() == null
                    ? objectMapper.createArrayNode() : readJsonValue(cells.getFirst().dependencyVersionJson());
        }
        var result = objectMapper.createArrayNode();
        for (var cell : cells) {
            if (cell.dependencyVersionJson() == null) continue;
            var dependencies = readJsonValue(cell.dependencyVersionJson());
            if (dependencies.isArray()) dependencies.forEach(result::add);
        }
        return result;
    }

    private String persistedDisplay(String type, List<PersistedCell> cells) {
        if ("LOOKUP".equals(type)) {
            return String.join(", ", cells.stream().map(PersistedCell::display)
                    .filter(Objects::nonNull).toList());
        }
        if (P4_C4_DERIVED_TYPES.contains(type)) {
            return cells.getFirst().display();
        }
        if ("MONEY".equals(type)) {
            var value = (CanonicalFieldValueCodec.MoneyValue) persistedValue(type, cells);
            return value.currency() + " " + value.amount();
        }
        var delimiter = switch (type) {
            case "DATE_RANGE", "TIME_RANGE" -> " - ";
            case "CASCADE" -> " / ";
            case "MULTI_SELECT", "TAG", "PHONE", "EMAIL", "ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE" -> ", ";
            default -> null;
        };
        return delimiter == null ? cells.getFirst().display()
                : String.join(delimiter, cells.stream().map(PersistedCell::display).toList());
    }

    private JsonNode readJsonValue(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored canonical JSON is invalid", exception);
        }
    }

    private static SensitiveCryptoService.ValueContext sensitiveContext(
            RuntimeSession session,
            Catalog catalog,
            long recordId,
            long logicalFieldId,
            long fieldSnapshotId,
            int ordinal,
            String fieldType
    ) {
        return new SensitiveCryptoService.ValueContext(
                session.systemId(), requiredTenant(session), Long.parseLong(catalog.moduleId()), logicalFieldId,
                recordId, fieldSnapshotId, ordinal, fieldType);
    }

    private Map<Long, List<RecordRuntimeViews.FieldValue>> legacyValues(
            RuntimeSession session,
            Catalog catalog,
            List<Long> recordIds,
            List<FieldDescriptor> fields
    ) {
        if (recordIds.isEmpty() || fields.isEmpty()) {
            return Map.of();
        }
        var fieldById = new HashMap<Long, FieldDescriptor>();
        fields.forEach(field -> fieldById.put(field.id(), field));
        var sql = "SELECT record_id,field_snapshot_id,field_type,ordinal,string_value,text_value,decimal_value,"
                + "date_value,datetime_value,reference_value,display_value FROM un_module_record_value "
                + "WHERE system_id=? AND tenant_id=? AND logical_module_id=? AND schema_version_id=? "
                + "AND module_snapshot_id=? AND record_id IN ("
                + placeholders(recordIds.size()) + ") AND field_snapshot_id IN (" + placeholders(fields.size())
                + ") ORDER BY record_id,field_snapshot_id,ordinal";
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(requiredTenant(session));
        arguments.add(Long.parseLong(catalog.moduleId()));
        arguments.add(Long.parseLong(catalog.versionId()));
        arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        arguments.addAll(recordIds);
        arguments.addAll(fields.stream().map(FieldDescriptor::id).toList());
        var result = new LinkedHashMap<Long, List<RecordRuntimeViews.FieldValue>>();
        jdbc.query(sql, row -> {
            var descriptor = fieldById.get(row.getLong("field_snapshot_id"));
            if (descriptor == null) {
                return;
            }
            result.computeIfAbsent(row.getLong("record_id"), ignored -> new ArrayList<>())
                    .add(new RecordRuntimeViews.FieldValue(
                            descriptor.code(), descriptor.name(), descriptor.type(), typedValue(row),
                            row.getString("display_value")));
        }, arguments.toArray());
        return result;
    }

    private static Object typedValue(ResultSet row) throws SQLException {
        return switch (row.getString("field_type")) {
            case "TEXT", "AUTO_NUMBER" -> row.getString("string_value");
            case "TEXTAREA" -> row.getString("text_value");
            case "NUMBER" -> row.getBigDecimal("decimal_value");
            case "DATE" -> row.getDate("date_value").toLocalDate().toString();
            case "DATETIME", "CREATED_AT", "UPDATED_AT" -> row.getTimestamp("datetime_value").toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "RADIO", "MEMBER", "DEPARTMENT", "TENANT", "CREATED_BY", "UPDATED_BY" ->
                    Long.toString(row.getLong("reference_value"));
            default -> null;
        };
    }

    private void assertSystemClientValuesAllowed(
            RuntimeSession session,
            Catalog catalog,
            Map<String, JsonNode> supplied,
            LocalDateTime occurredAt
    ) {
        systemFields.assertClientValuesAllowed(systemScope(session, catalog), systemDefinitions(catalog),
                clientValueMarkers(supplied),
                new SystemFieldValueService.ActorContext(session.memberId(), occurredAt));
    }

    private static Map<String, Object> clientValueMarkers(Map<String, JsonNode> supplied) {
        var result = new LinkedHashMap<String, Object>();
        supplied.keySet().forEach(code -> result.put(code, Boolean.TRUE));
        return Map.copyOf(result);
    }

    private static SystemFieldValueService.Scope systemScope(RuntimeSession session, Catalog catalog) {
        return new SystemFieldValueService.Scope(
                session.systemId(), requiredTenant(session), Long.parseLong(catalog.moduleId()));
    }

    private static List<SystemFieldDefinition> systemDefinitions(Catalog catalog) {
        return catalog.fields().stream()
                .filter(field -> SYSTEM_COMPUTED_TYPES.contains(field.type()))
                .map(field -> new SystemFieldDefinition(
                        field.id(),
                        field.code(),
                        SystemFieldDefinition.Type.valueOf(field.type()),
                        field.schema().path("autoNumberPrefix").asText(""),
                        field.schema().path("digits").asInt(6)))
                .toList();
    }

    private static List<NormalizedValue> systemNormalizedValues(
            Catalog catalog,
            Map<String, Object> generated
    ) {
        if (generated.isEmpty()) {
            return List.of();
        }
        var fieldsByCode = new HashMap<String, FieldDescriptor>();
        catalog.fields().stream().filter(field -> SYSTEM_COMPUTED_TYPES.contains(field.type()))
                .forEach(field -> fieldsByCode.put(field.code(), field));
        var result = new ArrayList<NormalizedValue>();
        for (var entry : generated.entrySet()) {
            var field = fieldsByCode.get(entry.getKey());
            if (field == null) {
                throw new IllegalStateException("Generated system field is absent from the runtime catalog");
            }
            var value = entry.getValue();
            var normalized = switch (field.type()) {
                case "AUTO_NUMBER" -> {
                    var text = (String) value;
                    yield new NormalizedValue(field, text, text,
                            List.of(CanonicalFieldValueCodec.ValueRow.string(0, text, text)));
                }
                case "TENANT", "CREATED_BY", "UPDATED_BY" -> {
                    var id = ((Number) value).longValue();
                    var display = Long.toString(id);
                    yield new NormalizedValue(field, id, display,
                            List.of(CanonicalFieldValueCodec.ValueRow.reference(0, id, display)));
                }
                case "CREATED_AT", "UPDATED_AT" -> {
                    var timestamp = (LocalDateTime) value;
                    var display = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    yield new NormalizedValue(field, display, display,
                            List.of(CanonicalFieldValueCodec.ValueRow.dateTime(0, timestamp, display)));
                }
                default -> throw new IllegalStateException("Unsupported system field type " + field.type());
            };
            result.add(normalized);
        }
        result.sort(Comparator.comparingLong(value -> value.field().id()));
        return List.copyOf(result);
    }

    private List<NormalizedValue> normalizeValues(
            RuntimeSession session,
            Catalog catalog,
            RuntimeReferenceFacade.ReferenceCatalog references,
            Map<String, JsonNode> supplied
    ) {
        var fieldsByCode = new LinkedHashMap<String, FieldDescriptor>();
        catalog.fields().forEach(field -> fieldsByCode.put(field.code(), field));
        for (var fieldCode : supplied.keySet()) {
            var field = fieldsByCode.get(fieldCode);
            if (field == null) {
                throw fieldError("RECORD_FIELD_FORBIDDEN", fieldCode,
                        "字段不存在、不可见或不允许写入", HttpStatus.FORBIDDEN);
            }
            if (P4_C3_COMPOSITION_TYPES.contains(field.type())) {
                throw fieldError("FIELD_WRITE_FORBIDDEN", fieldCode,
                        "Composition fields must use relations or subtables", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (!field.writable()) {
                throw fieldError("RECORD_FIELD_FORBIDDEN", fieldCode,
                        "字段为只读字段，不能由当前成员写入", HttpStatus.FORBIDDEN);
            }
        }
        var result = new ArrayList<NormalizedValue>();
        for (var field : catalog.fields()) {
            var node = supplied.get(field.code());
            if (node == null || node.isNull()
                    || node.isTextual() && node.asText().isBlank() && !"SECRET".equals(field.type())) {
                continue;
            }
            if (node.isArray() && node.isEmpty()
                    && Set.of("MULTI_SELECT", "CASCADE", "TAG", "PHONE", "EMAIL",
                    "ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE").contains(field.type())) {
                continue;
            }
            if (FILE_FIELD_TYPES.contains(field.type())) {
                var assets = fileFields.resolve(session, field.code(), field.type(), field.schema(), node);
                var values = assets.stream().map(asset -> Long.toString(asset.fileId())).toList();
                var display = String.join(", ", assets.stream().map(
                        RecordFileFieldBindingService.AssetValue::originalName).toList());
                var rows = new ArrayList<CanonicalFieldValueCodec.ValueRow>();
                for (var ordinal = 0; ordinal < assets.size(); ordinal++) {
                    var asset = assets.get(ordinal);
                    rows.add(CanonicalFieldValueCodec.ValueRow.reference(
                            ordinal, asset.fileId(), asset.originalName()));
                }
                result.add(new NormalizedValue(field, List.copyOf(values), display, List.copyOf(rows)));
            } else {
                result.add(normalizeCanonical(field, references, node));
            }
        }
        return List.copyOf(result);
    }

    private NormalizedValue normalizeCanonical(
            FieldDescriptor field,
            RuntimeReferenceFacade.ReferenceCatalog references,
            JsonNode node
    ) {
        try {
            var canonical = fieldValueCodec.normalize(new CanonicalFieldValueCodec.FieldContract(
                    field.code(), field.name(), field.type(), field.schema(), field.valueOptions(references)), node);
            return new NormalizedValue(field, canonical.value(), canonical.display(), canonical.rows());
        } catch (BusinessException exception) {
            if (!LEGACY_FIELD_TYPES.contains(field.type()) || !"FIELD_VALUE_INVALID".equals(exception.code())) {
                throw exception;
            }
            var errors = exception.errors().stream()
                    .map(error -> new ApiError("RECORD_VALIDATION_FAILED", error.path(), error.message()))
                    .toList();
            throw new BusinessException("RECORD_VALIDATION_FAILED", exception.getMessage(), exception.status(),
                    errors, exception.data());
        }
    }

    private static void validateStatusChanges(
            List<NormalizedValue> beforeValues,
            List<NormalizedValue> afterValues,
            boolean creating
    ) {
        var beforeByCode = new HashMap<String, NormalizedValue>();
        beforeValues.stream().filter(value -> "STATUS".equals(value.field().type()))
                .forEach(value -> beforeByCode.put(value.field().code(), value));
        for (var after : afterValues) {
            if (!"STATUS".equals(after.field().type())) {
                continue;
            }
            var target = String.valueOf(after.value());
            var before = beforeByCode.get(after.field().code());
            if (creating || before == null) {
                var initial = new HashSet<String>();
                after.field().schema().path("initialStateIds").forEach(value -> initial.add(value.asText()));
                if (!initial.contains(target)) {
                    throw fieldError("FIELD_TRANSITION_INVALID", after.field().code(),
                            "STATUS value is not an allowed initial state", HttpStatus.CONFLICT);
                }
                continue;
            }
            var source = String.valueOf(before.value());
            if (source.equals(target)) {
                continue;
            }
            var allowed = false;
            for (var transition : after.field().schema().path("transitions")) {
                if (source.equals(transition.path("from").asText())
                        && target.equals(transition.path("to").asText())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw fieldError("FIELD_TRANSITION_INVALID", after.field().code(),
                        "STATUS transition is not declared", HttpStatus.CONFLICT);
            }
        }
    }

    private NormalizedValue normalize(
            FieldDescriptor field,
            RuntimeReferenceFacade.ReferenceCatalog references,
            JsonNode node
    ) {
        try {
            return switch (field.type()) {
                case "TEXT" -> textValue(field, node, Math.min(field.schema().path("maxLength").asInt(4000), 4000));
                case "TEXTAREA" -> textValue(field, node,
                        Math.min(field.schema().path("maxLength").asInt(65535), 65535));
                case "NUMBER" -> numberValue(field, node);
                case "DATE" -> {
                    requireText(field, node);
                    var value = LocalDate.parse(node.asText(), DateTimeFormatter.ISO_LOCAL_DATE);
                    yield new NormalizedValue(field, value, value.toString());
                }
                case "DATETIME" -> {
                    requireText(field, node);
                    var value = LocalDateTime.parse(node.asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    yield new NormalizedValue(field, value, value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
                case "RADIO", "MEMBER", "DEPARTMENT" -> referenceValue(field, references, node);
                default -> throw new IllegalStateException("Unsupported runtime field type " + field.type());
            };
        } catch (DateTimeParseException | ArithmeticException exception) {
            throw fieldError("RECORD_VALIDATION_FAILED", field.code(),
                    field.name() + " 的格式无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private NormalizedValue textValue(FieldDescriptor field, JsonNode node, int maxLength) {
        requireText(field, node);
        var value = node.asText();
        if (value.length() > maxLength) {
            throw fieldError("RECORD_VALIDATION_FAILED", field.code(),
                    field.name() + " 最多允许 " + maxLength + " 个字符", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return new NormalizedValue(field, value, value);
    }

    private NormalizedValue numberValue(FieldDescriptor field, JsonNode node) {
        if (!node.isNumber()) {
            throw fieldError("RECORD_VALIDATION_FAILED", field.code(),
                    field.name() + " 必须是数字", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var value = node.decimalValue();
        if (value.precision() > 38 || Math.max(value.scale(), 0) > 10) {
            throw fieldError("RECORD_VALIDATION_FAILED", field.code(),
                    field.name() + " 最多支持 38 位有效数字和 10 位小数", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return new NormalizedValue(field, value, value.toPlainString());
    }

    private NormalizedValue referenceValue(
            FieldDescriptor field,
            RuntimeReferenceFacade.ReferenceCatalog references,
            JsonNode node
    ) {
        final long id;
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            id = node.longValue();
        } else if (node.isTextual() && node.asText().matches("^[1-9][0-9]{0,18}$")) {
            id = Long.parseLong(node.asText());
        } else {
            throw fieldError("RECORD_VALIDATION_FAILED", field.code(),
                    field.name() + " 必须选择一个有效选项", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var options = field.referenceOptions(references);
        var selected = options.stream().filter(option -> option.value().equals(Long.toString(id)))
                .findFirst().orElseThrow(() -> fieldError("RECORD_VALIDATION_FAILED", field.code(),
                        field.name() + " 的选项不存在或当前不可用", HttpStatus.UNPROCESSABLE_ENTITY));
        return new NormalizedValue(field, id, selected.label());
    }

    private static void requireText(FieldDescriptor field, JsonNode node) {
        if (!node.isTextual()) {
            throw fieldError("RECORD_VALIDATION_FAILED", field.code(),
                    field.name() + " 必须是文本", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private List<BatchEditField> batchEditFields(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            List<RecordBatchEditPlan.Change> changes
    ) {
        var fieldsByCode = new HashMap<String, FieldDescriptor>();
        catalog.fields().forEach(field -> fieldsByCode.put(field.code(), field));
        var references = referenceFacade.resolve(session.systemId(), tenantId, session.memberId());
        var result = new ArrayList<BatchEditField>(changes.size());
        for (var change : changes) {
            var field = fieldsByCode.get(change.fieldCode());
            if (field == null
                    || !field.writable()
                    || SENSITIVE_TYPES.contains(field.type())
                    || P4_C3_COMPOSITION_TYPES.contains(field.type())
                    || P4_C4_DERIVED_TYPES.contains(field.type())
                    || SYSTEM_COMPUTED_TYPES.contains(field.type())) {
                throw new BusinessException(
                        "BATCH_EDIT_INVALID",
                        "Batch edit field is unavailable, readonly, sensitive, derived, system or composition-owned",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        List.of(new ApiError(
                                "BATCH_EDIT_INVALID",
                                "changes." + change.fieldCode(),
                                "The published field cannot be changed by batch edit")));
            }
            NormalizedValue normalized = null;
            if (change.operation() == RecordBatchEditPlan.Operation.SET) {
                try {
                    normalized = normalizeCanonical(field, references, change.value());
                } catch (BusinessException exception) {
                    throw batchEditValidation(exception);
                }
                if (normalized.rows().isEmpty()) {
                    throw batchEditValidation(
                            "changes." + field.code(),
                            field.name() + " must contain a persistable SET value");
                }
            } else if (field.required()) {
                throw batchEditValidation(
                        "changes." + field.code(),
                        field.name() + " is required and cannot be cleared");
            }
            result.add(new BatchEditField(field, change.operation(), normalized));
        }
        return List.copyOf(result);
    }

    private static List<NormalizedValue> patchedValues(
            Catalog catalog,
            List<NormalizedValue> beforeValues,
            List<BatchEditField> edits
    ) {
        var valuesByCode = new HashMap<String, NormalizedValue>();
        beforeValues.forEach(value -> valuesByCode.put(value.field().code(), value));
        for (var edit : edits) {
            if (edit.operation() == RecordBatchEditPlan.Operation.CLEAR) {
                valuesByCode.remove(edit.field().code());
            } else {
                valuesByCode.put(edit.field().code(), edit.value());
            }
        }
        return catalog.fields().stream()
                .map(field -> valuesByCode.get(field.code()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static void validateBatchEditRecord(
            Catalog catalog,
            List<NormalizedValue> beforeValues,
            List<NormalizedValue> afterValues
    ) {
        var present = afterValues.stream()
                .map(value -> value.field().code())
                .collect(java.util.stream.Collectors.toSet());
        var missing = catalog.fields().stream()
                .filter(FieldDescriptor::required)
                .filter(field -> !P4_C3_COMPOSITION_TYPES.contains(field.type()))
                .filter(field -> !present.contains(field.code()))
                .findFirst();
        if (missing.isPresent()) {
            throw batchEditValidation(
                    "changes." + missing.get().code(),
                    missing.get().name() + " is required");
        }
        try {
            validateStatusChanges(beforeValues, afterValues, false);
        } catch (BusinessException exception) {
            throw batchEditValidation(exception);
        }
    }

    private void validateBatchEditUniqueValues(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            List<Long> recordIds,
            List<BatchEditField> edits
    ) {
        for (var edit : edits) {
            if (edit.operation() != RecordBatchEditPlan.Operation.SET
                    || !"UNIQUE".equals(edit.field().indexMode())) {
                continue;
            }
            if (recordIds.size() > 1) {
                throw batchEditValidation(
                        "changes." + edit.field().code(),
                        edit.field().name() + " cannot use the same unique value for multiple records");
            }
            final List<UniqueValue> proposed;
            try {
                proposed = uniqueValues(
                        session, tenantId, catalog, edit.field(), edit.value().value());
            } catch (BusinessException exception) {
                throw batchEditValidation(exception);
            }
            for (var value : proposed) {
                var arguments = new ArrayList<Object>();
                arguments.add(session.systemId());
                arguments.add(tenantId);
                arguments.add(Long.parseLong(catalog.moduleId()));
                arguments.add(edit.field().id());
                arguments.add(value.hash());
                arguments.add(value.currency() == null ? "---" : value.currency());
                arguments.addAll(recordIds);
                var conflicts = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM un_module_record_unique "
                                + "WHERE system_id=? AND tenant_id=? AND logical_module_id=? "
                                + "AND logical_field_id=? AND normalization_generation_id=1 "
                                + "AND normalized_hash=? AND currency_key=? AND record_id NOT IN ("
                                + placeholders(recordIds.size()) + ")",
                        Long.class,
                        arguments.toArray());
                if (conflicts != null && conflicts > 0) {
                    throw batchEditValidation(
                            "changes." + edit.field().code(),
                            edit.field().name() + " must be unique");
                }
            }
        }
    }

    private List<NormalizedValue> persistedValues(RuntimeSession session, Catalog catalog, long recordId) {
        var persisted = values(session, catalog, List.of(recordId), catalog.fields())
                .getOrDefault(recordId, List.of());
        var fieldsByCode = new HashMap<String, FieldDescriptor>();
        catalog.fields().forEach(field -> fieldsByCode.put(field.code(), field));
        return persisted.stream().map(value -> new NormalizedValue(
                fieldsByCode.get(value.fieldCode()), value.value(), value.displayValue())).toList();
    }

    private static void requireActivationValues(Catalog catalog, List<NormalizedValue> values) {
        var present = values.stream().map(value -> value.field().code()).collect(java.util.stream.Collectors.toSet());
        var errors = catalog.fields().stream().filter(FieldDescriptor::required)
                .filter(field -> !P4_C3_COMPOSITION_TYPES.contains(field.type()))
                .filter(field -> !present.contains(field.code()))
                .map(field -> new ApiError("RECORD_VALIDATION_FAILED", "values." + field.code(),
                        field.name() + " 为必填字段"))
                .toList();
        if (!errors.isEmpty()) {
            throw new BusinessException("RECORD_VALIDATION_FAILED", "记录存在未填写的必填字段",
                    HttpStatus.UNPROCESSABLE_ENTITY, errors);
        }
    }

    private void replaceWritableValues(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            String recordStatus,
            List<NormalizedValue> normalized,
            Map<String, JsonNode> supplied,
            LocalDateTime now
    ) {
        // Update is a field patch. Values omitted because a published rule made the
        // field hidden/readonly must remain untouched instead of being erased.
        var writableFieldIds = catalog.fields().stream().filter(FieldDescriptor::writable)
                .filter(field -> supplied.containsKey(field.code()))
                .map(FieldDescriptor::id).toList();
        if (!writableFieldIds.isEmpty()) {
            var fixedArguments = new ArrayList<Object>();
            fixedArguments.add(session.systemId());
            fixedArguments.add(tenantId);
            fixedArguments.add(recordId);
            fixedArguments.add(Long.parseLong(catalog.versionId()));
            fixedArguments.add(Long.parseLong(catalog.moduleSnapshotId()));
            fixedArguments.addAll(writableFieldIds);
            jdbc.update("DELETE FROM un_module_record_search WHERE system_id=? AND tenant_id=? AND record_id=? "
                            + "AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id IN ("
                            + placeholders(writableFieldIds.size()) + ")",
                    fixedArguments.toArray());
            jdbc.update("DELETE FROM un_module_record_index WHERE system_id=? AND tenant_id=? AND record_id=? "
                            + "AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id IN ("
                            + placeholders(writableFieldIds.size()) + ")",
                    fixedArguments.toArray());
            jdbc.update("DELETE FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND record_id=? "
                            + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id IN ("
                            + placeholders(writableFieldIds.size()) + ")",
                    fixedArguments.toArray());
        }
        for (var value : normalized) {
            insertValue(session, tenantId, catalog, recordId, value, recordStatus, now);
        }
    }

    private void synchronizeFileFields(RuntimeSession session, long recordId, Catalog catalog,
                                       List<NormalizedValue> normalized, Map<String, JsonNode> supplied,
                                       LocalDateTime now) {
        var desired = new HashMap<Long, List<Long>>();
        normalized.stream().filter(value -> FILE_FIELD_TYPES.contains(value.field().type()))
                .forEach(value -> desired.put(value.field().id(), value.rows().stream()
                        .map(CanonicalFieldValueCodec.ValueRow::referenceValue)
                        .filter(Objects::nonNull).toList()));
        var writableFileFields = catalog.fields().stream().filter(FieldDescriptor::writable)
                .filter(field -> FILE_FIELD_TYPES.contains(field.type()))
                .collect(Collectors.toMap(FieldDescriptor::code, FieldDescriptor::id,
                        (left, right) -> left, LinkedHashMap::new));
        RecordFileFieldBindingService.touchedBindings(writableFileFields, supplied.keySet(), desired)
                .forEach((fieldId, fileIds) -> fileFields.synchronize(
                        session, recordId, fieldId, fileIds, now));
    }

    private void replaceTouchedValues(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            String recordStatus,
            List<BatchEditField> edits,
            LocalDateTime now
    ) {
        var fieldIds = edits.stream().map(edit -> edit.field().id()).toList();
        var fixedArguments = new ArrayList<Object>();
        fixedArguments.add(session.systemId());
        fixedArguments.add(tenantId);
        fixedArguments.add(recordId);
        fixedArguments.add(Long.parseLong(catalog.versionId()));
        fixedArguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        fixedArguments.addAll(fieldIds);
        jdbc.update(
                RecordBatchEditPlan.DELETE_TOUCHED_SEARCH_SQL.formatted(placeholders(fieldIds.size())),
                fixedArguments.toArray());
        jdbc.update(
                RecordBatchEditPlan.DELETE_TOUCHED_INDEX_SQL.formatted(placeholders(fieldIds.size())),
                fixedArguments.toArray());
        jdbc.update(
                RecordBatchEditPlan.DELETE_TOUCHED_VALUE_SQL.formatted(placeholders(fieldIds.size())),
                fixedArguments.toArray());
        edits.stream()
                .filter(edit -> edit.operation() == RecordBatchEditPlan.Operation.SET)
                .map(BatchEditField::value)
                .forEach(value -> insertValue(
                        session, tenantId, catalog, recordId, value, recordStatus, now));
    }

    private void replaceSystemValues(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            String recordStatus,
            List<NormalizedValue> normalized,
            LocalDateTime now
    ) {
        if (normalized.isEmpty()) {
            return;
        }
        var fieldIds = normalized.stream().map(value -> value.field().id()).toList();
        var fixedArguments = new ArrayList<Object>();
        fixedArguments.add(session.systemId());
        fixedArguments.add(tenantId);
        fixedArguments.add(recordId);
        fixedArguments.add(Long.parseLong(catalog.versionId()));
        fixedArguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        fixedArguments.addAll(fieldIds);
        jdbc.update("DELETE FROM un_module_record_search WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id IN ("
                        + placeholders(fieldIds.size()) + ")",
                fixedArguments.toArray());
        jdbc.update("DELETE FROM un_module_record_index WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id IN ("
                        + placeholders(fieldIds.size()) + ")",
                fixedArguments.toArray());
        jdbc.update("DELETE FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id IN ("
                        + placeholders(fieldIds.size()) + ")",
                fixedArguments.toArray());
        normalized.forEach(value -> insertValue(session, tenantId, catalog, recordId, value, recordStatus, now));
    }

    private void insertValue(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            NormalizedValue normalized,
            String recordStatus,
            LocalDateTime now
    ) {
        if (normalized.rows().isEmpty()) {
            throw new IllegalStateException("Canonical value has no persistence rows for " + normalized.field().code());
        }
        for (var row : normalized.rows()) {
            insertValueRow(session, tenantId, catalog, recordId, normalized.field(), row, recordStatus, now);
        }
    }

    private void replaceUniqueReservations(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            String recordStatus,
            List<NormalizedValue> values,
            LocalDateTime now
    ) {
        jdbc.update("DELETE FROM un_module_record_unique WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=?",
                session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()));
        if (!Set.of("ACTIVE", "ARCHIVED").contains(recordStatus)) {
            return;
        }
        insertUniqueReservations(session, tenantId, catalog, recordId, recordStatus, values, now);
    }

    private void insertUniqueReservations(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            String recordStatus,
            List<NormalizedValue> values,
            LocalDateTime now
    ) {
        if (!Set.of("ACTIVE", "ARCHIVED").contains(recordStatus)) {
            return;
        }
        for (var normalized : values) {
            var field = normalized.field();
            if (field == null || !"UNIQUE".equals(field.indexMode())) {
                continue;
            }
            var uniqueValues = SENSITIVE_TYPES.contains(field.type())
                    ? persistedSensitiveUniqueValues(session, tenantId, catalog, recordId, field)
                    : uniqueValues(session, tenantId, catalog, field, normalized.value());
            for (var unique : uniqueValues) {
                try {
                    jdbc.update("INSERT INTO un_module_record_unique "
                                    + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,"
                                    + "logical_module_id,logical_field_id,normalization_generation_id,field_type,"
                                    + "record_status,currency_code,normalized_hash,hash_key_version,created_at,updated_at) "
                                    + "VALUES (?,?,?,?,?,?,?,?,1,?,?,?,?,?,?,?)",
                            idService.nextId(), session.systemId(), tenantId, recordId,
                            Long.parseLong(catalog.versionId()), Long.parseLong(catalog.moduleSnapshotId()),
                            Long.parseLong(catalog.moduleId()), field.id(), field.type(), recordStatus,
                            unique.currency(), unique.hash(), unique.hashKeyVersion(), now, now);
                } catch (DuplicateKeyException exception) {
                    var message = field.name() + " must be unique";
                    throw new BusinessException("FIELD_UNIQUE_CONFLICT", message, HttpStatus.CONFLICT,
                            List.of(new ApiError("FIELD_UNIQUE_CONFLICT", "values." + field.code(), message)));
                }
            }
        }
    }

    private void deleteTouchedUniqueReservations(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            List<Long> recordIds,
            List<Long> fieldIds
    ) {
        if (fieldIds.isEmpty()) {
            return;
        }
        var arguments = new ArrayList<Object>();
        arguments.add(session.systemId());
        arguments.add(tenantId);
        arguments.add(Long.parseLong(catalog.versionId()));
        arguments.add(Long.parseLong(catalog.moduleSnapshotId()));
        arguments.addAll(recordIds);
        arguments.addAll(fieldIds);
        jdbc.update(
                RecordBatchEditPlan.DELETE_TOUCHED_UNIQUE_SQL.formatted(
                        placeholders(recordIds.size()),
                        placeholders(fieldIds.size())),
                arguments.toArray());
    }

    private List<UniqueValue> persistedSensitiveUniqueValues(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            FieldDescriptor field
    ) {
        var values = jdbc.query("SELECT hash_key_version,hash_value FROM un_module_record_index "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND logical_module_id=? AND logical_field_id=? "
                        + "AND index_generation_id=1 AND value_kind='HASH' ORDER BY hash_key_version",
                (result, row) -> new UniqueValue(null, result.getString("hash_value"),
                        result.getString("hash_key_version")),
                session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), Long.parseLong(catalog.moduleId()), field.id());
        if (values.isEmpty()) {
            throw new IllegalStateException("Sensitive blind indexes are unavailable for " + field.code());
        }
        return List.copyOf(values);
    }

    private List<UniqueValue> uniqueValues(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            FieldDescriptor field,
            Object value
    ) {
        if (Set.of("IDENTITY", "SECRET").contains(field.type())) {
            var context = new SensitiveCryptoService.BlindIndexContext(
                    session.systemId(), tenantId, Long.parseLong(catalog.moduleId()), field.id(), field.type());
            return sensitiveCrypto.equalityHashes((String) value, context).stream()
                    .map(hash -> new UniqueValue(null, hash.valueHash(), hash.hashKeyVersion()))
                    .toList();
        }
        if (Set.of("PHONE", "EMAIL").contains(field.type())) {
            @SuppressWarnings("unchecked")
            var elements = (List<String>) value;
            return elements.stream().map(element -> new UniqueValue(
                    null, sha256(field.type() + "|" + element), null)).toList();
        }
        String canonical;
        String currency = null;
        switch (field.type()) {
            case "PERCENT", "PROGRESS" -> canonical = canonicalDecimal((BigDecimal) value);
            case "RATING" -> canonical = Integer.toString((Integer) value);
            case "MONEY" -> {
                var money = (CanonicalFieldValueCodec.MoneyValue) value;
                canonical = canonicalDecimal(new BigDecimal(money.amount()));
                currency = money.currency();
            }
            case "TIME" -> canonical = (String) value;
            case "SWITCH" -> canonical = Boolean.toString((Boolean) value);
            case "URL", "AUTO_NUMBER" -> canonical = (String) value;
            case "BARCODE" -> {
                var barcode = (JsonNode) value;
                canonical = barcode.path("symbology").asText() + ":" + barcode.path("payload").asText();
            }
            default -> throw new IllegalStateException("Unsupported unique field type " + field.type());
        }
        return List.of(new UniqueValue(currency, sha256(field.type() + "|" + canonical), null));
    }

    private static String canonicalDecimal(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void insertValueRow(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            FieldDescriptor field,
            CanonicalFieldValueCodec.ValueRow row,
            String recordStatus,
            LocalDateTime now
    ) {
        SensitiveCryptoService.EncryptedValue encrypted = null;
        if (SENSITIVE_TYPES.contains(field.type())) {
            if (row.sensitiveValue() == null) {
                throw new IllegalStateException("Sensitive canonical row has no plaintext input");
            }
            encrypted = sensitiveCrypto.encrypt(row.sensitiveValue(), sensitiveContext(
                    session, catalog, recordId, field.id(), field.id(), row.ordinal(), field.type()));
        }
        jdbc.update("INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,ordinal,string_value,text_value,"
                        + "decimal_value,date_value,datetime_value,time_value,boolean_value,currency_code,reference_value,"
                        + "encrypted_value,encryption_key_version,value_hash,hash_key_version,display_value,created_at,"
                        + "created_by,updated_at,updated_by,version) VALUES (" + placeholders(31) + ")",
                idService.nextId(), session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), Long.parseLong(catalog.moduleId()), field.id(), field.id(), 1L,
                field.type(), row.ordinal(), row.stringValue(), row.textValue(), row.decimalValue(), row.dateValue(),
                row.dateTimeValue(), row.timeValue(), row.booleanValue(), row.currencyCode(), row.referenceValue(),
                encrypted == null ? null : encrypted.envelope(),
                encrypted == null ? null : encrypted.encryptionKeyVersion(),
                encrypted == null ? null : encrypted.valueHash(),
                encrypted == null ? null : encrypted.hashKeyVersion(),
                row.display(), now, session.memberId(), now, session.memberId(), 0L);

        if (FILE_FIELD_TYPES.contains(field.type())) {
            return;
        }

        if (P4_C2_TYPES.contains(field.type())) {
            if (!"NONE".equals(field.indexMode()) || field.searchable()) {
                insertP4C2Projections(session, tenantId, catalog, recordId, field, row, recordStatus, now);
            }
            return;
        }

        var valueKind = switch (field.type()) {
            case "TEXT", "TEXTAREA", "TAG", "MULTI_SELECT", "CASCADE", "AUTO_NUMBER" -> "STRING";
            case "NUMBER", "PERCENT", "RATING", "PROGRESS" -> "DECIMAL";
            case "MONEY" -> "MONEY";
            case "DATE", "DATE_RANGE" -> "DATE";
            case "DATETIME", "CREATED_AT", "UPDATED_AT" -> "DATETIME";
            case "TIME", "TIME_RANGE" -> "TIME";
            case "SWITCH" -> "BOOLEAN";
            case "RADIO", "MEMBER", "DEPARTMENT", "TENANT", "CREATED_BY", "UPDATED_BY" -> "REFERENCE";
            default -> throw new IllegalStateException("Unsupported runtime index type " + field.type());
        };
        var indexString = switch (field.type()) {
            case "TEXT", "TAG", "AUTO_NUMBER" -> truncate(row.stringValue(), 512);
            case "TEXTAREA" -> truncate(row.textValue(), 512);
            case "MULTI_SELECT", "CASCADE" -> Long.toString(row.referenceValue());
            default -> null;
        };
        var indexReference = Set.of("RADIO", "MEMBER", "DEPARTMENT", "TENANT", "CREATED_BY", "UPDATED_BY")
                .contains(field.type())
                ? row.referenceValue() : null;
        jdbc.update("INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,string_value,decimal_value,date_value,datetime_value,time_value,"
                        + "boolean_value,reference_value,hash_value,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,?,?,?,?,?,?,?,?,?,?,NULL,?,?,?)",
                idService.nextId(), session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), Long.parseLong(catalog.moduleId()), field.id(), row.ordinal(),
                recordStatus, valueKind, indexString, row.decimalValue(), row.dateValue(), row.dateTimeValue(),
                row.timeValue(), row.booleanValue(), indexReference, row.currencyCode(), now, now);
        if (field.searchable() && indexString != null) {
            var tokens = RecordSearchTokenizer.indexTokens(indexString);
            for (var ordinal = 0; ordinal < tokens.size(); ordinal++) {
                var token = tokens.get(ordinal);
                jdbc.update("INSERT INTO un_module_record_search "
                                + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,"
                                + "logical_module_id,logical_field_id,index_generation_id,record_status,field_type,"
                                + "token_ordinal,token,token_hash,created_at) VALUES (?,?,?,?,?,?,?,?,1,?,?,?,?,?,?)",
                        idService.nextId(), session.systemId(), tenantId, recordId,
                        Long.parseLong(catalog.versionId()), Long.parseLong(catalog.moduleSnapshotId()),
                        Long.parseLong(catalog.moduleId()), field.id(), recordStatus, field.type(), ordinal, token,
                        RecordSearchTokenizer.hash(token), now);
            }
        }
    }

    private void insertP4C2Projections(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            FieldDescriptor field,
            CanonicalFieldValueCodec.ValueRow row,
            String recordStatus,
            LocalDateTime now
    ) {
        var projections = new ArrayList<IndexProjection>();
        switch (field.type()) {
            case "PHONE", "EMAIL", "URL" -> projections.add(IndexProjection.string(
                    row.ordinal(), 0, row.stringValue()));
            case "IDENTITY", "SECRET" -> {
                var context = new SensitiveCryptoService.BlindIndexContext(
                        session.systemId(), tenantId, Long.parseLong(catalog.moduleId()), field.id(), field.type());
                sensitiveCrypto.equalityHashes(row.sensitiveValue(), context).forEach(hash ->
                        projections.add(IndexProjection.hash(row.ordinal(), hash.hashKeyVersion(), hash.valueHash())));
            }
            case "ADDRESS" -> {
                var value = readJsonObject(row.textValue(), field);
                var country = value.path("countryCode").asText();
                var region = value.path("regionCode").asText("");
                projections.add(IndexProjection.string(0, 0, country + "|" + region));
                projections.add(IndexProjection.string(1, 0, value.path("display").asText()));
            }
            case "GEO" -> {
                var value = readJsonObject(row.textValue(), field);
                var lat = value.path("lat").decimalValue();
                var lng = value.path("lng").decimalValue();
                projections.add(IndexProjection.geo(0, GeoHashCodec.encode(lat, lng, 12), lat, lng));
            }
            case "BARCODE" -> {
                var value = readJsonObject(row.textValue(), field);
                projections.add(IndexProjection.string(0, 0,
                        value.path("symbology").asText() + ":" + value.path("payload").asText()));
            }
            case "RICH_TEXT" -> {
                var visible = Jsoup.parseBodyFragment(row.textValue()).text();
                projections.add(IndexProjection.string(0, 0, truncate(visible, 512)));
                insertSearchTokens(session, tenantId, catalog, recordId, field, recordStatus, visible, now);
            }
            case "JSON" -> projections.addAll(jsonPathProjections(field, readJsonObject(row.textValue(), field)));
            case "STATUS" -> projections.add(IndexProjection.string(
                    row.ordinal(), 0, Long.toString(row.referenceValue())));
            default -> throw new IllegalStateException("Unsupported P4-C2 projection type " + field.type());
        }
        projections.forEach(projection -> insertProjection(
                session, tenantId, catalog, recordId, field, recordStatus, projection, now));
    }

    private List<IndexProjection> jsonPathProjections(FieldDescriptor field, ObjectNode value) {
        var result = new ArrayList<IndexProjection>();
        for (var declaration : field.schema().path("queryPaths")) {
            var id = positivePathId(declaration.path("pathSnapshotId"), field);
            var selected = declaredJsonValue(value, declaration.path("path").asText());
            if (selected == null) {
                continue;
            }
            if (selected.isNull()) {
                result.add(IndexProjection.nullValue(0, id));
                continue;
            }
            var type = declaration.path("type").asText();
            try {
                result.add(switch (type) {
                    case "STRING" -> IndexProjection.string(0, id, jsonPathString(selected, field, id));
                    case "DECIMAL" -> IndexProjection.decimal(
                            0, id, jsonPathDecimal(selected, field, id, false));
                    case "INTEGER" -> IndexProjection.decimal(
                            0, id, jsonPathDecimal(selected, field, id, true));
                    case "BOOLEAN" -> {
                        if (!selected.isBoolean()) {
                            throw fieldProjectionInvalid(field,
                                    "contains a value incompatible with declared JSON path " + id);
                        }
                        yield IndexProjection.bool(0, id, selected.booleanValue());
                    }
                    case "DATE" -> IndexProjection.date(
                            0, id, LocalDate.parse(jsonPathString(selected, field, id)));
                    case "DATETIME" -> IndexProjection.dateTime(
                            0, id, LocalDateTime.parse(jsonPathString(selected, field, id)));
                    default -> throw new IllegalStateException("Unsupported declared JSON path type " + type);
                });
            } catch (DateTimeParseException | NullPointerException exception) {
                throw fieldProjectionInvalid(field, "contains a value incompatible with declared JSON path " + id);
            }
        }
        return List.copyOf(result);
    }

    private static String jsonPathString(JsonNode value, FieldDescriptor field, long pathId) {
        if (!value.isTextual() || value.textValue().isEmpty() || value.textValue().length() > 512) {
            throw fieldProjectionInvalid(field,
                    "contains a value incompatible with declared JSON path " + pathId);
        }
        return value.textValue();
    }

    private static BigDecimal jsonPathDecimal(
            JsonNode value,
            FieldDescriptor field,
            long pathId,
            boolean integer
    ) {
        if (!value.isNumber()) {
            throw fieldProjectionInvalid(field,
                    "contains a value incompatible with declared JSON path " + pathId);
        }
        var decimal = value.decimalValue();
        if (decimal.precision() > 38 || decimal.scale() > 10
                || integer && decimal.stripTrailingZeros().scale() > 0) {
            throw fieldProjectionInvalid(field,
                    "contains a value outside declared JSON path precision " + pathId);
        }
        return decimal;
    }

    private void insertProjection(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            FieldDescriptor field,
            String recordStatus,
            IndexProjection value,
            LocalDateTime now
    ) {
        jdbc.update("INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,string_value,decimal_value,date_value,datetime_value,time_value,"
                        + "boolean_value,reference_value,hash_value,hash_key_version,geohash,geo_lat,geo_lng,"
                        + "currency_code,created_at,updated_at) VALUES (" + placeholders(29) + ")",
                idService.nextId(), session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), Long.parseLong(catalog.moduleId()), field.id(), 1L, 1L,
                value.pathSnapshotId(), value.ordinal(), recordStatus, value.kind(), value.stringValue(),
                value.decimalValue(), value.dateValue(), value.dateTimeValue(), null, value.booleanValue(), null,
                value.hashValue(), value.hashKeyVersion(), value.geohash(), value.latitude(), value.longitude(),
                null, now, now);
    }

    private void insertSearchTokens(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            FieldDescriptor field,
            String recordStatus,
            String visibleText,
            LocalDateTime now
    ) {
        var tokens = RecordSearchTokenizer.indexTokens(visibleText);
        for (var ordinal = 0; ordinal < tokens.size(); ordinal++) {
            var token = tokens.get(ordinal);
            jdbc.update("INSERT INTO un_module_record_search "
                            + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,"
                            + "logical_module_id,logical_field_id,index_generation_id,record_status,field_type,"
                            + "token_ordinal,token,token_hash,created_at) VALUES (?,?,?,?,?,?,?,?,1,?,?,?,?,?,?)",
                    idService.nextId(), session.systemId(), tenantId, recordId,
                    Long.parseLong(catalog.versionId()), Long.parseLong(catalog.moduleSnapshotId()),
                    Long.parseLong(catalog.moduleId()), field.id(), recordStatus, field.type(), ordinal, token,
                    RecordSearchTokenizer.hash(token), now);
        }
    }

    private ObjectNode readJsonObject(String value, FieldDescriptor field) {
        try {
            var parsed = objectMapper.readTree(value);
            if (parsed instanceof ObjectNode object) {
                return object;
            }
        } catch (JsonProcessingException ignored) {
            // Canonical value validation should make this unreachable; retain a field-scoped stable failure.
        }
        throw fieldProjectionInvalid(field, "cannot be projected from its canonical JSON value");
    }

    private static JsonNode declaredJsonValue(ObjectNode root, String path) {
        if (path == null || !path.matches("^\\$\\.[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")) {
            return null;
        }
        JsonNode current = root;
        for (var segment : path.substring(2).split("\\.")) {
            if (!current.isObject() || !current.has(segment)) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private static long positivePathId(JsonNode node, FieldDescriptor field) {
        try {
            var value = node.isIntegralNumber() ? node.longValue() : Long.parseLong(node.asText());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Publication validates this; keep runtime fail closed if a snapshot is corrupt.
        }
        throw fieldProjectionInvalid(field, "contains an invalid declared JSON path id");
    }

    private static BusinessException fieldProjectionInvalid(FieldDescriptor field, String reason) {
        var message = field.name() + " " + reason;
        return new BusinessException("FIELD_VALUE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError("FIELD_VALUE_INVALID", "values." + field.code(), message)));
    }

    private void insertLegacyValue(
            RuntimeSession session,
            long tenantId,
            Catalog catalog,
            long recordId,
            NormalizedValue normalized,
            String recordStatus,
            LocalDateTime now
    ) {
        var field = normalized.field();
        var value = normalized.value();
        var stringValue = "TEXT".equals(field.type()) ? value : null;
        var textValue = "TEXTAREA".equals(field.type()) ? value : null;
        var decimalValue = "NUMBER".equals(field.type()) ? value : null;
        var dateValue = "DATE".equals(field.type()) ? value : null;
        var datetimeValue = "DATETIME".equals(field.type()) ? value : null;
        var referenceValue = Set.of("RADIO", "MEMBER", "DEPARTMENT").contains(field.type()) ? value : null;
        jdbc.update("INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,ordinal,string_value,text_value,"
                        + "decimal_value,date_value,datetime_value,reference_value,encrypted_value,value_hash,display_value,"
                        + "created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,1,?,0,?,?,?,?,?,?,NULL,NULL,?,?,?,?,?,0)",
                idService.nextId(), session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), Long.parseLong(catalog.moduleId()), field.id(), field.id(),
                field.type(), stringValue, textValue, decimalValue, dateValue, datetimeValue, referenceValue,
                normalized.display(), now, session.memberId(), now, session.memberId());

        var valueKind = switch (field.type()) {
            case "TEXT", "TEXTAREA" -> "STRING";
            case "NUMBER" -> "DECIMAL";
            case "DATE" -> "DATE";
            case "DATETIME" -> "DATETIME";
            case "RADIO", "MEMBER", "DEPARTMENT" -> "REFERENCE";
            default -> throw new IllegalStateException("Unsupported runtime index type " + field.type());
        };
        var indexString = stringValue != null ? truncate((String) stringValue, 512)
                : textValue != null ? truncate((String) textValue, 512) : null;
        jdbc.update("INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,string_value,decimal_value,date_value,datetime_value,boolean_value,"
                        + "reference_value,hash_value,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,0,?,?,?,?,?,?,NULL,?,NULL,NULL,?,?)",
                idService.nextId(), session.systemId(), tenantId, recordId, Long.parseLong(catalog.versionId()),
                Long.parseLong(catalog.moduleSnapshotId()), Long.parseLong(catalog.moduleId()), field.id(), recordStatus, valueKind,
                indexString, decimalValue, dateValue, datetimeValue, referenceValue, now, now);
        if (field.searchable() && indexString != null) {
            var tokens = RecordSearchTokenizer.indexTokens(indexString);
            for (var ordinal = 0; ordinal < tokens.size(); ordinal++) {
                var token = tokens.get(ordinal);
                jdbc.update("INSERT INTO un_module_record_search "
                                + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,"
                                + "logical_module_id,logical_field_id,index_generation_id,record_status,field_type,"
                                + "token_ordinal,token,token_hash,created_at) VALUES (?,?,?,?,?,?,?,?,1,?,?,?,?,?,?)",
                        idService.nextId(), session.systemId(), tenantId, recordId,
                        Long.parseLong(catalog.versionId()), Long.parseLong(catalog.moduleSnapshotId()),
                        Long.parseLong(catalog.moduleId()), field.id(), recordStatus, field.type(), ordinal, token,
                        RecordSearchTokenizer.hash(token), now);
            }
        }
    }

    private static RecordRuntimeViews.RecordDetail response(
            long recordId,
            String recordNo,
            long version,
            String status,
            String title,
            String schemaVersionId,
            List<NormalizedValue> values,
            List<String> actions
    ) {
        var fields = values.stream().map(value -> new RecordRuntimeViews.FieldValue(
                value.field().code(), value.field().name(), value.field().type(),
                visibleValue(value), value.display())).toList();
        return new RecordRuntimeViews.RecordDetail(Long.toString(recordId), recordNo, version, status, title,
                schemaVersionId, fields, actions);
    }

    private static Object visibleValue(NormalizedValue value) {
        return switch (value.field().type()) {
            case "SECRET" -> null;
            case "IDENTITY" -> value.field().sensitiveReadable() ? value.value() : null;
            default -> value.value();
        };
    }

    private static RecordRuntimeViews.RecordDetail redactSensitive(RecordRuntimeViews.RecordDetail record) {
        if (record == null) {
            return null;
        }
        var values = record.values().stream().map(value -> SENSITIVE_TYPES.contains(value.type())
                ? new RecordRuntimeViews.FieldValue(
                value.fieldCode(), value.fieldName(), value.type(), null, value.displayValue())
                : value).toList();
        return new RecordRuntimeViews.RecordDetail(record.recordId(), record.recordNo(), record.version(),
                record.status(), record.title(), record.schemaVersionId(), values, record.actions());
    }

    private static String title(String supplied, List<NormalizedValue> values, String fallback) {
        if (supplied != null && !supplied.isBlank()) {
            var normalized = supplied.trim();
            if (normalized.length() > 500) {
                throw new BusinessException("RECORD_VALIDATION_FAILED", "记录标题最多允许 500 个字符",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        List.of(new ApiError("RECORD_VALIDATION_FAILED", "title", "标题过长")));
            }
            return normalized;
        }
        return values.stream().filter(value -> Set.of("TEXT", "TEXTAREA").contains(value.field().type()))
                .map(value -> truncate(String.valueOf(value.value()), 500)).findFirst().orElse(fallback);
    }

    private static void requireCurrentSchema(String supplied, String current) {
        if (supplied == null || !supplied.matches("^[1-9][0-9]{0,18}$") || !current.equals(supplied)) {
            throw new BusinessException("RECORD_SCHEMA_STALE", "表单版本已更新，请刷新后重新创建",
                    HttpStatus.CONFLICT);
        }
    }

    private void requireVisibleRecord(RuntimeSession session, String moduleCode, Catalog catalog, long recordId) {
        if (recordId <= 0) {
            throw notFound();
        }
        var rows = detailRows(session, catalog, recordId, "r.status IN ('ACTIVE','ARCHIVED')");
        if (!rows.isEmpty()) {
            return;
        }
        var drafts = detailRows(session, catalog, recordId, "r.status='DRAFT'");
        if (drafts.isEmpty() || !allowed(session, moduleCode, "update")) {
            throw notFound();
        }
    }

    private FieldDescriptor compositionField(Catalog catalog, String fieldCode, String type, boolean write) {
        var field = catalog.fields().stream().filter(candidate -> candidate.code().equals(fieldCode)
                        && candidate.type().equals(type))
                .findFirst().orElseThrow(RecordRuntimeService::notFound);
        if (write && !field.writable()) {
            throw new BusinessException("PERMISSION_DENIED", "Composition field is not writable", HttpStatus.FORBIDDEN);
        }
        return field;
    }

    private String targetModuleCode(long systemId, long schemaVersionId, String targetModuleId) {
        if (targetModuleId == null || !targetModuleId.matches("^[1-9][0-9]{0,18}$")) {
            throw new BusinessException("RECORD_SCHEMA_STALE", "Relation target module is unavailable",
                    HttpStatus.CONFLICT);
        }
        var codes = jdbc.query("SELECT module_code FROM un_module_runtime_schema_module WHERE system_id=? "
                        + "AND schema_version_id=? AND source_module_id=?",
                (row, number) -> row.getString("module_code"), systemId, schemaVersionId,
                Long.parseLong(targetModuleId));
        if (codes.size() != 1) {
            throw new BusinessException("RECORD_SCHEMA_STALE", "Relation target module is unavailable",
                    HttpStatus.CONFLICT);
        }
        return codes.getFirst();
    }

    private RecordRuntimeViews.CompositionCapabilities compositionCapabilities(
            RuntimeSession session,
            String moduleCode,
            FieldDescriptor field
    ) {
        var updateAllowed = field.writable() && allowed(session, moduleCode, "update");
        var writePermission = "module." + moduleCode + ".field." + field.code() + ".write";
        var readPermission = "module." + moduleCode + ".field." + field.code() + ".read";
        var relation = "RELATION".equals(field.type());
        var subtable = "SUBTABLE".equals(field.type());
        return new RecordRuntimeViews.CompositionCapabilities(
                capability(readPermission, relation, "NOT_APPLICABLE"),
                capability(writePermission, relation && updateAllowed, updateAllowed ? "NOT_APPLICABLE" : "FORBIDDEN"),
                capability(writePermission, relation && updateAllowed, updateAllowed ? "NOT_APPLICABLE" : "FORBIDDEN"),
                capability(writePermission, relation && updateAllowed && field.schema().path("multiple").asBoolean(false),
                        updateAllowed ? "NOT_APPLICABLE" : "FORBIDDEN"),
                capability(readPermission, subtable, "NOT_APPLICABLE"),
                capability(writePermission, subtable && updateAllowed
                                && field.schema().path("allowRowCreate").asBoolean(false),
                        updateAllowed ? "DISABLED_BY_SCHEMA" : "FORBIDDEN"),
                capability(writePermission, subtable && updateAllowed
                                && field.schema().path("allowRowUpdate").asBoolean(false),
                        updateAllowed ? "DISABLED_BY_SCHEMA" : "FORBIDDEN"),
                capability(writePermission, subtable && updateAllowed
                                && field.schema().path("allowRowDelete").asBoolean(false),
                        updateAllowed ? "DISABLED_BY_SCHEMA" : "FORBIDDEN"),
                capability(writePermission, subtable && updateAllowed
                                && field.schema().path("allowRowReorder").asBoolean(false),
                        updateAllowed ? "DISABLED_BY_SCHEMA" : "FORBIDDEN")
        );
    }

    private static RecordRuntimeViews.OperationCapability capability(
            String permissionCode, boolean enabled, String disabledReason
    ) {
        return new RecordRuntimeViews.OperationCapability(permissionCode, enabled, enabled ? null : disabledReason);
    }

    private static void validateCompositionPage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("QUERY_INVALID", "Composition page must use page>=1 and size 1..100",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private CommandRecord mutableCompositionRecord(
            RuntimeSession session,
            String moduleCode,
            Catalog catalog,
            long recordId,
            long expectedVersion
    ) {
        if (recordId <= 0) {
            throw notFound();
        }
        var records = commandRows(session, catalog, recordId, true);
        if (records.isEmpty()) {
            throw notFound();
        }
        var record = records.getFirst();
        requireCurrentRecordSchema(record, catalog);
        if (!Set.of("DRAFT", "ACTIVE").contains(record.status())) {
            throw new BusinessException("RECORD_STATE_INVALID", "Composition cannot be edited in this state",
                    HttpStatus.CONFLICT);
        }
        if (expectedVersion < 0 || expectedVersion != record.version()) {
            var currentValues = persistedValues(session, catalog, recordId);
            var current = response(recordId, record.recordNo(), record.version(), record.status(), record.title(),
                    catalog.versionId(), currentValues,
                    ruleActions(session, moduleCode, record.status(), catalog, currentValues));
            throw versionConflict(current);
        }
        return record;
    }

    private void validateRelationAdds(
            RuntimeSession session,
            Catalog source,
            FieldDescriptor field,
            List<RecordRuntimeViews.RelationTargetInput> additions
    ) {
        if (additions.isEmpty()) {
            return;
        }
        var targetModuleId = field.schema().path("targetModuleId").asText("");
        var targetCode = targetModuleCode(session.systemId(), Long.parseLong(source.versionId()), targetModuleId);
        var target = availableCatalog(session, targetCode);
        var targetScope = scope(session, target);
        var targetFilter = compileRelationTargetFilter(session, field, target);
        var seen = new HashSet<String>();
        for (var addition : additions) {
            if (addition == null || addition.targetRecordId() == null
                    || !addition.targetRecordId().matches("^[1-9][0-9]{0,18}$")
                    || !seen.add(addition.targetRecordId())) {
                throw compositionInvalid("relations." + field.code(), "add contains an invalid or duplicate target");
            }
            var arguments = new ArrayList<Object>();
            arguments.add(session.systemId());
            arguments.add(requiredTenant(session));
            arguments.add(Long.parseLong(target.moduleId()));
            arguments.add(Long.parseLong(addition.targetRecordId()));
            arguments.addAll(targetScope.arguments());
            arguments.addAll(targetFilter.arguments());
            var versions = jdbc.query("SELECT r.version FROM un_module_record r WHERE r.system_id=? "
                            + "AND r.tenant_id=? AND r.logical_module_id=? AND r.record_id=? AND r.status='ACTIVE' "
                            + "AND " + targetScope.sql() + " AND " + targetFilter.predicateSql(),
                    (row, number) -> row.getLong("version"), arguments.toArray());
            if (versions.size() != 1 || versions.getFirst() != addition.targetExpectedVersion()) {
                throw compositionInvalid("relations." + field.code(), "add contains an unavailable or stale target");
            }
        }
    }

    private FieldDescriptor relationDisplayField(FieldDescriptor relation, Catalog target) {
        var configured = relation.schema().path("displayFieldId").asText("");
        if (configured.isBlank()) {
            return null;
        }
        return target.fields().stream()
                .filter(candidate -> Long.toString(candidate.id()).equals(configured)
                        && !SENSITIVE_TYPES.contains(candidate.type())
                        && !P4_C3_COMPOSITION_TYPES.contains(candidate.type()))
                .findFirst().orElse(null);
    }

    private static String relationDisplaySql(FieldDescriptor displayField) {
        if (displayField == null) {
            return "r.title";
        }
        var id = displayField.id();
        return "COALESCE((SELECT COALESCE(rv.display_value,rv.string_value,LEFT(rv.text_value,1000),"
                + "CAST(rv.decimal_value AS CHAR),CAST(rv.date_value AS CHAR),CAST(rv.datetime_value AS CHAR),"
                + "CAST(rv.time_value AS CHAR),CAST(rv.boolean_value AS CHAR),CAST(rv.reference_value AS CHAR)) "
                + "FROM un_module_record_value rv WHERE rv.system_id=r.system_id AND rv.tenant_id=r.tenant_id "
                + "AND rv.record_id=r.record_id AND rv.schema_version_id=r.schema_version_id "
                + "AND rv.module_snapshot_id=r.module_snapshot_id AND rv.logical_field_id=" + id
                + " ORDER BY rv.ordinal LIMIT 1),r.title)";
    }

    private RecordQueryModels.CompiledQuery compileRelationTargetFilter(
            RuntimeSession session,
            FieldDescriptor relation,
            Catalog target
    ) {
        var configured = relation.schema().path("filter");
        if (!configured.isObject()) {
            return new RecordQueryModels.CompiledQuery("1=1", List.of(), "r.record_id ASC", List.of());
        }
        var filter = relationFilterNode(configured, target);
        var request = objectMapper.createObjectNode();
        request.put("schemaVersionId", target.versionId());
        request.put("page", 1);
        request.put("size", 1);
        request.put("recordScope", "active");
        request.putNull("q");
        request.set("filter", filter);
        request.putArray("sort");
        request.putArray("columns");
        request.putNull("viewId");
        var parsed = queryParser.parse(request.toString());
        var fields = target.fields().stream().map(field -> new RecordQueryModels.QueryField(
                field.id(), field.code(), queryType(field), field.searchable(), field.filterable(), field.sortable(),
                field.sensitiveQueryable(), field.schema())).toList();
        return queryCompiler.compile(parsed, fields,
                (field, value) -> sensitiveQueryHashes(session, target, field, value));
    }

    private ObjectNode relationFilterNode(JsonNode configured, Catalog target) {
        if (configured.hasNonNull("join")) {
            var result = objectMapper.createObjectNode();
            result.put("kind", "OR".equals(configured.path("join").asText()) ? "OR" : "AND");
            var children = result.putArray("children");
            for (var child : configured.path("children")) {
                children.add(relationFilterNode(child, target));
            }
            return result;
        }
        var configuredId = configured.path("fieldId").asText("");
        var field = target.fields().stream()
                .filter(candidate -> Long.toString(candidate.id()).equals(configuredId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("QUERY_FIELD_UNAVAILABLE",
                        "Relation target filter is unavailable", HttpStatus.UNPROCESSABLE_ENTITY));
        var configuredOperator = configured.path("operator").asText("");
        var negate = Set.of("NOT_EMPTY", "NOT_IN").contains(configuredOperator);
        var operator = switch (configuredOperator) {
            case "NOT_EMPTY" -> "EMPTY";
            case "NOT_IN" -> "IN";
            default -> configuredOperator;
        };
        var predicate = objectMapper.createObjectNode();
        predicate.put("kind", "PREDICATE");
        predicate.put("fieldCode", field.code());
        predicate.put("operator", operator);
        if (configured.has("value")) {
            predicate.set("value", configured.get("value").deepCopy());
        }
        if (!negate) {
            return predicate;
        }
        var result = objectMapper.createObjectNode();
        result.put("kind", "NOT");
        result.putArray("children").add(predicate);
        return result;
    }

    private void validateAggregateRelations(
            RuntimeSession session,
            Catalog catalog,
            List<RecordRuntimeViews.RelationInput> relations
    ) {
        var fields = new HashMap<String, FieldDescriptor>();
        catalog.fields().forEach(field -> fields.put(field.code(), field));
        var seen = new HashSet<String>();
        for (var relation : relations) {
            var field = relation == null ? null : fields.get(relation.fieldCode());
            if (field == null || !"RELATION".equals(field.type()) || !field.writable()
                    || !seen.add(field.code())) {
                throw compositionInvalid("relations", "contains an unavailable, readonly, or duplicate field");
            }
            validateRelationAdds(session, catalog, field, relation.targets());
        }
    }

    private void validateRelationQueryTargets(
            RuntimeSession session,
            Catalog catalog,
            RecordQueryModels.FilterNode node,
            Active historicalVersion
    ) {
        if (node == null) {
            return;
        }
        if (node instanceof RecordQueryModels.Group group) {
            group.children().forEach(child -> validateRelationQueryTargets(
                    session, catalog, child, historicalVersion));
            return;
        }
        var predicate = (RecordQueryModels.Predicate) node;
        var field = catalog.fields().stream().filter(candidate -> candidate.code().equals(predicate.fieldCode()))
                .findFirst().orElse(null);
        if (field == null || !"RELATION".equals(field.type()) || !"HAS_ANY".equals(predicate.operator())) {
            return;
        }
        var targetModuleId = field.schema().path("targetModuleId").asText("");
        var targetCode = targetModuleCode(session.systemId(), Long.parseLong(catalog.versionId()), targetModuleId);
        HistoricalCatalog historicalTarget = null;
        final Catalog target;
        if (historicalVersion == null) {
            target = availableCatalog(session, targetCode);
        } else {
            historicalTarget = historicalCatalog(
                    session, targetCode, "view", historicalVersion);
            target = requireAvailable(historicalTarget.catalog());
        }
        var targetScope = scope(session, target);
        for (var supplied : predicate.value()) {
            var targetId = supplied.isTextual() ? supplied.textValue() : supplied.asText("");
            if (!targetId.matches("^[1-9][0-9]{0,18}$")) {
                throw invalidQuery("Relation query target is unavailable");
            }
            var arguments = new ArrayList<Object>();
            arguments.add(session.systemId());
            arguments.add(requiredTenant(session));
            var identity = historicalTarget == null
                    ? null : historicalTarget.identity();
            arguments.add(identity == null
                    ? Long.parseLong(target.moduleId())
                    : identity.logicalModuleId());
            arguments.add(Long.parseLong(targetId));
            arguments.addAll(targetScope.arguments());
            var visible = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_record r WHERE r.system_id=? "
                            + "AND r.tenant_id=? AND r.logical_module_id=? "
                            + "AND r.record_id=? AND r.status='ACTIVE' AND "
                            + targetScope.sql(),
                    Long.class, arguments.toArray());
            if (visible == null || visible != 1) {
                throw invalidQuery("Relation query target is unavailable");
            }
        }
    }

    private static List<RecordRuntimeViews.RelationTargetInput> orderedRelationTargets(
            LinkedHashMap<String, RecordRuntimeViews.RelationTargetInput> targets,
            List<String> order,
            String fieldCode
    ) {
        if (order.isEmpty()) {
            return targets.values().stream().sorted(Comparator.comparingInt(
                    RecordRuntimeViews.RelationTargetInput::ordinal)).toList();
        }
        var seen = new HashSet<String>();
        var result = new ArrayList<RecordRuntimeViews.RelationTargetInput>();
        for (var index = 0; index < order.size(); index++) {
            var target = targets.get(order.get(index));
            if (target == null || !seen.add(order.get(index))) {
                throw compositionInvalid("relations." + fieldCode, "order must contain each final target once");
            }
            result.add(new RecordRuntimeViews.RelationTargetInput(
                    target.targetRecordId(), target.targetExpectedVersion(), index));
        }
        if (result.size() != targets.size()) {
            throw compositionInvalid("relations." + fieldCode, "order must contain each final target once");
        }
        return List.copyOf(result);
    }

    private static ArrayList<RecordRuntimeViews.SubRowInput> orderedSubtableRows(
            List<RecordRuntimeViews.SubRowInput> rows,
            List<String> order,
            String fieldCode
    ) {
        var result = new ArrayList<>(rows);
        result.sort(Comparator.comparingInt(RecordRuntimeViews.SubRowInput::ordinal));
        if (order.isEmpty()) {
            return result;
        }
        var existing = result.stream().filter(row -> row.rowId() != null).toList();
        var orderedIds = existing.stream().map(RecordRuntimeViews.SubRowInput::rowId).toList();
        if (!orderedIds.equals(order) || new HashSet<>(order).size() != order.size()) {
            throw compositionInvalid("subtables." + fieldCode,
                    "order must match existing final rows in ordinal order");
        }
        return result;
    }

    private static void requireSubtableOperations(
            FieldDescriptor field, RecordRuntimeViews.SubtableMutationRequest request
    ) {
        if (!request.add().isEmpty() && !field.schema().path("allowRowCreate").asBoolean(false)) {
            throw new BusinessException("PERMISSION_DENIED", "Subtable row creation is disabled", HttpStatus.FORBIDDEN);
        }
        if (!request.update().isEmpty() && !field.schema().path("allowRowUpdate").asBoolean(false)) {
            throw new BusinessException("PERMISSION_DENIED", "Subtable row update is disabled", HttpStatus.FORBIDDEN);
        }
        if (!request.remove().isEmpty() && !field.schema().path("allowRowDelete").asBoolean(false)) {
            throw new BusinessException("PERMISSION_DENIED", "Subtable row removal is disabled", HttpStatus.FORBIDDEN);
        }
        if (!request.order().isEmpty() && !field.schema().path("allowRowReorder").asBoolean(false)) {
            throw new BusinessException("PERMISSION_DENIED", "Subtable row reorder is disabled", HttpStatus.FORBIDDEN);
        }
    }

    private void incrementCompositionVersion(
            RuntimeSession session,
            long tenantId,
            CommandRecord record,
            long recordId,
            LocalDateTime now
    ) {
        var updated = jdbc.update("UPDATE un_module_record SET updated_at=?,updated_by=?,version=version+1 "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND status=? AND version=?",
                now, session.memberId(), session.systemId(), tenantId, recordId, record.schemaVersionId(),
                record.moduleSnapshotId(), record.status(), record.version());
        if (updated != 1) {
            throw new BusinessException("RECORD_VERSION_CONFLICT", "Record changed during composition mutation",
                    HttpStatus.CONFLICT);
        }
    }

    private RecordRuntimeViews.RecordMutationResponse compositionMutationResponse(
            long recordId, CommandRecord record, String requestId, String traceId
    ) {
        var history = jdbc.query("SELECT id FROM un_audit_operation WHERE aggregate_type='RUNTIME_RECORD' "
                        + "AND aggregate_id=? AND request_id=? AND result='SUCCESS' ORDER BY created_at DESC,id DESC LIMIT 1",
                (row, number) -> row.getLong("id"), Long.toString(recordId), requestId);
        if (history.size() != 1) {
            throw new IllegalStateException("Composition mutation audit history was not persisted");
        }
        return new RecordRuntimeViews.RecordMutationResponse(Long.toString(recordId), record.version() + 1,
                Long.toString(record.schemaVersionId()), record.status(), Long.toString(history.getFirst()), traceId);
    }

    private static BusinessException compositionInvalid(String path, String message) {
        var relation = path.startsWith("relations.");
        var code = relation ? "RECORD_RELATION_INVALID" : "SUBTABLE_ROW_INVALID";
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError(code, path, message)));
    }

    private boolean allowed(RuntimeSession session, String moduleCode, String verb) {
        var permission = "module." + moduleCode + "." + verb;
        if (!session.permissions().contains(permission)) {
            return false;
        }
        return !authorizationFacade.resolve(new RuntimeAuthorizationFacade.RuntimeAuthorizationRequest(
                session.systemId(), requiredTenant(session), session.memberId(), permission)).denied();
    }

    private void requireScopePermission(RuntimeSession session, String moduleCode, String verb) {
        if (!allowed(session, moduleCode, verb)) {
            throw new BusinessException("QUERY_SCOPE_FORBIDDEN",
                    "Current member cannot query this record scope", HttpStatus.FORBIDDEN);
        }
    }

    private static List<FieldDescriptor> selectedFields(Catalog catalog, List<String> columns) {
        if (columns.isEmpty()) {
            return catalog.listFields();
        }
        var byCode = new LinkedHashMap<String, FieldDescriptor>();
        catalog.fields().forEach(field -> byCode.put(field.code(), field));
        var result = new ArrayList<FieldDescriptor>();
        for (var code : columns) {
            var field = byCode.get(code);
            if (field == null) {
                throw new BusinessException("QUERY_FIELD_UNAVAILABLE",
                        "Column is unknown, retired or unreadable: " + code, HttpStatus.UNPROCESSABLE_ENTITY);
            }
            result.add(field);
        }
        return List.copyOf(result);
    }

    private RecordQueryModels.RecordQuery applySavedView(
            RuntimeSession session,
            Catalog catalog,
            RecordQueryModels.RecordQuery supplied,
            List<RecordQueryModels.QueryField> fields,
            List<String> invalidNodes
    ) {
        var stored = savedViews.require(session, Long.parseLong(catalog.moduleId()),
                Long.parseLong(supplied.viewId()));
        final ObjectNode merged;
        final JsonNode suppliedNode;
        try {
            var storedNode = objectMapper.readTree(stored.queryJson());
            suppliedNode = objectMapper.readTree(supplied.canonicalJson());
            if (!storedNode.isObject()) {
                throw new IllegalStateException("Stored saved view query is not an object");
            }
            merged = ((ObjectNode) storedNode).deepCopy();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored saved view query is invalid", exception);
        }
        merged.put("schemaVersionId", catalog.versionId());
        merged.put("page", supplied.page());
        merged.put("size", supplied.size());
        merged.put("recordScope", supplied.recordScope());
        merged.put("viewId", supplied.viewId());
        if (supplied.q() != null) {
            merged.set("q", suppliedNode.get("q"));
        }
        if (supplied.filter() != null) {
            merged.set("filter", suppliedNode.get("filter"));
        }
        if (!supplied.sort().isEmpty()) {
            merged.set("sort", suppliedNode.get("sort"));
        }
        if (!supplied.columns().isEmpty()) {
            merged.set("columns", suppliedNode.get("columns"));
        }
        repairSavedQuery(merged, fields, invalidNodes);
        return queryParser.parse(merged.toString());
    }

    private void repairSavedQuery(
            ObjectNode query,
            List<RecordQueryModels.QueryField> fields,
            List<String> invalidNodes
    ) {
        var byCode = new LinkedHashMap<String, RecordQueryModels.QueryField>();
        fields.forEach(field -> byCode.put(field.code(), field));
        if (!query.path("q").isNull() && fields.stream().noneMatch(RecordQueryModels.QueryField::searchable)) {
            invalidNodes.add("/q");
            query.putNull("q");
        }
        if (!query.path("filter").isNull()) {
            var filter = repairFilter(query.get("filter"), byCode, "/filter", invalidNodes);
            if (filter == null) query.putNull("filter"); else query.set("filter", filter);
        }
        var repairedSort = objectMapper.createArrayNode();
        var sort = query.withArray("sort");
        for (var index = 0; index < sort.size(); index++) {
            var item = sort.get(index);
            var field = byCode.get(item.path("fieldCode").asText());
            if (field == null || !field.sortable()) {
                invalidNodes.add("/sort/" + index);
            } else {
                repairedSort.add(item);
            }
        }
        query.set("sort", repairedSort);
        var repairedColumns = objectMapper.createArrayNode();
        var columns = query.withArray("columns");
        for (var index = 0; index < columns.size(); index++) {
            var code = columns.get(index).asText();
            if (!byCode.containsKey(code)) {
                invalidNodes.add("/columns/" + index);
            } else {
                repairedColumns.add(code);
            }
        }
        query.set("columns", repairedColumns);
    }

    private JsonNode repairFilter(
            JsonNode node,
            Map<String, RecordQueryModels.QueryField> fields,
            String path,
            List<String> invalidNodes
    ) {
        if ("PREDICATE".equals(node.path("kind").asText())) {
            var field = fields.get(node.path("fieldCode").asText());
            if (!queryCompiler.supportsFilter(field, node.path("operator").asText())) {
                invalidNodes.add(path);
                return null;
            }
            return node;
        }
        var kind = node.path("kind").asText();
        var children = node.path("children");
        if (!Set.of("AND", "OR", "NOT").contains(kind) || !children.isArray()) {
            invalidNodes.add(path);
            return null;
        }
        var repaired = objectMapper.createArrayNode();
        for (var index = 0; index < children.size(); index++) {
            var child = repairFilter(children.get(index), fields, path + "/children/" + index, invalidNodes);
            if (child != null) repaired.add(child);
        }
        if (repaired.isEmpty() || "NOT".equals(kind) && repaired.size() != 1) {
            if (!invalidNodes.contains(path)) invalidNodes.add(path);
            return null;
        }
        var result = objectMapper.createObjectNode();
        result.put("kind", kind);
        result.set("children", repaired);
        return result;
    }

    private static BusinessException fieldError(String code, String fieldCode, String message, HttpStatus status) {
        return new BusinessException(code, message, status,
                List.of(new ApiError(code, "values." + fieldCode, message)));
    }

    private static BusinessException versionConflict() {
        return new BusinessException("RECORD_VERSION_CONFLICT", "记录版本已经变化，请刷新后重试",
                HttpStatus.CONFLICT);
    }

    private static BusinessException versionConflict(RecordRuntimeViews.RecordDetail current) {
        return new BusinessException(
                "RECORD_VERSION_CONFLICT",
                "The record version has changed; reload before retrying",
                HttpStatus.CONFLICT,
                List.of(),
                new RecordRuntimeViews.VersionConflictData(current.version(), current, List.of("record"))
        );
    }

    private BusinessException versionConflict(
            WriteInput input,
            RecordRuntimeViews.RecordDetail current
    ) {
        var fields = new ArrayList<String>();
        if (input.title() != null && !input.title().trim().equals(current.title())) {
            fields.add("title");
        }
        var currentByCode = new LinkedHashMap<String, RecordRuntimeViews.FieldValue>();
        current.values().forEach(value -> currentByCode.put(value.fieldCode(), value));
        for (var supplied : input.values().entrySet()) {
            var currentValue = currentByCode.remove(supplied.getKey());
            if (!sameValue(currentValue, supplied.getValue())) {
                fields.add("values." + supplied.getKey());
            }
        }
        for (var remaining : currentByCode.keySet()) {
            fields.add("values." + remaining);
        }
        if (fields.isEmpty()) {
            fields.add("record");
        }
        return new BusinessException(
                "RECORD_VERSION_CONFLICT",
                "记录版本已经变化，请选择重新加载或复制为新草稿",
                HttpStatus.CONFLICT,
                List.of(),
                new RecordRuntimeViews.VersionConflictData(current.version(), current, fields)
        );
    }

    private boolean sameValue(RecordRuntimeViews.FieldValue current, JsonNode supplied) {
        if (current == null) {
            return supplied == null || supplied.isNull() || (supplied.isTextual() && supplied.asText().isBlank());
        }
        if (supplied == null || supplied.isNull()) {
            return false;
        }
        return switch (current.type()) {
            case "NUMBER" -> supplied.isNumber()
                    && new BigDecimal(String.valueOf(current.value())).compareTo(supplied.decimalValue()) == 0;
            case "RADIO", "MEMBER", "DEPARTMENT" -> supplied.isValueNode()
                    && String.valueOf(current.value()).equals(supplied.asText());
            default -> objectMapper.valueToTree(current.value()).equals(supplied);
        };
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private ScopeSql scope(RuntimeSession session, Catalog catalog) {
        var grant = catalog.grant();
        if (grant.allRecords()) {
            return new ScopeSql("1=1", List.of());
        }
        var parts = new ArrayList<String>();
        var arguments = new ArrayList<Object>();
        if (!grant.ownerMemberIds().isEmpty()) {
            parts.add("r.owner_member_id IN (" + placeholders(grant.ownerMemberIds().size()) + ")");
            arguments.addAll(grant.ownerMemberIds());
        }
        if (!grant.ownerDepartmentIds().isEmpty()) {
            parts.add("r.owner_department_id IN (" + placeholders(grant.ownerDepartmentIds().size()) + ")");
            arguments.addAll(grant.ownerDepartmentIds());
        }
        if (!grant.fieldRuleAsts().isEmpty()) {
            var fields = queryFields(catalog);
            for (var fieldRuleAst : grant.fieldRuleAsts()) {
                final JsonNode filter;
                try {
                    filter = objectMapper.readTree(fieldRuleAst);
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new BusinessException("RUNTIME_SCOPE_INVALID",
                            "Published field-rule data scope is malformed", HttpStatus.CONFLICT);
                }
                var rule = queryParser.parseScenario(filter, objectMapper.createArrayNode());
                var compiled = queryCompiler.compile(rule, fields,
                        (field, value) -> sensitiveQueryHashes(session, catalog, field, value));
                parts.add("(" + compiled.predicateSql() + ")");
                arguments.addAll(compiled.arguments());
            }
        }
        return new ScopeSql(parts.isEmpty() ? "1=0" : "(" + String.join(" OR ", parts) + ")", arguments);
    }

    private List<RecordQueryModels.QueryField> queryFields(Catalog catalog) {
        var fields = new ArrayList<RecordQueryModels.QueryField>();
        fields.add(new RecordQueryModels.QueryField(
                0L, "record_no", "RECORD_NO", false, true, false, false,
                objectMapper.createObjectNode()));
        fields.add(new RecordQueryModels.QueryField(
                -1L, "updated_at", "RECORD_UPDATED_AT", false, false, true, false,
                objectMapper.createObjectNode()));
        catalog.fields().stream().map(field -> new RecordQueryModels.QueryField(
                        field.id(), field.code(), queryType(field), field.searchable(), field.filterable(),
                        field.sortable(), field.sensitiveQueryable(), field.schema()))
                .forEach(fields::add);
        return List.copyOf(fields);
    }

    private static void validateQuery(int page, int size, String status, String sort, String filter) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 200 || !RECORD_STATUSES.contains(status)) {
            throw invalidQuery("分页或记录状态无效");
        }
        if (filter != null && !filter.isBlank()) {
            throw invalidQuery("当前只读切片尚未开放结构化筛选");
        }
        order(sort);
    }

    private static String order(String sort) {
        if (sort == null || sort.isBlank()) {
            return "r.updated_at DESC,r.record_id ASC";
        }
        var parts = sort.split(":", -1);
        var column = SORT_COLUMNS.get(parts[0]);
        if (parts.length != 2 || column == null
                || !("asc".equalsIgnoreCase(parts[1]) || "desc".equalsIgnoreCase(parts[1]))) {
            throw invalidQuery("排序字段或方向无效");
        }
        return column + " " + parts[1].toUpperCase() + ",r.record_id ASC";
    }

    private static BusinessException invalidQuery(String message) {
        return new BusinessException("QUERY_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException invalidTransferTarget() {
        return new BusinessException(
                "BATCH_TRANSFER_TARGET_INVALID",
                "Batch transfer target is not an active member of the current tenant",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException batchEditValidation(BusinessException source) {
        if ("BATCH_EDIT_VALIDATION_FAILED".equals(source.code())) {
            return source;
        }
        var errors = source.errors().stream()
                .map(error -> new ApiError(
                        "BATCH_EDIT_VALIDATION_FAILED",
                        error.path(),
                        error.message()))
                .toList();
        return new BusinessException(
                "BATCH_EDIT_VALIDATION_FAILED",
                source.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY,
                errors,
                source.data());
    }

    private static BusinessException batchEditValidation(String path, String message) {
        return new BusinessException(
                "BATCH_EDIT_VALIDATION_FAILED",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError("BATCH_EDIT_VALIDATION_FAILED", path, message)));
    }

    private static Map<String, Object> ownershipSnapshot(long memberId, Long departmentId) {
        var result = new LinkedHashMap<String, Object>();
        result.put("ownerMemberId", Long.toString(memberId));
        result.put("ownerDepartmentId", departmentId == null ? null : Long.toString(departmentId));
        return result;
    }

    private static BusinessException notFound() {
        return new BusinessException("RECORD_NOT_FOUND", "记录不存在或当前成员无权查看", HttpStatus.NOT_FOUND);
    }

    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("CONTEXT_TENANT_REQUIRED", "当前请求缺少有效租户上下文", HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    private static void requireModuleCode(String moduleCode) {
        if (moduleCode == null
                || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new BusinessException(
                    "MODULE_NOT_PUBLISHED",
                    "模块未发布或已停用",
                    HttpStatus.NOT_FOUND);
        }
    }

    private static long positiveSchemaVersionId(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException exception) {
            throw historicalUnavailable(
                    "Exact published module schema is unavailable");
        }
    }

    private static BusinessException historicalUnavailable(String message) {
        return new BusinessException(
                "FIELD_RUNTIME_UNAVAILABLE", message,
                HttpStatus.CONFLICT);
    }

    private Map<String, Set<String>> permissionsByResource(JsonNode snapshot, String type) {
        var result = new HashMap<String, Set<String>>();
        snapshot.path("permissions").forEach(permission -> {
            if (type.equals(permission.path("resource_type").asText()) && enabled(permission)) {
                result.computeIfAbsent(permission.path("resource_id").asText(), ignored -> new java.util.LinkedHashSet<>())
                        .add(permission.path("permission_code").asText());
            }
        });
        return result;
    }

    private static List<CanonicalFieldValueCodec.ValueOption> dictionaryOptions(
            JsonNode snapshot,
            String dictionaryId
    ) {
        if (dictionaryId == null || dictionaryId.isBlank()) {
            return List.of();
        }
        var result = new ArrayList<CanonicalFieldValueCodec.ValueOption>();
        for (var item : sorted(snapshot.path("dictionaryItems"))) {
            if (dictionaryId.equals(item.path("dictionary_id").asText()) && enabled(item)) {
                result.add(new CanonicalFieldValueCodec.ValueOption(
                        item.path("id").asText(), item.path("item_label").asText(),
                        item.path("parent_id").isMissingNode() || item.path("parent_id").isNull()
                                ? null : item.path("parent_id").asText()));
            }
        }
        return List.copyOf(result);
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published snapshot is invalid", exception);
        }
    }

    private static List<JsonNode> sorted(JsonNode array) {
        var result = new ArrayList<JsonNode>();
        array.forEach(result::add);
        result.sort(Comparator.comparingInt((JsonNode node) -> node.path("sort_order").asInt())
                .thenComparing(node -> node.path("id").asText()));
        return result;
    }

    private static List<String> operators(String type) {
        return switch (type) {
            case "TEXT" -> List.of("EQ", "CONTAINS", "PREFIX", "EMPTY");
            case "TEXTAREA" -> List.of("CONTAINS", "EMPTY");
            case "NUMBER" -> List.of("EQ", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY");
            case "DATE", "DATETIME" -> List.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY");
            case "RADIO" -> List.of("EQ", "IN", "EMPTY");
            case "MEMBER", "DEPARTMENT" -> List.of("HAS_ANY", "EMPTY");
            case "PERCENT", "MONEY", "RATING", "PROGRESS" ->
                    List.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "EMPTY");
            case "DATE_RANGE" -> List.of("OVERLAPS", "CONTAINS", "BEFORE", "AFTER", "EMPTY");
            case "TIME" -> List.of("EQ", "BEFORE", "AFTER", "BETWEEN", "EMPTY");
            case "TIME_RANGE" -> List.of("OVERLAPS", "CONTAINS", "EMPTY");
            case "MULTI_SELECT" -> List.of("HAS_ANY", "HAS_ALL", "NOT_ANY", "EMPTY");
            case "CASCADE" -> List.of("CONTAINS_NODE", "LEAF_EQ", "EMPTY");
            case "SWITCH" -> List.of("EQ", "EMPTY");
            case "TAG" -> List.of("HAS_ANY", "HAS_ALL", "EMPTY");
            case "PHONE" -> List.of("EQ", "EMPTY");
            case "EMAIL", "URL", "BARCODE" -> List.of("EQ", "PREFIX", "EMPTY");
            case "IDENTITY", "SECRET" -> List.of("EQ", "EMPTY");
            case "ADDRESS" -> List.of("EQ_REGION", "PREFIX", "EMPTY");
            case "GEO" -> List.of("WITHIN_BOX", "NEAR", "EMPTY");
            case "RICH_TEXT" -> List.of("CONTAINS", "EMPTY");
            case "JSON" -> List.of("DECLARED_PATH_EQ", "DECLARED_PATH_EXISTS");
            case "STATUS" -> List.of("EQ", "IN", "EMPTY");
            case "RELATION" -> List.of("HAS_ANY", "EMPTY");
            case "SUBTABLE" -> List.of("DECLARED_AGGREGATE");
            default -> List.of();
        };
    }

    private static boolean enabled(JsonNode node) {
        return "ENABLED".equals(node.path("desired_status").asText());
    }

    private static boolean flag(JsonNode node, String key) {
        var value = node.path(key);
        return value.isBoolean() ? value.asBoolean() : value.asInt() != 0;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static RecordRow recordRow(ResultSet result) throws SQLException {
        return new RecordRow(result.getLong("record_id"), result.getString("record_no"),
                result.getLong("version"), result.getString("status"), result.getString("title"),
                result.getLong("schema_version_id"));
    }

    private QueryRecord queryRecordRow(
            ResultSet result,
            List<RecordQueryModels.EffectiveSort> sorts
    ) throws SQLException {
        var anchors = new ArrayList<JsonNode>();
        for (var index = 0; index < sorts.size(); index++) {
            anchors.add(anchorValue(
                    result,
                    RecordNeighborQueryBuilder.anchorAlias(index),
                    sorts.get(index).valueType()));
        }
        return new QueryRecord(recordRow(result), anchors);
    }

    private JsonNode anchorValue(ResultSet result, String alias, String valueType) throws SQLException {
        if (result.getObject(alias) == null) {
            return objectMapper.getNodeFactory().nullNode();
        }
        Object value = switch (valueType) {
            case "STRING" -> result.getString(alias);
            case "DECIMAL" -> result.getBigDecimal(alias);
            case "DATE" -> result.getDate(alias).toLocalDate().toString();
            case "DATETIME" -> result.getTimestamp(alias).toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "TIME" -> result.getTime(alias).toLocalTime()
                    .format(DateTimeFormatter.ISO_LOCAL_TIME);
            case "BOOLEAN" -> result.getBoolean(alias);
            case "LONG" -> result.getLong(alias);
            default -> throw new IllegalStateException("Unsupported query sort anchor type: " + valueType);
        };
        return objectMapper.valueToTree(value);
    }

    private static RecordRuntimeViews.RecordSummary summary(
            QueryRecord queryRecord,
            List<RecordRuntimeViews.FieldValue> values
    ) {
        var record = queryRecord.record();
        return new RecordRuntimeViews.RecordSummary(
                Long.toString(record.recordId()),
                record.recordNo(),
                record.version(),
                record.status(),
                record.title(),
                values,
                new RecordRuntimeViews.SortAnchor(
                        queryRecord.anchorValues(),
                        Long.toString(record.recordId())));
    }

    private static CommandRecord commandRecord(ResultSet result) throws SQLException {
        return new CommandRecord(result.getLong("record_id"), result.getString("record_no"),
                result.getLong("version"), result.getString("status"), result.getString("title"),
                result.getLong("schema_version_id"), result.getLong("module_snapshot_id"),
                result.getTimestamp("draft_expires_at") == null ? null
                        : result.getTimestamp("draft_expires_at").toLocalDateTime(),
                result.getString("prior_status"));
    }

    private record Active(long versionId, String checksum, JsonNode snapshot) { }

    private record ModuleIdentity(long moduleSnapshotId, long logicalModuleId) { }

    private record HistoricalCatalog(
            Catalog catalog,
            ModuleIdentity identity,
            Active version
    ) { }

    private record ProjectedField(long logicalFieldId, String code, String type) { }

    private record Catalog(
            String versionId,
            String checksum,
            String moduleId,
            String moduleSnapshotId,
            List<FieldDescriptor> fields,
            String unavailableReason,
            RuntimeAuthorizationFacade.RuntimeGrant grant,
            RecordRuntimeViews.PublishedRuntimeContract publishedRuntime
    ) {
        private List<FieldDescriptor> listFields() {
            return pageFields("LIST", FieldDescriptor::showInList);
        }

        private List<FieldDescriptor> detailFields() {
            return pageFields("DETAIL", FieldDescriptor::showInDetail);
        }

        private List<FieldDescriptor> pageFields(
                String pageType,
                java.util.function.Predicate<FieldDescriptor> fallback
        ) {
            var page = publishedRuntime.pages().stream()
                    .filter(candidate -> pageType.equals(candidate.type()))
                    .findFirst();
            if (page.isEmpty() || page.get().fields().isEmpty()) {
                return fields.stream().filter(fallback).toList();
            }
            var byCode = new LinkedHashMap<String, FieldDescriptor>();
            fields.forEach(field -> byCode.put(field.code(), field));
            return page.get().fields().stream()
                    .map(RecordRuntimeViews.PageFieldPlacement::fieldCode)
                    .map(byCode::get)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private record FieldDescriptor(
            long id,
            String code,
            String name,
            String type,
            boolean showInList,
            boolean showInDetail,
            boolean required,
            boolean writable,
            boolean sensitiveReadable,
            boolean sensitiveQueryable,
            boolean searchable,
            boolean filterable,
            boolean sortable,
            String indexMode,
            List<String> operators,
            List<CanonicalFieldValueCodec.ValueOption> options,
            ObjectNode schema
    ) {
        private List<CanonicalFieldValueCodec.ValueOption> valueOptions(
                RuntimeReferenceFacade.ReferenceCatalog references
        ) {
            return switch (type) {
                case "MEMBER" -> references.members().stream()
                        .map(option -> new CanonicalFieldValueCodec.ValueOption(
                                option.value(), option.label(), null)).toList();
                case "DEPARTMENT" -> references.departments().stream()
                        .map(option -> new CanonicalFieldValueCodec.ValueOption(
                                option.value(), option.label(), null)).toList();
                default -> options;
            };
        }

        private List<RecordRuntimeViews.ReferenceOption> referenceOptions(
                RuntimeReferenceFacade.ReferenceCatalog references
        ) {
            return valueOptions(references).stream()
                    .map(option -> new RecordRuntimeViews.ReferenceOption(
                            option.value(), option.label(), option.parentValue())).toList();
        }

        private RecordRuntimeViews.FieldCapability capability(RuntimeReferenceFacade.ReferenceCatalog references) {
            var mode = switch (type) {
                case "IDENTITY", "SECRET" -> "SENSITIVE_WRITABLE";
                case "STATUS" -> "WRITABLE_TRANSITION";
                case "FORMULA", "SUMMARY", "CALCULATED", "LOOKUP", "AGGREGATE", "AI_FILL" ->
                        "DERIVED_READONLY";
                case "TENANT", "AUTO_NUMBER", "CREATED_BY", "CREATED_AT", "UPDATED_BY", "UPDATED_AT" ->
                        "SYSTEM_READONLY";
                default -> writable ? "WRITABLE" : "READONLY";
            };
            var masked = "SECRET".equals(type) || "IDENTITY".equals(type) && !sensitiveReadable;
            return new RecordRuntimeViews.FieldCapability(code, name, Long.toString(id), type,
                    mode, true, writable, sensitiveReadable, sensitiveQueryable, masked, operators, sortable,
                    showInList, showInDetail, referenceOptions(references), schema);
        }
    }

    private record RecordRow(long recordId, String recordNo, long version, String status, String title,
                              long schemaVersionId) { }

    private record QueryRecord(RecordRow record, List<JsonNode> anchorValues) {
        private QueryRecord {
            anchorValues = List.copyOf(anchorValues);
        }
    }

    private record QueryExecution(
            RecordQueryModels.CompiledQuery compiled,
            List<FieldDescriptor> selectedFields,
            String whereSql,
            List<Object> whereArguments,
            boolean materializeSinglePredicate
    ) {
        private QueryExecution {
            selectedFields = List.copyOf(selectedFields);
            whereArguments = List.copyOf(whereArguments);
        }
    }

    private record IndexedSortRoute(String indexName, String columnName) { }

    private record DirectIndexedSortPlan(
            String fromSql,
            String whereSql,
            String anchorSql,
            String orderSql,
            String countFromSql,
            String countWhereSql
    ) { }

    private record CommandRecord(long recordId, String recordNo, long version, String status, String title,
                                  long schemaVersionId, long moduleSnapshotId, LocalDateTime draftExpiresAt,
                                  String priorStatus) { }

    private record PersistedCell(
            String type,
            int ordinal,
            Object value,
            String display,
            String currencyCode,
            String dependencyVersionJson,
            Integer evaluatorVersion,
            String recalculationState,
            String failureCorrelationId
    ) { }

    private record RelationProjection(
            long sourceRecordId,
            long targetRecordId,
            long targetVersion,
            int ordinal,
            String title
    ) { }

    private record UniqueValue(String currency, String hash, String hashKeyVersion) { }

    private record ReferenceTarget(String moduleId, String moduleCode) { }

    private record DerivedTarget(String moduleId, String moduleCode, String fieldId, String fieldCode) { }

    private record IndexProjection(
            int ordinal,
            long pathSnapshotId,
            String kind,
            String stringValue,
            BigDecimal decimalValue,
            LocalDate dateValue,
            LocalDateTime dateTimeValue,
            Boolean booleanValue,
            String hashValue,
            String hashKeyVersion,
            String geohash,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        private static IndexProjection string(int ordinal, long pathSnapshotId, String value) {
            return new IndexProjection(ordinal, pathSnapshotId, "STRING", value, null, null, null, null,
                    null, null, null, null, null);
        }

        private static IndexProjection decimal(int ordinal, long pathSnapshotId, BigDecimal value) {
            return new IndexProjection(ordinal, pathSnapshotId, "DECIMAL", null, value, null, null, null,
                    null, null, null, null, null);
        }

        private static IndexProjection date(int ordinal, long pathSnapshotId, LocalDate value) {
            return new IndexProjection(ordinal, pathSnapshotId, "DATE", null, null, value, null, null,
                    null, null, null, null, null);
        }

        private static IndexProjection dateTime(int ordinal, long pathSnapshotId, LocalDateTime value) {
            return new IndexProjection(ordinal, pathSnapshotId, "DATETIME", null, null, null, value, null,
                    null, null, null, null, null);
        }

        private static IndexProjection bool(int ordinal, long pathSnapshotId, boolean value) {
            return new IndexProjection(ordinal, pathSnapshotId, "BOOLEAN", null, null, null, null, value,
                    null, null, null, null, null);
        }

        private static IndexProjection hash(int ordinal, String version, String value) {
            return new IndexProjection(ordinal, 0, "HASH", null, null, null, null, null,
                    value, version, null, null, null);
        }

        private static IndexProjection geo(int ordinal, String geohash, BigDecimal lat, BigDecimal lng) {
            return new IndexProjection(ordinal, 0, "GEO", null, null, null, null, null,
                    null, null, geohash, lat, lng);
        }

        private static IndexProjection nullValue(int ordinal, long pathSnapshotId) {
            return new IndexProjection(ordinal, pathSnapshotId, "NULL", null, null, null, null, null,
                    null, null, null, null, null);
        }
    }

    private enum LifecycleCommand {
        ARCHIVE("archive", "action.archive", Set.of("ACTIVE"), "RECORD_ARCHIVED"),
        UNARCHIVE("unarchive", "action.unarchive", Set.of("ARCHIVED"), "RECORD_UNARCHIVED"),
        TRASH("trash", "delete", Set.of("DRAFT", "ACTIVE", "ARCHIVED", "EXPIRED"), "RECORD_TRASHED"),
        RESTORE_FROM_TRASH("restore-from-trash", "action.restore_trash", Set.of("TRASHED"),
                "RECORD_RESTORED_FROM_TRASH"),
        DISCARD("discard", "delete", Set.of("DRAFT"), "RECORD_DRAFT_DISCARDED"),
        RECOVER("recover", "action.recover_draft", Set.of("EXPIRED"), "RECORD_DRAFT_RECOVERED");

        private final String key;
        private final String permissionVerb;
        private final Set<String> sources;
        private final String eventType;

        LifecycleCommand(String key, String permissionVerb, Set<String> sources, String eventType) {
            this.key = key;
            this.permissionVerb = permissionVerb;
            this.sources = sources;
            this.eventType = eventType;
        }

        private String targetStatus(CommandRecord record) {
            return switch (this) {
                case ARCHIVE -> "ARCHIVED";
                case UNARCHIVE -> "ACTIVE";
                case TRASH, DISCARD -> "TRASHED";
                case RESTORE_FROM_TRASH -> record.priorStatus();
                case RECOVER -> "DRAFT";
            };
        }

        private String key() { return key; }
        private String permissionVerb() { return permissionVerb; }
        private Set<String> sources() { return sources; }
        private String eventType() { return eventType; }
        private RuntimeRecordFlowTriggerFacade.TriggerEvent triggerEvent() {
            return switch (this) {
                case TRASH, DISCARD -> RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_DELETED;
                case ARCHIVE, UNARCHIVE, RESTORE_FROM_TRASH, RECOVER ->
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_STATUS_CHANGED;
            };
        }
    }

    private record NormalizedValue(
            FieldDescriptor field,
            Object value,
            String display,
            List<CanonicalFieldValueCodec.ValueRow> rows
    ) {
        private NormalizedValue(FieldDescriptor field, Object value, String display) {
            this(field, value, display, List.of());
        }
    }

    private record BatchEditField(
            FieldDescriptor field,
            RecordBatchEditPlan.Operation operation,
            NormalizedValue value
    ) { }

    private record WriteInput(
            String schemaVersionId,
            String title,
            long expectedVersion,
            Map<String, JsonNode> values,
            List<RecordRuntimeViews.RelationInput> relations,
            List<RecordRuntimeViews.SubtableInput> subtables
    ) { }

    private record OpenApiCreateIdentity(
            String lifecycleState,
            Map<String, JsonNode> values
    ) { }

    private record OpenApiMutationIdentity(
            String action,
            long expectedVersion,
            Map<String, JsonNode> values
    ) { }

    private record WriteResult(
            RecordRuntimeViews.RecordDetail record,
            LocalDateTime savedAt,
            LocalDateTime expiresAt
    ) { }

    private record ScopeSql(String sql, List<Object> arguments) { }
}
