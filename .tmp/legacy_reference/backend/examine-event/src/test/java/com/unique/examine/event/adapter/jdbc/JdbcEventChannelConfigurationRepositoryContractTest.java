package com.unique.examine.event.adapter.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEventChannelConfigurationRepositoryContractTest {
    @Test
    void readsAndMutatesOnlyTheRequestedSystemAndChannel() {
        assertThat(JdbcEventChannelConfigurationRepository.FIND_SQL)
                .contains("system_id = ?", "channel = ?");
        assertThat(JdbcEventChannelConfigurationRepository.LIST_SQL)
                .contains("system_id = ?")
                .contains("FIELD(channel, 'EMAIL', 'WEBHOOK')");
        assertThat(JdbcEventChannelConfigurationRepository.UPDATE_SQL)
                .contains("system_id = ?", "channel = ?", "version = ?");
        assertThat(JdbcEventChannelConfigurationRepository.RECORD_CHECK_SQL)
                .contains("system_id = ?", "channel = ?")
                .doesNotContain("secret_ref =");
    }

    @Test
    void listProjectionSeparatesSecretReferencesFromConnectivityEvidence() {
        assertThat(JdbcEventChannelConfigurationRepository.COLUMNS)
                .contains("secret_ref", "last_check_trace_id", "last_check_duration_ms")
                .doesNotContain("secret_value", "response_body");
    }
}
