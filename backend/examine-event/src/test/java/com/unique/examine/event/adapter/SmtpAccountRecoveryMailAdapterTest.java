package com.unique.examine.event.adapter;

import com.unique.examine.core.api.AccountRecoveryMailFacade;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpAccountRecoveryMailAdapterTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String TOKEN = "t".repeat(32);

    @Test
    void sendsOneFixedPlainTextRecoveryMessageOnTheTrustedOrigin() {
        var sender = new CapturingMailSender();
        var adapter = adapter(sender);

        var receipt = adapter.deliver(command(NOW.plusSeconds(900)));

        assertThat(receipt).isEqualTo(new AccountRecoveryMailFacade.DeliveryReceipt(
                "request-7", AccountRecoveryMailFacade.Status.SENT));
        assertThat(sender.messages).singleElement().satisfies(message -> {
            assertThat(message.getFrom()).isEqualTo("no-reply@example.test");
            assertThat(message.getTo()).containsExactly("owner@example.test");
            assertThat(message.getSubject()).isEqualTo("重置您的 examine2 密码");
            assertThat(message.getText())
                    .contains("https://accounts.example.test/auth/password/reset?token=" + TOKEN)
                    .contains("2030-01-01 00:15 UTC")
                    .doesNotContain("ignored-base-path");
        });
        assertThat(receipt.toString()).doesNotContain(TOKEN);
    }

    @Test
    void returnsFailedWithoutThrowingOrRetryingWhenSmtpFails() {
        var sender = new CapturingMailSender();
        sender.failure = new TestMailException();

        var receipt = adapter(sender).deliver(command(NOW.plusSeconds(900)));

        assertThat(receipt.status()).isEqualTo(AccountRecoveryMailFacade.Status.FAILED);
        assertThat(sender.attempts).isOne();
    }

    @Test
    void doesNotSendAnAlreadyExpiredRecoveryToken() {
        var sender = new CapturingMailSender();

        var receipt = adapter(sender).deliver(command(NOW));

        assertThat(receipt.status()).isEqualTo(AccountRecoveryMailFacade.Status.FAILED);
        assertThat(sender.attempts).isZero();
        assertThat(sender.messages).isEmpty();
    }

    private static SmtpAccountRecoveryMailAdapter adapter(MailSender sender) {
        return new SmtpAccountRecoveryMailAdapter(
                sender,
                "no-reply@example.test",
                URI.create("https://accounts.example.test/ignored-base-path"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AccountRecoveryMailFacade.Command command(Instant expiresAt) {
        return new AccountRecoveryMailFacade.Command(
                "owner@example.test", TOKEN, expiresAt, "request-7");
    }

    private static final class CapturingMailSender implements MailSender {
        private final List<SimpleMailMessage> messages = new ArrayList<>();
        private RuntimeException failure;
        private int attempts;

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            attempts++;
            if (failure != null) throw failure;
            messages.add(new SimpleMailMessage(simpleMessage));
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            for (var message : simpleMessages) send(message);
        }
    }

    @SuppressWarnings("serial")
    private static final class TestMailException extends MailException {
        private TestMailException() {
            super("simulated SMTP failure containing sensitive provider details");
        }
    }
}
