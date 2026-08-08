package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.work.domain.WorkProjectQuery;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcWorkProjectRepositoryContractTest {
    @Test
    void mappersRestoreProjectAndMemberSnapshots() throws Exception {
        var projectRows = RowSetProvider.newFactory().createCachedRowSet();
        var projectMeta = new RowSetMetaDataImpl();
        projectMeta.setColumnCount(10);
        column(projectMeta, 1, "id", Types.BIGINT);
        column(projectMeta, 2, "system_id", Types.BIGINT);
        column(projectMeta, 3, "tenant_id", Types.BIGINT);
        column(projectMeta, 4, "creator_member_id", Types.BIGINT);
        column(projectMeta, 5, "title", Types.VARCHAR);
        column(projectMeta, 6, "description", Types.VARCHAR);
        column(projectMeta, 7, "status", Types.VARCHAR);
        column(projectMeta, 8, "created_at", Types.TIMESTAMP);
        column(projectMeta, 9, "updated_at", Types.TIMESTAMP);
        column(projectMeta, 10, "version", Types.BIGINT);
        projectRows.setMetaData(projectMeta);
        projectRows.moveToInsertRow();
        projectRows.updateLong("id", 1L);
        projectRows.updateLong("system_id", 10L);
        projectRows.updateLong("tenant_id", 20L);
        projectRows.updateLong("creator_member_id", 100L);
        projectRows.updateString("title", "Delivery");
        projectRows.updateString("description", "Release");
        projectRows.updateString("status", "ACTIVE");
        projectRows.updateTimestamp("created_at", java.sql.Timestamp.from(NOW));
        projectRows.updateTimestamp("updated_at", java.sql.Timestamp.from(NOW));
        projectRows.updateLong("version", 1L);
        projectRows.insertRow();
        projectRows.moveToCurrentRow();
        projectRows.beforeFirst();
        projectRows.next();

        var project = JdbcWorkProjectRepository.PROJECT_MAPPER
                .mapRow(projectRows, 0);
        assertThat(project.title()).isEqualTo("Delivery");
        assertThat(project.version()).isEqualTo(1L);
    }

    @Test
    void sqlScopesVisibilityPagesCasAndLastOwnerLock() {
        assertThat(JdbcWorkProjectRepository.FIND_VISIBLE_PROJECT)
                .contains("project_row.system_id=?")
                .contains("project_row.tenant_id=?")
                .contains("member_row.member_id=?")
                .contains("member_row.status='ACTIVE'");
        assertThat(JdbcWorkProjectRepository.UPDATE_PROJECT)
                .contains("system_id=?", "tenant_id=?", "id=?", "version=?");
        assertThat(JdbcWorkProjectRepository.UPDATE_MEMBER)
                .contains("system_id=?", "tenant_id=?", "project_id=?")
                .contains("member_id=?", "version=?");
        assertThat(JdbcWorkProjectRepository.LOCK_ACTIVE_OWNERS)
                .contains("role='OWNER'")
                .contains("status='ACTIVE'")
                .contains("ORDER BY member_id FOR UPDATE");

        var page = JdbcWorkProjectRepository.pageStatements(
                10L, 20L, 100L, false,
                new WorkProjectQuery(
                        "alpha%", WorkProjectQuery.StatusFilter.ACTIVE,
                        2, 20));
        assertThat(page.pageSql())
                .contains("EXISTS")
                .contains("member_row.member_id=?")
                .contains("project_row.status=?")
                .contains("updated_at DESC,project_row.id DESC")
                .contains("LIMIT ? OFFSET ?");
        assertThat(page.countArguments())
                .containsExactly(10L, 20L, 100L, "ACTIVE",
                        "%alpha!%%", "%alpha!%%");
    }

    private static final Instant NOW =
            Instant.parse("2026-07-31T15:00:00Z");

    private static void column(
            RowSetMetaDataImpl metadata, int index, String name, int type
    ) throws Exception {
        metadata.setColumnName(index, name);
        metadata.setColumnLabel(index, name);
        metadata.setColumnType(index, type);
    }
}
