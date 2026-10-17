package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 文件预览状态及同范围衍生文件
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_preview")
public class FilePreview {

    /**
     * 文件预览任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 需要预览的原文件对象主键
     */
    @TableField("source_file_id")
    private Long sourceFileId;

    /**
     * 成功生成的预览文件对象主键
     */
    @TableField("preview_file_id")
    private Long previewFileId;

    /**
     * 预览所属作用域：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 预览所属平台
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 预览所属系统，平台预览为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 预览所属租户，平台预览为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 预览产物格式或处理方式
     */
    @TableField("preview_format")
    private String previewFormat;

    /**
     * 预览处理状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 预览失败原因编码
     */
    @TableField("failure_code")
    private String failureCode;

    /**
     * 预览任务创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 预览处理完成时间
     */
    @TableField("completed_time")
    private LocalDateTime completedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(Long sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public Long getPreviewFileId() {
        return previewFileId;
    }

    public void setPreviewFileId(Long previewFileId) {
        this.previewFileId = previewFileId;
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

    public String getPreviewFormat() {
        return previewFormat;
    }

    public void setPreviewFormat(String previewFormat) {
        this.previewFormat = previewFormat;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(LocalDateTime completedTime) {
        this.completedTime = completedTime;
    }

    @Override
    public String toString() {
        return "FilePreview{" +
            "id = " + id +
            ", sourceFileId = " + sourceFileId +
            ", previewFileId = " + previewFileId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", previewFormat = " + previewFormat +
            ", status = " + status +
            ", failureCode = " + failureCode +
            ", createdTime = " + createdTime +
            ", completedTime = " + completedTime +
            "}";
    }
}
