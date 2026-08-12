package com.unique.examine.event.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeliveryTransportPropertiesTest {
    @Test
    void smtpAcceptsOnlyBoundedSecretRefsAndNeverPrintsThem() {
        var properties = smtp();

        assertThat(properties.isReady()).isTrue();
        assertThat(properties.toString())
                .contains("usernameSecretRef=[redacted]")
                .contains("passwordSecretRef=[redacted]")
                .doesNotContain("EXAMINE_EVENT_SMTP_USER")
                .doesNotContain("EXAMINE_EVENT_SMTP_PASSWORD");

        properties.setPasswordSecretRef("literal-password");
        assertThat(properties.isReady()).isFalse();
        properties.setPasswordSecretRef("env://lowercase");
        assertThat(properties.isReady()).isFalse();
        properties.setPasswordSecretRef("file:///run/secrets/event-smtp-password");
        assertThat(properties.isReady()).isTrue();
    }

    @Test
    void smtpRejectsUnsafeHeadersTlsContradictionsAndUnboundedTimeouts() {
        var properties = smtp();
        properties.setFrom("sender@example.test\r\nBcc: stolen@example.test");
        assertThat(properties.isReady()).isFalse();

        properties = smtp();
        properties.setStartTls(false);
        assertThat(properties.isReady()).isFalse();

        properties = smtp();
        properties.setConnectTimeout(Duration.ofSeconds(31));
        assertThat(properties.isReady()).isFalse();
    }

    @Test
    void webhookRequiresEnabledBoundedNetworkTimeouts() {
        var properties = new WebhookTransportProperties();
        assertThat(properties.isReady()).isFalse();

        properties.setEnabled(true);
        assertThat(properties.isReady()).isTrue();
        properties.setRequestTimeout(Duration.ZERO);
        assertThat(properties.isReady()).isFalse();
        properties.setRequestTimeout(Duration.ofSeconds(31));
        assertThat(properties.isReady()).isFalse();
        assertThat(properties.toString()).doesNotContain("secret");
    }

    private static BusinessSmtpProperties smtp() {
        var properties = new BusinessSmtpProperties();
        properties.setEnabled(true);
        properties.setHost("smtp.example.test");
        properties.setPort(587);
        properties.setFrom("events@example.test");
        properties.setAuthentication(true);
        properties.setUsernameSecretRef("env://EXAMINE_EVENT_SMTP_USER");
        properties.setPasswordSecretRef("env://EXAMINE_EVENT_SMTP_PASSWORD");
        return properties;
    }
}
