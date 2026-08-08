package com.unique.examine.file.config;

import com.unique.examine.file.adapter.local.LocalFileContentStore;
import com.unique.examine.file.adapter.s3.S3FileContentStore;
import com.unique.examine.file.domain.FileStorageStatus;
import com.unique.examine.file.port.FileContentStore;
import com.unique.examine.file.port.FileStorageStatusProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "examine.file.storage", name = "mode", havingValue = "S3")
    S3Client fileStorageS3Client(FileStorageProperties properties) {
        properties.validateLimits();
        var s3 = properties.getS3();
        var endpoint = endpoint(s3.getEndpoint(), s3.isAllowLoopbackHttpForTesting());
        var region = required(s3.getRegion(), "S3 region", 100);
        var accessKey = required(s3.getAccessKey(), "S3 access key", 256);
        var secretKey = required(s3.getSecretKey(), "S3 secret key", 1024);
        required(s3.getBucket(), "S3 bucket", 63);
        required(s3.getPrefix(), "S3 prefix", 300);
        var connectTimeout = timeout(s3.getConnectTimeout(), "S3 connect timeout");
        var readTimeout = timeout(s3.getReadTimeout(), "S3 read timeout");
        var requestTimeout = timeout(s3.getRequestTimeout(), "S3 request timeout");
        if (requestTimeout.compareTo(readTimeout) < 0) {
            throw new IllegalStateException("S3 request timeout must cover the read timeout");
        }

        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyle())
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(connectTimeout)
                        .connectionAcquisitionTimeout(connectTimeout)
                        .socketTimeout(readTimeout)
                        .expectContinueEnabled(false)
                        .maxConnections(32))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(readTimeout)
                        .apiCallTimeout(requestTimeout)
                        .build())
                .build();
    }

    @Bean
    FileContentStore fileContentStore(
            FileStorageProperties properties,
            ObjectProvider<S3Client> s3Client
    ) {
        properties.validateLimits();
        if (properties.getMode() == null) {
            throw new IllegalStateException("File storage mode is required");
        }
        return switch (properties.getMode()) {
            case LOCAL -> new LocalFileContentStore(localRoot(properties));
            case S3 -> new S3FileContentStore(
                    requiredClient(s3Client),
                    properties.getS3().getBucket(),
                    properties.getS3().getPrefix());
        };
    }

    @Bean
    FileStorageStatusProvider fileStorageStatusProvider(
            FileStorageProperties properties,
            FileContentStore contentStore
    ) {
        return switch (properties.getMode()) {
            case LOCAL -> {
                var local = (LocalFileContentStore) contentStore;
                yield provider(properties, FileStorageStatus.Mode.LOCAL, "LOCAL_ROOT", local::available);
            }
            case S3 -> {
                var s3 = (S3FileContentStore) contentStore;
                yield provider(properties, FileStorageStatus.Mode.S3, s3.location(), s3::available);
            }
        };
    }

    private static ConfiguredFileStorageStatusProvider provider(
            FileStorageProperties properties,
            FileStorageStatus.Mode mode,
            String location,
            java.util.function.BooleanSupplier health
    ) {
        return new ConfiguredFileStorageStatusProvider(
                mode,
                location,
                properties.getMaxSingleUploadBytes(),
                properties.getMaxMultipartUploadBytes(),
                properties.getMaxPartBytes(),
                health
        );
    }

    private static Path localRoot(FileStorageProperties properties) {
        var value = properties.getLocal().getRoot();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Local file storage root is required");
        }
        try {
            return Path.of(value.strip());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Local file storage root is invalid", failure);
        }
    }

    private static S3Client requiredClient(ObjectProvider<S3Client> provider) {
        var client = provider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("S3 file storage client is unavailable");
        }
        return client;
    }

    private static URI endpoint(String value, boolean allowLoopbackHttpForTesting) {
        var text = required(value, "S3 endpoint", 2048);
        final URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("S3 endpoint is invalid", failure);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("S3 endpoint is invalid");
        }
        if ("http".equalsIgnoreCase(uri.getScheme())
                && !(allowLoopbackHttpForTesting && resolvesOnlyToLoopback(uri.getHost()))) {
            throw new IllegalStateException("S3 endpoint must use HTTPS");
        }
        return uri;
    }

    private static boolean resolvesOnlyToLoopback(String host) {
        try {
            var addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) return false;
            for (var address : addresses) {
                if (!address.isLoopbackAddress()) return false;
            }
            return true;
        } catch (UnknownHostException failure) {
            return false;
        }
    }

    private static String required(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalStateException(name + " is required");
        }
        return value.strip();
    }

    private static Duration timeout(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()
                || value.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException(name + " must be between 1 ms and 2 minutes");
        }
        return value;
    }
}
