package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批定义
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_examine")
public class UnExamine implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批定义主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 定义审批的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属审批分组主键
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 审批业务表单所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 租户内稳定审批编码
     */
    @TableField("code")
    private String code;

    /**
     * 审批展示名称
     */
    @TableField("name")
    private String name;

    /**
     * 审批展示图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 审批业务说明
     */
    @TableField("description")
    private String description;

    /**
     * 审批入口显示顺序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 草稿发布停用或归档状态
     */
    @TableField("status")
    private String status;

    /**
     * 最近发布审批定义版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

    /**
     * 最近发布的节点人员字段和高级规则快照
     */
    @TableField("published_snapshot_json")
    private String publishedSnapshotJson;

    /**
     * 创建审批的平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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

    public String getPublishedSnapshotJson() {
        return publishedSnapshotJson;
    }

    public void setPublishedSnapshotJson(String publishedSnapshotJson) {
        this.publishedSnapshotJson = publishedSnapshotJson;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
