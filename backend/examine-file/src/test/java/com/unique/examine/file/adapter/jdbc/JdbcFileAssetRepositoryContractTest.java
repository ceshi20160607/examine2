package com.unique.examine.file.adapter.jdbc;

import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcFileAssetRepositoryContractTest {
    @Test
    void assetMapperRestoresScopeMetadataAndVersion() throws Exception {
        var rows = assetRow("ACTIVE");
        var asset = JdbcFileAssetRepository.ASSET_ROW_MAPPER.mapRow(rows, 0);

        assertThat(asset.systemId()).isEqualTo(10);
        assertThat(asset.tenantId()).isEqualTo(20);
        assertThat(asset.objectKey()).isEqualTo("system/10/tenant/20/file/9");
        assertThat(asset.size()).isEqualTo(3);
        assertThat(asset.sha256()).hasSize(64);
        assertThat(asset.version()).isEqualTo(2);
    }

    @Test
    void assetMapperRejectsAnUnexpectedDatabaseStatus() throws Exception {
        var rows = assetRow("DELETED");
        assertThatThrownBy(() -> JdbcFileAssetRepository.ASSET_ROW_MAPPER.mapRow(rows, 0))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void repositorySqlAlwaysScopesObjectsAndReferencesByTenantAndVersion() {
        assertThat(JdbcFileAssetRepository.FIND_OBJECT_SQL)
                .contains("system_id = ?", "tenant_id = ?", "id = ?");
        assertThat(JdbcFileAssetRepository.FIND_REFERENCES_SQL)
                .contains("system_id = ?", "tenant_id = ?", "file_id = ?");
        assertThat(JdbcFileAssetRepository.UPDATE_OBJECT_SQL)
                .contains("system_id = ?", "tenant_id = ?", "version = ?");
        assertThat(JdbcFileAssetRepository.DELETE_OBJECT_SQL)
                .contains("system_id = ?", "tenant_id = ?", "version = ?");
        assertThat(JdbcFileAssetRepository.FIND_REFERENCE_PAGE_SQL)
                .contains(
                        "r.system_id = ?",
                        "r.tenant_id = ?",
                        "r.target_type = ?",
                        "r.target_id = ?",
                        "ORDER BY r.created_at DESC, r.file_id DESC",
                        "LIMIT ? OFFSET ?");
        assertThat(JdbcFileAssetRepository.COUNT_REFERENCE_PAGE_SQL)
                .contains(
                        "r.system_id = ?",
                        "r.tenant_id = ?",
                        "r.target_type = ?",
                        "r.target_id = ?");
        assertThat(JdbcFileAssetRepository.FIND_PAGE_BASE_SQL)
                .contains("system_id = ?", "tenant_id = ?", "status = 'ACTIVE'")
                .doesNotContain("object_key =");
        assertThat(JdbcFileAssetRepository.COUNT_PAGE_BASE_SQL)
                .contains("system_id = ?", "tenant_id = ?", "status = 'ACTIVE'");
    }

    private static javax.sql.rowset.CachedRowSet assetRow(String status) throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        var names = new String[]{
                "id", "system_id", "tenant_id", "uploader_member_id", "object_key",
                "original_name", "media_type", "size_bytes", "sha256", "status", "created_at", "version"
        };
        metadata.setColumnCount(names.length);
        for (int index = 0; index < names.length; index++) {
            int type = switch (names[index]) {
                case "id", "system_id", "tenant_id", "uploader_member_id", "size_bytes", "version" ->
                        Types.BIGINT;
                case "created_at" -> Types.TIMESTAMP;
                default -> Types.VARCHAR;
            };
            metadata.setColumnName(index + 1, names[index]);
            metadata.setColumnLabel(index + 1, names[index]);
            metadata.setColumnType(index + 1, type);
        }
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateLong("id", 9);
        rows.updateLong("system_id", 10);
        rows.updateLong("tenant_id", 20);
        rows.updateLong("uploader_member_id", 100);
        rows.updateString("object_key", "system/10/tenant/20/file/9");
        rows.updateString("original_name", "a.txt");
        rows.updateString("media_type", "text/plain");
        rows.updateLong("size_bytes", 3);
        rows.updateString("sha256", "a".repeat(64));
        rows.updateString("status", status);
        rows.updateTimestamp("created_at", java.sql.Timestamp.from(Instant.parse("2026-07-25T08:00:00Z")));
        rows.updateLong("version", 2);
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        rows.next();
        return rows;
    }
}
