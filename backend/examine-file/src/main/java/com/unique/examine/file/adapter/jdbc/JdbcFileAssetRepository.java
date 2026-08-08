package com.unique.examine.file.adapter.jdbc;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.id.IdService;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileAssetPage;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.FileReference;
import com.unique.examine.file.domain.RuntimeRecordFile;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import com.unique.examine.file.port.FileAssetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcFileAssetRepository implements FileAssetRepository {
    static final String INSERT_OBJECT_SQL = """
            INSERT INTO un_file_object (
                id, system_id, tenant_id, uploader_member_id, object_key,
                original_name, media_type, size_bytes, sha256, status, created_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    static final String UPDATE_OBJECT_SQL = """
            UPDATE un_file_object
               SET version = ?
             WHERE id = ? AND system_id = ? AND tenant_id = ? AND version = ? AND status = 'ACTIVE'
            """;
    static final String FIND_OBJECT_SQL = """
            SELECT id, system_id, tenant_id, uploader_member_id, object_key,
                   original_name, media_type, size_bytes, sha256, status, created_at, version
              FROM un_file_object
             WHERE system_id = ? AND tenant_id = ? AND id = ? AND status = 'ACTIVE'
            """;
    static final String FIND_REFERENCES_SQL = """
            SELECT target_type, target_id, created_by_member_id, created_at
              FROM un_file_reference
             WHERE system_id = ? AND tenant_id = ? AND file_id = ?
            ORDER BY target_type, target_id
            """;
    static final String FIND_PAGE_BASE_SQL = """
            SELECT id, system_id, tenant_id, uploader_member_id, object_key,
                   original_name, media_type, size_bytes, sha256, status, created_at, version
              FROM un_file_object
             WHERE system_id = ? AND tenant_id = ? AND status = 'ACTIVE'
            """;
    static final String COUNT_PAGE_BASE_SQL = """
            SELECT COUNT(*)
              FROM un_file_object
             WHERE system_id = ? AND tenant_id = ? AND status = 'ACTIVE'
            """;
    static final String FIND_REFERENCE_PAGE_SQL = """
            SELECT o.id, o.system_id, o.tenant_id, o.uploader_member_id, o.object_key,
                   o.original_name, o.media_type, o.size_bytes, o.sha256,
                   o.created_at AS object_created_at, o.version,
                   r.target_type, r.target_id, r.created_by_member_id,
                   r.created_at AS reference_created_at
              FROM un_file_reference r
              JOIN un_file_object o
                ON o.system_id = r.system_id
               AND o.tenant_id = r.tenant_id
               AND o.id = r.file_id
               AND o.status = 'ACTIVE'
             WHERE r.system_id = ?
               AND r.tenant_id = ?
               AND r.target_type = ?
               AND r.target_id = ?
             ORDER BY r.created_at DESC, r.file_id DESC
             LIMIT ? OFFSET ?
            """;
    static final String COUNT_REFERENCE_PAGE_SQL = """
            SELECT COUNT(*)
              FROM un_file_reference r
              JOIN un_file_object o
                ON o.system_id = r.system_id
               AND o.tenant_id = r.tenant_id
               AND o.id = r.file_id
               AND o.status = 'ACTIVE'
             WHERE r.system_id = ?
               AND r.tenant_id = ?
               AND r.target_type = ?
               AND r.target_id = ?
            """;
    static final String DELETE_REFERENCES_SQL = """
            DELETE FROM un_file_reference
             WHERE system_id = ? AND tenant_id = ? AND file_id = ?
            """;
    static final String INSERT_REFERENCE_SQL = """
            INSERT INTO un_file_reference (
                system_id, tenant_id, file_id, target_type, target_id,
                created_by_member_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    static final String DELETE_OBJECT_SQL = """
            DELETE FROM un_file_object
             WHERE id = ? AND system_id = ? AND tenant_id = ? AND version = ? AND status = 'ACTIVE'
            """;
    static final RowMapper<FileAsset> ASSET_ROW_MAPPER = (resultSet, rowNum) -> mapAsset(resultSet);
    static final RowMapper<FileReference> REFERENCE_ROW_MAPPER =
            (resultSet, rowNum) -> new FileReference(
                    new AggregateRef(resultSet.getString("target_type"), resultSet.getString("target_id")),
                    resultSet.getLong("created_by_member_id"),
                    resultSet.getTimestamp("created_at").toInstant());
    static final RowMapper<RuntimeRecordFile> RUNTIME_RECORD_FILE_ROW_MAPPER =
            (resultSet, rowNum) -> mapRuntimeRecordFile(resultSet);

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcFileAssetRepository(JdbcTemplate jdbc, IdService ids) {
        if (jdbc == null || ids == null) {
            throw new IllegalArgumentException("JdbcTemplate and IdService are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public long nextId() {
        return ids.nextId();
    }

    @Override
    public Optional<FileAsset> findById(long systemId, long tenantId, long id) {
        var asset = jdbc.query(FIND_OBJECT_SQL, ASSET_ROW_MAPPER, systemId, tenantId, id)
                .stream().findFirst();
        if (asset.isEmpty()) {
            return Optional.empty();
        }
        var references = new LinkedHashMap<AggregateRef, FileReference>();
        for (var reference : jdbc.query(FIND_REFERENCES_SQL, REFERENCE_ROW_MAPPER,
                systemId, tenantId, id)) {
            references.put(reference.target(), reference);
        }
        var value = asset.orElseThrow();
        return Optional.of(new FileAsset(
                value.id(), value.systemId(), value.tenantId(), value.uploaderMemberId(),
                value.objectKey(), value.originalName(), value.mediaType(), value.size(), value.sha256(),
                value.createdAt(), references, value.version()));
    }

    @Override
    public FileAssetPage findPage(
            long systemId,
            long tenantId,
            String keyword,
            String mediaType,
            int page,
            int size
    ) {
        if (systemId <= 0 || tenantId <= 0 || page < 1 || size < 1) {
            throw new IllegalArgumentException("File asset page scope is invalid");
        }
        var where = new StringBuilder();
        var parameters = new ArrayList<Object>();
        parameters.add(systemId);
        parameters.add(tenantId);
        if (keyword != null) {
            where.append(" AND LOCATE(LOWER(?), LOWER(original_name)) > 0");
            parameters.add(keyword);
        }
        if (mediaType != null && mediaType.endsWith("/*")) {
            where.append(" AND LOWER(media_type) LIKE CONCAT(?, '%')");
            parameters.add(mediaType.substring(0, mediaType.length() - 1));
        } else if (mediaType != null) {
            where.append(" AND LOWER(media_type) = ?");
            parameters.add(mediaType);
        }

        var count = jdbc.queryForObject(COUNT_PAGE_BASE_SQL + where, Long.class,
                parameters.toArray());
        var pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) (page - 1) * size);
        var rows = jdbc.query(FIND_PAGE_BASE_SQL + where
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ASSET_ROW_MAPPER, pageParameters.toArray());
        var assets = rows.stream().map(this::withReferences).toList();
        return new FileAssetPage(assets, page, size, count == null ? 0 : count);
    }

    @Override
    public RuntimeRecordFilePage findReferencePage(
            long systemId,
            long tenantId,
            AggregateRef target,
            int page,
            int size
    ) {
        var values = jdbc.query(
                FIND_REFERENCE_PAGE_SQL,
                RUNTIME_RECORD_FILE_ROW_MAPPER,
                systemId,
                tenantId,
                target.type(),
                target.id(),
                size,
                (long) (page - 1) * size);
        var total = jdbc.queryForObject(
                COUNT_REFERENCE_PAGE_SQL,
                Long.class,
                systemId,
                tenantId,
                target.type(),
                target.id());
        return new RuntimeRecordFilePage(
                values,
                page,
                size,
                total == null ? 0 : total);
    }

    @Override
    public FileAsset save(FileAsset asset) {
        if (asset.version() == 1) {
            insert(asset);
        } else {
            update(asset);
        }
        return asset;
    }

    @Override
    public void delete(long systemId, long tenantId, long id, long expectedVersion) {
        try {
            int deleted = jdbc.update(DELETE_OBJECT_SQL, id, systemId, tenantId, expectedVersion);
            if (deleted != 1) {
                throw conflict(null);
            }
        } catch (DataIntegrityViolationException referenced) {
            throw referenced(referenced);
        }
    }

    private void insert(FileAsset asset) {
        try {
            jdbc.update(INSERT_OBJECT_SQL,
                    asset.id(), asset.systemId(), asset.tenantId(), asset.uploaderMemberId(),
                    asset.objectKey(), asset.originalName(), asset.mediaType(), asset.size(), asset.sha256(),
                    "ACTIVE", Timestamp.from(asset.createdAt()), asset.version());
        } catch (DuplicateKeyException duplicate) {
            throw conflict(duplicate);
        }
    }

    private FileAsset withReferences(FileAsset asset) {
        var references = new LinkedHashMap<AggregateRef, FileReference>();
        for (var reference : jdbc.query(FIND_REFERENCES_SQL, REFERENCE_ROW_MAPPER,
                asset.systemId(), asset.tenantId(), asset.id())) {
            references.put(reference.target(), reference);
        }
        return new FileAsset(asset.id(), asset.systemId(), asset.tenantId(), asset.uploaderMemberId(),
                asset.objectKey(), asset.originalName(), asset.mediaType(), asset.size(), asset.sha256(),
                asset.createdAt(), references, asset.version());
    }

    private void update(FileAsset asset) {
        int updated = jdbc.update(UPDATE_OBJECT_SQL,
                asset.version(), asset.id(), asset.systemId(), asset.tenantId(), asset.version() - 1);
        if (updated != 1) {
            throw conflict(null);
        }
        jdbc.update(DELETE_REFERENCES_SQL, asset.systemId(), asset.tenantId(), asset.id());
        try {
            for (var reference : asset.references().values()) {
                jdbc.update(INSERT_REFERENCE_SQL,
                        asset.systemId(), asset.tenantId(), asset.id(),
                        reference.target().type(), reference.target().id(),
                        reference.createdByMemberId(), Timestamp.from(reference.createdAt()));
            }
        } catch (DuplicateKeyException duplicate) {
            throw conflict(duplicate);
        }
    }

    private static FileAsset mapAsset(ResultSet resultSet) throws SQLException {
        if (!"ACTIVE".equals(resultSet.getString("status"))) {
            throw new SQLException("Only ACTIVE file objects can be mapped");
        }
        return new FileAsset(
                resultSet.getLong("id"),
                resultSet.getLong("system_id"),
                resultSet.getLong("tenant_id"),
                resultSet.getLong("uploader_member_id"),
                resultSet.getString("object_key"),
                resultSet.getString("original_name"),
                resultSet.getString("media_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256"),
                resultSet.getTimestamp("created_at").toInstant(),
                Map.of(),
                resultSet.getLong("version"));
    }

    private static RuntimeRecordFile mapRuntimeRecordFile(ResultSet resultSet)
            throws SQLException {
        var target = new AggregateRef(
                resultSet.getString("target_type"),
                resultSet.getString("target_id"));
        var reference = new FileReference(
                target,
                resultSet.getLong("created_by_member_id"),
                resultSet.getTimestamp("reference_created_at").toInstant());
        var asset = new FileAsset(
                resultSet.getLong("id"),
                resultSet.getLong("system_id"),
                resultSet.getLong("tenant_id"),
                resultSet.getLong("uploader_member_id"),
                resultSet.getString("object_key"),
                resultSet.getString("original_name"),
                resultSet.getString("media_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256"),
                resultSet.getTimestamp("object_created_at").toInstant(),
                Map.of(target, reference),
                resultSet.getLong("version"));
        return new RuntimeRecordFile(asset, reference);
    }

    private static FileDomainException conflict(Throwable cause) {
        var error = new FileDomainException("FILE_VERSION_CONFLICT", "File metadata version is stale");
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    private static FileDomainException referenced(Throwable cause) {
        var error = new FileDomainException("FILE_STILL_REFERENCED", "Referenced files cannot be deleted");
        error.initCause(cause);
        return error;
    }
}
