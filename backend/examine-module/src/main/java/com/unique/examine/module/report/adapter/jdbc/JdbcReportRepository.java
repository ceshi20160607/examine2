package com.unique.examine.module.report.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.report.domain.ReportDefinition;
import com.unique.examine.module.report.domain.ReportDraft;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.domain.ReportFieldPin;
import com.unique.examine.module.report.domain.ReportSourcePin;
import com.unique.examine.module.report.domain.ReportVersion;
import com.unique.examine.module.report.port.ReportRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository("jdbcReportRepository")
public class JdbcReportRepository implements ReportRepository {
    static final String ROOT_COLUMNS = """
            id,system_id,tenant_id,report_code,report_name,description,
            draft_json,draft_version,active_version_id,active_version_no,
            created_at,updated_at,version
            """;
    static final String VERSION_COLUMNS = """
            id,report_id,system_id,tenant_id,version_no,source_draft_version,
            report_code,report_name,description,data_source_id,
            data_source_version_id,data_source_version_no,data_source_code,
            data_source_name,module_id,module_code,schema_version_id,
            snapshot_fingerprint,published_by_member_id,published_at
            """;
    static final String FIELD_COLUMNS = """
            id,report_version_id,report_id,system_id,tenant_id,
            report_version_no,field_ordinal,field_id,field_code,field_name,
            field_type,query_type
            """;
    static final String INSERT_ROOT = """
            INSERT INTO un_module_report (
                id,system_id,tenant_id,report_code,report_name,description,
                draft_json,draft_version,active_version_id,active_version_no,
                created_at,updated_at,version
            ) VALUES (?,?,?,?,?,?,?,?,NULL,NULL,?,?,?)
            """;
    static final String SAVE_DRAFT_CAS = """
            UPDATE un_module_report
               SET report_name=?,description=?,draft_json=?,draft_version=?,
                   updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=? AND report_code=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;
    static final String INSERT_VERSION = """
            INSERT INTO un_module_report_version (
                id,system_id,tenant_id,report_id,version_no,
                source_draft_version,report_code,report_name,description,
                data_source_id,data_source_version_id,data_source_version_no,
                data_source_code,data_source_name,module_id,module_code,
                schema_version_id,field_count,snapshot_json,
                snapshot_fingerprint,published_by_member_id,published_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,(
                SELECT source.module_id
                  FROM un_module_data_source_version source
                 WHERE source.system_id=? AND source.tenant_id=?
                   AND source.data_source_id=? AND source.id=?
                   AND source.version_no=?
                   AND source.module_id=?
            ),?,?,?,?,?,?,?)
            """;
    static final String INSERT_FIELD = """
            INSERT INTO un_module_report_version_field (
                id,system_id,tenant_id,report_id,report_version_id,
                report_version_no,field_ordinal,field_id,field_code,
                field_name,field_type,query_type
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String ACTIVATE_CAS = """
            UPDATE un_module_report
               SET active_version_id=?,active_version_no=?,updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=? AND report_code=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdService ids;
    private final TransactionTemplate transactions;

    public JdbcReportRepository(
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
    public long nextReportId() {
        return ids.nextId();
    }

    @Override
    public long nextVersionId() {
        return ids.nextId();
    }

    @Override
    public Optional<ReportDefinition> findById(
            long systemId, long tenantId, long reportId
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                this::root, systemId, tenantId, reportId));
    }

    @Override
    public Optional<ReportDefinition> findByCode(
            long systemId, long tenantId, String code
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=? AND report_code=?",
                this::root, systemId, tenantId, code));
    }

    @Override
    public List<ReportDefinition> findAll(long systemId, long tenantId) {
        return jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_report "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "ORDER BY updated_at DESC,id DESC",
                this::root, systemId, tenantId);
    }

    @Override
    public ReportDefinition insert(ReportDefinition root) {
        Objects.requireNonNull(root, "root");
        if (root.activeVersionId() != null
                || root.activeVersionNumber() != null) {
            throw invalid("A new report cannot already be published");
        }
        try {
            requireOne(jdbc.update(INSERT_ROOT,
                            root.id(), root.systemId(), root.tenantId(),
                            root.code(), root.name(), root.description(),
                            write(root.draft()), root.draftVersion(),
                            Timestamp.from(root.createdAt()),
                            Timestamp.from(root.updatedAt()), root.version()),
                    "Report root insert did not affect one row");
            return root;
        } catch (DuplicateKeyException exception) {
            throw new ReportException("REPORT_CODE_CONFLICT",
                    "Report code already exists in this tenant");
        }
    }

    @Override
    public ReportDefinition saveDraft(
            ReportDefinition expected, ReportDefinition revised
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
            throw conflict("Report draft changed before this save");
        }
        return revised;
    }

    @Override
    public ReportVersion publish(
            ReportDefinition expected,
            ReportDefinition activated,
            ReportVersion version
    ) {
        requirePublication(expected, activated, version);
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                var source = version.source();
                requireOne(jdbc.update(INSERT_VERSION,
                                version.id(), version.systemId(),
                                version.tenantId(), version.reportId(),
                                version.versionNumber(),
                                version.sourceDraftVersion(), version.code(),
                                version.name(), version.description(),
                                source.dataSourceId(),
                                source.dataSourceVersionId(),
                                source.dataSourceVersionNumber(),
                                source.dataSourceCode(),
                                source.dataSourceName(), version.systemId(),
                                version.tenantId(), source.dataSourceId(),
                                source.dataSourceVersionId(),
                                source.dataSourceVersionNumber(), source.moduleId(),
                                source.moduleCode(), source.schemaVersionId(),
                                source.fields().size(), writeSnapshot(version),
                                version.fingerprint(),
                                version.publishedByMemberId(),
                                Timestamp.from(version.publishedAt())),
                        "Report version insert did not affect one row");
                insertFields(version);
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
                    throw conflict(
                            "Report changed before publication completed");
                }
                return version;
            }));
        } catch (DuplicateKeyException exception) {
            throw conflict("Report draft or version was already published");
        } catch (DataIntegrityViolationException exception) {
            throw invalid(
                    "Report publication violated a scoped immutable reference");
        }
    }

    @Override
    public Optional<ReportVersion> findActiveVersion(
            long systemId, long tenantId, long reportId
    ) {
        var row = one(jdbc.query(
                "SELECT " + prefixed(VERSION_COLUMNS, "version_row")
                        + " FROM un_module_report root "
                        + "JOIN un_module_report_version version_row "
                        + "ON version_row.system_id=root.system_id "
                        + "AND version_row.tenant_id=root.tenant_id "
                        + "AND version_row.report_id=root.id "
                        + "AND version_row.id=root.active_version_id "
                        + "AND version_row.version_no=root.active_version_no "
                        + "WHERE root.system_id=? AND root.tenant_id=? "
                        + "AND root.id=?",
                JdbcReportRepository::versionRow,
                systemId, tenantId, reportId));
        return hydrate(row);
    }

    @Override
    public Optional<ReportVersion> findVersion(
            long systemId,
            long tenantId,
            long reportId,
            int versionNumber
    ) {
        var row = one(jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND report_id=? AND version_no=?",
                JdbcReportRepository::versionRow,
                systemId, tenantId, reportId, versionNumber));
        return hydrate(row);
    }

    @Override
    public Optional<ReportVersion> findVersionById(
            long systemId,
            long tenantId,
            long reportId,
            long versionId
    ) {
        var row = one(jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND report_id=? AND id=?",
                JdbcReportRepository::versionRow,
                systemId, tenantId, reportId, versionId));
        return hydrate(row);
    }

    @Override
    public List<ReportVersion> findVersions(
            long systemId, long tenantId, long reportId
    ) {
        var rows = jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_report_version "
                        + "WHERE system_id=? AND tenant_id=? AND report_id=? "
                        + "ORDER BY version_no DESC,id DESC",
                JdbcReportRepository::versionRow,
                systemId, tenantId, reportId);
        if (rows.isEmpty()) {
            return List.of();
        }
        var fields = fields(systemId, tenantId, reportId, null);
        Map<Long, List<ReportFieldPin>> byVersion = new LinkedHashMap<>();
        fields.forEach(field -> byVersion.computeIfAbsent(
                field.reportVersionId(), ignored -> new ArrayList<>())
                .add(field.pin()));
        return rows.stream().map(row -> materialize(
                row, byVersion.getOrDefault(row.id(), List.of()))).toList();
    }

    private Optional<ReportVersion> hydrate(Optional<VersionRow> row) {
        if (row.isEmpty()) {
            return Optional.empty();
        }
        var value = row.orElseThrow();
        var fields = fields(value.systemId(), value.tenantId(),
                value.reportId(), value.id()).stream()
                .map(FieldRow::pin).toList();
        return Optional.of(materialize(value, fields));
    }

    private List<FieldRow> fields(
            long systemId,
            long tenantId,
            long reportId,
            Long versionId
    ) {
        var filter = versionId == null ? "" : " AND report_version_id=?";
        var arguments = versionId == null
                ? new Object[]{systemId, tenantId, reportId}
                : new Object[]{systemId, tenantId, reportId, versionId};
        return jdbc.query(
                "SELECT " + FIELD_COLUMNS
                        + " FROM un_module_report_version_field "
                        + "WHERE system_id=? AND tenant_id=? AND report_id=?"
                        + filter
                        + " ORDER BY report_version_no DESC,"
                        + "field_ordinal ASC,id ASC",
                JdbcReportRepository::fieldRow,
                arguments);
    }

    private void insertFields(ReportVersion version) {
        var arguments = new ArrayList<Object[]>();
        for (var ordinal = 0;
             ordinal < version.source().fields().size(); ordinal++) {
            var field = version.source().fields().get(ordinal);
            arguments.add(new Object[]{
                    ids.nextId(), version.systemId(), version.tenantId(),
                    version.reportId(), version.id(), version.versionNumber(),
                    ordinal, field.logicalFieldId(), field.code(), field.name(),
                    field.type(), field.queryType()});
        }
        var affected = jdbc.batchUpdate(INSERT_FIELD, arguments);
        if (affected.length != arguments.size()) {
            throw new IllegalStateException(
                    "Report field snapshot insert is incomplete");
        }
        for (var count : affected) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException(
                        "Report field snapshot insert did not affect one row");
            }
        }
    }

    private ReportDefinition root(ResultSet result, int rowNumber)
            throws SQLException {
        return new ReportDefinition(
                result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"),
                result.getString("report_code"),
                result.getString("report_name"),
                result.getString("description"),
                readDraft(result.getString("draft_json")),
                result.getLong("draft_version"),
                result.getObject("active_version_id", Long.class),
                result.getObject("active_version_no", Integer.class),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version"));
    }

    private static VersionRow versionRow(ResultSet result, int rowNumber)
            throws SQLException {
        return new VersionRow(
                result.getLong("id"), result.getLong("report_id"),
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getInt("version_no"),
                result.getLong("source_draft_version"),
                result.getString("report_code"),
                result.getString("report_name"),
                result.getString("description"),
                result.getLong("data_source_id"),
                result.getString("data_source_code"),
                result.getString("data_source_name"),
                result.getLong("data_source_version_id"),
                result.getInt("data_source_version_no"),
                result.getLong("module_id"),
                result.getString("module_code"),
                result.getString("schema_version_id"),
                result.getString("snapshot_fingerprint"),
                result.getLong("published_by_member_id"),
                result.getTimestamp("published_at").toInstant());
    }

    private static FieldRow fieldRow(ResultSet result, int rowNumber)
            throws SQLException {
        return new FieldRow(
                result.getLong("report_version_id"),
                new ReportFieldPin(
                        result.getLong("field_id"),
                        result.getString("field_code"),
                        result.getString("field_name"),
                        result.getString("field_type"),
                        result.getString("query_type")));
    }

    private static ReportVersion materialize(
            VersionRow row, List<ReportFieldPin> fields
    ) {
        return new ReportVersion(
                row.id(), row.reportId(), row.systemId(), row.tenantId(),
                row.versionNumber(), row.sourceDraftVersion(), row.code(),
                row.name(), row.description(), new ReportSourcePin(
                row.dataSourceId(), row.dataSourceCode(), row.dataSourceName(),
                row.dataSourceVersionId(), row.dataSourceVersionNumber(),
                row.moduleId(), row.moduleCode(), row.schemaVersionId(), fields),
                row.fingerprint(), row.publishedByMemberId(),
                row.publishedAt());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("Report snapshot is not JSON serializable");
        }
    }

    private String writeSnapshot(ReportVersion value) {
        return write(new ReportSnapshot(value.source()));
    }

    private ReportDraft readDraft(String value) {
        try {
            return json.readValue(value, ReportDraft.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Stored report draft JSON is invalid", exception);
        }
    }

    private static void requireDraftRevision(
            ReportDefinition expected, ReportDefinition revised
    ) {
        requireSameRoot(expected, revised);
        if (revised.draftVersion() != expected.draftVersion() + 1
                || revised.version() != expected.version() + 1
                || !Objects.equals(revised.activeVersionId(),
                expected.activeVersionId())
                || !Objects.equals(revised.activeVersionNumber(),
                expected.activeVersionNumber())
                || !revised.createdAt().equals(expected.createdAt())) {
            throw invalid("Report draft CAS state is invalid");
        }
    }

    private static void requirePublication(
            ReportDefinition expected,
            ReportDefinition activated,
            ReportVersion version
    ) {
        requireSameRoot(expected, activated);
        if (version == null || version.reportId() != expected.id()
                || version.systemId() != expected.systemId()
                || version.tenantId() != expected.tenantId()
                || !version.code().equals(expected.code())
                || !version.name().equals(expected.name())
                || !Objects.equals(version.description(), expected.description())
                || !Objects.equals(activated.activeVersionId(), version.id())
                || !Objects.equals(activated.activeVersionNumber(),
                version.versionNumber())
                || version.versionNumber() != (expected.activeVersionNumber()
                == null ? 1 : expected.activeVersionNumber() + 1)
                || version.sourceDraftVersion() != expected.draftVersion()
                || version.source().dataSourceId()
                != expected.draft().dataSourceId()
                || !version.source().fields().stream()
                .map(ReportFieldPin::code).toList()
                .equals(expected.draft().outputFieldCodes())
                || activated.draftVersion() != expected.draftVersion()
                || activated.version() != expected.version() + 1
                || !activated.draft().equals(expected.draft())
                || !activated.name().equals(expected.name())
                || !Objects.equals(activated.description(),
                expected.description())
                || !activated.createdAt().equals(expected.createdAt())
                || !activated.updatedAt().equals(version.publishedAt())) {
            throw invalid("Report publication CAS state is invalid");
        }
    }

    private static void requireSameRoot(
            ReportDefinition expected, ReportDefinition next
    ) {
        if (expected == null || next == null
                || expected.id() != next.id()
                || expected.systemId() != next.systemId()
                || expected.tenantId() != next.tenantId()
                || !expected.code().equals(next.code())) {
            throw invalid("Report immutable identity changed");
        }
    }

    private static String prefixed(String columns, String alias) {
        return columns.replaceAll(
                "(?m)(^|,)\\s*([a-z_]+)", "$1" + alias + ".$2");
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Scoped report lookup returned duplicate rows");
        }
        return rows.stream().findFirst();
    }

    private static void requireOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static ReportException conflict(String message) {
        return new ReportException("REPORT_VERSION_CONFLICT", message);
    }

    private static ReportException invalid(String message) {
        return new ReportException("REPORT_INVALID", message);
    }

    private record VersionRow(
            long id,
            long reportId,
            long systemId,
            long tenantId,
            int versionNumber,
            long sourceDraftVersion,
            String code,
            String name,
            String description,
            long dataSourceId,
            String dataSourceCode,
            String dataSourceName,
            long dataSourceVersionId,
            int dataSourceVersionNumber,
            long moduleId,
            String moduleCode,
            String schemaVersionId,
            String fingerprint,
            long publishedByMemberId,
            java.time.Instant publishedAt
    ) {
    }

    private record FieldRow(long reportVersionId, ReportFieldPin pin) {
    }

    private record ReportSnapshot(ReportSourcePin source) {
    }
}
