package com.unique.examine.event.adapter.jdbc;

import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDeliveryPreferenceRepositoryContractTest {
    @Test
    void rowMapperRestoresTheExactOwnerAndExplicitState() throws Exception {
        var preference = JdbcDeliveryPreferenceRepository.ROW_MAPPER.mapRow(row(), 0);

        assertThat(preference.id()).isEqualTo(8);
        assertThat(preference.systemId()).isEqualTo(10);
        assertThat(preference.tenantId()).isEqualTo(20);
        assertThat(preference.memberId()).isEqualTo(101);
        assertThat(preference.templateCode()).isEqualTo("MODULE_EXPORT_SUCCEEDED");
        assertThat(preference.channel().name()).isEqualTo("INBOX");
        assertThat(preference.enabled()).isFalse();
        assertThat(preference.updatedAt()).isEqualTo(Instant.parse("2026-08-05T08:30:00Z"));
        assertThat(preference.version()).isEqualTo(3);
    }

    @Test
    void everyLookupAndMutationSqlCarriesTheFullOwnerScope() {
        assertThat(JdbcDeliveryPreferenceRepository.FIND_SQL)
                .contains("system_id = ?", "tenant_id = ?", "member_id = ?",
                        "template_code = ?", "channel = ?");
        assertThat(JdbcDeliveryPreferenceRepository.FIND_ALL_SQL)
                .contains("system_id = ?", "tenant_id = ?", "member_id = ?", "channel = ?")
                .contains("ORDER BY template_code ASC");
        assertThat(JdbcDeliveryPreferenceRepository.INSERT_SQL)
                .contains("system_id, tenant_id, member_id, template_code, channel")
                .contains("enabled, created_at, updated_at, version");
        assertThat(JdbcDeliveryPreferenceRepository.UPDATE_SQL)
                .contains("system_id = ?", "tenant_id = ?", "member_id = ?",
                        "template_code = ?", "channel = ?", "version = ?")
                .doesNotContain("member_id <> ?", "tenant_id <> ?");
    }

    private static javax.sql.rowset.CachedRowSet row() throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        var names = new String[]{
                "id", "system_id", "tenant_id", "member_id", "template_code", "channel",
                "enabled", "created_at", "updated_at", "version"
        };
        metadata.setColumnCount(names.length);
        for (int index = 0; index < names.length; index++) {
            int type = switch (names[index]) {
                case "id", "system_id", "tenant_id", "member_id", "version" -> Types.BIGINT;
                case "enabled" -> Types.BOOLEAN;
                case "created_at", "updated_at" -> Types.TIMESTAMP;
                default -> Types.VARCHAR;
            };
            metadata.setColumnName(index + 1, names[index]);
            metadata.setColumnLabel(index + 1, names[index]);
            metadata.setColumnType(index + 1, type);
        }
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateLong("id", 8);
        rows.updateLong("system_id", 10);
        rows.updateLong("tenant_id", 20);
        rows.updateLong("member_id", 101);
        rows.updateString("template_code", "MODULE_EXPORT_SUCCEEDED");
        rows.updateString("channel", "INBOX");
        rows.updateBoolean("enabled", false);
        rows.updateTimestamp("created_at", java.sql.Timestamp.from(Instant.parse("2026-08-05T08:00:00Z")));
        rows.updateTimestamp("updated_at", java.sql.Timestamp.from(Instant.parse("2026-08-05T08:30:00Z")));
        rows.updateLong("version", 3);
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        rows.next();
        return rows;
    }
}
