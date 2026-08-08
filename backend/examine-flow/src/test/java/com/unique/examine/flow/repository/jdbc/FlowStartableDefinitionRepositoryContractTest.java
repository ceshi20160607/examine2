package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowStartableDefinitionRepositoryContractTest {
    @Test
    void catalogSelectsOnlyTheLatestPublishedVersionPerTenantDefinition() {
        var sql = normalize(JdbcApprovalSql.SELECT_STARTABLE_DEFINITIONS);

        assertThat(sql).contains(
                "from un_flow_definition_version version_row",
                "select definition_id,max(version_no) as version_no",
                "where system_id=? and tenant_id=?",
                "group by definition_id",
                "latest.definition_id=version_row.definition_id",
                "latest.version_no=version_row.version_no",
                "where version_row.system_id=? and version_row.tenant_id=?",
                "order by version_row.published_at desc,version_row.definition_id desc",
                "limit ? offset ?"
        );
        assertThat(sql).doesNotContain("un_flow_definition_draft");
    }

    @Test
    void catalogCountIsTenantScopedAndCountsPublishedDefinitionsOnce() {
        assertThat(normalize(JdbcApprovalSql.COUNT_STARTABLE_DEFINITIONS))
                .contains(
                        "select count(distinct definition_id)",
                        "from un_flow_definition_version",
                        "where system_id=? and tenant_id=?"
                )
                .doesNotContain("un_flow_definition_draft");
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
