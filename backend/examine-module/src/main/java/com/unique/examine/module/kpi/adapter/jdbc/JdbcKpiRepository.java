package com.unique.examine.module.kpi.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.kpi.domain.KpiCalculation;
import com.unique.examine.module.kpi.domain.KpiDecimal;
import com.unique.examine.module.kpi.domain.KpiDefinition;
import com.unique.examine.module.kpi.domain.KpiDraft;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.domain.KpiPeriod;
import com.unique.examine.module.kpi.domain.KpiSubjectType;
import com.unique.examine.module.kpi.domain.KpiTarget;
import com.unique.examine.module.kpi.domain.KpiVersion;
import com.unique.examine.module.kpi.domain.KpiWarningStatus;
import com.unique.examine.module.kpi.port.KpiRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository("jdbcKpiRepository")
public class JdbcKpiRepository implements KpiRepository {
    static final String CALCULATOR_VERSION = "kpi-v1";
    static final String ROOT_COLUMNS = """
            id,system_id,tenant_id,kpi_code,kpi_name,description,draft_json,
            draft_version,active_version_id,active_version_no,created_at,
            updated_at,version
            """;
    static final String TARGET_COLUMNS = """
            id,system_id,tenant_id,kpi_id,kpi_version_id,kpi_version_no,
            subject_type,subject_id,subject_name,period_type,period_start,
            period_end,target_value,created_by_member_id,updated_by_member_id,
            created_at,updated_at,version
            """;
    static final String INSERT_ROOT = """
            INSERT INTO un_module_kpi (
                id,system_id,tenant_id,kpi_code,kpi_name,description,draft_json,
                draft_version,active_version_id,active_version_no,created_at,
                updated_at,version
            ) VALUES (?,?,?,?,?,?,?,?,NULL,NULL,?,?,?)
            """;
    static final String SAVE_DRAFT_CAS = """
            UPDATE un_module_kpi
               SET kpi_name=?,description=?,draft_json=?,draft_version=?,
                   updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=? AND kpi_code=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;
    static final String INSERT_VERSION = """
            INSERT INTO un_module_kpi_version (
                id,system_id,tenant_id,kpi_id,version_no,source_draft_version,
                kpi_code,kpi_name,description,subject_type,period_type,
                attainment_direction,warning_threshold,data_source_id,
                data_source_version_id,data_source_version_no,data_source_code,
                module_id,module_code,schema_version_id,aggregation,
                measure_field_id,measure_field_code,measure_field_name,
                measure_field_type,measure_query_type,time_field_id,
                time_field_code,time_field_name,time_field_type,time_query_type,
                calculator_version,snapshot_json,snapshot_fingerprint,
                published_by_member_id,published_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,(
                SELECT source.module_id
                  FROM un_module_data_source_version source
                 WHERE source.system_id=? AND source.tenant_id=?
                   AND source.data_source_id=? AND source.id=?
                   AND source.version_no=?
            ),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String ACTIVATE_CAS = """
            UPDATE un_module_kpi
               SET active_version_id=?,active_version_no=?,updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=? AND kpi_code=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;
    static final String INSERT_TARGET = """
            INSERT INTO un_module_kpi_target (
                id,system_id,tenant_id,kpi_id,kpi_version_id,kpi_version_no,
                subject_type,subject_id,subject_name,period_type,period_start,
                period_end,target_value,request_key_hash,request_fingerprint,
                status,latest_calculation_id,latest_calculation_no,
                created_by_member_id,created_at,updated_by_member_id,updated_at,
                version
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',NULL,NULL,?,?,?,?,?)
            """;
    static final String SAVE_TARGET_CAS = """
            UPDATE un_module_kpi_target
               SET target_value=?,updated_by_member_id=?,updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=? AND kpi_id=?
               AND kpi_version_id=? AND kpi_version_no=?
               AND subject_type=? AND subject_id=? AND period_type=?
               AND period_start=? AND period_end=? AND version=?
               AND status='ACTIVE'
            """;
    static final String INSERT_CALCULATION = """
            INSERT INTO un_module_kpi_calculation (
                id,system_id,tenant_id,kpi_id,kpi_version_id,kpi_version_no,
                target_id,calculation_no,request_key_hash,request_fingerprint,
                status,calculator_version,authz_epoch,subject_type,subject_id,
                subject_name,subject_member_count,subject_members_fingerprint,
                subject_snapshot_json,period_type,period_start,period_end,
                aggregation,target_value_snapshot,actual_value,attainment_rate,
                warning_status,statistics_query_id,matched_record_count,
                trend_json,explanation_json,error_code,error_message,
                requested_by_member_id,started_at,completed_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,
                      ?,?,?,?,?,?,?,?)
            """;
    static final String INSERT_CALCULATION_MEMBER = """
            INSERT INTO un_module_kpi_calculation_member (
                system_id,tenant_id,kpi_id,target_id,calculation_id,
                member_ordinal,member_id
            ) VALUES (?,?,?,?,?,?,?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdService ids;
    private final TransactionTemplate transactions;

    public JdbcKpiRepository(
            JdbcTemplate jdbc,
            ObjectMapper json,
            IdService ids,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager"));
    }

    @Override
    public long nextKpiId() {
        return ids.nextId();
    }

    @Override
    public long nextVersionId() {
        return ids.nextId();
    }

    @Override
    public long nextTargetId() {
        return ids.nextId();
    }

    @Override
    public long nextCalculationId() {
        return ids.nextId();
    }

    @Override
    public Optional<KpiDefinition> findById(
            long systemId,
            long tenantId,
            long kpiId
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_kpi "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                this::root, systemId, tenantId, kpiId));
    }

    @Override
    public Optional<KpiDefinition> findByCode(
            long systemId,
            long tenantId,
            String code
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_kpi "
                        + "WHERE system_id=? AND tenant_id=? AND kpi_code=?",
                this::root, systemId, tenantId, code));
    }

    @Override
    public List<KpiDefinition> findAll(long systemId, long tenantId) {
        return jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_kpi "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "ORDER BY updated_at DESC,id DESC",
                this::root, systemId, tenantId);
    }

    @Override
    public KpiDefinition insert(KpiDefinition root) {
        Objects.requireNonNull(root, "root");
        if (root.activeVersionId() != null
                || root.activeVersionNumber() != null) {
            throw invalid("A new KPI cannot already be published");
        }
        try {
            requireOne(jdbc.update(INSERT_ROOT,
                            root.id(), root.systemId(), root.tenantId(),
                            root.code(), root.name(), root.description(),
                            write(root.draft()), root.draftVersion(),
                            Timestamp.from(root.createdAt()),
                            Timestamp.from(root.updatedAt()), root.version()),
                    "KPI root insert did not affect one row");
            return root;
        } catch (DuplicateKeyException exception) {
            throw new KpiException("KPI_CODE_CONFLICT",
                    "KPI code already exists in this tenant");
        }
    }

    @Override
    public KpiDefinition saveDraft(
            KpiDefinition expected,
            KpiDefinition revised
    ) {
        requireDraftRevision(expected, revised);
        var affected = jdbc.update(SAVE_DRAFT_CAS,
                revised.name(), revised.description(), write(revised.draft()),
                revised.draftVersion(), Timestamp.from(revised.updatedAt()),
                revised.version(), expected.systemId(), expected.tenantId(),
                expected.id(), expected.code(), expected.draftVersion(),
                expected.version(), expected.activeVersionId(),
                expected.activeVersionNumber());
        if (affected != 1) {
            throw conflict("KPI draft changed before this save");
        }
        return revised;
    }

    @Override
    public KpiVersion publish(
            KpiDefinition expected,
            KpiDefinition activated,
            KpiVersion version
    ) {
        requirePublication(expected, activated, version);
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                var source = version.source();
                var measure = source.measureField();
                var time = source.timeField();
                requireOne(jdbc.update(INSERT_VERSION,
                                version.id(), version.systemId(),
                                version.tenantId(), version.kpiId(),
                                version.versionNumber(),
                                version.sourceDraftVersion(), version.code(),
                                version.name(), version.description(),
                                version.subjectType().name(),
                                version.periodType().name(),
                                version.direction().name(),
                                version.warningThreshold(),
                                source.dataSourceId(),
                                source.dataSourceVersionId(),
                                source.dataSourceVersionNumber(),
                                source.dataSourceCode(), version.systemId(),
                                version.tenantId(), source.dataSourceId(),
                                source.dataSourceVersionId(),
                                source.dataSourceVersionNumber(),
                                source.moduleCode(), source.schemaVersionId(),
                                version.aggregation().name(),
                                measure == null ? null
                                        : measure.logicalFieldId(),
                                measure == null ? null : measure.code(),
                                measure == null ? null : measure.name(),
                                measure == null ? null : measure.type(),
                                measure == null ? null : measure.queryType(),
                                time.logicalFieldId(), time.code(), time.name(),
                                time.type(), time.queryType(), CALCULATOR_VERSION,
                                write(version), version.fingerprint(),
                                version.publishedByMemberId(),
                                Timestamp.from(version.publishedAt())),
                        "KPI version insert did not affect one row");
                var affected = jdbc.update(ACTIVATE_CAS,
                        activated.activeVersionId(),
                        activated.activeVersionNumber(),
                        Timestamp.from(activated.updatedAt()),
                        activated.version(), expected.systemId(),
                        expected.tenantId(), expected.id(), expected.code(),
                        expected.draftVersion(), expected.version(),
                        expected.activeVersionId(),
                        expected.activeVersionNumber());
                if (affected != 1) {
                    throw conflict("KPI changed before publication completed");
                }
                return version;
            }));
        } catch (DuplicateKeyException exception) {
            throw conflict("KPI draft or version was already published");
        } catch (DataIntegrityViolationException exception) {
            throw invalid(
                    "KPI publication violated an exact immutable reference");
        }
    }

    @Override
    public Optional<KpiVersion> findActiveVersion(
            long systemId,
            long tenantId,
            long kpiId
    ) {
        return one(jdbc.query("""
                        SELECT version_row.snapshot_json
                          FROM un_module_kpi root
                          JOIN un_module_kpi_version version_row
                            ON version_row.system_id=root.system_id
                           AND version_row.tenant_id=root.tenant_id
                           AND version_row.kpi_id=root.id
                           AND version_row.id=root.active_version_id
                           AND version_row.version_no=root.active_version_no
                         WHERE root.system_id=? AND root.tenant_id=?
                           AND root.id=?
                        """, this::version,
                systemId, tenantId, kpiId));
    }

    @Override
    public Optional<KpiVersion> findVersionById(
            long systemId,
            long tenantId,
            long kpiId,
            long versionId
    ) {
        return one(jdbc.query("""
                        SELECT snapshot_json
                          FROM un_module_kpi_version
                         WHERE system_id=? AND tenant_id=? AND kpi_id=? AND id=?
                        """, this::version,
                systemId, tenantId, kpiId, versionId));
    }

    @Override
    public Optional<KpiVersion> findVersion(
            long systemId,
            long tenantId,
            long kpiId,
            int versionNumber
    ) {
        return one(jdbc.query("""
                        SELECT snapshot_json
                          FROM un_module_kpi_version
                         WHERE system_id=? AND tenant_id=? AND kpi_id=?
                           AND version_no=?
                        """, this::version,
                systemId, tenantId, kpiId, versionNumber));
    }

    @Override
    public List<KpiVersion> findVersions(
            long systemId,
            long tenantId,
            long kpiId
    ) {
        return jdbc.query("""
                        SELECT snapshot_json
                          FROM un_module_kpi_version
                         WHERE system_id=? AND tenant_id=? AND kpi_id=?
                         ORDER BY version_no DESC,id DESC
                        """, this::version,
                systemId, tenantId, kpiId);
    }

    @Override
    public Optional<KpiTarget> findTargetById(
            long systemId,
            long tenantId,
            long targetId
    ) {
        return one(jdbc.query(
                "SELECT " + TARGET_COLUMNS + " FROM un_module_kpi_target "
                        + "WHERE system_id=? AND tenant_id=? AND id=? "
                        + "AND status='ACTIVE'",
                JdbcKpiRepository::target, systemId, tenantId, targetId));
    }

    @Override
    public Optional<KpiTarget> findTargetByBusinessKey(
            long systemId,
            long tenantId,
            long kpiVersionId,
            KpiSubjectType subjectType,
            long subjectId,
            KpiPeriod period
    ) {
        return one(jdbc.query(
                "SELECT " + TARGET_COLUMNS + " FROM un_module_kpi_target "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND kpi_version_id=? AND subject_type=? "
                        + "AND subject_id=? AND period_type=? "
                        + "AND period_start=? AND period_end=? "
                        + "AND status='ACTIVE'",
                JdbcKpiRepository::target, systemId, tenantId, kpiVersionId,
                subjectType.name(), subjectId, period.type().name(),
                period.startInclusive(), period.endExclusive()));
    }

    @Override
    public List<KpiTarget> findTargets(long systemId, long tenantId) {
        return jdbc.query(
                "SELECT " + TARGET_COLUMNS + " FROM un_module_kpi_target "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND status='ACTIVE' "
                        + "ORDER BY period_start DESC,id DESC",
                JdbcKpiRepository::target, systemId, tenantId);
    }

    @Override
    public KpiTarget insertTarget(KpiTarget target) {
        Objects.requireNonNull(target, "target");
        var requestKey = targetBusinessKey(target);
        var fingerprint = hash(requestKey + "|" + target.targetValue());
        try {
            requireOne(jdbc.update(INSERT_TARGET,
                            target.id(), target.systemId(), target.tenantId(),
                            target.kpiId(), target.kpiVersionId(),
                            target.kpiVersionNumber(),
                            target.subjectType().name(), target.subjectId(),
                            target.subjectDisplayName(),
                            target.period().type().name(),
                            target.period().startInclusive(),
                            target.period().endExclusive(),
                            target.targetValue(), hash(requestKey),
                            fingerprint, target.createdByMemberId(),
                            Timestamp.from(target.createdAt()),
                            target.updatedByMemberId(),
                            Timestamp.from(target.updatedAt()), target.version()),
                    "KPI target insert did not affect one row");
            return target;
        } catch (DuplicateKeyException exception) {
            var existing = findTargetByBusinessKey(
                    target.systemId(), target.tenantId(),
                    target.kpiVersionId(), target.subjectType(),
                    target.subjectId(), target.period());
            if (existing.isPresent()
                    && existing.orElseThrow().targetValue()
                    .equals(target.targetValue())) {
                return existing.orElseThrow();
            }
            throw conflict(
                    "A different target already uses this KPI, subject and period");
        }
    }

    @Override
    public KpiTarget saveTarget(KpiTarget expected, KpiTarget revised) {
        requireTargetRevision(expected, revised);
        var affected = jdbc.update(SAVE_TARGET_CAS,
                revised.targetValue(), revised.updatedByMemberId(),
                Timestamp.from(revised.updatedAt()), revised.version(),
                expected.systemId(), expected.tenantId(), expected.id(),
                expected.kpiId(), expected.kpiVersionId(),
                expected.kpiVersionNumber(), expected.subjectType().name(),
                expected.subjectId(), expected.period().type().name(),
                expected.period().startInclusive(),
                expected.period().endExclusive(), expected.version());
        if (affected != 1) {
            throw conflict("KPI target changed before this save");
        }
        return revised;
    }

    @Override
    public Optional<KpiCalculation> findCalculationByCommandKey(
            long systemId,
            long tenantId,
            String commandKey
    ) {
        return one(jdbc.query("""
                        SELECT explanation_json
                          FROM un_module_kpi_calculation
                         WHERE system_id=? AND tenant_id=? AND request_key_hash=?
                        """, this::calculation,
                systemId, tenantId, hash(commandKey)));
    }

    @Override
    public Optional<KpiCalculation> findLatestCalculation(
            long systemId,
            long tenantId,
            long targetId
    ) {
        return one(jdbc.query("""
                        SELECT explanation_json
                          FROM un_module_kpi_calculation
                         WHERE system_id=? AND tenant_id=? AND target_id=?
                         ORDER BY calculation_no DESC,id DESC
                         LIMIT 1
                        """, this::calculation,
                systemId, tenantId, targetId));
    }

    @Override
    public List<KpiCalculation> findCalculations(
            long systemId,
            long tenantId,
            long targetId
    ) {
        return jdbc.query("""
                        SELECT explanation_json
                          FROM un_module_kpi_calculation
                         WHERE system_id=? AND tenant_id=? AND target_id=?
                         ORDER BY calculation_no DESC,id DESC
                        """, this::calculation,
                systemId, tenantId, targetId);
    }

    @Override
    public KpiCalculation insertCalculation(KpiCalculation calculation) {
        Objects.requireNonNull(calculation, "calculation");
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                var targetId = calculation.target().targetId();
                lockTarget(calculation.systemId(), calculation.tenantId(),
                        targetId);
                var replay = findCalculationByCommandKey(
                        calculation.systemId(), calculation.tenantId(),
                        calculation.commandKey());
                if (replay.isPresent()) {
                    if (replay.orElseThrow().target().targetId() != targetId) {
                        throw conflict(
                                "Calculation command key belongs to another target");
                    }
                    return replay.orElseThrow();
                }
                var calculationNumber = nextCalculationNumber(
                        calculation.systemId(), calculation.tenantId(), targetId);
                insertCalculationRow(calculation, calculationNumber);
                insertCalculationMembers(calculation);
                requireOne(jdbc.update("""
                                UPDATE un_module_kpi_target
                                   SET latest_calculation_id=?,
                                       latest_calculation_no=?
                                 WHERE system_id=? AND tenant_id=? AND id=?
                                   AND status='ACTIVE'
                                """,
                                calculation.id(), calculationNumber,
                                calculation.systemId(), calculation.tenantId(),
                                targetId),
                        "KPI latest calculation pointer update failed");
                return calculation;
            }));
        } catch (DuplicateKeyException exception) {
            var replay = findCalculationByCommandKey(
                    calculation.systemId(), calculation.tenantId(),
                    calculation.commandKey());
            if (replay.isPresent()
                    && replay.orElseThrow().target().targetId()
                    == calculation.target().targetId()) {
                return replay.orElseThrow();
            }
            throw conflict("KPI calculation command or sequence conflicted");
        } catch (DataIntegrityViolationException exception) {
            throw invalid(
                    "KPI calculation violated an immutable target reference");
        }
    }

    private void insertCalculationRow(
            KpiCalculation calculation,
            int calculationNumber
    ) {
        var failed = calculation.status()
                == KpiWarningStatus.CALCULATION_FAILED;
        var definition = calculation.definition();
        var target = calculation.target();
        var subject = calculation.subject();
        var commandHash = hash(calculation.commandKey());
        requireOne(jdbc.update(INSERT_CALCULATION,
                        calculation.id(), calculation.systemId(),
                        calculation.tenantId(), definition.kpiId(),
                        definition.kpiVersionId(),
                        definition.kpiVersionNumber(), target.targetId(),
                        calculationNumber, commandHash,
                        hash(target.targetId() + "|" + commandHash),
                        failed ? "FAILED" : "SUCCEEDED", CALCULATOR_VERSION,
                        calculation.authorizationEpoch(), subject.type().name(),
                        subject.id(), subject.displayName(),
                        subjectMemberCount(subject.type(),
                                subject.roleMemberIds().size()),
                        subject.type() == KpiSubjectType.ROLE
                                ? memberFingerprint(subject.roleMemberIds())
                                : null,
                        write(subject), target.period().type().name(),
                        target.period().startInclusive(),
                        target.period().endExclusive(),
                        definition.aggregation().name(),
                        target.targetValue(),
                        failed ? null : calculation.actualValue(),
                        failed ? null : calculation.attainment(),
                        calculation.status().name(),
                        calculation.statisticsQueryId(),
                        failed ? null : calculation.matchedRecordCount(),
                        failed ? null : write(calculation.trend()),
                        write(calculation), calculation.errorCode(),
                        failed ? "KPI calculation failed: "
                                + calculation.errorCode() : null,
                        calculation.actorMemberId(),
                        Timestamp.from(calculation.startedAt()),
                        Timestamp.from(calculation.completedAt())),
                "KPI calculation insert did not affect one row");
    }

    private void insertCalculationMembers(KpiCalculation calculation) {
        if (calculation.subject().type() != KpiSubjectType.ROLE
                || calculation.subject().roleMemberIds().isEmpty()) {
            return;
        }
        var arguments = new java.util.ArrayList<Object[]>();
        for (var index = 0;
             index < calculation.subject().roleMemberIds().size(); index++) {
            arguments.add(new Object[]{
                    calculation.systemId(), calculation.tenantId(),
                    calculation.definition().kpiId(),
                    calculation.target().targetId(), calculation.id(), index,
                    calculation.subject().roleMemberIds().get(index)});
        }
        var affected = jdbc.batchUpdate(INSERT_CALCULATION_MEMBER, arguments);
        if (affected.length != arguments.size()) {
            throw new IllegalStateException(
                    "KPI calculation member snapshot insert is incomplete");
        }
        for (var count : affected) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException(
                        "KPI calculation member insert did not affect one row");
            }
        }
    }

    private void lockTarget(long systemId, long tenantId, long targetId) {
        var locked = jdbc.queryForList("""
                SELECT id
                  FROM un_module_kpi_target
                 WHERE system_id=? AND tenant_id=? AND id=? AND status='ACTIVE'
                 FOR UPDATE
                """, Long.class, systemId, tenantId, targetId);
        if (locked.size() != 1) {
            throw new KpiException("KPI_TARGET_NOT_FOUND",
                    "KPI target does not exist");
        }
    }

    private int nextCalculationNumber(
            long systemId,
            long tenantId,
            long targetId
    ) {
        var value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(calculation_no),0) + 1
                  FROM un_module_kpi_calculation
                 WHERE system_id=? AND tenant_id=? AND target_id=?
                """, Integer.class, systemId, tenantId, targetId);
        if (value == null || value <= 0) {
            throw new IllegalStateException(
                    "KPI calculation sequence could not be allocated");
        }
        return value;
    }

    private KpiDefinition root(ResultSet result, int rowNumber)
            throws SQLException {
        return new KpiDefinition(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getString("kpi_code"),
                result.getString("kpi_name"),
                result.getString("description"),
                read(result.getString("draft_json"), KpiDraft.class,
                        "KPI draft"),
                result.getLong("draft_version"),
                result.getObject("active_version_id", Long.class),
                result.getObject("active_version_no", Integer.class),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version"));
    }

    private KpiVersion version(ResultSet result, int rowNumber)
            throws SQLException {
        return read(result.getString("snapshot_json"), KpiVersion.class,
                "KPI version snapshot");
    }

    private KpiCalculation calculation(ResultSet result, int rowNumber)
            throws SQLException {
        return read(result.getString("explanation_json"),
                KpiCalculation.class, "KPI calculation explanation");
    }

    private static KpiTarget target(ResultSet result, int rowNumber)
            throws SQLException {
        return new KpiTarget(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("kpi_id"),
                result.getLong("kpi_version_id"),
                result.getInt("kpi_version_no"),
                KpiSubjectType.valueOf(result.getString("subject_type")),
                result.getLong("subject_id"),
                result.getString("subject_name"),
                new KpiPeriod(
                        com.unique.examine.module.kpi.domain.KpiPeriodType
                                .valueOf(result.getString("period_type")),
                        result.getObject("period_start", java.time.LocalDate.class),
                        result.getObject("period_end", java.time.LocalDate.class)),
                result.getString("target_value"),
                result.getLong("created_by_member_id"),
                result.getLong("updated_by_member_id"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version"));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("KPI snapshot is not JSON serializable");
        }
    }

    private <T> T read(String value, Class<T> type, String label) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Stored " + label + " JSON is invalid", exception);
        }
    }

    private static String targetBusinessKey(KpiTarget target) {
        return target.systemId() + "|" + target.tenantId() + "|"
                + target.kpiId() + "|" + target.kpiVersionId() + "|"
                + target.kpiVersionNumber() + "|" + target.subjectType() + "|"
                + target.subjectId() + "|" + target.period().type() + "|"
                + target.period().startInclusive();
    }

    private static int subjectMemberCount(KpiSubjectType type, int roleCount) {
        return switch (type) {
            case MEMBER -> 1;
            case DEPARTMENT -> 0;
            case ROLE -> roleCount;
        };
    }

    private static String memberFingerprint(List<Long> memberIds) {
        return hash(memberIds.stream().map(String::valueOf)
                .collect(Collectors.joining(",")));
    }

    private static String hash(String value) {
        if (value == null) {
            throw invalid("KPI request key is required");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireDraftRevision(
            KpiDefinition expected,
            KpiDefinition revised
    ) {
        requireSameRoot(expected, revised);
        if (revised.draftVersion() != expected.draftVersion() + 1
                || revised.version() != expected.version() + 1
                || !Objects.equals(revised.activeVersionId(),
                expected.activeVersionId())
                || !Objects.equals(revised.activeVersionNumber(),
                expected.activeVersionNumber())
                || !revised.createdAt().equals(expected.createdAt())) {
            throw invalid("KPI draft CAS state is invalid");
        }
    }

    private static void requirePublication(
            KpiDefinition expected,
            KpiDefinition activated,
            KpiVersion version
    ) {
        requireSameRoot(expected, activated);
        if (version == null || version.kpiId() != expected.id()
                || version.systemId() != expected.systemId()
                || version.tenantId() != expected.tenantId()
                || !version.code().equals(expected.code())
                || !Objects.equals(activated.activeVersionId(), version.id())
                || !Objects.equals(activated.activeVersionNumber(),
                version.versionNumber())
                || activated.draftVersion() != expected.draftVersion()
                || activated.version() != expected.version() + 1
                || !activated.draft().equals(expected.draft())
                || !activated.name().equals(expected.name())
                || !Objects.equals(activated.description(),
                expected.description())) {
            throw invalid("KPI publication CAS state is invalid");
        }
    }

    private static void requireTargetRevision(
            KpiTarget expected,
            KpiTarget revised
    ) {
        if (expected == null || revised == null
                || expected.id() != revised.id()
                || expected.systemId() != revised.systemId()
                || expected.tenantId() != revised.tenantId()
                || expected.kpiId() != revised.kpiId()
                || expected.kpiVersionId() != revised.kpiVersionId()
                || expected.kpiVersionNumber() != revised.kpiVersionNumber()
                || expected.subjectType() != revised.subjectType()
                || expected.subjectId() != revised.subjectId()
                || !expected.period().equals(revised.period())
                || !expected.subjectDisplayName()
                .equals(revised.subjectDisplayName())
                || !expected.createdAt().equals(revised.createdAt())
                || expected.createdByMemberId()
                != revised.createdByMemberId()
                || revised.version() != expected.version() + 1) {
            throw invalid("KPI target revision changed immutable identity");
        }
    }

    private static void requireSameRoot(
            KpiDefinition expected,
            KpiDefinition next
    ) {
        if (expected == null || next == null
                || expected.id() != next.id()
                || expected.systemId() != next.systemId()
                || expected.tenantId() != next.tenantId()
                || !expected.code().equals(next.code())) {
            throw invalid("KPI immutable identity changed");
        }
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Scoped KPI lookup returned duplicate rows");
        }
        return rows.stream().findFirst();
    }

    private static void requireOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static KpiException conflict(String message) {
        return new KpiException("KPI_VERSION_CONFLICT", message);
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_INVALID", message);
    }
}
