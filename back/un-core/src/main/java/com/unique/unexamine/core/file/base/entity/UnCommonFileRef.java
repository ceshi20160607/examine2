package com.unique.unexamine.core.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 文件与多个业务对象的引用关系
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_common_file_ref")
public class UnCommonFileRef implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件业务引用主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被引用公共文件元数据主键
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 平台或系统引用作用域
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 系统业务引用所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统业务引用所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 模块记录任务工作日志等对象类型
     */
    @TableField("object_type")
    private String objectType;

    /**
     * 业务对象主键
     */
    @TableField("object_id")
    private Long objectId;

    /**
     * 模块字段引用时的字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 建立引用的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

    /**
     * 建立引用时间
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

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
