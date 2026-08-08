package com.unique.examine.module.runtime.flow;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRecordFlowProjectionContractTest {

    @Test
    void jdbcStoreUsesScopedLocksAndOptimisticMonotonicTransitions() throws Exception {
        var source = normalize(Files.readString(sourcePath()));

        assertThat(source)
                .contains("where r.system_id=? and r.tenant_id=? and r.record_id=? and m.module_code=? for update")
                .contains("where system_id=? and tenant_id=? and record_id=? for update")
                .contains("where system_id=? and tenant_id=? and instance_id=? for update")
                .contains("insert into un_module_record_flow_state")
                .contains("set logical_module_id=?,instance_id=?,status='pending',version=version+1")
                .contains("set status=?,version=version+1")
                .contains("and instance_id=? and status='pending' and version=?")
                .contains("from un_module_record_flow_state_item")
                .contains("insert into un_module_record_flow_state_item")
                .contains("update un_module_record_flow_state_item")
                .contains("and record_id=? and status='pending' order by instance_id asc for update")
                .contains("order by created_at asc,instance_id asc")
                .doesNotContain("status='pending',version=0, created_at");
    }

    private static Path sourcePath() {
        var path = Path.of(
                "src/main/java/com/unique/examine/module/runtime/flow/JdbcRecordFlowProjectionStore.java");
        if (Files.exists(path)) {
            return path;
        }
        return Path.of(
                "backend/examine-module/src/main/java/com/unique/examine/module/runtime/flow/"
                        + "JdbcRecordFlowProjectionStore.java");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ");
    }
}
