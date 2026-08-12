package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowCompensationMigrationContractTest {
    @Test
    void freezesPoliciesAndCreatesRestrictiveIndependentCompensationFacts()
            throws Exception {
        var sql = Files.readString(Path.of(
                        "..", "..", "sql", "migration",
                        "V8_45_0__flow_completion_compensation.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        assertThat(sql)
                .contains("completion_failure_policy varchar(20)")
                .contains("not null default 'manual_retry'")
                .contains("'compensating'")
                .contains("'completion_compensated'")
                .contains("create table un_flow_completion_compensation")
                .contains("create table un_flow_compensation_attempt")
                .contains("create table un_flow_compensation_subflow_run")
                .doesNotContain("on delete cascade");
        assertThat(sql.split("on delete restrict", -1).length - 1)
                .isGreaterThanOrEqualTo(7);
        assertThat(JdbcApprovalCompensationSql.SELECT_PLAN_FOR_UPDATE)
                .contains("ORDER BY original_ordinal DESC,compensation_id DESC")
                .contains("FOR UPDATE");
        assertThat(JdbcApprovalCompensationSql.UPDATE)
                .contains("state_version=?")
                .contains("AND state_version=?");
    }
}
