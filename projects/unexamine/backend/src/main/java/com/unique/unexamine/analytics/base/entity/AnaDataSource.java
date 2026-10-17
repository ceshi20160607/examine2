package com.unique.unexamine.analytics.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 可复用数据源草稿及当前发布指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ana_data_source")
public class AnaDataSource {

    /**
     * 数据源主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 运行上下文，PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统数据源所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 租户扩展数据源所属租户主键，系统默认配置为空
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 唯一归属层级，PLATFORM、SYSTEM 或 TENANT
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 归属层级对应的平台、系统或租户主键
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 同一归属层级内稳定的数据源编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 数据源显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 数据源类型：平台能力、模块记录、模块关联及内部事项
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 当前草稿修订号
     */
    @TableField("draft_revision")
    private Integer draftRevision;

    /**
     * 输出字段、关联、筛选、公式与连接参数定义，不含密钥
     */
    @TableField("definition_json")
    private String definitionJson;

    /**
     * 数据范围及字段权限策略定义，执行时仍需复核当前权限
     */
    @TableField("permission_policy_json")
    private String permissionPolicyJson;

    /**
     * 当前发布的数据源版本主键，草稿修改不改变已发布版本
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 草稿、已发布或停用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建者账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

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

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Integer getDraftRevision() {
        return draftRevision;
    }

    public void setDraftRevision(Integer draftRevision) {
        this.draftRevision = draftRevision;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public void setDefinitionJson(String definitionJson) {
        this.definitionJson = definitionJson;
    }

    public String getPermissionPolicyJson() {
        return permissionPolicyJson;
    }

    public void setPermissionPolicyJson(String permissionPolicyJson) {
        this.permissionPolicyJson = permissionPolicyJson;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
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
        return "AnaDataSource{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", scopeType = " + scopeType +
            ", scopeOwnerId = " + scopeOwnerId +
            ", code = " + code +
            ", name = " + name +
            ", sourceType = " + sourceType +
            ", draftRevision = " + draftRevision +
            ", definitionJson = " + definitionJson +
            ", permissionPolicyJson = " + permissionPolicyJson +
            ", currentVersionId = " + currentVersionId +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
