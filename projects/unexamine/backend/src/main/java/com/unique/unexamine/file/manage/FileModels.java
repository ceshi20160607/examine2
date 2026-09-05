package com.unique.unexamine.file.manage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
            @Min(1) @Max(1073741824L) Long expectedSize,
            @Pattern(regexp = "[a-fA-F0-9]{64}") String expectedSha256) {
    }

    public record UploadSessionView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            String originalName, String contentType, Long expectedSize, String expectedSha256,
            String status, String uploadToken, boolean shownOnce, LocalDateTime expiresAt,
            Integer version) {
    }

    public record AddReferenceRequest(
            @NotBlank @Pattern(regexp = "ACCOUNT|FLOW_INSTANCE|BUSINESS_RECORD|WORK_TASK") String ownerType,
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
