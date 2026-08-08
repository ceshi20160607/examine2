package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowVersionHistoryRepositoryContractTest {
    @Test
    void historyIsDefinitionAndTenantScopedNewestFirstAndBounded() {
        assertThat(normalize(JdbcApprovalSql.SELECT_VERSIONS))
                .contains(
                        "from un_flow_definition_version",
                        "where system_id=? and tenant_id=? and definition_id=?",
                        "order by version_no desc",
                        "limit ? offset ?",
                        "step.version_no=un_flow_definition_version.version_no"
                )
                .doesNotContain("un_flow_definition_draft");
    }

    @Test
    void historyCountUsesTheSameDefinitionScope() {
        assertThat(normalize(JdbcApprovalSql.COUNT_VERSIONS))
                .isEqualTo(
                        "select count(*) from un_flow_definition_version "
                                + "where system_id=? and tenant_id=? and definition_id=?"
                );
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
