package com.unique.examine.module.datasource.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository("jdbcDataSourceRepository")
public class JdbcDataSourceRepository implements DataSourceRepository {
    static final String ROOT_COLUMNS = """
            id,system_id,tenant_id,data_source_code,module_id,data_source_name,
            description,draft_json,draft_version,active_version_id,
            active_version_no,created_at,updated_at,version
            """;
    static final String VERSION_COLUMNS = """
            id,data_source_id,system_id,tenant_id,version_no,data_source_code,
            module_id,module_code,schema_version_id,data_source_name,description,
            snapshot_json,snapshot_fingerprint,published_by_member_id,published_at
            """;
    static final String INSERT_ROOT = """
            INSERT INTO un_module_data_source (
                id,system_id,tenant_id,data_source_code,module_id,
                data_source_name,description,draft_json,draft_version,
                active_version_id,active_version_no,created_at,updated_at,version
            ) VALUES (?,?,?,?,?,?,?,?,?,NULL,NULL,?,?,?)
            """;
    static final String SAVE_DRAFT_CAS = """
            UPDATE un_module_data_source
               SET data_source_name=?,description=?,draft_json=?,draft_version=?,
                   updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=?
               AND data_source_code=? AND module_id=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;
    static final String INSERT_VERSION = """
            INSERT INTO un_module_data_source_version (
                id,system_id,tenant_id,data_source_id,version_no,
                source_draft_version,data_source_code,module_id,module_code,
                schema_version_id,data_source_name,description,snapshot_json,
                snapshot_fingerprint,published_by_member_id,published_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String ACTIVATE_CAS = """
            UPDATE un_module_data_source
               SET active_version_id=?,active_version_no=?,updated_at=?,version=?
             WHERE system_id=? AND tenant_id=? AND id=?
               AND data_source_code=? AND module_id=?
               AND draft_version=? AND version=?
               AND active_version_id <=> ? AND active_version_no <=> ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdService ids;
    private final TransactionTemplate transactions;

    public JdbcDataSourceRepository(
            JdbcTemplate jdbc,
            ObjectMapper json,
            IdService ids,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public long nextDataSourceId() {
        return ids.nextId();
    }

    @Override
    public long nextVersionId() {
        return ids.nextId();
    }

    @Override
    public Optional<ModuleDataSource> findById(
            long systemId,
            long tenantId,
            long dataSourceId
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? AND id=?",
                this::root,
                systemId, tenantId, dataSourceId));
    }

    @Override
    public Optional<ModuleDataSource> findByCode(
            long systemId,
            long tenantId,
            String code
    ) {
        return one(jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? AND data_source_code=?",
                this::root,
                systemId, tenantId, code));
    }

    @Override
    public List<ModuleDataSource> findAll(long systemId, long tenantId) {
        return jdbc.query(
                "SELECT " + ROOT_COLUMNS + " FROM un_module_data_source "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "ORDER BY updated_at DESC,id DESC",
                this::root,
                systemId, tenantId);
    }

    @Override
    public ModuleDataSource insert(ModuleDataSource root) {
        Objects.requireNonNull(root, "root");
        if (root.activeVersionId() != null || root.activeVersionNumber() != null) {
            throw invalid("A new data source cannot already be published");
        }
        try {
            var inserted = jdbc.update(
                    INSERT_ROOT,
                    root.id(), root.systemId(), root.tenantId(), root.code(),
                    root.moduleId(), root.name(), root.description(),
                    write(root.draft()), root.draftVersion(),
                    Timestamp.from(root.createdAt()), Timestamp.from(root.updatedAt()),
                    root.version());
            requireOne(inserted, "Data source root insert did not affect one row");
            return root;
        } catch (DuplicateKeyException exception) {
            throw new DataSourceException(
                    "DATA_SOURCE_CODE_CONFLICT",
                    "Data source code or identity already exists");
        }
    }

    @Override
    public ModuleDataSource saveDraft(
            ModuleDataSource expected,
            ModuleDataSource revised
    ) {
        requireDraftRevision(expected, revised);
        var updated = jdbc.update(
                SAVE_DRAFT_CAS,
                revised.name(), revised.description(), write(revised.draft()),
                revised.draftVersion(), Timestamp.from(revised.updatedAt()),
                revised.version(), expected.systemId(), expected.tenantId(),
                expected.id(), expected.code(), expected.moduleId(),
                expected.draftVersion(), expected.version(),
                expected.activeVersionId(), expected.activeVersionNumber());
        if (updated != 1) {
            throw new DataSourceException(
                    "DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft changed before this save");
        }
        return revised;
    }

    @Override
    public DataSourceVersion publish(
            ModuleDataSource expected,
            ModuleDataSource activated,
            DataSourceVersion version
    ) {
        requirePublication(expected, activated, version);
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                requireOne(jdbc.update(
                                INSERT_VERSION,
                                version.id(), version.systemId(), version.tenantId(),
                                version.dataSourceId(), version.versionNumber(),
                                expected.draftVersion(), version.code(), version.moduleId(),
                                version.moduleCode(), version.schemaVersionId(),
                                version.name(), version.description(),
                                write(version.snapshot()), version.fingerprint(),
                                version.publishedByMemberId(),
                                Timestamp.from(version.publishedAt())),
                        "Data source version insert did not affect one row");
                var updated = jdbc.update(
                        ACTIVATE_CAS,
                        activated.activeVersionId(), activated.activeVersionNumber(),
                        Timestamp.from(activated.updatedAt()), activated.version(),
                        expected.systemId(), expected.tenantId(), expected.id(),
                        expected.code(), expected.moduleId(), expected.draftVersion(),
                        expected.version(), expected.activeVersionId(),
                        expected.activeVersionNumber());
                if (updated != 1) {
                    throw new DataSourceException(
                            "DATA_SOURCE_VERSION_CONFLICT",
                            "Data source changed before publication completed");
                }
                return version;
            }));
        } catch (DuplicateKeyException exception) {
            throw new DataSourceException(
                    "DATA_SOURCE_VERSION_CONFLICT",
                    "Data source draft or version was already published");
        }
    }

    @Override
    public Optional<DataSourceVersion> findActiveVersion(
            long systemId,
            long tenantId,
            long dataSourceId
    ) {
        return one(jdbc.query(
                "SELECT " + prefixedVersionColumns("version_row")
                        + " FROM un_module_data_source root "
                        + "JOIN un_module_data_source_version version_row "
                        + "ON version_row.system_id=root.system_id "
                        + "AND version_row.tenant_id=root.tenant_id "
                        + "AND version_row.data_source_id=root.id "
                        + "AND version_row.id=root.active_version_id "
                        + "AND version_row.version_no=root.active_version_no "
                        + "WHERE root.system_id=? AND root.tenant_id=? AND root.id=?",
                this::version,
                systemId, tenantId, dataSourceId));
    }

    @Override
    public Optional<DataSourceVersion> findVersion(
            long systemId,
            long tenantId,
            long dataSourceId,
            int versionNumber
    ) {
        return one(jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND data_source_id=? AND version_no=?",
                this::version,
                systemId, tenantId, dataSourceId, versionNumber));
    }

    @Override
    public Optional<DataSourceVersion> findVersionById(
            long systemId,
            long tenantId,
            long dataSourceId,
            long versionId
    ) {
        return one(jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=? "
                        + "AND data_source_id=? AND id=?",
                this::version,
                systemId, tenantId, dataSourceId, versionId));
    }

    @Override
    public List<DataSourceVersion> findVersions(
            long systemId,
            long tenantId,
            long dataSourceId
    ) {
        return jdbc.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM un_module_data_source_version "
                        + "WHERE system_id=? AND tenant_id=? AND data_source_id=? "
                        + "ORDER BY version_no DESC,id DESC",
                this::version,
                systemId, tenantId, dataSourceId);
    }

    private ModuleDataSource root(ResultSet result, int rowNumber)
            throws SQLException {
        return new ModuleDataSource(
                result.getLong("id"),
                result.getLong("system_id"),
                result.getLong("tenant_id"),
                result.getString("data_source_code"),
                result.getLong("module_id"),
                result.getString("data_source_name"),
                result.getString("description"),
                readDraft(result.getString("draft_json")),
                result.getLong("draft_version"),
                nullableLong(result, "active_version_id"),
                nullableInteger(result, "active_version_no"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version"));
    }

    private DataSourceVersion version(ResultSet result, int rowNumber)
            throws SQLException {
        return new DataSourceVersion(
                result.getLong("id"),
                result.getLong("data_source_id"),
                result.getLong("system_id"),
                result.getLong("tenant_id"),
                result.getInt("version_no"),
                result.getString("data_source_code"),
                result.getLong("module_id"),
                result.getString("module_code"),
                result.getString("schema_version_id"),
                result.getString("data_source_name"),
                result.getString("description"),
                readDraft(result.getString("snapshot_json")),
                result.getString("snapshot_fingerprint"),
                result.getLong("published_by_member_id"),
                result.getTimestamp("published_at").toInstant());
    }

    private String write(DataSourceDraft value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("Data source draft is not JSON serializable");
        }
    }

    private DataSourceDraft readDraft(String value) {
        try {
            return json.readValue(value, DataSourceDraft.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored data source draft JSON is invalid", exception);
        }
    }

    private static void requireDraftRevision(
            ModuleDataSource expected,
            ModuleDataSource revised
    ) {
        requireSameRoot(expected, revised);
        if (revised.draftVersion() != expected.draftVersion() + 1
                || revised.version() != expected.version() + 1
                || !Objects.equals(revised.activeVersionId(), expected.activeVersionId())
                || !Objects.equals(
                revised.activeVersionNumber(), expected.activeVersionNumber())
                || !revised.createdAt().equals(expected.createdAt())) {
            throw invalid("Data source draft CAS state is invalid");
        }
    }

    private static void requirePublication(
            ModuleDataSource expected,
            ModuleDataSource activated,
            DataSourceVersion version
    ) {
        requireSameRoot(expected, activated);
        if (version == null || version.dataSourceId() != expected.id()
                || version.systemId() != expected.systemId()
                || version.tenantId() != expected.tenantId()
                || !version.code().equals(expected.code())
                || version.moduleId() != expected.moduleId()
                || !Objects.equals(activated.activeVersionId(), version.id())
                || !Objects.equals(
                activated.activeVersionNumber(), version.versionNumber())
                || activated.draftVersion() != expected.draftVersion()
                || activated.version() != expected.version() + 1
                || !activated.draft().equals(expected.draft())
                || !activated.name().equals(expected.name())
                || !Objects.equals(activated.description(), expected.description())) {
            throw invalid("Data source publication CAS state is invalid");
        }
    }

    private static void requireSameRoot(
            ModuleDataSource expected,
            ModuleDataSource next
    ) {
        if (expected == null || next == null
                || expected.id() != next.id()
                || expected.systemId() != next.systemId()
                || expected.tenantId() != next.tenantId()
                || expected.moduleId() != next.moduleId()
                || !expected.code().equals(next.code())) {
            throw invalid("Data source immutable identity changed");
        }
    }

    private static String prefixedVersionColumns(String alias) {
        return VERSION_COLUMNS.replaceAll(
                "(?m)(^|,)\\s*([a-z_]+)", "$1" + alias + ".$2");
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Scoped data source lookup returned duplicate rows");
        }
        return rows.stream().findFirst();
    }

    private static Long nullableLong(ResultSet result, String column)
            throws SQLException {
        var value = result.getObject(column, Long.class);
        return value;
    }

    private static Integer nullableInteger(ResultSet result, String column)
            throws SQLException {
        return result.getObject(column, Integer.class);
    }

    private static void requireOne(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private static DataSourceException invalid(String message) {
        return new DataSourceException("DATA_SOURCE_INVALID", message);
    }
}
