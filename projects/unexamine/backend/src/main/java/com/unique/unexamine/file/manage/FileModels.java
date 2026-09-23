package com.unique.unexamine.file.manage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class FileModels {
    private FileModels() {
    }

    public record StartUploadRequest(
            @NotBlank @Size(max = 500) String originalName,
            @NotBlank @Size(max = 255) String contentType,
            @NotNull @Min(1) @Max(1073741824L) Long expectedSize,
            @Pattern(regexp = "[a-fA-F0-9]{64}") String expectedSha256) {
    }

    public record UploadSessionView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            String originalName, String contentType, Long expectedSize, String expectedSha256,
            String status, String uploadToken, boolean shownOnce, LocalDateTime expiresAt,
            Long uploadedSize, Integer version) {
    }

    /** 分片写入结果只返回续传位置，不再次暴露上传凭证。 */
    public record UploadChunkView(
            Long sessionId, Long uploadedSize, Long expectedSize, String status, boolean idempotent) {
    }

    /** 上传前可见的实际容量和存储位置，页面不需要推测部署配置。 */
    public record StoragePolicyView(
            Long maximumFileSizeBytes, Long contextQuotaBytes, Long usedBytes, Long reservedBytes,
            Long availableBytes, String backendType, String backendName) {
    }

    /** 存储配置只返回可展示字段，访问密钥永不回传。 */
    public record StorageBackendView(
            Long id, String code, String name, String backendType, String endpoint, String bucketName,
            String region, boolean pathStyle, boolean credentialConfigured, String status, Integer version) {
    }

    public record ConfigureObjectStorageRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 1000) String endpoint,
            @NotBlank @Size(max = 255) @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{1,253}") String bucketName,
            @NotBlank @Size(max = 100) String region,
            @Size(max = 200) String accessKeyId,
            @Size(max = 200) String secretAccessKey,
            boolean pathStyle,
            boolean activate) {
    }

    public record AddReferenceRequest(
            @NotBlank @Pattern(regexp = "ACCOUNT|FLOW_INSTANCE|BUSINESS_RECORD|WORK_TASK|WORK_LOG") String ownerType,
            @NotBlank @Size(max = 100) String ownerId,
            @Size(max = 100) String fieldCode,
            @NotBlank @Pattern(regexp = "ATTACHMENT|IMAGE|DOCUMENT|RESULT") String referenceType) {
    }

    public record ReferenceView(
            Long id, String contextType, Long systemId, Long tenantId,
            String ownerType, String ownerId, String fieldCode, String referenceType,
            Long createdByAccountId, LocalDateTime createdAt) {
    }

    public record ScanView(
            Long id, String scanner, String scanVersion, String status,
            String resultCode, String resultDetail, LocalDateTime startedAt, LocalDateTime finishedAt) {
    }

    public record FileView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            Long uploadSessionId, String originalName, String contentType, Long sizeBytes,
            String sha256, String scanStatus, String previewStatus, String status,
            Long uploadedByAccountId, LocalDateTime createdAt, LocalDateTime deletedAt,
            Integer version, List<ReferenceView> references, List<ScanView> scans) {
    }

    public record BusinessAttachmentView(
            Long attachmentId, Long fileId, String originalName, String contentType, Long sizeBytes,
            String scanStatus, String previewStatus, String status, String purpose,
            LocalDateTime createdAt, boolean previewable, boolean downloadable, boolean removable) {
    }

    public record DeleteResult(Long fileId, String status, boolean bytesRemoved) {
    }

    public record BinaryContent(byte[] bytes, String contentType, String fileName, boolean inline) {
    }
}
