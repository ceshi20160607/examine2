package com.unique.unexamine.operations.manage;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StartupPreflightChecksTest {
    @Test
    void missingDependenciesHaveSpecificReasonsWithoutSecretValues() {
        StartupPreflightChecks checks = new StartupPreflightChecks(null, null, null, "", "", false);
        Map<String, StartupPreflightChecks.Check> result = checks.inspect().stream()
                .collect(Collectors.toMap(StartupPreflightChecks.Check::code, item -> item));

        assertThat(result.get("DATABASE").message()).contains("数据库不可连接");
        assertThat(result.get("REDIS").message()).contains("Redis 不可连接");
        assertThat(result.get("FILE_STORAGE").message()).contains("APP_FILE_STORAGE_ROOT");
        assertThat(result.get("APPLICATION_SECRET").message()).contains("APP_APPLICATION_SECRET_MASTER_KEY");
        assertThat(result.values()).allSatisfy(item -> {
            assertThat(item.status()).isEqualTo("FAILED");
            assertThat(item.critical()).isTrue();
            assertThat(item.message()).doesNotContain("password=").doesNotContain("secret=");
        });
    }

    @Test
    void productionRejectsDevelopmentSecretWithoutExposingIt() {
        StartupPreflightChecks checks = new StartupPreflightChecks(
                null, null, null, "./data/files", StartupPreflightChecks.DEVELOPMENT_SECRET, false);
        StartupPreflightChecks.Check secret = checks.inspect().stream()
                .filter(item -> "APPLICATION_SECRET".equals(item.code())).findFirst().orElseThrow();
        assertThat(secret.status()).isEqualTo("FAILED");
        assertThat(secret.message()).contains("开发默认值").doesNotContain(StartupPreflightChecks.DEVELOPMENT_SECRET);
    }
}
