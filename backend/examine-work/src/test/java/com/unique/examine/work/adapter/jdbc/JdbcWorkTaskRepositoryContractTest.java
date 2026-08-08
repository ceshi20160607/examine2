package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.work.domain.WorkTaskQuery;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcWorkTaskRepositoryContractTest {
    @Test
    void rowMapperRestoresScopeStateAndVersion() throws Exception {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(14);
        column(metadata, 1, "id", Types.BIGINT);
        column(metadata, 2, "system_id", Types.BIGINT);
        column(metadata, 3, "tenant_id", Types.BIGINT);
        column(metadata, 4, "creator_member_id", Types.BIGINT);
        column(metadata, 5, "assignee_member_id", Types.BIGINT);
        column(metadata, 6, "title", Types.VARCHAR);
        column(metadata, 7, "status", Types.VARCHAR);
        column(metadata, 8, "created_at", Types.TIMESTAMP);
        column(metadata, 9, "updated_at", Types.TIMESTAMP);
        column(metadata, 10, "version", Types.BIGINT);
        column(metadata, 11, "project_id", Types.BIGINT);
        column(metadata, 12, "description", Types.VARCHAR);
        column(metadata, 13, "due_at", Types.TIMESTAMP);
        column(metadata, 14, "reminder_at", Types.TIMESTAMP);
        rows.setMetaData(metadata);
        rows.moveToInsertRow();
        rows.updateLong("id", 7);
        rows.updateLong("system_id", 10);
        rows.updateLong("tenant_id", 20);
        rows.updateLong("creator_member_id", 100);
        rows.updateLong("assignee_member_id", 101);
        rows.updateString("title", "Persisted");
        rows.updateString("status", "COMPLETED");
        rows.updateTimestamp("created_at", java.sql.Timestamp.from(Instant.parse("2026-07-25T08:00:00Z")));
        rows.updateTimestamp("updated_at", java.sql.Timestamp.from(Instant.parse("2026-07-25T09:00:00Z")));
        rows.updateLong("version", 4);
        rows.updateLong("project_id", 77);
        rows.updateString("description", "Shared task");
        rows.updateTimestamp("due_at", java.sql.Timestamp.from(
                Instant.parse("2026-07-26T09:00:00Z")));
        rows.updateNull("reminder_at");
        rows.insertRow();
        rows.moveToCurrentRow();
        rows.beforeFirst();
        rows.next();

        var task = JdbcWorkTaskRepository.ROW_MAPPER.mapRow(rows, 0);
        assertThat(task.id()).isEqualTo(7);
        assertThat(task.systemId()).isEqualTo(10);
        assertThat(task.tenantId()).isEqualTo(20);
        assertThat(task.status().name()).isEqualTo("COMPLETED");
        assertThat(task.version()).isEqualTo(4);
        assertThat(task.projectId()).isEqualTo(77L);
        assertThat(task.description()).isEqualTo("Shared task");
        assertThat(task.dueAt())
                .isEqualTo(Instant.parse("2026-07-26T09:00:00Z"));
    }

    @Test
    void updateAndLookupSqlAlwaysIncludeTenantAndOptimisticVersion() {
        assertThat(JdbcWorkTaskRepository.FIND_SQL)
                .contains("system_id = ?", "tenant_id = ?", "id = ?");
        assertThat(JdbcWorkTaskRepository.UPDATE_SQL)
                .contains("system_id = ?", "tenant_id = ?", "version = ?");
        assertThat(JdbcWorkTaskRepository.FIND_ALL_SQL)
                .contains("system_id = ?", "tenant_id = ?");
        assertThat(JdbcWorkTaskRepository.FIND_PARTICIPATING_SQL)
                .contains("system_id = ?", "tenant_id = ?", "creator_member_id = ?", "assignee_member_id = ?");
    }

    @Test
    void pageSqlScopesFiltersEscapesLiteralLikeAndUsesFrozenOrder() {
        var statements = JdbcWorkTaskRepository.pageStatements(
                10,
                20,
                100,
                new WorkTaskQuery(
                        "100%_!",
                        WorkTaskQuery.StatusFilter.OPEN,
                        WorkTaskQuery.RoleFilter.ASSIGNED_TO_ME,
                        2,
                        20));

        assertThat(statements.pageSql())
                .contains("system_id = ? AND tenant_id = ?")
                .contains("status = ?")
                .contains("assignee_member_id = ?")
                .contains("title LIKE ? ESCAPE '!'")
                .contains("CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END ASC")
                .contains("updated_at DESC, id DESC")
                .contains("LIMIT ? OFFSET ?");
        assertThat(statements.countSql())
                .contains("system_id = ? AND tenant_id = ?")
                .doesNotContain("ORDER BY", "LIMIT");
        assertThat(statements.countArguments())
                .containsExactly(10L, 20L, "OPEN", 100L, "%100!%!_!!%");
        assertThat(statements.pageArguments())
                .containsExactly(10L, 20L, "OPEN", 100L, "%100!%!_!!%", 20, 20L);
    }

    @Test
    void rolePredicatesAreExactAndAllDoesNotBindAnActorId() {
        var participating = JdbcWorkTaskRepository.pageStatements(
                10,
                20,
                100,
                new WorkTaskQuery(
                        "",
                        WorkTaskQuery.StatusFilter.ALL,
                        WorkTaskQuery.RoleFilter.PARTICIPATING,
                        1,
                        20));
        assertThat(participating.pageSql())
                .contains("creator_member_id = ? OR assignee_member_id = ?")
                .contains("un_work_project_member")
                .contains("project_member.status='ACTIVE'");
        assertThat(participating.countArguments())
                .containsExactly(10L, 20L, 100L, 100L, 100L);

        var all = JdbcWorkTaskRepository.pageStatements(
                10,
                20,
                100,
                new WorkTaskQuery(
                        "",
                        WorkTaskQuery.StatusFilter.ALL,
                        WorkTaskQuery.RoleFilter.ALL,
                        1,
                        20));
        assertThat(all.pageSql())
                .doesNotContain("creator_member_id = ?", "assignee_member_id = ?");
        assertThat(all.countArguments()).containsExactly(10L, 20L);
    }

    @Test
    void metricsSqlUsesExactUtcWindowsVisibilityAndStableTopOrder() {
        var from = Instant.parse("2026-08-01T00:00:00Z");
        var to = Instant.parse("2026-08-08T00:00:00Z");
        var now = Instant.parse("2026-08-10T00:00:00Z");
        var member = JdbcWorkTaskRepository.metricsStatements(
                10, 20, 100, false, from, to, now);

        assertThat(member.summarySql())
                .contains("task.system_id = ? AND task.tenant_id = ?")
                .contains("COUNT(*) total_count")
                .contains("task.status='COMPLETED' THEN 1 ELSE 0 END")
                .contains("task.due_at < ?")
                .contains("task.due_at >= ? AND task.due_at < ?")
                .contains("task.updated_at >= ? AND task.updated_at < ?")
                .contains("task.creator_member_id = ? OR task.assignee_member_id = ?")
                .contains("un_work_project_member")
                .contains("project_member.status='ACTIVE'");
        assertThat(member.createdDailySql())
                .contains("task.created_at >= ? AND task.created_at < ?")
                .contains("SELECT task.created_at metric_at")
                .contains("ORDER BY task.created_at ASC")
                .doesNotContain("DATE(task.created_at)");
        assertThat(member.completedDailySql())
                .contains("task.status='COMPLETED'")
                .contains("task.updated_at >= ? AND task.updated_at < ?")
                .contains("SELECT task.updated_at metric_at")
                .doesNotContain("DATE(task.updated_at)");
        assertThat(member.topAssigneesSql())
                .contains("task.status='OPEN'")
                .contains("ORDER BY open_count DESC, task.assignee_member_id ASC")
                .contains("LIMIT 20");
        assertThat(member.scopeArguments())
                .containsExactly(10L, 20L, 100L, 100L, 100L);
        assertThat(member.dailyArguments())
                .containsExactly(
                        10L, 20L, 100L, 100L, 100L,
                        java.sql.Timestamp.from(from),
                        java.sql.Timestamp.from(to));

        var manager = JdbcWorkTaskRepository.metricsStatements(
                10, 20, 100, true, from, to, now);
        assertThat(manager.summarySql())
                .doesNotContain(
                        "task.creator_member_id = ?",
                        "task.assignee_member_id = ?",
                        "un_work_project_member");
        assertThat(manager.scopeArguments()).containsExactly(10L, 20L);

        var project = JdbcWorkTaskRepository.metricsStatements(
                10, 20, 100, false, 77L, from, to, now);
        assertThat(project.summarySql())
                .contains("AND task.project_id = ?");
        assertThat(project.createdDailySql())
                .contains("AND task.project_id = ?");
        assertThat(project.completedDailySql())
                .contains("AND task.project_id = ?");
        assertThat(project.topAssigneesSql())
                .contains("AND task.project_id = ?");
        assertThat(project.scopeArguments())
                .containsExactly(10L, 20L, 100L, 100L, 100L, 77L);
        assertThat(project.dailyArguments())
                .containsExactly(
                        10L, 20L, 100L, 100L, 100L, 77L,
                        java.sql.Timestamp.from(from),
                        java.sql.Timestamp.from(to));
    }

    @Test
    void nativeDrillFiltersKeepOldInclusiveDueToAndAddExclusiveWindows() {
        var query = new WorkTaskQuery(
                "", WorkTaskQuery.StatusFilter.OPEN,
                WorkTaskQuery.RoleFilter.PARTICIPATING,
                1, 20, null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T23:59:59Z"),
                null, null,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"),
                101L);
        var statements = JdbcWorkTaskRepository.pageStatements(
                10, 20, 100, query);

        assertThat(statements.pageSql())
                .contains("due_at >= ?")
                .contains("due_at <= ?")
                .contains("due_at < ?")
                .contains("created_at >= ?", "created_at < ?")
                .contains("updated_at >= ?", "updated_at < ?")
                .contains("assignee_member_id = ?");
    }

    @Test
    void metricsDailyBucketsUseUtcInstantsInsteadOfDatabaseLocalDates() {
        assertThat(JdbcWorkTaskRepository.utcDayCounts(java.util.List.of(
                Instant.parse("2026-07-31T17:54:00Z"),
                Instant.parse("2026-07-31T23:59:59Z"),
                Instant.parse("2026-08-01T00:00:00Z"))))
                .extracting(
                        value -> value.date().toString(),
                        com.unique.examine.work.domain.WorkTaskMetricFacts.DayCount::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2026-07-31", 2L),
                        org.assertj.core.groups.Tuple.tuple("2026-08-01", 1L));
    }

    @Test
    void memberDirectoryQueryRequiresActiveMemberAndActiveTenantMembership() {
        assertThat(JdbcWorkMemberDirectory.ACTIVE_MEMBER_SQL)
                .contains("member.system_id = ?")
                .contains("member.id = ?")
                .contains("membership.tenant_id = ?")
                .contains("member.status = 'ACTIVE'")
                .contains("membership.status = 'ACTIVE'")
                .contains("membership.expires_at");
    }

    private static void column(RowSetMetaDataImpl metadata, int index, String name, int type)
            throws Exception {
        metadata.setColumnName(index, name);
        metadata.setColumnLabel(index, name);
        metadata.setColumnType(index, type);
    }
}
