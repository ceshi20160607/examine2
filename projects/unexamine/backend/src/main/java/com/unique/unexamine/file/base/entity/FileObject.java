package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 本地文件内容的元数据与存储定位
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
     * 文件所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 文件所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 本地存储中随机生成的文件名
     */
    @TableField("storage_key")
    private String storageKey;

    /**
     * 上传时的原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 服务端识别的安全文件类型
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 文件实际字节数
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * 文件内容的 SHA-256 摘要
     */
    @TableField("sha256_hex")
    private String sha256Hex;

    /**
     * 文件可用或已移除状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 上传文件的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 文件上传时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 文件移除时间
     */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
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

    public String getSha256Hex() {
        return sha256Hex;
    }

    public void setSha256Hex(String sha256Hex) {
        this.sha256Hex = sha256Hex;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
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

    @Override
    public String toString() {
        return "FileObject{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", storageKey = " + storageKey +
            ", originalName = " + originalName +
            ", contentType = " + contentType +
            ", sizeBytes = " + sizeBytes +
            ", sha256Hex = " + sha256Hex +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", deleteTime = " + deleteTime +
            "}";
    }
}
