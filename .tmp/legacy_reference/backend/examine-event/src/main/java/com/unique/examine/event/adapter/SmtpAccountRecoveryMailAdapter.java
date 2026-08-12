package com.unique.examine.event.adapter;

import com.unique.examine.core.api.AccountRecoveryMailFacade;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Synchronous, non-persisting SMTP delivery for password recovery. */
public final class SmtpAccountRecoveryMailAdapter implements AccountRecoveryMailFacade {
    static final String RESET_PATH = "/auth/password/reset";
    private static final String SUBJECT = "重置您的 examine2 密码";
    private static final DateTimeFormatter EXPIRATION =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final MailSender sender;
    private final String from;
    private final URI publicBaseUrl;
    private final Clock clock;

    public SmtpAccountRecoveryMailAdapter(
            MailSender sender,
            String from,
            URI publicBaseUrl,
            Clock clock
    ) {
        this.sender = Objects.requireNonNull(sender, "MailSender is required");
        this.from = requireText(from, "Sender address");
        this.publicBaseUrl = Objects.requireNonNull(publicBaseUrl,
                "Public base URL is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public DeliveryReceipt deliver(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Account recovery mail command is required");
        }
        if (!command.expiresAt().isAfter(clock.instant())) {
            return receipt(command, Status.FAILED);
        }
        try {
            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(command.recipientEmail());
            message.setSubject(SUBJECT);
            message.setText(body(command));
            sender.send(message);
            return receipt(command, Status.SENT);
        } catch (RuntimeException deliveryFailure) {
            // Deliberately do not log the exception, address, token, or rendered URL.
            return receipt(command, Status.FAILED);
        }
    }

    private String body(Command command) {
        var resetUrl = UriComponentsBuilder.fromUri(publicBaseUrl)
                .replacePath(RESET_PATH)
                .replaceQuery(null)
                .fragment(null)
                .queryParam("token", command.rawRecoveryToken())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return "我们收到了重置您 examine2 账号密码的请求。\n\n"
                + "请打开以下链接设置新密码：\n"
                + resetUrl + "\n\n"
                + "链接有效期至：" + EXPIRATION.format(command.expiresAt()) + "。\n"
                + "如果这不是您的操作，请忽略此邮件。";
    }

    private static DeliveryReceipt receipt(Command command, Status status) {
        return new DeliveryReceipt(command.requestId(), status);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}
