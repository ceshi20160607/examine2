package com.unique.examine.openapi.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiCallbackMigrationContractTest {
    @Test
    void migrationOwnsVersionedSubscriptionDedupeAttemptsAndTenantScope() throws Exception {
        var sql = Files.readString(findMigration("V8_85_0__openapi_callback_subscription.sql"));

        assertThat(sql)
                .contains("CREATE TABLE un_openapi_callback_subscription")
                .contains("CREATE TABLE un_openapi_callback_version")
                .contains("CREATE TABLE un_openapi_callback_delivery")
                .contains("CREATE TABLE un_openapi_callback_attempt")
                .contains("UNIQUE KEY uk_openapi_callback_delivery_dedupe")
                .contains("system_id, tenant_id, application_id, subscription_id")
                .contains("status IN ('ACTIVE', 'RETIRED')")
                .contains("outcome IN ('SUCCEEDED', 'RETRYABLE_FAILURE', 'TERMINAL_FAILURE')")
                .contains("secret_ref")
                .doesNotContain("secret_value")
                .doesNotContain("ON DELETE CASCADE")
                .doesNotContain("DROP TABLE");
    }

    private static Path findMigration(String name) {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql").resolve("migration").resolve(name);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate migration " + name);
    }
}
