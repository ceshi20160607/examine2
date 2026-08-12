package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowApproverSourcesMigrationContractTest {
    @Test
    void storesImmutableDraftAndVersionSourceSelectors() throws Exception {
        var migration = Path.of(
                "..", "..", "sql", "migration", "V8_33_0__flow_approver_sources.sql"
        );
        var sql = Files.readString(migration);

        assertThat(sql).contains(
                "ALTER TABLE un_flow_definition_draft",
                "ALTER TABLE un_flow_definition_version",
                "ADD COLUMN approver_sources JSON NULL"
        );
        assertThat(sql.split("ADD COLUMN approver_sources JSON NULL", -1)).hasSize(3);
        assertThat(JdbcApprovalSql.INSERT_DRAFT).contains("approver_sources");
        assertThat(JdbcApprovalSql.UPDATE_DRAFT).contains("approver_sources=?");
        assertThat(JdbcApprovalSql.INSERT_VERSION).contains("approver_sources");
        assertThat(JdbcApprovalSql.SELECT_DRAFT).contains("approver_sources");
        assertThat(JdbcApprovalSql.SELECT_VERSION).contains("approver_sources");
    }
}
