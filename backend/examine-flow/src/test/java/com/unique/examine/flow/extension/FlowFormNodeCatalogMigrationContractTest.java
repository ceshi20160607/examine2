package com.unique.examine.flow.extension;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowFormNodeCatalogMigrationContractTest {
    @Test
    void migrationKeepsPublishedGraphsAndRuntimeWritesImmutableScopedAndRestrictive()
            throws Exception {
        var sql = Files.readString(Path.of(
                        "..", "..", "sql", "migration",
                        "V8_87_0__flow_form_node_catalog.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        assertThat(sql)
                .contains("create table un_flow_definition_extension_draft")
                .contains("create table un_flow_definition_extension_version")
                .contains("create table un_flow_instance_form_snapshot")
                .contains("create table un_flow_form_write_history")
                .contains("create table un_flow_node_execution")
                .contains("create table un_flow_node_execution_event")
                .contains("primary key ( system_id, tenant_id, definition_id, definition_version)")
                .contains("foreign key ( system_id, tenant_id, definition_id, definition_version ) references un_flow_definition_version")
                .contains("foreign key ( system_id, tenant_id, instance_id ) references un_flow_instance")
                .contains("snapshot_version bigint unsigned not null default 0")
                .contains("execution_version bigint unsigned not null default 0")
                .contains("record_version_after > record_version_before")
                .contains("'waiting_confirmation'")
                .contains("json_type(policy_json) = 'array'")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade");
    }
}
