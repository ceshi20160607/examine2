package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalTaskStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowSqlContractTest {
    @Test
    void everyMutableOrReadableSqlPathCarriesTheBoundTenantScope() {
        var scopedStatements = List.of(
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.DELETE_DRAFT_STEPS,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.SELECT_DRAFTS,
                JdbcApprovalSql.COUNT_DRAFTS,
                JdbcApprovalSql.SELECT_VERSION,
                JdbcApprovalSql.SELECT_LATEST_VERSION,
                JdbcApprovalSql.UPDATE_INSTANCE_DECISION,
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.SELECT_INSTANCES,
                JdbcApprovalSql.COUNT_INSTANCES,
                JdbcApprovalSql.SELECT_APPROVAL_TASKS,
                JdbcApprovalSql.COUNT_APPROVAL_TASKS,
                JdbcApprovalSql.SELECT_CLAIMABLE_TASKS,
                JdbcApprovalSql.COUNT_CLAIMABLE_TASKS,
                JdbcApprovalSql.SELECT_HISTORY
        );

        assertThat(scopedStatements).allSatisfy(statement -> {
            var normalized = normalize(statement);
            assertThat(normalized).contains("system_id=?").contains("tenant_id=?");
        });
        assertThat(normalize(JdbcApprovalSql.INSERT_DRAFT)).startsWith("insert into");
        assertThat(normalize(JdbcApprovalSql.INSERT_VERSION))
                .startsWith("insert into")
                .doesNotContain("update", "delete");
        assertThat(normalize(JdbcApprovalSql.INSERT_VERSION_STEP))
                .startsWith("insert into")
                .doesNotContain("update", "delete");
        assertThat(normalize(JdbcApprovalSql.UPDATE_INSTANCE_DECISION))
                .contains(
                        "approver_id=?",
                        "approver_ids_json=?",
                        "current_step_index=?",
                        "claim_state=?",
                        "status='pending'",
                        "state_version=?");
        assertThat(normalize(JdbcApprovalSql.SELECT_DRAFTS))
                .contains("order by updated_at desc,definition_id desc", "limit ? offset ?");
        assertThat(normalize(JdbcApprovalSql.SELECT_INSTANCES))
                .contains("order by started_at desc,instance_id desc", "limit ? offset ?");
        assertThat(normalize(JdbcApprovalSql.SELECT_APPROVAL_TASKS))
                .contains(
                        "approver_id=?",
                        "order by started_at desc,instance_id desc",
                        "limit ? offset ?");
        assertThat(normalize(JdbcApprovalSql.approvalTasks(ApprovalTaskStatus.PENDING)))
                .contains("status='pending'");
        assertThat(normalize(JdbcApprovalSql.approvalTasks(ApprovalTaskStatus.COMPLETED)))
                .contains("status in ('approved','rejected','withdrawn','terminated')");
        assertThat(normalize(JdbcApprovalSql.approvalTasks(ApprovalTaskStatus.ALL)))
                .contains(
                        "status='pending'",
                        "status<>'pending'",
                        "json_contains(approver_ids_json"
                );
        assertThat(normalize(JdbcApprovalSql.INSERT_INSTANCE))
                .contains("approver_ids_json");
        assertThat(List.of(
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.SELECT_INSTANCES,
                JdbcApprovalSql.SELECT_APPROVAL_TASKS
        )).allSatisfy(sql -> assertThat(normalize(sql))
                .contains("approver_ids_json as approver_ids")
                .doesNotContain("un_flow_definition_version_step"));
        assertThat(normalize(JdbcApprovalSql.INSERT_HISTORY))
                .contains("target_member_id", "assignment_position", "target_step_index");
    }

    @Test
    void migrationFreezesFourScopedTablesAndTheirIntegrityContract() throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "create table un_flow_definition_draft",
                "create table un_flow_definition_version",
                "create table un_flow_instance",
                "create table un_flow_history_event",
                "primary key (system_id, tenant_id, definition_id)",
                "primary key (system_id, tenant_id, definition_id, version_no)",
                "primary key (system_id, tenant_id, instance_id)",
                "primary key (system_id, tenant_id, instance_id, event_sequence)",
                "unique key uk_flow_version_source_revision",
                "unique key uk_flow_instance_business",
                "constraint ck_flow_instance_status",
                "constraint ck_flow_instance_completion",
                "constraint ck_flow_history_event",
                "on delete restrict"
        );
        assertThat(count(migration, "create table un_flow_")).isEqualTo(4);
        assertThat(count(migration, "foreign key (")).isEqualTo(3);
        assertThat(count(migration, "on delete restrict")).isEqualTo(3);
    }

    @Test
    void sequentialMigrationBackfillsLegacyStepsAndRelaxesOnlyDecisionProgress() throws IOException {
        var migration = normalize(Files.readString(findMigration(
                "sql/migration/V8_4_0__flow_sequential_steps.sql")));

        assertThat(migration).contains(
                "create table un_flow_definition_draft_step",
                "create table un_flow_definition_version_step",
                "primary key (system_id, tenant_id, definition_id, step_no)",
                "primary key (system_id, tenant_id, definition_id, version_no, step_no)",
                "select system_id, tenant_id, definition_id, 1, approver_id",
                "select system_id, tenant_id, definition_id, version_no, 1, approver_id",
                "drop check ck_flow_instance_state_version",
                "state_version between 0 and 10",
                "to_status in ('pending', 'approved')",
                "event_sequence between 1 and 11"
        );
        assertThat(count(migration, "create table un_flow_definition_")).isEqualTo(2);
        assertThat(count(migration, "on delete restrict")).isEqualTo(2);
    }

    private static Path findMigration() {
        return findMigration("sql/migration/V6_0_0__flow_definition.sql");
    }

    private static Path findMigration(String relativePath) {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "Cannot locate flow migration from the test working directory: " + relativePath);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
