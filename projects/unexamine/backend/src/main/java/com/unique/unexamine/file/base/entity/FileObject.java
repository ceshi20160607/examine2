package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 可被业务引用的文件对象元数据
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_object")
public class FileObject {

    /**
     * 文件对象主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文件作用域：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 文件所属平台
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统文件所属系统，平台文件为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统文件所属租户，平台文件为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 文件字节所在存储后端主键
     */
    @TableField("storage_backend_id")
    private Long storageBackendId;

    /**
     * 原始上传会话主键；生成的预览产物可为空
     */
    @TableField("upload_session_id")
    private Long uploadSessionId;

    /**
     * 后端内存储对象键
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 对象键 SHA-256，用于稳定唯一约束
     */
    @TableField("object_key_hash")
    private String objectKeyHash;

    /**
     * 展示和下载时使用的文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 文件实际媒体类型
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 文件完整字节数
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * 完整文件 SHA-256 校验值
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 安全扫描结果状态
     */
    @TableField("scan_status")
    private String scanStatus;

    /**
     * 预览处理汇总状态，产物详见 un_file_preview
     */
    @TableField("preview_status")
    private String previewStatus;

    /**
     * 文件可用或删除状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 原文件上传人或预览产物生成主体账号
     */
    @TableField("uploaded_by_account_id")
    private Long uploadedByAccountId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 逻辑删除时间
     */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    /**
     * 乐观锁版本
     */
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getDeleteTime() {
        return deleteTime;
    }

    public void setDeleteTime(LocalDateTime deleteTime) {
        this.deleteTime = deleteTime;
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
            ", createTime = " + createTime +
            ", deleteTime = " + deleteTime +
            ", version = " + version +
            "}";
    }
}
