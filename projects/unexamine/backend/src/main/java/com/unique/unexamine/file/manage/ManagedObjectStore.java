package com.unique.unexamine.file.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.file.base.entity.FileStorageBackend;
import com.unique.unexamine.shared.manage.security.RuntimeSecretCipher;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

/**
 * 文件字节存储适配器。业务层始终使用同一份文件元数据和引用关系，只在这里区分本地与 S3 兼容存储。
 */
@Service
public class ManagedObjectStore {
    private final ObjectMapper objectMapper;
    private final RuntimeSecretCipher secretCipher;
    private final Path storageRoot;

    public ManagedObjectStore(
            ObjectMapper objectMapper,
            RuntimeSecretCipher secretCipher,
            @Value("${app.file.storage-root:./data/files}") String storageRoot) {
        this.objectMapper = objectMapper;
        this.secretCipher = secretCipher;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public void put(FileStorageBackend backend, String objectKey, byte[] content, String contentType) {
        if ("LOCAL".equals(backend.getBackendType())) {
            Path path = localPath(objectKey);
            try {
                Files.createDirectories(path.getParent());
                Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return;
            } catch (IOException exception) {
                throw unavailable("FILE_STORAGE_WRITE_FAILED", "文件写入本地存储失败", exception);
            }
        }
        try (S3Client client = client(backend)) {
            client.putObject(PutObjectRequest.builder().bucket(backend.getBucketName()).key(objectKey)
                    .contentType(contentType).contentLength((long) content.length).build(), RequestBody.fromBytes(content));
        } catch (RuntimeException exception) {
            throw unavailable("FILE_STORAGE_WRITE_FAILED", "文件写入对象存储失败，请检查存储配置和网络", exception);
        }
    }

    public byte[] read(FileStorageBackend backend, String objectKey) {
        if ("LOCAL".equals(backend.getBackendType())) {
            try {
                return Files.readAllBytes(localPath(objectKey));
            } catch (IOException exception) {
                throw new DomainException("FILE_CONTENT_MISSING", "文件内容不存在或暂时不可读", HttpStatus.NOT_FOUND);
            }
        }
        try (S3Client client = client(backend)) {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(backend.getBucketName()).key(objectKey).build())
                    .asByteArray();
        } catch (RuntimeException exception) {
            throw unavailable("FILE_CONTENT_MISSING", "对象存储中的文件不存在或暂时不可读", exception);
        }
    }

    public boolean delete(FileStorageBackend backend, String objectKey) {
        if ("LOCAL".equals(backend.getBackendType())) {
            try {
                return Files.deleteIfExists(localPath(objectKey));
            } catch (IOException ignored) {
                return false;
            }
        }
        try (S3Client client = client(backend)) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(backend.getBucketName()).key(objectKey).build());
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 启用前执行真实写入、读取和删除，避免把不可用配置切到生产上传链路。 */
    public void probe(FileStorageBackend backend) {
        String key = "_connection-test/" + UUID.randomUUID();
        byte[] expected = "unexamine-object-storage-probe".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        put(backend, key, expected, "text/plain");
        try {
            if (!java.util.Arrays.equals(expected, read(backend, key))) {
                throw new DomainException("OBJECT_STORAGE_PROBE_MISMATCH", "对象存储读写校验不一致", HttpStatus.CONFLICT);
            }
        } finally {
            delete(backend, key);
        }
    }

    private S3Client client(FileStorageBackend backend) {
        try {
            JsonNode configuration = objectMapper.readTree(backend.getConfigJson());
            JsonNode credential = objectMapper.readTree(secretCipher.decrypt(
                    backend.getCredentialRef(), credentialBinding(backend)));
            return S3Client.builder()
                    .endpointOverride(URI.create(backend.getEndpoint()))
                    .region(Region.of(configuration.path("region").asText()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            credential.path("accessKeyId").asText(), credential.path("secretAccessKey").asText())))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(configuration.path("pathStyle").asBoolean(true))
                            // 保持普通 Content-Length 请求，兼容 MinIO、Ceph 等常见 S3 实现。
                            .chunkedEncodingEnabled(false).checksumValidationEnabled(false)
                            .expectContinueEnabled(false).build())
                    .build();
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable("OBJECT_STORAGE_CONFIGURATION_INVALID", "对象存储配置无法读取", exception);
        }
    }

    public String credentialBinding(FileStorageBackend backend) {
        return "file-storage:" + backend.getContextType() + ":" + backend.getPlatformId() + ":"
                + backend.getSystemId() + ":" + backend.getCode();
    }

    private Path localPath(String objectKey) {
        Path resolved = storageRoot.resolve(objectKey).normalize();
        if (!resolved.startsWith(storageRoot)) throw new IllegalStateException("Unsafe managed object key");
        return resolved;
    }

    private DomainException unavailable(String code, String message, Exception cause) {
        return new DomainException(code, message, HttpStatus.SERVICE_UNAVAILABLE,
                Map.of("cause", cause.getClass().getSimpleName()));
    }
}
