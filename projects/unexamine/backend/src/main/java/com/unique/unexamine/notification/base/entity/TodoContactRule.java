package com.unique.unexamine.notification.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务模块日期字段的今日联系待办生成规则
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("todo_contact_rule")
public class TodoContactRule {

    /**
     * 今日联系规则主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 配置上下文类型：平台或系统
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 所属平台主键
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 所属系统主键，平台配置时为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户主键，平台配置时为空
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 平台、系统和租户组成的稳定配置范围键
     */
    @TableField("scope_key")
    private String scopeKey;

    /**
     * 规则显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 已发布业务模块编码
     */
    @TableField("module_code")
    private String moduleCode;

    /**
     * 保存规则时的业务模块名称快照
     */
    @TableField("module_name_snapshot")
    private String moduleNameSnapshot;

    /**
     * 用于计算联系日期的字段编码
     */
    @TableField("contact_field_code")
    private String contactFieldCode;

    /**
     * 保存规则时的日期字段名称快照
     */
    @TableField("contact_field_name_snapshot")
    private String contactFieldNameSnapshot;

    /**
     * 联系字段类型：日期或日期时间
     */
    @TableField("contact_field_type")
    private String contactFieldType;

    /**
     * 提前生成待办的天数，零表示字段日期当天
     */
    @TableField("lead_days")
    private Integer leadDays;

    /**
     * 生成待办的优先级
     */
    @TableField("priority")
    private String priority;

    /**
     * 规则状态：草稿、已发布或已停用
     */
    @TableField("`status`")
    private String status;

    /**
     * 创建规则的平台账号主键
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 规则创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 规则最后更新时间
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getModuleNameSnapshot() {
        return moduleNameSnapshot;
    }

    public void setModuleNameSnapshot(String moduleNameSnapshot) {
        this.moduleNameSnapshot = moduleNameSnapshot;
    }

    public String getContactFieldCode() {
        return contactFieldCode;
    }

    public void setContactFieldCode(String contactFieldCode) {
        this.contactFieldCode = contactFieldCode;
    }

    public String getContactFieldNameSnapshot() {
        return contactFieldNameSnapshot;
    }

    public void setContactFieldNameSnapshot(String contactFieldNameSnapshot) {
        this.contactFieldNameSnapshot = contactFieldNameSnapshot;
    }

    public String getContactFieldType() {
        return contactFieldType;
    }

    public void setContactFieldType(String contactFieldType) {
        this.contactFieldType = contactFieldType;
    }

    public Integer getLeadDays() {
        return leadDays;
    }

    public void setLeadDays(Integer leadDays) {
        this.leadDays = leadDays;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
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
        return "TodoContactRule{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", scopeKey = " + scopeKey +
            ", name = " + name +
            ", moduleCode = " + moduleCode +
            ", moduleNameSnapshot = " + moduleNameSnapshot +
            ", contactFieldCode = " + contactFieldCode +
            ", contactFieldNameSnapshot = " + contactFieldNameSnapshot +
            ", contactFieldType = " + contactFieldType +
            ", leadDays = " + leadDays +
            ", priority = " + priority +
            ", status = " + status +
            ", createdByAccountId = " + createdByAccountId +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
