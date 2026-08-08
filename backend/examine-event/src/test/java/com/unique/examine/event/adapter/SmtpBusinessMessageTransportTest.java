package com.unique.examine.event.adapter;

import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.event.config.BusinessSmtpProperties;
import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.EventChannelTargetDirectory;
import com.unique.examine.event.port.EventChannelTransport;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpBusinessMessageTransportTest {
    @Test
    void deliversRenderedBusinessMailOverARealSmtpConversation() throws Exception {
        try (var smtp = new SmtpServer(250)) {
            var transport = transport(smtp.port(), request -> Optional.empty());

            var result = transport.deliver(command());

            assertThat(result.status()).isEqualTo(EventChannelTransport.Status.SENT);
            assertThat(result.failureCode()).isNull();
            assertThat(result.maskedDestination()).isEqualTo("o***@example.test");
            assertThat(result.traceId()).isEqualTo("trace-email-1");
            assertThat(result.toString()).doesNotContain("owner@example.test");
            assertThat(smtp.awaitMessage()).satisfies(message -> {
                assertThat(message).contains("From: events@example.test");
                assertThat(message).contains("To: owner@example.test");
                assertThat(message).contains("Content-Type: text/plain");
            });
            assertThat(smtp.commands()).anyMatch(value -> value.startsWith("EHLO"));
        }
    }

    @Test
    void classifiesRealSmtpFourAndFiveHundredRecipientFailures() throws Exception {
        try (var temporary = new SmtpServer(450)) {
            var result = transport(temporary.port(), request -> Optional.empty()).deliver(command());
            assertThat(result.status()).isEqualTo(EventChannelTransport.Status.TEMPORARY_FAILURE);
            assertThat(result.failureCode()).isEqualTo("EMAIL_PROVIDER_TEMPORARY");
        }
        try (var permanent = new SmtpServer(550)) {
            var result = transport(permanent.port(), request -> Optional.empty()).deliver(command());
            assertThat(result.status()).isEqualTo(EventChannelTransport.Status.PERMANENT_FAILURE);
            assertThat(result.failureCode()).isEqualTo("EMAIL_PROVIDER_REJECTED");
        }
    }

    @Test
    void resolvesScopedCredentialRefsAndClearsSecretMaterial() {
        var properties = properties(25_252);
        properties.setAuthentication(true);
        properties.setUsernameSecretRef("env://EXAMINE_EVENT_SMTP_USER");
        properties.setPasswordSecretRef("env://EXAMINE_EVENT_SMTP_PASSWORD");
        var captured = new ArrayList<SecretResolverFacade.SecretRequest>();
        var materials = new ArrayList<SecretResolverFacade.ResolvedSecret>();
        SecretResolverFacade secrets = request -> {
            captured.add(request);
            var value = SecretResolverFacade.ResolvedSecret.utf8(
                    request.reference().contains("USER") ? "smtp-user" : "smtp-password");
            materials.add(value);
            return Optional.of(value);
        };
        var transport = new SmtpBusinessMessageTransport(
                properties, targets("owner@example.test", null, null), secrets,
                new Ticker(), () -> "trace-secret");

        var result = transport.deliver(command());

        assertThat(result.status()).isEqualTo(EventChannelTransport.Status.TEMPORARY_FAILURE);
        assertThat(result.failureCode()).isEqualTo("EMAIL_PROVIDER_UNAVAILABLE");
        assertThat(captured).extracting(SecretResolverFacade.SecretRequest::systemId)
                .containsOnly(7L);
        assertThat(captured).extracting(SecretResolverFacade.SecretRequest::tenantId)
                .containsOnly(9L);
        assertThat(captured).extracting(SecretResolverFacade.SecretRequest::reference)
                .containsExactly("env://EXAMINE_EVENT_SMTP_USER",
                        "env://EXAMINE_EVENT_SMTP_PASSWORD");
        assertThat(materials).allSatisfy(material ->
                assertThatThrownBy(material::copyBytes)
                        .isInstanceOf(IllegalStateException.class));
        assertThat(result.toString()).doesNotContain("smtp-password").doesNotContain("smtp-user");
    }

    @Test
    void missingTargetOrSecretIsPermanentAndLookupFailureIsTemporary() {
        var missingTarget = new SmtpBusinessMessageTransport(
                unauthenticatedProperties(), (channel, system, tenant, member) -> Optional.empty(),
                request -> Optional.empty(), new Ticker(), () -> "trace-1");
        assertThat(missingTarget.deliver(command()).failureCode())
                .isEqualTo("EMAIL_RECIPIENT_UNAVAILABLE");

        var lookupFailure = new SmtpBusinessMessageTransport(
                unauthenticatedProperties(), (channel, system, tenant, member) -> {
                    throw new IllegalStateException("sensitive database failure");
                }, request -> Optional.empty(), new Ticker(), () -> "trace-2");
        var result = lookupFailure.deliver(command());
        assertThat(result.status()).isEqualTo(EventChannelTransport.Status.TEMPORARY_FAILURE);
        assertThat(result.failureCode()).isEqualTo("EMAIL_TARGET_LOOKUP_FAILED");
        assertThat(result.toString()).doesNotContain("sensitive");

        var authenticated = properties(25_252);
        var secretMissing = new SmtpBusinessMessageTransport(
                authenticated, targets("owner@example.test", null, null),
                request -> Optional.empty(), new Ticker(), () -> "trace-3");
        assertThat(secretMissing.deliver(command()).failureCode())
                .isEqualTo("EMAIL_SECRET_UNAVAILABLE");
    }

    private static SmtpBusinessMessageTransport transport(
            int port, SecretResolverFacade secrets
    ) {
        return new SmtpBusinessMessageTransport(
                unauthenticatedProperties(port), targets("owner@example.test", null, null),
                secrets, new Ticker(), () -> "trace-email-1");
    }

    private static BusinessSmtpProperties unauthenticatedProperties() {
        return unauthenticatedProperties(25_252);
    }

    private static BusinessSmtpProperties unauthenticatedProperties(int port) {
        var properties = properties(port);
        properties.setAuthentication(false);
        return properties;
    }

    private static BusinessSmtpProperties properties(int port) {
        var properties = new BusinessSmtpProperties();
        properties.setEnabled(true);
        properties.setHost("127.0.0.1");
        properties.setPort(port);
        properties.setFrom("events@example.test");
        properties.setAuthentication(true);
        properties.setUsernameSecretRef("env://EXAMINE_EVENT_SMTP_USER");
        properties.setPasswordSecretRef("env://EXAMINE_EVENT_SMTP_PASSWORD");
        properties.setStartTls(false);
        properties.setStartTlsRequired(false);
        properties.setConnectTimeout(Duration.ofMillis(300));
        properties.setReadTimeout(Duration.ofSeconds(1));
        properties.setWriteTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private static EventChannelTargetDirectory targets(
            String recipient, java.net.URI endpoint, String secretRef
    ) {
        return (channel, system, tenant, member) -> Optional.of(
                new EventChannelTargetDirectory.Target(recipient, endpoint, secretRef, null));
    }

    private static EventChannelTransport.DeliveryCommand command() {
        return new EventChannelTransport.DeliveryCommand(
                101, 7, 9, 11, "TASK_ASSIGNED", "task:42:assigned",
                "任务已分派", "任务 42 已分派给您。", "TASK", "42", "/tasks/42",
                Map.of("taskName", "季度复盘"));
    }

    private static final class Ticker implements java.util.function.LongSupplier {
        private long value;

        @Override
        public long getAsLong() {
            value += 1_000_000;
            return value;
        }
    }

    private static final class SmtpServer implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;
        private final int recipientCode;
        private final List<String> commands = java.util.Collections.synchronizedList(
                new ArrayList<>());
        private final AtomicReference<String> message = new AtomicReference<>();
        private final CountDownLatch messageReceived = new CountDownLatch(1);

        private SmtpServer(int recipientCode) throws IOException {
            this.recipientCode = recipientCode;
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            thread = Thread.ofPlatform().daemon().name("smtp-test-server").start(this::serve);
        }

        private int port() {
            return server.getLocalPort();
        }

        private List<String> commands() {
            return List.copyOf(commands);
        }

        private String awaitMessage() throws InterruptedException {
            assertThat(messageReceived.await(2, TimeUnit.SECONDS)).isTrue();
            return message.get();
        }

        private void serve() {
            try (Socket socket = server.accept();
                 var reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.US_ASCII));
                 var writer = new BufferedWriter(new OutputStreamWriter(
                         socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                reply(writer, "220 smtp.test ESMTP ready");
                String line;
                while ((line = reader.readLine()) != null) {
                    commands.add(line);
                    if (line.startsWith("EHLO")) {
                        reply(writer, "250-smtp.test");
                        reply(writer, "250 8BITMIME");
                    } else if (line.startsWith("HELO") || line.startsWith("MAIL FROM")) {
                        reply(writer, "250 ok");
                    } else if (line.startsWith("RCPT TO")) {
                        reply(writer, recipientCode + (recipientCode < 500
                                ? " temporary recipient failure" : " recipient rejected"));
                    } else if (line.equals("DATA")) {
                        reply(writer, "354 end with dot");
                        var data = new StringBuilder();
                        while ((line = reader.readLine()) != null && !line.equals(".")) {
                            data.append(line).append('\n');
                        }
                        message.set(data.toString());
                        messageReceived.countDown();
                        reply(writer, "250 queued");
                    } else if (line.equals("RSET")) {
                        reply(writer, "250 reset");
                    } else if (line.equals("QUIT")) {
                        reply(writer, "221 bye");
                        return;
                    } else {
                        reply(writer, "250 ok");
                    }
                }
            } catch (IOException ignored) {
                // Closing the test server terminates the protocol loop.
            }
        }

        private static void reply(BufferedWriter writer, String value) throws IOException {
            writer.write(value);
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(1_000);
        }
    }
}
