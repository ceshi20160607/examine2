package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块草稿定义
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_module")
public class ConfiguredModule {

    /**
     * 主键标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 配置所属租户标识
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 所属模块分组标识
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 范围内稳定编码
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
     * 记录标题字段编码
     */
    @TableField("title_field_code")
    private String titleFieldCode;

    /**
     * 记录编号序列编码
     */
    @TableField("number_sequence_code")
    private String numberSequenceCode;

    /**
     * 记录状态字段编码
     */
    @TableField("status_field_code")
    private String statusFieldCode;

    /**
     * 生命周期状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 创建系统成员标识
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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

    public String getTitleFieldCode() {
        return titleFieldCode;
    }

    public void setTitleFieldCode(String titleFieldCode) {
        this.titleFieldCode = titleFieldCode;
    }

    public String getNumberSequenceCode() {
        return numberSequenceCode;
    }

    public void setNumberSequenceCode(String numberSequenceCode) {
        this.numberSequenceCode = numberSequenceCode;
    }

    public String getStatusFieldCode() {
        return statusFieldCode;
    }

    public void setStatusFieldCode(String statusFieldCode) {
        this.statusFieldCode = statusFieldCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
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
        return "ConfiguredModule{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", groupId = " + groupId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", titleFieldCode = " + titleFieldCode +
            ", numberSequenceCode = " + numberSequenceCode +
            ", statusFieldCode = " + statusFieldCode +
            ", status = " + status +
            ", draftRevision = " + draftRevision +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
