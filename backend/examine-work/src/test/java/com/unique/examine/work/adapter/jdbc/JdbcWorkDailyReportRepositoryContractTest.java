package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.work.domain.WorkDailyReportQuery;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcWorkDailyReportRepositoryContractTest {
    @Test
    void mapperRestoresExactNarrativeStateAndVersion() throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(13);
        column(metadata, 1, "id", Types.BIGINT);
        column(metadata, 2, "system_id", Types.BIGINT);
        column(metadata, 3, "tenant_id", Types.BIGINT);
        column(metadata, 4, "author_member_id", Types.BIGINT);
        column(metadata, 5, "work_date", Types.DATE);
        column(metadata, 6, "completed_work", Types.VARCHAR);
        column(metadata, 7, "planned_work", Types.VARCHAR);
        column(metadata, 8, "blockers", Types.VARCHAR);
        column(metadata, 9, "status", Types.VARCHAR);
        column(metadata, 10, "created_at", Types.TIMESTAMP);
        column(metadata, 11, "updated_at", Types.TIMESTAMP);
        column(metadata, 12, "submitted_at", Types.TIMESTAMP);
        column(metadata, 13, "version", Types.BIGINT);
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateLong("id", 1L);
        rows.updateLong("system_id", 10L);
        rows.updateLong("tenant_id", 20L);
        rows.updateLong("author_member_id", 100L);
        rows.updateDate("work_date", java.sql.Date.valueOf(DATE));
        rows.updateString("completed_work", "Done");
        rows.updateString("planned_work", "Next");
        rows.updateString("blockers", "Waiting");
        rows.updateString("status", "SUBMITTED");
        rows.updateTimestamp("created_at", java.sql.Timestamp.from(NOW));
        rows.updateTimestamp("updated_at", java.sql.Timestamp.from(NOW));
        rows.updateTimestamp("submitted_at", java.sql.Timestamp.from(NOW));
        rows.updateLong("version", 3L);
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        rows.next();

        var report = JdbcWorkDailyReportRepository.ROW_MAPPER
                .mapRow(rows, 0);
        assertThat(report.workDate()).isEqualTo(DATE);
        assertThat(report.blockers()).isEqualTo("Waiting");
        assertThat(report.status().name()).isEqualTo("SUBMITTED");
        assertThat(report.version()).isEqualTo(3L);
    }

    @Test
    void sqlScopesActiveAuthorsCasAndStableInclusivePages() {
        assertThat(JdbcWorkDailyReportRepository.FIND_BY_ID)
                .contains("report_row.system_id=?")
                .contains("report_row.tenant_id=?")
                .contains("author_row.status='ACTIVE'")
                .contains("membership_row.status='ACTIVE'")
                .contains("membership_row.expires_at");
        assertThat(JdbcWorkDailyReportRepository.UPDATE)
                .contains("system_id=?", "tenant_id=?", "id=?", "version=?");
        var statements = JdbcWorkDailyReportRepository.pageStatements(
                10L, 20L, 999L, true,
                new WorkDailyReportQuery(
                        WorkDailyReportQuery.Scope.ALL, 100L,
                        DATE.minusDays(6), DATE,
                        WorkDailyReportQuery.StatusFilter.SUBMITTED, 2, 20));
        assertThat(statements.pageSql())
                .contains("author_member_id=?")
                .contains("work_date BETWEEN ? AND ?")
                .contains("status=?")
                .contains("work_date DESC")
                .contains("updated_at DESC,report_row.id DESC")
                .contains("LIMIT ? OFFSET ?");
        assertThat(statements.pageArguments())
                .containsExactly(
                        10L, 20L, 100L,
                        java.sql.Date.valueOf(DATE.minusDays(6)),
                        java.sql.Date.valueOf(DATE),
                        "SUBMITTED", 20, 20L);
        assertThat(statements.countSql()).doesNotContain("ORDER BY", "LIMIT");
    }

    private static final LocalDate DATE = LocalDate.parse("2026-07-31");
    private static final Instant NOW = Instant.parse("2026-07-31T11:00:00Z");

    private static void column(
            RowSetMetaDataImpl metadata, int index, String name, int type
    ) throws Exception {
        metadata.setColumnName(index, name);
        metadata.setColumnLabel(index, name);
        metadata.setColumnType(index, type);
    }
}
