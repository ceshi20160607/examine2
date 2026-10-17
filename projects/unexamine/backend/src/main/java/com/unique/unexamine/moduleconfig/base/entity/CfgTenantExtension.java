package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户对基础模块的扩展草稿
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_tenant_extension")
public class CfgTenantExtension {

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
     * 扩展所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 扩展的基础模块标识
     */
    @TableField("base_module_id")
    private Long baseModuleId;

    /**
     * 草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 租户扩展草稿配置
     */
    @TableField("extension_json")
    private String extensionJson;

    /**
     * 生命周期状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 当前生效的租户扩展发布版本标识
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 最近切换发布版本的系统成员标识
     */
    @TableField("updated_by_member_id")
    private Long updatedByMemberId;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getBaseModuleId() {
        return baseModuleId;
    }

    public void setBaseModuleId(Long baseModuleId) {
        this.baseModuleId = baseModuleId;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getExtensionJson() {
        return extensionJson;
    }

    public void setExtensionJson(String extensionJson) {
        this.extensionJson = extensionJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Long getUpdatedByMemberId() {
        return updatedByMemberId;
    }

    public void setUpdatedByMemberId(Long updatedByMemberId) {
        this.updatedByMemberId = updatedByMemberId;
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
        return "CfgTenantExtension{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", baseModuleId = " + baseModuleId +
            ", draftRevision = " + draftRevision +
            ", extensionJson = " + extensionJson +
            ", status = " + status +
            ", currentVersionId = " + currentVersionId +
            ", updatedByMemberId = " + updatedByMemberId +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
