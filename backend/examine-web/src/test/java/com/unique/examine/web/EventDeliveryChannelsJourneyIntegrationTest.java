package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EventDeliveryChannelsJourneyIntegrationTest.TestSecrets.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventDeliveryChannelsJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "event_delivery_root";
    private static final String ROOT_PASSWORD = "Event-Delivery-Root-Password-111!";
    private static final String WEBHOOK_SECRET_REF = "env://EVENT_DELIVERY_WEBHOOK_V1";
    private static final String WEBHOOK_SECRET = "event-delivery-test-signing-key-111";
    private static final SmtpCaptureServer SMTP = SmtpCaptureServer.start();
    private static final WebhookCaptureServer WEBHOOK = WebhookCaptureServer.start();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_event_delivery_test")
            .withUsername("examine_event_delivery_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

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
        registry.add("examine.bootstrap.root.username", () -> ROOT_USERNAME);
        registry.add("examine.bootstrap.root.password", () -> ROOT_PASSWORD);
        registry.add("examine.bootstrap.root.display-name", () -> "Event Delivery Test Root");
        registry.add("examine.event.delivery.smtp.enabled", () -> true);
        registry.add("examine.event.delivery.smtp.host", () -> "127.0.0.1");
        registry.add("examine.event.delivery.smtp.port", SMTP::port);
        registry.add("examine.event.delivery.smtp.from", () -> "event@example.test");
        registry.add("examine.event.delivery.smtp.authentication", () -> false);
        registry.add("examine.event.delivery.smtp.start-tls", () -> false);
        registry.add("examine.event.delivery.smtp.start-tls-required", () -> false);
        registry.add("examine.event.delivery.webhook.enabled", () -> true);
        registry.add("examine.event.delivery.webhook.allow-loopback-http-for-testing", () -> true);
        registry.add("examine.event.delivery.webhook.connect-timeout", () -> "2s");
        registry.add("examine.event.delivery.webhook.request-timeout", () -> "3s");
        registry.add("examine.work.task-reminder.scheduler.enabled", () -> false);
    }

    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ResultNotificationFacade notifications;

    @AfterAll
    static void closeServers() {
        SMTP.close();
        WEBHOOK.close();
    }

    @Test
    void deliversIndependentInboxEmailAndSignedWebhookWithRedactedAdminEvidence() throws Exception {
        var client = login();
        assertOk(client.post("/api/v1/context/platform:switch", "{}"));
        var system = client.post("/api/v1/platform/admin/systems", json(Map.of(
                "code", "event_delivery_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Event Delivery Journey", "description", "Three real channels",
                "tenantMode", "MULTI")), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = system.body().at("/data/id").asLong();
        var switched = client.post("/api/v1/context/systems/" + systemId + ":switch", "{}");
        assertOk(switched);
        var tenantId = switched.body().at("/data/context/tenantId").asLong();
        var memberId = switched.body().at("/data/context/memberId").asLong();
        var recipient = "event-owner@example.test";
        jdbc.update("""
                UPDATE un_plat_account account
                  JOIN un_plat_member member ON member.account_id=account.id
                   SET account.email=?, account.email_normalized=?
                 WHERE member.system_id=? AND member.id=?
                """, recipient, recipient, systemId, memberId);
        assertOk(client.post("/api/v1/auth/refresh", "{}"));

        var templatesRoot = "/api/v1/systems/" + systemId + "/admin/event/message-templates";
        var templates = client.get(templatesRoot);
        assertOk(templates);
        var current = find(templates.body().at("/data"), "templateCode", "MODULE_EXPORT_SUCCEEDED");
        var updated = client.put(templatesRoot + "/MODULE_EXPORT_SUCCEEDED", json(Map.of(
                "expectedVersion", current.path("version").asLong(),
                "name", "Export result", "enabled", true,
                "titleTemplate", "Export ready",
                "bodyTemplate", "{moduleCode} exported {rows} rows.",
                "channels", List.of("WEBHOOK", "INBOX", "EMAIL"))));
        assertOk(updated);
        assertThat(updated.body().at("/data/channels")).extracting(JsonNode::asText)
                .containsExactly("INBOX", "EMAIL", "WEBHOOK");
        assertOk(client.post(templatesRoot + "/MODULE_EXPORT_SUCCEEDED:publish", json(Map.of(
                "expectedVersion", updated.body().at("/data/version").asLong()))));

        var channelsRoot = "/api/v1/systems/" + systemId + "/event/channels";
        var initialChannels = client.get(channelsRoot);
        assertOk(initialChannels);
        assertThat(initialChannels.body().at("/data")).extracting(node -> node.path("channel").asText())
                .containsExactly("INBOX", "EMAIL", "WEBHOOK");
        var email = client.put(channelsRoot + "/EMAIL", json(Map.of(
                "enabled", true, "expectedVersion", 0)));
        assertOk(email);
        var webhook = client.put(channelsRoot + "/WEBHOOK", json(Map.of(
                "enabled", true, "endpoint", WEBHOOK.endpoint(), "secretRef", WEBHOOK_SECRET_REF,
                "timeoutMs", 2500, "expectedVersion", 0)));
        assertOk(webhook);
        assertThat(webhook.body().toString())
                .contains("env://********", "127.0.0.1")
                .doesNotContain(WEBHOOK_SECRET_REF, WEBHOOK_SECRET, "/event-delivery?", "responseBody");

        assertOk(client.post(channelsRoot + "/EMAIL:check", "{}"));
        assertThat(SMTP.await(Duration.ofSeconds(5))).contains("To: " + recipient);
        assertOk(client.post(channelsRoot + "/WEBHOOK:check", "{}"));
        assertSigned(WEBHOOK.await(Duration.ofSeconds(5)));
        SMTP.clear();
        WEBHOOK.clear();

        var firstKey = "event-delivery-first-" + key();
        var first = command(systemId, tenantId, memberId, firstKey, "7");
        var firstReceipt = notifications.dispatch(first);
        assertThat(firstReceipt.status()).isEqualTo("DELIVERED");
        assertThat(firstReceipt.replay()).isFalse();
        var mail = SMTP.await(Duration.ofSeconds(5));
        assertThat(mail).contains("To: " + recipient, "Subject: Export ready", "import_item exported 7 rows.")
                .doesNotContain(WEBHOOK_SECRET);
        var webhookRequest = WEBHOOK.await(Duration.ofSeconds(5));
        assertSigned(webhookRequest);
        assertThat(json.readTree(webhookRequest.body()).at("/event/templateCode").asText())
                .isEqualTo("MODULE_EXPORT_SUCCEEDED");
        assertThat(json.readTree(webhookRequest.body()).at("/event/body").asText())
                .isEqualTo("import_item exported 7 rows.");
        assertDeliveryStates(systemId, tenantId, firstKey,
                Map.of("INBOX", "DELIVERED", "EMAIL", "DELIVERED", "WEBHOOK", "DELIVERED"));

        var replay = notifications.dispatch(first);
        assertThat(replay.replay()).isTrue();
        assertThat(SMTP.poll(Duration.ofMillis(300))).isEmpty();
        assertThat(WEBHOOK.poll(Duration.ofMillis(300))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_event_message_delivery_log "
                + "WHERE system_id=? AND tenant_id=? AND dedupe_key=?", Integer.class,
                systemId, tenantId, firstKey)).isEqualTo(3);

        var preferencesRoot = "/api/v1/systems/" + systemId + "/event/delivery-preferences";
        var preferences = client.get(preferencesRoot);
        assertOk(preferences);
        var emailPreference = find(preferences.body().at("/data"),
                Map.of("templateCode", "MODULE_EXPORT_SUCCEEDED", "channel", "EMAIL"));
        assertOk(client.put(preferencesRoot + "/MODULE_EXPORT_SUCCEEDED/EMAIL", json(Map.of(
                "enabled", false, "expectedVersion", emailPreference.path("version").asLong()))));
        SMTP.clear();
        WEBHOOK.clear();
        var secondKey = "event-delivery-preference-" + key();
        notifications.dispatch(command(systemId, tenantId, memberId, secondKey, "8"));
        assertThat(SMTP.poll(Duration.ofMillis(500))).isEmpty();
        assertSigned(WEBHOOK.await(Duration.ofSeconds(5)));
        assertDeliveryStates(systemId, tenantId, secondKey,
                Map.of("INBOX", "DELIVERED", "EMAIL", "SKIPPED", "WEBHOOK", "DELIVERED"));

        assertOk(client.put(channelsRoot + "/WEBHOOK", json(Map.of(
                "enabled", false, "expectedVersion", webhook.body().at("/data/version").asLong()))));
        WEBHOOK.clear();
        var thirdKey = "event-delivery-disabled-" + key();
        notifications.dispatch(command(systemId, tenantId, memberId, thirdKey, "9"));
        assertThat(WEBHOOK.poll(Duration.ofMillis(500))).isEmpty();
        assertDeliveryStates(systemId, tenantId, thirdKey,
                Map.of("INBOX", "DELIVERED", "EMAIL", "SKIPPED", "WEBHOOK", "FAILED"));

        var logs = client.get("/api/v1/systems/" + systemId
                + "/event/delivery-logs?page=0&size=20&channel=WEBHOOK&templateCode=MODULE_EXPORT_SUCCEEDED");
        assertOk(logs);
        assertThat(logs.body().at("/data/total").asLong()).isEqualTo(3);
        assertThat(logs.body().toString())
                .doesNotContain(recipient, WEBHOOK_SECRET_REF, WEBHOOK_SECRET, WEBHOOK.endpoint(), "responseBody");
        var deliveryId = logs.body().at("/data/items/0/deliveryId").asLong();
        var detail = client.get("/api/v1/systems/" + systemId + "/event/delivery-logs/" + deliveryId);
        assertOk(detail);
        assertThat(detail.body().at("/data/attempts").size()).isGreaterThanOrEqualTo(1);
        assertThat(detail.body().toString())
                .doesNotContain(recipient, WEBHOOK_SECRET_REF, WEBHOOK_SECRET, WEBHOOK.endpoint(), firstKey);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class))
                .isEqualTo(migrationFileCount());
    }

    private ResultNotificationFacade.Command command(long systemId, long tenantId, long memberId,
                                                     String dedupeKey, String rows) {
        return new ResultNotificationFacade.Command(systemId, tenantId, memberId, memberId,
                "MODULE_EXPORT_SUCCEEDED", Map.of("moduleCode", "import_item", "rows", rows),
                new AggregateRef("MODULE_EXPORT_TASK", rows),
                "/systems/" + systemId + "/modules/import_item/exports/" + rows, dedupeKey);
    }

    private void assertDeliveryStates(long systemId, long tenantId, String dedupeKey,
                                      Map<String, String> expected) {
        var actual = jdbc.query("""
                        SELECT channel,status FROM un_event_message_delivery_log
                         WHERE system_id=? AND tenant_id=? AND dedupe_key=? ORDER BY channel
                        """, (row, number) -> Map.entry(row.getString("channel"), row.getString("status")),
                systemId, tenantId, dedupeKey).stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_event_message_delivery_attempt attempt
                JOIN un_event_message_delivery_log delivery ON delivery.id=attempt.delivery_id
                WHERE delivery.system_id=? AND delivery.tenant_id=? AND delivery.dedupe_key=?
                """, Integer.class, systemId, tenantId, dedupeKey)).isEqualTo(3);
    }

    private static void assertSigned(WebhookRequest request) throws Exception {
        var timestamp = request.header("X-Examine-Timestamp");
        var deliveryId = request.header("X-Examine-Delivery-Id");
        var dedupeKey = request.header("X-Examine-Dedupe-Key");
        var input = (timestamp + "." + deliveryId + "." + dedupeKey + ".")
                .getBytes(StandardCharsets.UTF_8);
        var body = request.body().getBytes(StandardCharsets.UTF_8);
        var signed = new byte[input.length + body.length];
        System.arraycopy(input, 0, signed, 0, input.length);
        System.arraycopy(body, 0, signed, input.length, body.length);
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertThat(request.header("X-Examine-Signature"))
                .isEqualTo("v1=" + HexFormat.of().formatHex(mac.doFinal(signed)));
        assertThat(request.header("X-Examine-Trace-Id")).isNotBlank();
    }

    private TestClient login() throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of(
                "account", ROOT_USERNAME, "password", ROOT_PASSWORD))));
        return client;
    }

    private String json(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private static JsonNode find(JsonNode items, String field, String value) {
        for (var item : items) if (value.equals(item.path(field).asText())) return item;
        throw new AssertionError("Missing " + field + "=" + value);
    }

    private static JsonNode find(JsonNode items, Map<String, String> fields) {
        for (var item : items) {
            if (fields.entrySet().stream().allMatch(entry ->
                    entry.getValue().equals(item.path(entry.getKey()).asText()))) return item;
        }
        throw new AssertionError("Missing item " + fields);
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
        assertThat(response.body().path("code").asText()).isEqualTo("OK");
    }

    private static String key() {
        return UUID.randomUUID().toString();
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

    private static int migrationFileCount() throws IOException {
        try (var migrationFiles = Files.list(migrationRoot())) {
            return Math.toIntExact(migrationFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("V.+__.+\\.sql"))
                    .count());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecrets {
        @Bean
        @Primary
        SecretResolverFacade eventDeliveryTestSecrets() {
            return request -> WEBHOOK_SECRET_REF.equals(request.reference())
                    ? Optional.of(SecretResolverFacade.ResolvedSecret.utf8(WEBHOOK_SECRET))
                    : Optional.empty();
        }
    }

    private record TestResponse(int status, JsonNode body) {
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        TestResponse post(String path, String body) throws Exception {
            return post(path, body, Map.of());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers);
        }

        TestResponse put(String path, String body) throws Exception {
            return request("PUT", path, body, Map.of());
        }

        private TestResponse request(String method, String path, String body,
                                     Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (!path.equals("/api/v1/auth/login")) builder.header("X-CSRF-Token", csrf());
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder request) throws Exception {
            var response = client.send(request.header("X-Request-ID", "event-delivery-" + key()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), json.readTree(response.body()));
        }

        private URI uri(String path) {
            return URI.create("http://127.0.0.1:" + port + path);
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue).findFirst().orElseThrow();
        }
    }

    private record WebhookRequest(Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream()).findFirst().orElse("");
        }
    }

    private static final class WebhookCaptureServer implements AutoCloseable {
        private final HttpServer server;
        private final BlockingQueue<WebhookRequest> requests = new LinkedBlockingQueue<>();

        private WebhookCaptureServer(HttpServer server) {
            this.server = server;
        }

        static WebhookCaptureServer start() {
            try {
                var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                var capture = new WebhookCaptureServer(server);
                server.createContext("/event-delivery", exchange -> {
                    var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    var headers = new LinkedHashMap<String, List<String>>();
                    exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
                    capture.requests.add(new WebhookRequest(Map.copyOf(headers), body));
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                });
                server.start();
                return capture;
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot start webhook capture server", failure);
            }
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/event-delivery";
        }

        WebhookRequest await(Duration timeout) throws InterruptedException {
            var value = requests.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (value == null) throw new AssertionError("Webhook request was not received");
            return value;
        }

        Optional<WebhookRequest> poll(Duration timeout) throws InterruptedException {
            return Optional.ofNullable(requests.poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
        }

        void clear() {
            requests.clear();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class SmtpCaptureServer implements AutoCloseable {
        private final ServerSocket server;
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final Thread thread;
        private volatile boolean closed;

        private SmtpCaptureServer(ServerSocket server) {
            this.server = server;
            thread = Thread.ofPlatform().daemon().name("event-smtp-capture").start(this::accept);
        }

        static SmtpCaptureServer start() {
            try {
                return new SmtpCaptureServer(new ServerSocket(0, 16, InetAddress.getLoopbackAddress()));
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot start SMTP capture server", failure);
            }
        }

        int port() {
            return server.getLocalPort();
        }

        String await(Duration timeout) throws InterruptedException {
            var value = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (value == null) throw new AssertionError("SMTP message was not received");
            return value;
        }

        Optional<String> poll(Duration timeout) throws InterruptedException {
            return Optional.ofNullable(messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
        }

        void clear() {
            messages.clear();
        }

        private void accept() {
            while (!closed) {
                try {
                    handle(server.accept());
                } catch (IOException failure) {
                    if (!closed) throw new IllegalStateException(failure);
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            try (socket;
                 var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                 var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                reply(writer, "220 smtp.example.test ESMTP ready");
                var data = false;
                var content = new StringBuilder();
                for (String line; (line = reader.readLine()) != null; ) {
                    if (data) {
                        if (".".equals(line)) {
                            messages.add(content.toString());
                            reply(writer, "250 queued");
                            data = false;
                        } else {
                            content.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                        }
                        continue;
                    }
                    var command = line.toUpperCase(Locale.ROOT);
                    if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                        reply(writer, "250-smtp.example.test");
                        reply(writer, "250 8BITMIME");
                    } else if (command.startsWith("MAIL FROM") || command.startsWith("RCPT TO")
                            || command.equals("RSET")) {
                        reply(writer, "250 OK");
                    } else if (command.equals("DATA")) {
                        content.setLength(0);
                        data = true;
                        reply(writer, "354 End data with <CR><LF>.<CR><LF>");
                    } else if (command.equals("QUIT")) {
                        reply(writer, "221 Bye");
                        break;
                    } else {
                        reply(writer, "250 OK");
                    }
                }
            }
        }

        private static void reply(BufferedWriter writer, String value) throws IOException {
            writer.write(value);
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() {
            closed = true;
            try {
                server.close();
                thread.join(1000);
            } catch (IOException ignored) {
                // already closed
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
