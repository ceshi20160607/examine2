package com.unique.unexamine.file.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.file.base.entity.FileStorageBackend;
import com.unique.unexamine.operations.base.entity.OpsSecretRef;
import com.unique.unexamine.operations.base.entity.OpsSecretRotation;
import com.unique.unexamine.operations.base.service.OpsSecretRefBaseService;
import com.unique.unexamine.operations.base.service.OpsSecretRotationBaseService;
import com.unique.unexamine.operations.manage.OperationsSecretCipher;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件字节存储适配器。业务层始终使用同一份文件元数据和引用关系，只在这里区分本地与 S3 兼容存储。
 */
@Service
public class ManagedObjectStore {
    private static final String CREDENTIAL_PROVIDER = "INTERNAL_ENCRYPTED_FILE_STORAGE";
    private final ObjectMapper objectMapper;
    private final OpsSecretRefBaseService secretRefs;
    private final OpsSecretRotationBaseService secretRotations;
    private final OperationsSecretCipher secretCipher;
    private final Path storageRoot;

    public ManagedObjectStore(
            ObjectMapper objectMapper,
            OpsSecretRefBaseService secretRefs,
            OpsSecretRotationBaseService secretRotations,
            OperationsSecretCipher secretCipher,
            @Value("${app.file.storage-root:./data/files}") String storageRoot) {
        this.objectMapper = objectMapper;
        this.secretRefs = secretRefs;
        this.secretRotations = secretRotations;
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
            JsonNode credential = objectMapper.readTree(currentCredentials(backend));
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

    /** 文件后端只保存受控密钥引用主键，密文按版本留在密钥轮换记录中。 */
    public Long saveCredentials(FileStorageBackend backend, String credentialsJson, Long accountId) {
        OpsSecretRef ref = backend.getCredentialRefId() == null ? null : requireCredentialRef(backend);
        String nextVersion = ref == null ? "v1" : "v" + (Integer.parseInt(ref.getCurrentVersion().substring(1)) + 1);
        LocalDateTime now = LocalDateTime.now();
        if (ref == null) {
            ref = new OpsSecretRef();
            ref.setContextType(backend.getContextType());
            ref.setPlatformId(backend.getPlatformId());
            ref.setSystemId(backend.getSystemId());
            ref.setScopeOwnerId(backend.getScopeOwnerId());
            ref.setSecretCode("FILE_STORAGE_" + backend.getCode());
            ref.setProvider(CREDENTIAL_PROVIDER);
            ref.setCurrentVersion(nextVersion);
            ref.setReferencePath(credentialPath(backend, nextVersion));
            ref.setStatus("ACTIVE");
            ref.setVersion(0);
            secretRefs.insert(ref);
        }
        OpsSecretRotation rotation = new OpsSecretRotation();
        rotation.setSecretRefId(ref.getId());
        rotation.setFromVersion("v1".equals(nextVersion) ? "NONE" : ref.getCurrentVersion());
        rotation.setToVersion(nextVersion);
        rotation.setStatus("ACTIVE");
        rotation.setVerificationJson(writeCredentialEvidence(secretCipher.encrypt(
                credentialsJson, ref.getId(), nextVersion)));
        rotation.setRequestedByAccountId(accountId);
        rotation.setStartTime(now);
        rotation.setFinishTime(now);
        rotation.setVersion(0);
        secretRotations.insert(rotation);
        if (!"v1".equals(nextVersion)) {
            ref.setCurrentVersion(nextVersion);
            ref.setReferencePath(credentialPath(backend, nextVersion));
            if (secretRefs.updateById(ref) != 1) {
                throw new DomainException("FILE_CREDENTIAL_VERSION_CONFLICT", "存储凭证已变化，请刷新后重试", HttpStatus.CONFLICT);
            }
        }
        return ref.getId();
    }

    /** 仅在创建 S3 客户端时解密当前版本，禁止把访问密钥写回文件配置或日志。 */
    private String currentCredentials(FileStorageBackend backend) throws IOException {
        OpsSecretRef ref = requireCredentialRef(backend);
        OpsSecretRotation rotation = secretRotations.selectList(Wrappers.<OpsSecretRotation>lambdaQuery()
                .eq(OpsSecretRotation::getSecretRefId, ref.getId())
                .eq(OpsSecretRotation::getToVersion, ref.getCurrentVersion())
                .eq(OpsSecretRotation::getStatus, "ACTIVE")
                .orderByDesc(OpsSecretRotation::getId).last("LIMIT 1")).stream().findFirst().orElse(null);
        if (rotation == null || rotation.getVerificationJson() == null) {
            throw new DomainException("FILE_CREDENTIAL_MISSING", "对象存储凭证版本不存在", HttpStatus.CONFLICT);
        }
        String encrypted = objectMapper.readTree(rotation.getVerificationJson()).path("materialReference").asText();
        return secretCipher.decrypt(encrypted, ref.getId(), ref.getCurrentVersion());
    }

    private OpsSecretRef requireCredentialRef(FileStorageBackend backend) {
        OpsSecretRef ref = backend.getCredentialRefId() == null ? null : secretRefs.selectById(backend.getCredentialRefId());
        if (ref == null || !CREDENTIAL_PROVIDER.equals(ref.getProvider()) || !"ACTIVE".equals(ref.getStatus())
                || !Objects.equals(ref.getContextType(), backend.getContextType())
                || !Objects.equals(ref.getPlatformId(), backend.getPlatformId())
                || !Objects.equals(ref.getSystemId(), backend.getSystemId())
                || !Objects.equals(ref.getScopeOwnerId(), backend.getScopeOwnerId())
                || !Objects.equals(ref.getSecretCode(), "FILE_STORAGE_" + backend.getCode())) {
            throw new DomainException("FILE_CREDENTIAL_SCOPE_INVALID", "对象存储凭证不属于当前后端", HttpStatus.CONFLICT);
        }
        return ref;
    }

    private String credentialPath(FileStorageBackend backend, String version) {
        return "ops-secret://file-storage/" + backend.getContextType().toLowerCase() + "/"
                + backend.getScopeOwnerId() + "/" + backend.getCode() + "/" + version;
    }

    private String writeCredentialEvidence(String encrypted) {
        try {
            return objectMapper.writeValueAsString(Map.of("materialReference", encrypted, "purpose", "FILE_STORAGE"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Cannot save file credential reference", exception);
        }
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
