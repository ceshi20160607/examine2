package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FileStorageJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "file_storage_root";
    private static final String ROOT_PASSWORD = "File-Storage-Root-Password-110!";
    private static final String S3_ACCESS_KEY = "cycle110-access";
    private static final String S3_SECRET_KEY = "cycle110-secret-password";
    private static final String S3_BUCKET = "examine-cycle110";
    private static final String S3_PREFIX = "tenant-files";
    private static final int PART_BYTES = 5 * 1024 * 1024;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_file_storage_test")
            .withUsername("examine_file_storage_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "minio/minio:RELEASE.2024-01-16T16-07-38Z"))
            .withEnv("MINIO_ROOT_USER", S3_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", S3_SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        ensureBucket();
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
        registry.add("examine.bootstrap.root.display-name", () -> "File Storage Test Root");
        registry.add("examine.file.storage.mode", () -> "S3");
        registry.add("examine.file.storage.s3.endpoint", FileStorageJourneyIntegrationTest::s3Endpoint);
        registry.add("examine.file.storage.s3.allow-loopback-http-for-testing", () -> true);
        registry.add("examine.file.storage.s3.region", () -> "us-east-1");
        registry.add("examine.file.storage.s3.bucket", () -> S3_BUCKET);
        registry.add("examine.file.storage.s3.prefix", () -> S3_PREFIX);
        registry.add("examine.file.storage.s3.path-style", () -> true);
        registry.add("examine.file.storage.s3.access-key", () -> S3_ACCESS_KEY);
        registry.add("examine.file.storage.s3.secret-key", () -> S3_SECRET_KEY);
        registry.add("examine.work.task-reminder.scheduler.enabled", () -> false);
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
    }

    @Test
    void realS3FileCenterCoversPreviewMultipartReferenceAndTenantIsolation() throws Exception {
        var client = login();
        assertOk(client.postJson("/api/v1/context/platform:switch", "{}", Map.of()));
        var createdSystem = client.postJson("/api/v1/platform/admin/systems", json(Map.of(
                "code", "file_storage_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "File Storage Journey",
                "description", "Real S3-compatible file lifecycle",
                "tenantMode", "MULTI"
        )), Map.of("Idempotency-Key", key()));
        assertOk(createdSystem);
        var systemId = text(createdSystem.body(), "/data/id");
        var switched = client.postJson(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        var firstTenantId = text(switched.body(), "/data/context/tenantId");
        var fileRoot = "/api/v1/systems/" + systemId + "/files";

        var status = client.getJson(fileRoot + "/storage-status");
        assertOk(status);
        assertThat(text(status.body(), "/data/mode")).isEqualTo("S3");
        assertThat(text(status.body(), "/data/location")).isEqualTo(S3_BUCKET + "/" + S3_PREFIX);
        assertThat(status.body().at("/data/available").asBoolean()).isTrue();
        assertThat(status.body().toString())
                .doesNotContain(S3_ACCESS_KEY)
                .doesNotContain(S3_SECRET_KEY)
                .doesNotContain(s3Endpoint());

        var imageContent = png();
        var imageUpload = client.multipart(
                fileRoot, "cycle110.png", "image/png", imageContent,
                Map.of("Idempotency-Key", key()));
        assertCreated(imageUpload);
        var imageId = text(imageUpload.body(), "/data/id");

        var imagePage = client.getJson(fileRoot
                + "?page=1&size=20&keyword=cycle110&mediaType=image%2F*");
        assertOk(imagePage);
        assertThat(imagePage.body().at("/data/total").asLong()).isOne();
        assertThat(text(imagePage.body(), "/data/items/0/id")).isEqualTo(imageId);

        var preview = client.getBytes(fileRoot + "/" + imageId + "/preview");
        assertThat(preview.status()).isEqualTo(200);
        assertThat(preview.body()).isEqualTo(imageContent);
        assertThat(preview.headers().firstValue("Content-Disposition").orElse(""))
                .startsWith("inline");
        assertThat(preview.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(preview.headers().firstValue("Cache-Control").orElse(""))
                .contains("private");

        var thumbnail = client.getBytes(fileRoot + "/" + imageId
                + "/thumbnail?maxWidth=12&maxHeight=12");
        assertThat(thumbnail.status()).isEqualTo(200);
        assertThat(thumbnail.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(thumbnail.body())).getWidth())
                .isLessThanOrEqualTo(12);

        var largeContent = new byte[20 * 1024 * 1024 + 73];
        for (int index = 0; index < largeContent.length; index++) {
            largeContent[index] = (byte) (index * 31 + 17);
        }
        var largeHash = sha256(largeContent);
        var initialized = client.postJson(fileRoot + "/multipart-uploads", json(Map.of(
                "originalName", "cycle110-large.bin",
                "mediaType", "application/octet-stream",
                "sizeBytes", largeContent.length,
                "sha256", largeHash
        )), Map.of("Idempotency-Key", key()));
        assertCreated(initialized);
        var uploadId = text(initialized.body(), "/data/uploadId");
        assertThat(initialized.body().at("/data/partSizeBytes").asInt()).isEqualTo(PART_BYTES);
        var partCount = initialized.body().at("/data/partCount").asInt();
        assertThat(partCount).isEqualTo(5);

        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            var from = (partNumber - 1) * PART_BYTES;
            var to = Math.min(from + PART_BYTES, largeContent.length);
            var part = Arrays.copyOfRange(largeContent, from, to);
            var receipt = client.putBytes(
                    fileRoot + "/multipart-uploads/" + uploadId + "/parts/" + partNumber,
                    part,
                    Map.of("X-Part-SHA256", sha256(part)));
            assertOk(receipt);
            assertThat(receipt.body().at("/data/partNumber").asInt()).isEqualTo(partNumber);
            assertThat(receipt.body().at("/data/replay").asBoolean()).isFalse();
            if (partNumber == 1) {
                var replay = client.putBytes(
                        fileRoot + "/multipart-uploads/" + uploadId + "/parts/1",
                        part,
                        Map.of("X-Part-SHA256", sha256(part)));
                assertOk(replay);
                assertThat(replay.body().at("/data/replay").asBoolean()).isTrue();
            }
        }

        var completed = client.postJson(
                fileRoot + "/multipart-uploads/" + uploadId + ":complete",
                json(Map.of("sha256", largeHash)), Map.of("Idempotency-Key", key()));
        assertOk(completed);
        var largeId = text(completed.body(), "/data/id");
        assertThat(completed.body().at("/data/size").asLong()).isEqualTo(largeContent.length);
        assertThat(text(completed.body(), "/data/sha256")).isEqualTo(largeHash);
        assertThat(client.getBytes(fileRoot + "/" + largeId + "/content").body())
                .isEqualTo(largeContent);
        assertError(client.getJson(fileRoot + "/" + largeId + "/preview"),
                415, "FILE_PREVIEW_UNSUPPORTED");

        var reference = json(Map.of("targetType", "MODULE_RECORD", "targetId", "110"));
        assertOk(client.postJson(fileRoot + "/" + largeId + "/references", reference, Map.of()));
        assertError(client.deleteJson(fileRoot + "/" + largeId, "{}"),
                409, "FILE_STILL_REFERENCED");

        var secondTenant = client.postJson(
                "/api/v1/systems/" + systemId + "/admin/tenants",
                json(Map.of("code", "isolated", "name", "Isolated Tenant")),
                Map.of("Idempotency-Key", key()));
        assertOk(secondTenant);
        var secondTenantId = text(secondTenant.body(), "/data/id");
        assertThat(secondTenantId).isNotEqualTo(firstTenantId);
        assertOk(client.postJson("/api/v1/auth/refresh", "{}", Map.of()));
        assertOk(client.postJson(
                "/api/v1/context/tenants/" + secondTenantId + ":switch", "{}", Map.of()));
        assertThat(client.getJson(fileRoot + "?page=1&size=20").body().at("/data/total").asLong())
                .isZero();
        assertError(client.getJson(fileRoot + "/" + imageId), 404, "FILE_NOT_FOUND");

        assertOk(client.postJson(
                "/api/v1/context/tenants/" + firstTenantId + ":switch", "{}", Map.of()));
        assertOk(client.deleteJson(fileRoot + "/" + largeId + "/references", reference));
        assertOk(client.deleteJson(fileRoot + "/" + largeId, "{}"));
        assertOk(client.deleteJson(fileRoot + "/" + imageId, "{}"));
        assertThat(jdbc.queryForObject("select count(*) from un_file_object", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success=1", Integer.class))
                .isEqualTo(migrationFileCount());
    }

    private TestClient login() throws Exception {
        var client = new TestClient();
        assertOk(client.postJson("/api/v1/auth/login", json(Map.of(
                "account", ROOT_USERNAME,
                "password", ROOT_PASSWORD
        )), Map.of()));
        return client;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static void ensureBucket() {
        try (var client = S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true).build())
                .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(S3_BUCKET).build());
        }
    }

    private static String s3Endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static byte[] png() throws IOException {
        var image = new BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(18, 128, 110));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 200, got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 201, got %s: %s", response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected %s, got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
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
        try (var migrations = Files.list(migrationRoot())) {
            return Math.toIntExact(migrations
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("V"))
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .count());
        }
    }

    private record TestResponse(int status, JsonNode body) { }

    private record BinaryResponse(int status, byte[] body, HttpHeaders headers) { }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        TestResponse getJson(String path) throws Exception {
            return sendJson(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        TestResponse postJson(String path, String body, Map<String, String> headers) throws Exception {
            return jsonRequest("POST", path, body, headers);
        }

        TestResponse deleteJson(String path, String body) throws Exception {
            return jsonRequest("DELETE", path, body, Map.of());
        }

        TestResponse putBytes(String path, byte[] body, Map<String, String> headers) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/octet-stream")
                    .header("X-CSRF-Token", csrf())
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(builder::header);
            return sendJson(builder);
        }

        TestResponse multipart(
                String path,
                String filename,
                String mediaType,
                byte[] content,
                Map<String, String> headers
        ) throws Exception {
            var boundary = "----ExamineFileStorage" + UUID.randomUUID().toString().replace("-", "");
            var output = new ByteArrayOutputStream();
            output.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + mediaType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(content);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("X-CSRF-Token", csrf())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()));
            headers.forEach(builder::header);
            return sendJson(builder);
        }

        BinaryResponse getBytes(String path) throws IOException, InterruptedException {
            var response = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + path))
                            .header("X-Request-ID", "file-storage-test-" + key())
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(response.statusCode(), response.body(), response.headers());
        }

        private TestResponse jsonRequest(
                String method,
                String path,
                String body,
                Map<String, String> headers
        ) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            var csrf = optionalCsrf();
            if (csrf != null) builder.header("X-CSRF-Token", csrf);
            headers.forEach(builder::header);
            return sendJson(builder);
        }

        private TestResponse sendJson(HttpRequest.Builder builder)
                throws IOException, InterruptedException {
            var response = client.send(
                    builder.header("X-Request-ID", "file-storage-test-" + key()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
        }

        private String csrf() {
            var value = optionalCsrf();
            if (value == null) throw new IllegalStateException("CSRF cookie is missing");
            return value;
        }

        private String optionalCsrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }
}
