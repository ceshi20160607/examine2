package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDelegationMigrationContractTest {
    @Test
    void storesScopedRulesAndExplicitOnBehalfDecisionAudit() throws Exception {
        var sql = Files.readString(Path.of(
                "..", "..", "sql", "migration",
                "V8_37_0__flow_delegation_proxy.sql"
        ));

        assertThat(sql).contains(
                "CREATE TABLE un_flow_approval_delegation",
                "delegator_member_id BIGINT NOT NULL",
                "delegate_member_id BIGINT NOT NULL",
                "definition_id BIGINT NULL",
                "starts_at DATETIME(6) NOT NULL",
                "ends_at DATETIME(6) NOT NULL",
                "idx_flow_delegation_outgoing",
                "idx_flow_delegation_incoming",
                "ck_flow_delegation_window",
                "ck_flow_delegation_revocation",
                "ADD COLUMN represented_member_id BIGINT NULL",
                "ADD COLUMN delegation_id BIGINT NULL",
                "fk_flow_history_delegation",
                "ck_flow_history_delegation_actor"
        );
        assertThat(JdbcApprovalSql.INSERT_DELEGATION).contains(
                "un_flow_approval_delegation",
                "delegator_member_id",
                "delegate_member_id",
                "definition_id",
                "created_by"
        );
        assertThat(JdbcApprovalSql.SELECT_ACTIVE_DELEGATION_FOR_UPDATE).contains(
                "delegate_member_id=?",
                "delegator_member_id=?",
                "starts_at<=?",
                "ends_at>?",
                "FOR UPDATE"
        );
        assertThat(JdbcApprovalSql.INSERT_HISTORY).contains(
                "represented_member_id",
                "delegation_id"
        );
        assertThat(JdbcApprovalSql.SELECT_HISTORY).contains(
                "represented_member_id",
                "delegation_id"
        );
    }
}
