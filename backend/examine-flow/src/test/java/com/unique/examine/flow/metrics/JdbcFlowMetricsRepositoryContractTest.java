package com.unique.examine.flow.metrics;

import com.unique.examine.flow.domain.ApprovalInstance;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcFlowMetricsRepositoryContractTest {
    @Test
    void queryScopesTenantAndUsesInclusiveExclusiveStartedAndTerminalBounds() {
        assertThat(JdbcFlowMetricsRepository.FIND_CURRENT_FACTS)
                .contains("system_id=?", "tenant_id=?")
                .contains("status='PENDING'")
                .contains("started_at>=? AND started_at<?")
                .contains("completed_at>=? AND completed_at<?")
                .contains("'APPROVED','REJECTED','WITHDRAWN','TERMINATED'")
                .doesNotContain("requester_id=?", "approver_id=?");
    }

    @Test
    void mapperRestoresCurrentTerminalFactWithoutRawPayloads() throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(3);
        column(metadata, 1, "status", Types.VARCHAR);
        column(metadata, 2, "started_at", Types.TIMESTAMP);
        column(metadata, 3, "completed_at", Types.TIMESTAMP);
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateString("status", "WITHDRAWN");
        rows.updateTimestamp("started_at", java.sql.Timestamp.from(
                Instant.parse("2026-08-01T01:00:00Z")));
        rows.updateTimestamp("completed_at", java.sql.Timestamp.from(
                Instant.parse("2026-08-02T01:00:00Z")));
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        rows.next();

        var fact = JdbcFlowMetricsRepository.ROW_MAPPER.mapRow(rows, 0);
        assertThat(fact.status()).isEqualTo(ApprovalInstance.Status.WITHDRAWN);
        assertThat(fact.completedAt()).isEqualTo(
                Instant.parse("2026-08-02T01:00:00Z"));
    }

    private static void column(RowSetMetaDataImpl metadata, int index,
                               String name, int type) throws Exception {
        metadata.setColumnName(index, name);
        metadata.setColumnLabel(index, name);
        metadata.setColumnType(index, type);
    }
}
