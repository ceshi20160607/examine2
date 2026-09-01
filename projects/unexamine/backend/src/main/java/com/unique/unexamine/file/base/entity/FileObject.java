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
@TableName("file_object")
public class FileObject {

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

    @TableField("upload_session_id")
    private Long uploadSessionId;

    @TableField("object_key")
    private String objectKey;

    @TableField("object_key_hash")
    private String objectKeyHash;

    @TableField("original_name")
    private String originalName;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("sha256")
    private String sha256;

    @TableField("scan_status")
    private String scanStatus;

    @TableField("preview_status")
    private String previewStatus;

    @TableField("`status`")
    private String status;

    @TableField("uploaded_by_account_id")
    private Long uploadedByAccountId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

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

    public Long getUploadSessionId() {
        return uploadSessionId;
    }

    public void setUploadSessionId(Long uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getObjectKeyHash() {
        return objectKeyHash;
    }

    public void setObjectKeyHash(String objectKeyHash) {
        this.objectKeyHash = objectKeyHash;
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

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
    }

    public String getPreviewStatus() {
        return previewStatus;
    }

    public void setPreviewStatus(String previewStatus) {
        this.previewStatus = previewStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUploadedByAccountId() {
        return uploadedByAccountId;
    }

    public void setUploadedByAccountId(Long uploadedByAccountId) {
        this.uploadedByAccountId = uploadedByAccountId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "FileObject{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", storageBackendId = " + storageBackendId +
            ", uploadSessionId = " + uploadSessionId +
            ", objectKey = " + objectKey +
            ", objectKeyHash = " + objectKeyHash +
            ", originalName = " + originalName +
            ", contentType = " + contentType +
            ", sizeBytes = " + sizeBytes +
            ", sha256 = " + sha256 +
            ", scanStatus = " + scanStatus +
            ", previewStatus = " + previewStatus +
            ", status = " + status +
            ", uploadedByAccountId = " + uploadedByAccountId +
            ", createdAt = " + createdAt +
            ", deletedAt = " + deletedAt +
            ", version = " + version +
            "}";
    }
}
