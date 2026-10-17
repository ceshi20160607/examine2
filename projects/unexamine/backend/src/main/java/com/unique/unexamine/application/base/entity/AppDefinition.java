package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统范围内的受控访问应用定义
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_definition")
public class AppDefinition {

    /**
     * 应用主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 应用所属上下文：PLATFORM平台、SYSTEM系统
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统主键，平台应用为空
     */
    @TableField("owner_system_id")
    private Long ownerSystemId;

    /**
     * 所属租户主键，平台应用为空
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 当前上下文内唯一的应用编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 应用名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 应用用途说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 应用类型：SERVICE服务调用、WEBHOOK事件回调
     */
    @TableField("application_type")
    private String applicationType;

    /**
     * 应用预设图标代码
     */
    @TableField("icon_code")
    private String iconCode;

    /**
     * 草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 应用状态：DRAFT草稿、ACTIVE启用、DISABLED停用、DELETED删除
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建应用的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间，旧字段保持兼容
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间，旧字段保持兼容
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 应用修改的乐观锁版本
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

    public Long getOwnerSystemId() {
        return ownerSystemId;
    }

    public void setOwnerSystemId(Long ownerSystemId) {
        this.ownerSystemId = ownerSystemId;
    }

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getIconCode() {
        return iconCode;
    }

    public void setIconCode(String iconCode) {
        this.iconCode = iconCode;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
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
        return "AppDefinition{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", ownerSystemId = " + ownerSystemId +
            ", ownerTenantId = " + ownerTenantId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", applicationType = " + applicationType +
            ", iconCode = " + iconCode +
            ", draftRevision = " + draftRevision +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
