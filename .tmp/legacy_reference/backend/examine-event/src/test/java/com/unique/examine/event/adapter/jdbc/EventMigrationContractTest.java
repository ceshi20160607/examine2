package com.unique.examine.event.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EventMigrationContractTest {
    @Test
    void migrationFreezesScopeStateVersionTargetIntegrityAndIndexes() throws Exception {
        var sql = Files.readString(findMigration("V7_1_0__event_message.sql"));
        assertThat(sql)
                .contains("CREATE TABLE un_event_message")
                .contains("UNIQUE KEY uk_event_message_scope_id (system_id, tenant_id, id)")
                .contains("chk_event_message_target")
                .contains("chk_event_message_status")
                .contains("chk_event_message_state")
                .contains("chk_event_message_version CHECK (version > 0)")
                .contains("idx_event_message_inbox_state");
    }

    @Test
    void templateDeliveryMigrationOwnsPublicationDedupeLogAndManagementPermission() throws Exception {
        var sql = Files.readString(findMigration("V8_26_0__event_message_template_delivery.sql"));
        assertThat(sql)
                .contains("CREATE TABLE un_event_message_template")
                .contains("CREATE TABLE un_event_message_template_version")
                .contains("CREATE TABLE un_event_message_delivery_log")
                .contains("uk_event_delivery_dedupe")
                .contains("source_draft_version")
                .contains("status IN ('PENDING','DELIVERED','SKIPPED','FAILED')")
                .contains("ADD COLUMN target_path VARCHAR(500)")
                .contains("event.template.manage")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void deliveryPreferenceMigrationOwnsOnlyExactMemberTemplateInboxOverride() throws Exception {
        var sql = Files.readString(findMigration("V8_73_0__event_delivery_preference.sql"));
        assertThat(sql)
                .contains("CREATE TABLE un_event_delivery_preference")
                .contains("system_id, tenant_id, member_id, template_code, channel")
                .contains("channel = 'INBOX'")
                .contains("enabled IN (0, 1)")
                .contains("version > 0")
                .contains("FOREIGN KEY (system_id, member_id, tenant_id)")
                .contains("REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)")
                .contains("FOREIGN KEY (system_id, template_code)")
                .contains("REFERENCES un_event_message_template (system_id, template_code)")
                .doesNotContain("un_plat_permission")
                .doesNotContain("un_plat_authz_epoch")
                .doesNotContain("un_event_message_delivery_log")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void multiChannelMigrationFreezesControlledChannelsAdminIndexAndDurableAttempts() throws Exception {
        var sql = Files.readString(findMigration("V8_81_0__event_multi_channel_delivery.sql"));
        assertThat(sql)
                .contains("channel IN ('INBOX', 'EMAIL', 'WEBHOOK')")
                .contains("CREATE TABLE un_event_message_delivery_attempt")
                .contains("UNIQUE KEY uk_event_delivery_attempt (delivery_id, attempt_no)")
                .contains("idx_event_delivery_admin")
                .contains("masked_destination")
                .contains("duration_ms")
                .contains("trace_id")
                .contains("WHERE delivery.attempt_count > 0")
                .contains("FOREIGN KEY (delivery_id) REFERENCES un_event_message_delivery_log (id)")
                .doesNotContain("DROP TABLE");
    }

    private static Path findMigration(String name) {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql").resolve("migration").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate migration " + name);
    }
}
