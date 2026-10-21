package com.unique.unexamine.common.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 公共文件记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_common_file")
public class UnCommonFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 公共文件主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 资源范围：platform、system或tenant
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 所属系统或租户主键，平台范围为0
     */
    @TableField("scope_id")
    private Long scopeId;

    /**
     * 上传时的原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 文件在存储中的唯一位置
     */
    @TableField("storage_key")
    private String storageKey;

    /**
     * 文件媒体类型
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 文件大小，单位字节
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 上传人对应范围内的成员主键
     */
    @TableField("uploader_id")
    private Long uploaderId;

    /**
     * 上传时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
