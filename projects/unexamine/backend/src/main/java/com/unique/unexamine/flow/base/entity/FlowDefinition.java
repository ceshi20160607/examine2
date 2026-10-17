package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 流程定义与草稿作用域
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_flow_definition")
public class FlowDefinition {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程所属上下文类型
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台ID
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统ID
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 配置来源租户ID
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 配置此流程的应用ID；系统后台配置为空
     */
    @TableField("owner_application_id")
    private Long ownerApplicationId;

    /**
     * 稳定业务编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 业务说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 当前业务状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建人平台账号ID
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最后修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
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

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getOwnerApplicationId() {
        return ownerApplicationId;
    }

    public void setOwnerApplicationId(Long ownerApplicationId) {
        this.ownerApplicationId = ownerApplicationId;
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
        return "FlowDefinition{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", ownerApplicationId = " + ownerApplicationId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", draftRevision = " + draftRevision +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
