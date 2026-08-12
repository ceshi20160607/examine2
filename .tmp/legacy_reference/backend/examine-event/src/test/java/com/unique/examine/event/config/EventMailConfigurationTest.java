package com.unique.examine.event.config;

import com.unique.examine.core.api.AccountRecoveryMailFacade;
import com.unique.examine.event.adapter.SmtpAccountRecoveryMailAdapter;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventMailConfigurationTest {
    private static final AccountRecoveryMailFacade.Command COMMAND =
            new AccountRecoveryMailFacade.Command(
                    "owner@example.test", "a".repeat(32),
                    Instant.parse("2030-01-01T00:15:00Z"), "request-1");

    @Test
    void missingOrUnsafeConfigurationProvidesANonSendingFacade() {
        var configuration = new EventMailConfiguration();
        var disabled = new AccountRecoveryMailProperties();

        assertThat(configuration.accountRecoveryMailFacade(disabled).deliver(COMMAND).status())
                .isEqualTo(AccountRecoveryMailFacade.Status.UNAVAILABLE);

        var unsafe = configured();
        unsafe.setPublicBaseUrl(URI.create("http://accounts.example.test"));
        assertThat(unsafe.isReady()).isFalse();
        assertThat(configuration.accountRecoveryMailFacade(unsafe).deliver(COMMAND).status())
                .isEqualTo(AccountRecoveryMailFacade.Status.UNAVAILABLE);
    }

    @Test
    void completeTlsOrLoopbackConfigurationCreatesTheRealAdapterAndRedactsCredentials() {
        var properties = configured();
        properties.setUsername("smtp-user");
        properties.setPassword("smtp-secret");
        properties.setSmtpAuth(true);

        assertThat(properties.isReady()).isTrue();
        assertThat(properties.toString())
                .contains("password=[redacted]")
                .doesNotContain("smtp-secret")
                .doesNotContain("smtp-user");
        assertThat(new EventMailConfiguration().accountRecoveryMailFacade(properties))
                .isInstanceOf(SmtpAccountRecoveryMailAdapter.class);

        properties.setPublicBaseUrl(URI.create("http://127.0.0.1:5173"));
        properties.setSmtpAuth(false);
        assertThat(properties.isReady()).isTrue();
    }

    private static AccountRecoveryMailProperties configured() {
        var properties = new AccountRecoveryMailProperties();
        properties.setEnabled(true);
        properties.setHost("smtp.example.test");
        properties.setPort(587);
        properties.setFrom("no-reply@example.test");
        properties.setPublicBaseUrl(URI.create("https://accounts.example.test"));
        return properties;
    }
}
