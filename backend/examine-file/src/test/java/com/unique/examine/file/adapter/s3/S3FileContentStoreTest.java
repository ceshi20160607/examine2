package com.unique.examine.file.adapter.s3;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3FileContentStoreTest {
    private static final String ACCESS_KEY = "fs110-access-key";
    private static final String SECRET_KEY = "fs110-secret-key-never-log";

    @Test
    void realSdkRoundTripsThroughLocalS3ProtocolWithFixedBucketPrefixAndHealth() throws Exception {
        try (var server = new LocalS3ProtocolServer(); var client = client(server.endpoint())) {
            var store = new S3FileContentStore(client, "fs110-bucket", "fixed-prefix");
            var objectKey = "system/10/tenant/20/file/30";
            var bytes = "real s3-compatible content".getBytes(StandardCharsets.UTF_8);

            assertThat(store.available()).isTrue();
            store.put(objectKey, bytes);
            assertThat(store.read(objectKey).orElseThrow()).containsExactly(bytes);
            store.delete(objectKey);
            assertThat(store.read(objectKey)).isEmpty();

            assertThat(server.paths()).contains(
                    "/fs110-bucket",
                    "/fs110-bucket/fixed-prefix/system/10/tenant/20/file/30");
            assertThat(server.authorizations()).isNotEmpty().allSatisfy(value ->
                    assertThat(value).startsWith("AWS4-HMAC-SHA256 ")
                            .doesNotContain(SECRET_KEY));
            assertThat(store.location()).isEqualTo("fs110-bucket/fixed-prefix")
                    .doesNotContain(server.endpoint().toString(), ACCESS_KEY, SECRET_KEY);
        }
    }

    @Test
    void rejectsUnsafeKeysBeforeNetworkAndRedactsTransportFailure() throws Exception {
        try (var server = new LocalS3ProtocolServer(); var client = client(server.endpoint())) {
            var store = new S3FileContentStore(client, "fs110-bucket", "fixed-prefix");
            assertThatThrownBy(() -> store.put("../outside", new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Object key is invalid");
            assertThatThrownBy(() -> store.put("nested\\outside", new byte[0]))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(server.paths()).isEmpty();

            server.failWrites();
            assertThatThrownBy(() -> store.put("system/10/file/40", new byte[]{1}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to write S3 file content")
                    .satisfies(failure -> assertThat(failure.toString())
                            .doesNotContain(SECRET_KEY, ACCESS_KEY, server.endpoint().toString(),
                                    "system/10/file/40"));
        }
    }

    private static S3Client client(URI endpoint) {
        var timeout = Duration.ofSeconds(3);
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(timeout)
                        .socketTimeout(timeout)
                        .expectContinueEnabled(false))
                .build();
    }

    private static final class LocalS3ProtocolServer implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final List<String> authorizations = new CopyOnWriteArrayList<>();
        private volatile boolean failWrites;

        private LocalS3ProtocolServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                var thread = new Thread(runnable, "fs110-local-s3");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        private List<String> paths() { return List.copyOf(paths); }
        private List<String> authorizations() { return List.copyOf(authorizations); }
        private void failWrites() { failWrites = true; }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                var method = exchange.getRequestMethod();
                var path = exchange.getRequestURI().getPath();
                paths.add(path);
                var authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization != null) authorizations.add(authorization);

                if ("HEAD".equals(method) && "/fs110-bucket".equals(path)) {
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                if ("PUT".equals(method)) {
                    if (failWrites) {
                        error(exchange, 500, "InternalError");
                        return;
                    }
                    var content = exchange.getRequestBody().readAllBytes();
                    objects.put(path, content);
                    exchange.getResponseHeaders().add("ETag", "\"" + md5(content) + "\"");
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }
                if ("GET".equals(method)) {
                    var value = objects.get(path);
                    if (value == null) {
                        error(exchange, 404, "NoSuchKey");
                        return;
                    }
                    exchange.sendResponseHeaders(200, value.length);
                    exchange.getResponseBody().write(value);
                    return;
                }
                if ("DELETE".equals(method)) {
                    objects.remove(path);
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                error(exchange, 405, "MethodNotAllowed");
            }
        }

        private static void error(HttpExchange exchange, int status, String code) throws IOException {
            var body = ("<Error><Code>" + code + "</Code><Message>failed</Message></Error>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }

        private static String md5(byte[] content) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
