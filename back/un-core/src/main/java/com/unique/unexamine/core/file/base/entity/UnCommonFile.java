package com.unique.unexamine.core.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 公共文件元数据
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_common_file")
public class UnCommonFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件元数据主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台或系统作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统作用域的业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统作用域的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 附件所属业务模块主键，未关联记录时为空
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 附件所属模块业务记录主键，未关联记录时为空
     */
    @TableField("data_id")
    private Long dataId;

    /**
     * 同一附件字段共享的32位UUID批次标识
     */
    @TableField("batch_id")
    private String batchId;

    /**
     * 实际上传平台账号主键
     */
    @TableField("uploader_account_id")
    private Long uploaderAccountId;

    /**
     * 上传时的原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 文件内容类型
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 文件字节数
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * 文件内容摘要
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 本地或对象存储类型
     */
    @TableField("storage_type")
    private String storageType;

    /**
     * 存储对象键，不向普通接口直接暴露
     */
    @TableField("storage_key")
    private String storageKey;

    /**
     * 预览或缩略图对象键
     */
    @TableField("preview_key")
    private String previewKey;

    /**
     * 分片上传会话与合并校验信息
     */
    @TableField("upload_json")
    private String uploadJson;

    /**
     * 上传中可用或待清理状态
     */
    @TableField("status")
    private String status;

    /**
     * 文件登记时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最近状态更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getDataId() {
        return dataId;
    }

    public void setDataId(Long dataId) {
        this.dataId = dataId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
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

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getPreviewKey() {
        return previewKey;
    }

    public void setPreviewKey(String previewKey) {
        this.previewKey = previewKey;
    }

    public String getUploadJson() {
        return uploadJson;
    }

    public void setUploadJson(String uploadJson) {
        this.uploadJson = uploadJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
