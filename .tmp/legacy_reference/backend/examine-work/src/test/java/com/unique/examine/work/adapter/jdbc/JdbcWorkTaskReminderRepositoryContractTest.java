package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.work.domain.WorkTaskReminder;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcWorkTaskReminderRepositoryContractTest {
    @Test
    void mapperRestoresLeaseGenerationAndCasFacts() throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(18);
        var names = new String[]{"system_id","tenant_id","task_id","generation",
                "scheduled_at","status","attempt_count","lease_owner",
                "lease_token_hash","lease_expires_at","created_at","updated_at",
                "sent_at","failed_at","cancelled_at","failure_code",
                "failure_message","version"};
        for (int index = 0; index < names.length; index++) {
            column(metadata, index + 1, names[index],
                    names[index].endsWith("_at") ? Types.TIMESTAMP :
                            names[index].equals("status") || names[index].startsWith("lease_")
                                    || names[index].startsWith("failure_")
                                    ? Types.VARCHAR : Types.BIGINT);
        }
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateLong("system_id", 10); rows.updateLong("tenant_id", 20);
        rows.updateLong("task_id", 30); rows.updateInt("generation", 2);
        rows.updateTimestamp("scheduled_at", timestamp("2026-08-01T00:00:00Z"));
        rows.updateString("status", "PROCESSING"); rows.updateInt("attempt_count", 3);
        rows.updateString("lease_owner", "worker");
        rows.updateString("lease_token_hash", "a".repeat(64));
        rows.updateTimestamp("lease_expires_at", timestamp("2026-08-01T00:02:00Z"));
        rows.updateTimestamp("created_at", timestamp("2026-07-31T23:00:00Z"));
        rows.updateTimestamp("updated_at", timestamp("2026-08-01T00:01:00Z"));
        rows.updateNull("sent_at"); rows.updateNull("failed_at");
        rows.updateNull("cancelled_at"); rows.updateNull("failure_code");
        rows.updateNull("failure_message"); rows.updateLong("version", 8);
        rows.insertRow(); rows.moveToCurrentRow(); rows.beforeFirst(); rows.next();

        var reminder = JdbcWorkTaskReminderRepository.ROW_MAPPER.mapRow(rows, 0);

        assertThat(reminder.status()).isEqualTo(WorkTaskReminder.Status.PROCESSING);
        assertThat(reminder.generation()).isEqualTo(2);
        assertThat(reminder.attemptCount()).isEqualTo(3);
        assertThat(reminder.lease().owner()).isEqualTo("worker");
        assertThat(reminder.version()).isEqualTo(8);
    }

    @Test
    void sqlFreezesTenantCasStableScanAndSkipLocked() {
        assertThat(JdbcWorkTaskReminderRepository.FIND)
                .contains("system_id=?", "tenant_id=?", "task_id=?", "generation=?");
        assertThat(JdbcWorkTaskReminderRepository.UPDATE)
                .contains("version=?", "system_id=?", "tenant_id=?", "task_id=?");
        assertThat(JdbcWorkTaskReminderRepository.FIND_DUE_FOR_UPDATE)
                .contains("scheduled_at<=?", "lease_expires_at<=?")
                .contains("ORDER BY scheduled_at ASC,task_id ASC,generation ASC")
                .contains("FOR UPDATE SKIP LOCKED");
        assertThat(JdbcWorkTaskReminderRepository.UPDATE)
                .doesNotContain("scheduled_at=", "created_at=");
    }

    private static java.sql.Timestamp timestamp(String value) {
        return java.sql.Timestamp.from(Instant.parse(value));
    }

    private static void column(RowSetMetaDataImpl metadata, int index,
                               String name, int type) throws Exception {
        metadata.setColumnName(index, name);
        metadata.setColumnLabel(index, name);
        metadata.setColumnType(index, type);
    }
}
