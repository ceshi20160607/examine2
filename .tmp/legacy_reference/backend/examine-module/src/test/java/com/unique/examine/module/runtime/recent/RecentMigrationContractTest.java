package com.unique.examine.module.runtime.recent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RecentMigrationContractTest {
    @Test
    void freezesOwnerUniquenessOrderingAndCanonicalAnchors() throws Exception {
        var sql = normalize(Files.readString(findMigration()));

        assertThat(sql).contains("create table un_module_recent");
        assertThat(sql).contains("unique key uk_recent_record");
        assertThat(sql).contains(
                "system_id, tenant_id, member_id, logical_module_id, record_id");
        assertThat(sql).contains("key idx_recent_member");
        assertThat(sql).contains(
                "system_id, tenant_id, member_id, last_accessed_at desc, id desc");
        assertThat(sql).contains("constraint fk_recent_module foreign key");
        assertThat(sql).contains("references un_module_runtime_schema_module");
        assertThat(sql).contains("constraint fk_recent_record foreign key");
        assertThat(sql).contains("references un_module_record");
        assertThat(sql).contains("constraint fk_recent_member foreign key");
        assertThat(sql).contains("constraint ck_recent_access_count check (access_count > 0)");
        assertThat(sql).doesNotContain("runtime.recent");
    }

    private static Path findMigration() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration/V8_6_0__module_recent_record.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate V8_6_0__module_recent_record.sql");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
