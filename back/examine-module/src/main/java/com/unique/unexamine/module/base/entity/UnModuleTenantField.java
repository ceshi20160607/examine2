package com.unique.unexamine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 租户对字段的扩展记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-21
 */
@TableName("un_module_tenant_field")
public class UnModuleTenantField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户字段扩展主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 基础业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 扩展字段的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 租户扩展模块字段主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 租户扩展字段发布状态
     */
    @TableField("status")
    private String status;

    /**
     * 租户扩展生效版本号
     */
    @TableField("published_version")
    private Integer publishedVersion;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
}
