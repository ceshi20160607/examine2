package com.unique.examine.event.adapter.jdbc;

import com.unique.examine.event.domain.InboxMessageFilter;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcInboxMessageRepositoryContractTest {
    @Test
    void rowMapperRestoresTargetScopeStatusAndVersion() throws Exception {
        var rows = row("READ", Instant.parse("2026-07-25T09:00:00Z"), null);
        var message = JdbcInboxMessageRepository.ROW_MAPPER.mapRow(rows, 0);

        assertThat(message.systemId()).isEqualTo(10);
        assertThat(message.tenantId()).isEqualTo(20);
        assertThat(message.target().type()).isEqualTo("WORK_TASK");
        assertThat(message.target().id()).isEqualTo("42");
        assertThat(message.targetPath()).isEqualTo("/systems/10/workbench?task=42");
        assertThat(message.status().name()).isEqualTo("READ");
        assertThat(message.version()).isEqualTo(2);
    }

    @Test
    void rowMapperRejectsDatabaseStatusThatDisagreesWithTimestamps() throws Exception {
        var rows = row("UNREAD", Instant.parse("2026-07-25T09:00:00Z"), null);
        assertThatThrownBy(() -> JdbcInboxMessageRepository.ROW_MAPPER.mapRow(rows, 0))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("status and timestamps disagree");
    }

    @Test
    void lookupAndUpdateSqlAlwaysIncludeTenantAndOptimisticVersion() {
        assertThat(JdbcInboxMessageRepository.FIND_SQL)
                .contains("system_id = ?", "tenant_id = ?", "id = ?");
        assertThat(JdbcInboxMessageRepository.INBOX_SQL)
                .contains("system_id = ?", "tenant_id = ?", "recipient_member_id = ?");
        assertThat(JdbcInboxMessageRepository.UPDATE_SQL)
                .contains("system_id = ?", "tenant_id = ?", "version = ?");
        assertThat(JdbcInboxMessageRepository.PAGE_SCOPE_SQL)
                .contains("system_id = ?", "tenant_id = ?", "recipient_member_id = ?");
        assertThat(JdbcInboxMessageRepository.statusPredicate(InboxMessageFilter.ALL))
                .contains("status <> 'ARCHIVED'");
        assertThat(JdbcInboxMessageRepository.statusPredicate(InboxMessageFilter.UNREAD))
                .contains("status = 'UNREAD'");
        assertThat(JdbcInboxMessageRepository.statusPredicate(InboxMessageFilter.READ))
                .contains("status = 'READ'");
        assertThat(JdbcInboxMessageRepository.statusPredicate(InboxMessageFilter.ARCHIVED))
                .contains("status = 'ARCHIVED'");
        assertThat(JdbcInboxMessageRepository.INBOX_SQL)
                .contains("ORDER BY created_at DESC, id DESC");
    }

    @Test
    void recipientDirectoryRequiresActiveMemberAndActiveTenantMembership() {
        assertThat(JdbcMessageRecipientDirectory.ACTIVE_RECIPIENT_SQL)
                .contains("member.system_id = ?")
                .contains("member.id = ?")
                .contains("membership.tenant_id = ?")
                .contains("member.status = 'ACTIVE'")
                .contains("membership.status = 'ACTIVE'")
                .contains("membership.expires_at");
    }

    private static javax.sql.rowset.CachedRowSet row(
            String status,
            Instant readAt,
            Instant archivedAt
    ) throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        var names = new String[]{
                "id", "system_id", "tenant_id", "sender_member_id", "recipient_member_id",
                "template_code", "title", "body", "target_type", "target_id", "target_path", "status",
                "created_at", "read_at", "archived_at", "version"
        };
        metadata.setColumnCount(names.length);
        for (int index = 0; index < names.length; index++) {
            int type = switch (names[index]) {
                case "id", "system_id", "tenant_id", "sender_member_id",
                     "recipient_member_id", "version" -> Types.BIGINT;
                case "created_at", "read_at", "archived_at" -> Types.TIMESTAMP;
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
        rows.updateLong("sender_member_id", 100);
        rows.updateLong("recipient_member_id", 101);
        rows.updateString("template_code", "TASK_ASSIGNED");
        rows.updateString("title", "Assigned");
        rows.updateString("body", "Review release");
        rows.updateString("target_type", "WORK_TASK");
        rows.updateString("target_id", "42");
        rows.updateString("target_path", "/systems/10/workbench?task=42");
        rows.updateString("status", status);
        rows.updateTimestamp("created_at", java.sql.Timestamp.from(Instant.parse("2026-07-25T08:00:00Z")));
        if (readAt != null) {
            rows.updateTimestamp("read_at", java.sql.Timestamp.from(readAt));
        } else {
            rows.updateNull("read_at");
        }
        if (archivedAt != null) {
            rows.updateTimestamp("archived_at", java.sql.Timestamp.from(archivedAt));
        } else {
            rows.updateNull("archived_at");
        }
        rows.updateLong("version", 2);
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        rows.next();
        return rows;
    }
}
