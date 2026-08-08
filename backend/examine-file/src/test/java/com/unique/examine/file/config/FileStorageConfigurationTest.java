package com.unique.examine.file.config;

import com.unique.examine.file.adapter.local.LocalFileContentStore;
import com.unique.examine.file.adapter.s3.S3FileContentStore;
import com.unique.examine.file.domain.FileStorageStatus;
import com.unique.examine.file.port.FileContentStore;
import com.unique.examine.file.port.FileStorageStatusProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageConfigurationTest {
    @TempDir
    Path root;

    @Test
    void localModeWiresExactlyOneSafeStoreAndRedactedHealthyDescriptor() {
        new ApplicationContextRunner()
                .withUserConfiguration(FileStorageConfiguration.class)
                .withPropertyValues(
                        "examine.file.storage.mode=LOCAL",
                        "examine.file.storage.local.root=" + portable(root))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileContentStore.class)
                            .hasSingleBean(FileStorageStatusProvider.class)
                            .doesNotHaveBean(S3Client.class);
                    assertThat(context.getBean(FileContentStore.class))
                            .isInstanceOf(LocalFileContentStore.class);
                    assertThat(context.getBean(FileStorageStatusProvider.class).status())
                            .isEqualTo(new FileStorageStatus(
                                    FileStorageStatus.Mode.LOCAL,
                                    "LOCAL_ROOT",
                                    20L * 1024 * 1024,
                                    100L * 1024 * 1024,
                                    5L * 1024 * 1024,
                                    true))
                            .extracting(FileStorageStatus::location)
                            .asString()
                            .doesNotContain(root.toAbsolutePath().toString());
                });
    }

    @Test
    void s3ModeWiresOnlyS3AndNeverSilentlyFallsBackToLocal() {
        new ApplicationContextRunner()
                .withUserConfiguration(FileStorageConfiguration.class)
                .withPropertyValues(
                        "examine.file.storage.mode=S3",
                        "examine.file.storage.s3.endpoint=http://127.0.0.1:1",
                        "examine.file.storage.s3.allow-loopback-http-for-testing=true",
                        "examine.file.storage.s3.region=us-east-1",
                        "examine.file.storage.s3.bucket=fs110-bucket",
                        "examine.file.storage.s3.prefix=fixed-prefix",
                        "examine.file.storage.s3.path-style=true",
                        "examine.file.storage.s3.access-key=fs110-access",
                        "examine.file.storage.s3.secret-key=fs110-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FileContentStore.class)
                            .hasSingleBean(FileStorageStatusProvider.class)
                            .hasSingleBean(S3Client.class);
                    assertThat(context.getBean(FileContentStore.class))
                            .isInstanceOf(S3FileContentStore.class)
                            .isNotInstanceOf(LocalFileContentStore.class);
                });
    }

    @Test
    void incompleteS3ConfigurationFailsStartupWithoutLeakingSuppliedCredential() {
        var suppliedAccessKey = "access-key-must-not-leak";
        new ApplicationContextRunner()
                .withUserConfiguration(FileStorageConfiguration.class)
                .withPropertyValues(
                        "examine.file.storage.mode=S3",
                        "examine.file.storage.s3.endpoint=http://127.0.0.1:9000",
                        "examine.file.storage.s3.allow-loopback-http-for-testing=true",
                        "examine.file.storage.s3.region=us-east-1",
                        "examine.file.storage.s3.bucket=fs110-bucket",
                        "examine.file.storage.s3.prefix=fixed-prefix",
                        "examine.file.storage.s3.access-key=" + suppliedAccessKey)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("S3 secret key is required");
                    assertThat(context.getStartupFailure().toString())
                            .doesNotContain(suppliedAccessKey);
                });
    }

    @Test
    void propertyDescriptorsRedactEndpointAndCredentials() {
        var s3 = new FileStorageProperties.S3();
        s3.setEndpoint("https://private-storage.example.test");
        s3.setAccessKey("private-access");
        s3.setSecretKey("private-secret");
        assertThat(s3.toString())
                .doesNotContain("private-storage", "private-access", "private-secret")
                .contains("[redacted]");
    }

    @Test
    void httpS3RequiresExplicitLoopbackOnlyTestAllowance() {
        new ApplicationContextRunner()
                .withUserConfiguration(FileStorageConfiguration.class)
                .withPropertyValues(
                        "examine.file.storage.mode=S3",
                        "examine.file.storage.s3.endpoint=http://127.0.0.1:9000",
                        "examine.file.storage.s3.region=us-east-1",
                        "examine.file.storage.s3.bucket=fs110-bucket",
                        "examine.file.storage.s3.prefix=fixed-prefix",
                        "examine.file.storage.s3.access-key=test-access",
                        "examine.file.storage.s3.secret-key=test-secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("S3 endpoint must use HTTPS");
                });
    }

    private static String portable(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
