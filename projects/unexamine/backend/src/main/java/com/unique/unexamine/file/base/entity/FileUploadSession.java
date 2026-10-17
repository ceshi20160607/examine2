package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 大文件上传会话及目标范围
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_upload_session")
public class FileUploadSession {

    /**
     * 上传会话主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上传作用域：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 上传所属平台
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统上传所属系统，平台上传为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统上传所属租户，平台上传为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 实际写入的存储后端主键
     */
    @TableField("storage_backend_id")
    private Long storageBackendId;

    /**
     * 上传人的平台账号主键
     */
    @TableField("uploader_account_id")
    private Long uploaderAccountId;

    /**
     * 上传时的原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 客户端声明的媒体类型
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 上传完成后预期的文件字节数
     */
    @TableField("expected_size")
    private Long expectedSize;

    /**
     * 可选的完整文件 SHA-256 校验值
     */
    @TableField("expected_sha256")
    private String expectedSha256;

    /**
     * 存储后端内的目标对象键
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 上传会话状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 上传令牌哈希，不保存原始令牌
     */
    @TableField("upload_token_hash")
    private String uploadTokenHash;

    /**
     * 上传会话过期时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 上传完成时间
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
            ", expireTime = " + expireTime +
            ", completeTime = " + completeTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
