package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowPeriodicTriggerMigrationContractTest {
    @Test
    void migrationAddsImmutablePeriodicSnapshotsAndScopedRuntimeState() throws Exception {
        var migration = Files.readString(Path.of(
                "..",
                "..",
                "sql",
                "migration",
                "V8_28_0__flow_periodic_trigger.sql"
        ));

        assertThat(migration)
                .contains(
                        "trigger_start_at",
                        "trigger_interval_minutes",
                        "trigger_requester_id",
                        "trigger_event = 'PERIODIC'",
                        "CREATE TABLE un_flow_periodic_schedule",
                        "PRIMARY KEY (system_id, tenant_id, definition_id)",
                        "idx_flow_periodic_due",
                        "fk_flow_periodic_version",
                        "fk_flow_periodic_instance",
                        "status = 'ACTIVE'",
                        "status = 'PAUSED'"
                );
        assertThat(JdbcFlowPeriodicSql.SELECT_SCHEDULE_FOR_UPDATE)
                .contains("system_id=?", "tenant_id=?", "definition_id=?", "FOR UPDATE");
        assertThat(JdbcFlowPeriodicSql.UPDATE_FIRED)
                .contains("definition_version=?", "status='ACTIVE'", "next_fire_at=?");
    }
}
