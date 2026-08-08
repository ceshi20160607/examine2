package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountRecoveryJourneyIntegrationTest {
    private static final String OLD_PASSWORD = "Recovery-Old-Password-42!";
    private static final String NEW_PASSWORD = "Recovery-New-Password-84!";
    private static final Pattern TOKEN_LINK = Pattern.compile(
            "http://127\\.0\\.0\\.1:5173/auth/password/reset\\?token=([A-Za-z0-9_-]{43})");
    private static final SmtpCaptureServer SMTP = SmtpCaptureServer.start();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_recovery_test")
            .withUsername("examine_test")
            .withPassword("test-only-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.locations", () -> "filesystem:"
                + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.event.account-recovery-mail.enabled", () -> true);
        registry.add("examine.event.account-recovery-mail.host", () -> "127.0.0.1");
        registry.add("examine.event.account-recovery-mail.port", SMTP::port);
        registry.add("examine.event.account-recovery-mail.smtp-auth", () -> false);
        registry.add("examine.event.account-recovery-mail.start-tls", () -> false);
        registry.add("examine.event.account-recovery-mail.from", () -> "no-reply@example.test");
        registry.add("examine.event.account-recovery-mail.public-base-url",
                () -> "http://127.0.0.1:5173");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
        SMTP.clear();
    }

    @AfterAll
    static void stopSmtp() {
        SMTP.close();
    }

    @Test
    void deliversRealSmtpLinkAndResetsPasswordWithSessionRevocation() throws Exception {
        var registered = register();
        var accountId = registered.body().at("/data/account/id").asLong();
        var recoveryEmail = "recovery-owner@example.test";
        jdbc.update("update un_plat_account set email=?, email_normalized=? where id=?",
                recoveryEmail, recoveryEmail, accountId);

        var sibling = login(registered.username(), OLD_PASSWORD);
        assertThat(sibling.status()).isEqualTo(200);
        assertThat(activeSessionCount(accountId)).isEqualTo(2);

        var anonymous = new TestClient();
        var unknown = anonymous.post(
                "/api/v1/auth/password-recovery/request",
                objectMapper.writeValueAsString(Map.of("account", "unknown-account")),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        var requested = anonymous.post(
                "/api/v1/auth/password-recovery/request",
                objectMapper.writeValueAsString(Map.of("account", registered.username())),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(unknown.status()).isEqualTo(200);
        assertThat(requested.status()).isEqualTo(200);
        assertThat(unknown.body().at("/code").asText()).isEqualTo(requested.body().at("/code").asText());
        assertThat(unknown.body().at("/message").asText()).isEqualTo(requested.body().at("/message").asText());
        assertThat(unknown.body().at("/data").isNull()).isEqualTo(requested.body().at("/data").isNull());

        var message = SMTP.awaitMessage(Duration.ofSeconds(10));
        assertThat(message).contains("To: " + recoveryEmail)
                .contains("Content-Transfer-Encoding: base64")
                .doesNotContain(OLD_PASSWORD);
        var decodedBody = decodeMimeBody(message);
        assertThat(decodedBody).contains("重置您 examine2 账号密码")
                .doesNotContain(OLD_PASSWORD);
        var matcher = TOKEN_LINK.matcher(decodedBody);
        assertThat(matcher.find()).isTrue();
        var rawToken = matcher.group(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from un_plat_password_recovery_token where token_hash=?",
                Integer.class, rawToken)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from un_plat_password_recovery_token where account_id=? "
                        + "and status='ACTIVE' and char_length(token_hash)=64",
                Integer.class, accountId)).isOne();

        var reset = anonymous.post(
                "/api/v1/auth/password-recovery/reset",
                objectMapper.writeValueAsString(Map.of(
                        "token", rawToken,
                        "newPassword", NEW_PASSWORD)),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(reset.status()).isEqualTo(200);
        assertThat(reset.headers().allValues("Set-Cookie"))
                .filteredOn(value -> value.contains("Max-Age=0"))
                .anyMatch(value -> value.startsWith("EXAMINE_ACCESS="))
                .anyMatch(value -> value.startsWith("EXAMINE_REFRESH="))
                .anyMatch(value -> value.startsWith("EXAMINE_CSRF="));
        assertThat(activeSessionCount(accountId)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from un_plat_password_recovery_token where account_id=? and status='USED'",
                Integer.class, accountId)).isOne();

        assertThat(registered.client().get("/api/v1/me/context").status()).isEqualTo(401);
        assertThat(sibling.client().get("/api/v1/me/context").status()).isEqualTo(401);
        assertThat(login(registered.username(), OLD_PASSWORD).status()).isEqualTo(401);
        assertThat(login(registered.username(), NEW_PASSWORD).status()).isEqualTo(200);

        var reused = new TestClient().post(
                "/api/v1/auth/password-recovery/reset",
                objectMapper.writeValueAsString(Map.of(
                        "token", rawToken,
                        "newPassword", "Recovery-Another-Password-126!")),
                Map.of());
        assertThat(reused.status()).isEqualTo(400);
        assertThat(reused.body().at("/code").asText()).isEqualTo("PASSWORD_RECOVERY_INVALID");
        assertThat(jdbc.queryForObject(
                "select count(*) from un_audit_security where account_id=? "
                        + "and event_type like 'PASSWORD_RECOVERY_%'",
                Integer.class, accountId)).isGreaterThanOrEqualTo(3);
    }

    private Registration register() throws Exception {
        var suffix = Long.toUnsignedString(System.nanoTime(), 36);
        var username = "recovery_" + suffix;
        var body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "displayName", "Recovery Owner",
                "password", OLD_PASSWORD,
                "systemName", "Recovery System",
                "systemCode", "recovery_" + suffix));
        var client = new TestClient();
        var response = client.post(
                "/api/v1/auth/register",
                body,
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(response.status()).isEqualTo(200);
        return new Registration(client, response.body(), username);
    }

    private TestResponse login(String username, String password) throws Exception {
        var client = new TestClient();
        var response = client.post(
                "/api/v1/auth/login",
                objectMapper.writeValueAsString(Map.of("account", username, "password", password)),
                Map.of());
        return new TestResponse(response.status(), response.body(), response.headers(), client);
    }

    private int activeSessionCount(long accountId) {
        return jdbc.queryForObject(
                "select count(*) from un_plat_context_session where account_id=? and status='ACTIVE'",
                Integer.class, accountId);
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private static String decodeMimeBody(String message) {
        var boundary = message.indexOf("\n\n");
        if (boundary < 0) throw new AssertionError("SMTP message has no MIME body");
        return new String(
                Base64.getMimeDecoder().decode(message.substring(boundary + 2)),
                StandardCharsets.UTF_8);
    }

    private record Registration(TestClient client, JsonNode body, String username) {
    }

    private record TestResponse(
            int status,
            JsonNode body,
            HttpHeaders headers,
            TestClient client
    ) {
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "recovery-" + UUID.randomUUID()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(
                    response.statusCode(), objectMapper.readTree(response.body()), response.headers(), this);
        }
    }

    /** Minimal SMTP sink that captures the actual JavaMail TCP conversation. */
    private static final class SmtpCaptureServer implements AutoCloseable {
        private final ServerSocket server;
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private volatile boolean running = true;

        private SmtpCaptureServer(ServerSocket server) {
            this.server = server;
            Thread.ofPlatform().daemon().name("account-recovery-smtp-capture").start(this::serve);
        }

        static SmtpCaptureServer start() {
            try {
                return new SmtpCaptureServer(new ServerSocket(
                        0, 16, InetAddress.getLoopbackAddress()));
            } catch (IOException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }

        int port() {
            return server.getLocalPort();
        }

        void clear() {
            messages.clear();
        }

        String awaitMessage(Duration timeout) throws InterruptedException {
            var message = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (message == null) throw new AssertionError("No SMTP recovery message was received");
            return message;
        }

        private void serve() {
            while (running) {
                try (var socket = server.accept()) {
                    handle(socket);
                } catch (IOException failure) {
                    if (running) throw new IllegalStateException("SMTP capture failed", failure);
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            socket.setSoTimeout(5000);
            var reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            var writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.US_ASCII));
            reply(writer, "220 localhost ESMTP");
            String line;
            while ((line = reader.readLine()) != null) {
                var command = line.toUpperCase(java.util.Locale.ROOT);
                if (command.startsWith("EHLO")) {
                    reply(writer, "250-localhost");
                    reply(writer, "250 8BITMIME");
                } else if (command.startsWith("HELO") || command.startsWith("MAIL FROM")
                        || command.startsWith("RCPT TO") || command.startsWith("RSET")
                        || command.startsWith("NOOP")) {
                    reply(writer, "250 OK");
                } else if (command.equals("DATA")) {
                    reply(writer, "354 End data with <CR><LF>.<CR><LF>");
                    var message = new StringBuilder();
                    while ((line = reader.readLine()) != null && !".".equals(line)) {
                        message.append(line.startsWith("..") ? line.substring(1) : line).append('\n');
                    }
                    messages.add(message.toString());
                    reply(writer, "250 queued");
                } else if (command.equals("QUIT")) {
                    reply(writer, "221 bye");
                    return;
                } else {
                    reply(writer, "250 OK");
                }
            }
        }

        private static void reply(BufferedWriter writer, String line) throws IOException {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() {
            running = false;
            try {
                server.close();
            } catch (IOException ignored) {
                // Test-only loopback listener is already closed.
            }
        }
    }
}
