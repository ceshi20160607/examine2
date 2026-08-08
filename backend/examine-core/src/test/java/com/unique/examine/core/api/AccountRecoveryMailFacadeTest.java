package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRecoveryMailFacadeTest {
    private static final String TOKEN = "a".repeat(32);

    @Test
    void freezesThePlatformScopedTransientDeliveryContract() {
        var command = new AccountRecoveryMailFacade.Command(
                "  owner@example.test  ", TOKEN,
                Instant.parse("2030-01-01T00:15:00Z"), " request-1 ");

        assertThat(command.recipientEmail()).isEqualTo("owner@example.test");
        assertThat(command.requestId()).isEqualTo("request-1");
        assertThat(Arrays.stream(AccountRecoveryMailFacade.Command.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("recipientEmail", "rawRecoveryToken", "expiresAt", "requestId")
                .doesNotContain("systemId", "tenantId", "memberId", "recoveryUrl");
        assertThat(command.toString())
                .contains("rawRecoveryToken=[redacted]")
                .doesNotContain(TOKEN)
                .doesNotContain("owner@example.test");

        var receipt = new AccountRecoveryMailFacade.DeliveryReceipt(
                command.requestId(), AccountRecoveryMailFacade.Status.SENT);
        assertThat(receipt.status()).isEqualTo(AccountRecoveryMailFacade.Status.SENT);
    }

    @Test
    void rejectsMalformedAddressesTokensAndReceipts() {
        assertThatThrownBy(() -> command("missing-at", TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("a@example.test\r\nBcc:x@example.test", TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("a@example.test", "too-short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command("a@example.test", "a".repeat(31) + "+"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRecoveryMailFacade.DeliveryReceipt(
                "request-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AccountRecoveryMailFacade.Command command(String email, String token) {
        return new AccountRecoveryMailFacade.Command(
                email, token, Instant.parse("2030-01-01T00:15:00Z"), "request-1");
    }
}
