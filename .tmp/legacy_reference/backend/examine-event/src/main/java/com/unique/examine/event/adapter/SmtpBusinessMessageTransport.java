package com.unique.examine.event.adapter;

import com.unique.examine.event.config.BusinessSmtpProperties;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.EventChannelTargetDirectory;
import com.unique.examine.event.port.EventChannelTransport;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Real SMTP transport for rendered business notifications. */
public final class SmtpBusinessMessageTransport implements EventChannelTransport {
    private static final String MASKED_UNKNOWN = "email:[redacted]";

    private final BusinessSmtpProperties properties;
    private final EventChannelTargetDirectory targets;
    private final SecretResolverFacade secrets;
    private final LongSupplier nanoTime;
    private final Supplier<String> traceIds;

    public SmtpBusinessMessageTransport(
            BusinessSmtpProperties properties,
            EventChannelTargetDirectory targets,
            SecretResolverFacade secrets
    ) {
        this(properties, targets, secrets, System::nanoTime,
                () -> UUID.randomUUID().toString());
    }

    SmtpBusinessMessageTransport(
            BusinessSmtpProperties properties,
            EventChannelTargetDirectory targets,
            SecretResolverFacade secrets,
            LongSupplier nanoTime,
            Supplier<String> traceIds
    ) {
        this.properties = Objects.requireNonNull(properties, "SMTP properties are required");
        this.targets = Objects.requireNonNull(targets, "Channel target directory is required");
        this.secrets = Objects.requireNonNull(secrets, "SecretRef resolver is required");
        this.nanoTime = Objects.requireNonNull(nanoTime, "Monotonic clock is required");
        this.traceIds = Objects.requireNonNull(traceIds, "Trace id supplier is required");
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.EMAIL;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        var started = nanoTime.getAsLong();
        var traceId = safeTraceId();
        if (!valid(command)) {
            return failure(Status.PERMANENT_FAILURE, "EMAIL_COMMAND_INVALID",
                    MASKED_UNKNOWN, started, traceId);
        }
        if (!properties.isReady()) {
            return failure(Status.PERMANENT_FAILURE, "EMAIL_TRANSPORT_UNAVAILABLE",
                    MASKED_UNKNOWN, started, traceId);
        }

        final EventChannelTargetDirectory.Target target;
        try {
            target = targets.resolve(channel(), command.systemId(), command.tenantId(),
                    command.recipientMemberId()).orElse(null);
        } catch (RuntimeException unavailable) {
            return failure(Status.TEMPORARY_FAILURE, "EMAIL_TARGET_LOOKUP_FAILED",
                    MASKED_UNKNOWN, started, traceId);
        }
        if (target == null || !validEmail(target.recipient())) {
            return failure(Status.PERMANENT_FAILURE, "EMAIL_RECIPIENT_UNAVAILABLE",
                    MASKED_UNKNOWN, started, traceId);
        }

        var destination = maskEmail(target.recipient());
        SecretResolverFacade.ResolvedSecret usernameSecret = null;
        SecretResolverFacade.ResolvedSecret passwordSecret = null;
        byte[] username = null;
        byte[] password = null;
        JavaMailSenderImpl sender = null;
        try {
            if (properties.isAuthentication()) {
                usernameSecret = resolve(command, properties.getUsernameSecretRef());
                passwordSecret = resolve(command, properties.getPasswordSecretRef());
                username = usernameSecret.copyBytes();
                password = passwordSecret.copyBytes();
            }
            sender = sender(username, password);
            var message = new SimpleMailMessage();
            message.setFrom(properties.getFrom());
            message.setTo(target.recipient().strip());
            message.setSubject(command.subject());
            message.setText(command.body());
            sender.send(message);
            return new DeliveryResult(Status.SENT, null, destination,
                    elapsedMillis(started), traceId);
        } catch (SecretUnavailableException unavailable) {
            return failure(Status.PERMANENT_FAILURE, "EMAIL_SECRET_UNAVAILABLE",
                    destination, started, traceId);
        } catch (RuntimeException deliveryFailure) {
            var classified = classify(deliveryFailure);
            return failure(classified.status(), classified.code(), destination, started, traceId);
        } finally {
            if (sender != null) {
                sender.setUsername(null);
                sender.setPassword(null);
            }
            if (usernameSecret != null) usernameSecret.close();
            if (passwordSecret != null) passwordSecret.close();
            wipe(username);
            wipe(password);
        }
    }

    private SecretResolverFacade.ResolvedSecret resolve(
            DeliveryCommand command, String secretRef
    ) {
        try {
            return secrets.resolve(new SecretResolverFacade.SecretRequest(
                    command.systemId(), command.tenantId(), secretRef))
                    .orElseThrow(SecretUnavailableException::new);
        } catch (RuntimeException unavailable) {
            throw new SecretUnavailableException();
        }
    }

    private JavaMailSenderImpl sender(byte[] username, byte[] password) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (properties.isAuthentication()) {
            sender.setUsername(new String(username, StandardCharsets.UTF_8));
            sender.setPassword(new String(password, StandardCharsets.UTF_8));
        }
        var mail = sender.getJavaMailProperties();
        mail.setProperty("mail.smtp.auth", Boolean.toString(properties.isAuthentication()));
        mail.setProperty("mail.smtp.starttls.enable", Boolean.toString(properties.isStartTls()));
        mail.setProperty("mail.smtp.starttls.required",
                Boolean.toString(properties.isStartTlsRequired()));
        mail.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        mail.setProperty("mail.smtp.connectiontimeout", millis(properties.getConnectTimeout()));
        mail.setProperty("mail.smtp.timeout", millis(properties.getReadTimeout()));
        mail.setProperty("mail.smtp.writetimeout", millis(properties.getWriteTimeout()));
        mail.setProperty("mail.debug", "false");
        return sender;
    }

    private static Classification classify(Throwable failure) {
        if (contains(failure, MailAuthenticationException.class)) {
            return permanent("EMAIL_AUTH_REJECTED");
        }
        if (contains(failure, MailParseException.class)
                || contains(failure, MailPreparationException.class)) {
            return permanent("EMAIL_MESSAGE_INVALID");
        }
        var smtpCode = smtpReturnCode(failure);
        if (smtpCode >= 400 && smtpCode < 500) {
            return temporary("EMAIL_PROVIDER_TEMPORARY");
        }
        if (smtpCode >= 500) {
            return permanent("EMAIL_PROVIDER_REJECTED");
        }
        if (contains(failure, SocketTimeoutException.class)
                || contains(failure, ConnectException.class)) {
            return temporary("EMAIL_PROVIDER_UNAVAILABLE");
        }
        if (contains(failure, MailSendException.class)
                || contains(failure, MailException.class)) {
            return temporary("EMAIL_DELIVERY_FAILED");
        }
        return temporary("EMAIL_DELIVERY_FAILED");
    }

    private static int smtpReturnCode(Throwable failure) {
        var current = failure;
        for (var depth = 0; current != null && depth < 20; depth++) {
            if ("SMTPAddressFailedException".equals(current.getClass().getSimpleName())
                    || "SMTPSendFailedException".equals(current.getClass().getSimpleName())) {
                try {
                    Method getter = current.getClass().getMethod("getReturnCode");
                    var value = getter.invoke(current);
                    if (value instanceof Integer code) return code;
                } catch (ReflectiveOperationException ignored) {
                    return -1;
                }
            }
            current = next(current);
        }
        return -1;
    }

    private static boolean contains(Throwable failure, Class<? extends Throwable> type) {
        var current = failure;
        for (var depth = 0; current != null && depth < 20; depth++) {
            if (type.isInstance(current)) return true;
            current = next(current);
        }
        return false;
    }

    private static Throwable next(Throwable value) {
        if (value instanceof MailSendException send && send.getMessageExceptions().length > 0) {
            return send.getMessageExceptions()[0];
        }
        try {
            var getter = value.getClass().getMethod("getNextException");
            var next = getter.invoke(value);
            if (next instanceof Throwable throwable && throwable != value) return throwable;
        } catch (ReflectiveOperationException ignored) {
            // Not a Jakarta Mail chained exception.
        }
        return value.getCause() == value ? null : value.getCause();
    }

    private DeliveryResult failure(Status status, String code, String destination,
                                   long started, String traceId) {
        return new DeliveryResult(status, code, destination, elapsedMillis(started), traceId);
    }

    private long elapsedMillis(long started) {
        return Math.max(0, Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - started)).toMillis());
    }

    private String safeTraceId() {
        var value = traceIds.get();
        return value == null || value.isBlank() || hasLineBreak(value)
                ? UUID.randomUUID().toString() : value.strip();
    }

    private static boolean valid(DeliveryCommand command) {
        return command != null && command.deliveryId() > 0 && command.systemId() > 0
                && command.tenantId() > 0 && command.recipientMemberId() > 0
                && bounded(command.templateCode(), 100, false)
                && bounded(command.dedupeKey(), 256, false)
                && bounded(command.subject(), 998, false)
                && bounded(command.body(), 1_048_576, true);
    }

    private static boolean bounded(String value, int max, boolean multiline) {
        return value != null && !value.isBlank() && value.length() <= max
                && (multiline || !hasLineBreak(value));
    }

    private static boolean validEmail(String value) {
        if (value == null || value.isBlank() || value.length() > 254 || hasLineBreak(value)) {
            return false;
        }
        var at = value.indexOf('@');
        return at > 0 && at == value.lastIndexOf('@') && at < value.length() - 1;
    }

    private static String maskEmail(String value) {
        var stripped = value.strip();
        var at = stripped.indexOf('@');
        return stripped.substring(0, 1) + "***" + stripped.substring(at);
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static String millis(Duration value) {
        return Long.toString(value.toMillis());
    }

    private static void wipe(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }

    private static Classification temporary(String code) {
        return new Classification(Status.TEMPORARY_FAILURE, code);
    }

    private static Classification permanent(String code) {
        return new Classification(Status.PERMANENT_FAILURE, code);
    }

    private record Classification(Status status, String code) {
    }

    private static final class SecretUnavailableException extends RuntimeException {
    }
}
