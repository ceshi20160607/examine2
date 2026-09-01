package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("file_upload_session")
public class FileUploadSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("context_type")
    private String contextType;

    @TableField("platform_id")
    private Long platformId;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("storage_backend_id")
    private Long storageBackendId;

    @TableField("uploader_account_id")
    private Long uploaderAccountId;

    @TableField("original_name")
    private String originalName;

    @TableField("content_type")
    private String contentType;

    @TableField("expected_size")
    private Long expectedSize;

    @TableField("expected_sha256")
    private String expectedSha256;

    @TableField("object_key")
    private String objectKey;

    @TableField("`status`")
    private String status;

    @TableField("upload_token_hash")
    private String uploadTokenHash;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getStorageBackendId() {
        return storageBackendId;
    }

    public void setStorageBackendId(Long storageBackendId) {
        this.storageBackendId = storageBackendId;
    }

    public Long getUploaderAccountId() {
        return uploaderAccountId;
    }

    public void setUploaderAccountId(Long uploaderAccountId) {
        this.uploaderAccountId = uploaderAccountId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getExpectedSize() {
        return expectedSize;
    }

    public void setExpectedSize(Long expectedSize) {
        this.expectedSize = expectedSize;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public void setExpectedSha256(String expectedSha256) {
        this.expectedSha256 = expectedSha256;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUploadTokenHash() {
        return uploadTokenHash;
    }

    public void setUploadTokenHash(String uploadTokenHash) {
        this.uploadTokenHash = uploadTokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "FileUploadSession{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", storageBackendId = " + storageBackendId +
            ", uploaderAccountId = " + uploaderAccountId +
            ", originalName = " + originalName +
            ", contentType = " + contentType +
            ", expectedSize = " + expectedSize +
            ", expectedSha256 = " + expectedSha256 +
            ", objectKey = " + objectKey +
            ", status = " + status +
            ", uploadTokenHash = " + uploadTokenHash +
            ", expiresAt = " + expiresAt +
            ", completedAt = " + completedAt +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
