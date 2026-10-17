package com.unique.unexamine.authentication.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetDeliveryTest {
    @Test
    void productionCannotExposeLocalOutboxOrClaimDeliveryWithoutRelay() {
        PasswordResetDelivery local = delivery("production", "LOCAL_OUTBOX", "https://app.example.test", "", "");
        assertThatThrownBy(local::availableMode).isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).code())
                        .isEqualTo("PASSWORD_RESET_DELIVERY_UNAVAILABLE"));

        PasswordResetDelivery incomplete = delivery("production", "WEBHOOK", "https://app.example.test",
                "https://relay.example.test/reset", "");
        assertThatThrownBy(incomplete::availableMode).isInstanceOf(DomainException.class);
    }

    @Test
    void testOutboxRequiresExplicitModeAndValidWebhookRequiresHttps() {
        assertThat(delivery("development", "", "http://localhost:15173", "", "").isAvailable()).isFalse();
        assertThat(delivery("test", "LOCAL_OUTBOX", "http://localhost:15173", "", "").availableMode())
                .isEqualTo("LOCAL_OUTBOX");
        assertThatThrownBy(delivery("production", "WEBHOOK", "http://app.example.test",
                "https://relay.example.test/reset", "secret")::availableMode).isInstanceOf(DomainException.class);
        assertThat(delivery("production", "WEBHOOK", "https://app.example.test",
                "https://relay.example.test/reset", "secret").availableMode()).isEqualTo("WEBHOOK");
    }

    private PasswordResetDelivery delivery(String environment, String mode, String publicUrl,
            String webhookUrl, String webhookToken) {
        return new PasswordResetDelivery(new ObjectMapper(), environment, mode, publicUrl,
                "/tmp/password-reset-test", webhookUrl, webhookToken);
    }
}
