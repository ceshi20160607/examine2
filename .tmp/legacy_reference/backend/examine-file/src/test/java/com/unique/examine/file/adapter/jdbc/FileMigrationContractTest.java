package com.unique.examine.file.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileMigrationContractTest {
    @Test
    void migrationFreezesScopeVersionUniquenessIndexesAndReferenceSafety() throws Exception {
        var sql = Files.readString(findMigration("V8_0_0__file_object.sql"));
        assertThat(sql)
                .contains("CREATE TABLE un_file_object")
                .contains("CREATE TABLE un_file_reference")
                .contains("UNIQUE KEY uk_file_object_scope_id (system_id, tenant_id, id)")
                .contains("UNIQUE KEY uk_file_object_key (object_key)")
                .contains("PRIMARY KEY (system_id, tenant_id, file_id, target_type, target_id)")
                .contains("FOREIGN KEY (system_id, tenant_id, file_id)")
                .contains("ON DELETE RESTRICT")
                .contains("chk_file_object_status CHECK (status = 'ACTIVE')")
                .contains("chk_file_object_version CHECK (version > 0)")
                .contains("idx_file_reference_target");
    }

    @Test
    void listIndexMatchesTheScopedStatusAndStableCreatedOrder() throws Exception {
        var sql = Files.readString(findMigration("V8_80_0__file_center_list_index.sql"));
        assertThat(sql)
                .contains("ALTER TABLE un_file_object")
                .contains("idx_file_object_scope_status_created")
                .contains("system_id, tenant_id, status, created_at, id");
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
