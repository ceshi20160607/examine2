package com.unique.examine.work.configuration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkConfigurationMigrationContractTest {
    @Test
    void migrationKeepsVersionsTenantScopedAndRuntimeValuesRevisionBound() throws Exception {
        var migration = findMigration(
                "V8_84_0__work_configuration_platform_todo.sql");
        var sql = Files.readString(migration, StandardCharsets.UTF_8)
                .toLowerCase(java.util.Locale.ROOT);

        assertThat(sql).contains(
                "create table un_work_configuration",
                "unique key uk_work_configuration_revision",
                "unique key uk_work_configuration_active",
                "create table un_work_runtime_field_value",
                "fk_work_runtime_configuration",
                "work.config.manage",
                "create table un_platform_todo_action");
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
