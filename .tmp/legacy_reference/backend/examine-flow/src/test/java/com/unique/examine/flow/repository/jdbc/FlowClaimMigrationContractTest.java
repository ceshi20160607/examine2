package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowClaimMigrationContractTest {
    @Test
    void migrationBackfillsCursorStrengthensSnapshotAndRegistersClaimFacts() throws Exception {
        var sql = Files.readString(Path.of(
                "..",
                "..",
                "sql",
                "migration",
                "V8_11_0__flow_return_claim.sql"
        )).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains(
                        "add column current_step_index",
                        "add column claim_state",
                        "event_type = 'approved'",
                        "to_status = 'pending'",
                        "current_step_index < json_length(approver_ids_json)",
                        "json_extract( approver_ids_json, concat('$[', current_step_index, ']') ) is not null",
                        "as unsigned) = approver_id",
                        "claim_state in ('claimed', 'open')",
                        "idx_flow_instance_claim_pool",
                        "event_type = 'returned'",
                        "event_type = 'claim_cancelled'",
                        "event_type = 'claimed'",
                        "'flow.instance.return'",
                        "'flow.instance.claim'",
                        "'flow.instance.cancel-claim'",
                        "role_row.role_type = 'root'",
                        "update un_plat_authz_epoch"
                )
                .doesNotContain(
                        "select current_step_index from",
                        "json_type(json_extract"
                );
    }
}
