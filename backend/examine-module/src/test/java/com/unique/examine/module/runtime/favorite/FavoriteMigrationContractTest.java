package com.unique.examine.module.runtime.favorite;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteMigrationContractTest {
    @Test
    void freezesNullableSafeUniquenessAnchorsPermissionGrantAndEpochInvalidation() throws Exception {
        var sql = normalize(Files.readString(findMigration()));

        assertThat(sql).contains("create table un_module_favorite");
        assertThat(sql).contains(
                "target_record_key bigint generated always as (coalesce(record_id, 0)) stored");
        assertThat(sql).contains("unique key uk_favorite_target");
        assertThat(sql).contains("logical_module_id, target_record_key, active_marker");
        assertThat(sql).contains("constraint fk_favorite_module foreign key");
        assertThat(sql).contains("references un_module_runtime_schema_module");
        assertThat(sql).contains("constraint fk_favorite_record foreign key");
        assertThat(sql).contains("references un_module_record");
        assertThat(sql).contains("constraint fk_favorite_member foreign key");
        assertThat(sql).contains("'runtime.favorite.manage'");
        assertThat(sql).contains("role_row.role_type = 'root'");
        assertThat(sql).contains("update un_plat_authz_epoch");
        assertThat(sql).contains("set system_row.permission_version = epoch_row.epoch");
    }

    private static Path findMigration() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration/V8_5_0__module_favorite.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate V8_5_0__module_favorite.sql");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
