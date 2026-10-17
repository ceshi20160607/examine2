package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 文件与业务对象的同范围引用关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_reference")
public class FileReference {

    /**
     * 文件引用主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被引用的文件对象主键
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 引用作用域：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 引用所属平台
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 引用所属系统，平台引用为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 引用所属租户，平台引用为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 引用目标类型，由业务服务核验目标身份和权限
     */
    @TableField("owner_type")
    private String ownerType;

    /**
     * 引用目标在该类型下的稳定业务标识
     */
    @TableField("owner_id")
    private String ownerId;

    /**
     * 业务记录中所属附件字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 附件或业务关联类别
     */
    @TableField("reference_type")
    private String referenceType;

    /**
     * 建立引用的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
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

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
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

    @Override
    public String toString() {
        return "FileReference{" +
            "id = " + id +
            ", fileId = " + fileId +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", ownerType = " + ownerType +
            ", ownerId = " + ownerId +
            ", fieldCode = " + fieldCode +
            ", referenceType = " + referenceType +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            "}";
    }
}
