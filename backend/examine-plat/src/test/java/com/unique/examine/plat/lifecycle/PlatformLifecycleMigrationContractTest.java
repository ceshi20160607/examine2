package com.unique.examine.plat.lifecycle;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformLifecycleMigrationContractTest {
    @Test
    void persistsOnlyEncryptedDataPayloadAndConfirmedPlanLedger() throws Exception {
        var sql = Files.readString(migration("V8_86_0__platform_system_tenant_lifecycle.sql"));

        assertThat(sql)
                .contains("payload_ciphertext LONGBLOB")
                .contains("payload_plaintext_sha256")
                .contains("encryption_key_ref")
                .contains("plan_fingerprint")
                .contains("confirmation_token_hash")
                .contains("plan_operation_id")
                .contains("'MIGRATION_PREVIEW'")
                .contains("'RECOVERY_PREVIEW'")
                .contains("payload_schema_version = 1")
                .doesNotContain("payload_plaintext LONGBLOB", "payload_json JSON");
    }

    private static Path migration(String name) {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration").resolve(name);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate " + name);
    }
}
