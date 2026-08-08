package com.unique.examine.file.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageApplicationConfigurationContractTest {

    @Test
    void deploymentEnvironmentMapsModeLocalS3TimeoutsLimitsAndHttpEnvelope() throws Exception {
        var yaml = Files.readString(findApplicationYaml());
        assertThat(yaml)
                .contains("mode: ${EXAMINE_FILE_STORAGE_MODE:LOCAL}")
                .contains("root: ${EXAMINE_FILE_LOCAL_ROOT:./data/files}")
                .contains("endpoint: ${EXAMINE_FILE_S3_ENDPOINT:}")
                .contains("allow-loopback-http-for-testing: ${EXAMINE_FILE_S3_ALLOW_LOOPBACK_HTTP_FOR_TESTING:false}")
                .contains("enabled: ${EXAMINE_REDIS_TLS_ENABLED:false}")
                .contains("region: ${EXAMINE_FILE_S3_REGION:}")
                .contains("bucket: ${EXAMINE_FILE_S3_BUCKET:}")
                .contains("prefix: ${EXAMINE_FILE_S3_PREFIX:}")
                .contains("path-style: ${EXAMINE_FILE_S3_PATH_STYLE:true}")
                .contains("access-key: ${EXAMINE_FILE_S3_ACCESS_KEY:}")
                .contains("secret-key: ${EXAMINE_FILE_S3_SECRET_KEY:}")
                .contains("connect-timeout: ${EXAMINE_FILE_S3_CONNECT_TIMEOUT:3s}")
                .contains("read-timeout: ${EXAMINE_FILE_S3_READ_TIMEOUT:10s}")
                .contains("request-timeout: ${EXAMINE_FILE_S3_REQUEST_TIMEOUT:15s}")
                .contains("max-single-upload-bytes: ${EXAMINE_FILE_MAX_SINGLE_UPLOAD_BYTES:20971520}")
                .contains("max-multipart-upload-bytes: ${EXAMINE_FILE_MAX_MULTIPART_UPLOAD_BYTES:104857600}")
                .contains("max-part-bytes: ${EXAMINE_FILE_MAX_PART_BYTES:5242880}")
                .contains("max-file-size: ${EXAMINE_FILE_MAX_HTTP_FILE_SIZE:20MB}")
                .contains("max-request-size: ${EXAMINE_FILE_MAX_HTTP_REQUEST_SIZE:25MB}")
                .contains("max-http-post-size: ${EXAMINE_MAX_HTTP_POST_SIZE:25MB}");
    }

    private static Path findApplicationYaml() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("backend").resolve("examine-web")
                    .resolve("src/main/resources/application.yml");
            if (Files.isRegularFile(candidate)) return candidate;
            candidate = current.resolve("examine-web")
                    .resolve("src/main/resources/application.yml");
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate examine-web application.yml");
    }
}
