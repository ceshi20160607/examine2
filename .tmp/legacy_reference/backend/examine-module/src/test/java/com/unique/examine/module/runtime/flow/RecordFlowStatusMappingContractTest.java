package com.unique.examine.module.runtime.flow;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFlowStatusMappingContractTest {

    @Test
    void migrationSnapshotsCompleteCanonicalMappingsInBothProjectionTables() throws Exception {
        var source = normalize(Files.readString(migrationPath()));

        assertThat(source)
                .contains("alter table un_module_record_flow_state ")
                .contains("alter table un_module_record_flow_state_item ")
                .contains("status_field_code varchar(64)")
                .contains("status_approved_value varchar(19)")
                .contains("status_rejected_value varchar(19)")
                .contains("status_withdrawn_value varchar(19)")
                .contains("status_terminated_value varchar(19)")
                .contains("^[a-za-z][a-za-z0-9_]{0,63}$")
                .contains("^[1-9][0-9]{0,18}$")
                .contains("ck_record_flow_status_mapping_presence")
                .contains("ck_record_flow_item_mapping_presence");
    }

    @Test
    void terminalFieldMutationPrecedesProjectionTransitionInsideTheTransactionalAdapter() throws Exception {
        var adapter = Files.readString(adapterPath());
        var apply = adapter.indexOf("statusMappings.applyTerminal(");
        var primaryTransition = adapter.indexOf("projections.transition(", apply);
        var additionalTransition = adapter.indexOf("projections.transitionAdditional(", apply);

        assertThat(apply).isGreaterThanOrEqualTo(0);
        assertThat(primaryTransition).isGreaterThan(apply);
        assertThat(additionalTransition).isGreaterThan(apply);
        assertThat(adapter)
                .contains("catch (DataIntegrityViolationException exception)")
                .contains("throw stateConflict(exception)");

        var service = normalize(Files.readString(servicePath()));
        assertThat(service)
                .contains("from un_module_record ")
                .contains("for update")
                .contains("delete from un_module_record_value")
                .contains("delete from un_module_record_index")
                .contains("insert into un_module_record_value")
                .contains("insert into un_module_record_index")
                .contains("set version=version+1,updated_at=?,updated_by=?")
                .contains("history.appendflowfield(");
    }

    private static Path migrationPath() {
        var path = Path.of("../../sql/migration/V8_18_0__module_record_flow_status_mapping.sql");
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("sql/migration/V8_18_0__module_record_flow_status_mapping.sql");
    }

    private static Path adapterPath() {
        var path = Path.of(
                "src/main/java/com/unique/examine/module/runtime/flow/RuntimeRecordFlowAdapter.java");
        if (Files.exists(path)) {
            return path;
        }
        return Path.of(
                "backend/examine-module/src/main/java/com/unique/examine/module/runtime/flow/"
                        + "RuntimeRecordFlowAdapter.java");
    }

    private static Path servicePath() {
        var path = Path.of(
                "src/main/java/com/unique/examine/module/runtime/service/RecordFlowStatusMappingService.java");
        if (Files.exists(path)) {
            return path;
        }
        return Path.of(
                "backend/examine-module/src/main/java/com/unique/examine/module/runtime/service/"
                        + "RecordFlowStatusMappingService.java");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ");
    }
}
