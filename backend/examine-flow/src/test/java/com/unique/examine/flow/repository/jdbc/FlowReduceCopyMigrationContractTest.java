package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowReduceCopyMigrationContractTest {
    @Test
    void migrationAddsReduceFactCopyUniquenessPagingAndPermissions() throws Exception {
        var sql = Files.readString(Path.of(
                "..",
                "..",
                "sql",
                "migration",
                "V8_12_0__flow_reduce_sign_copy.sql"
        )).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql).contains(
                "add column target_step_index int unsigned null",
                "event_type = 'sign_removed'",
                "target_member_id > 0",
                "target_step_index is not null",
                "create table un_flow_copy_recipient",
                "message varchar(500) not null default ''",
                "primary key (system_id, tenant_id, copy_id)",
                "unique key uk_flow_copy_instance_recipient",
                "system_id, tenant_id, instance_id, recipient_id",
                "key idx_flow_copy_page",
                "system_id, tenant_id, instance_id, created_at, copy_id",
                "references un_flow_instance",
                "on delete restrict",
                "actor_id <> recipient_id",
                "char_length(message) <= 500",
                "'flow.instance.reduce-sign'",
                "'flow.instance.copy'",
                "role_row.role_type = 'root'",
                "update un_plat_authz_epoch"
        );
    }
}
