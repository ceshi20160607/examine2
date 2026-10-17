package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块的基础审批流程定义
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow")
public class FlowDefinition {

    /**
     * 流程定义主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属主租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 绑定的业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 审批流程名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 流程状态，草稿或已发布
     */
    @TableField("`status`")
    private String status;

    /**
     * 当前生效的不可变发布版本主键
     */
    @TableField("published_version_id")
    private Long publishedVersionId;

    /**
     * 流程创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 流程更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    public void setPublishedVersionId(Long publishedVersionId) {
        this.publishedVersionId = publishedVersionId;
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

    @Override
    public String toString() {
        return "FlowDefinition{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", name = " + name +
            ", status = " + status +
            ", publishedVersionId = " + publishedVersionId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
