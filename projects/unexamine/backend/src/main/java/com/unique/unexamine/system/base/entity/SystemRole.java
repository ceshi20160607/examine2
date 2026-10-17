package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户角色
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_role")
public class SystemRole {

    /**
     * 租户角色主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 租户内角色编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 角色展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 角色用途说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 是否为租户内置角色
     */
    @TableField("built_in")
    private Boolean builtIn;

    /**
     * 租户角色状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 租户角色创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 租户角色更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 租户角色乐观锁版本
     */
    @Version
    @TableField("version")
    private Integer version;

    /**
     * 待发布角色编码，为空表示没有待发布草稿
     */
    @TableField("draft_code")
    private String draftCode;

    /**
     * 待发布角色展示名称
     */
    @TableField("draft_name")
    private String draftName;

    /**
     * 待发布角色用途说明
     */
    @TableField("draft_description")
    private String draftDescription;

    /**
     * 待发布资源动作和数据范围配置
     */
    @TableField("draft_permissions_json")
    private String draftPermissionsJson;

    /**
     * 待发布字段与渠道权限配置
     */
    @TableField("draft_field_policies_json")
    private String draftFieldPoliciesJson;

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

    public Boolean getBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(Boolean builtIn) {
        this.builtIn = builtIn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getDraftCode() {
        return draftCode;
    }

    public void setDraftCode(String draftCode) {
        this.draftCode = draftCode;
    }

    public String getDraftName() {
        return draftName;
    }

    public void setDraftName(String draftName) {
        this.draftName = draftName;
    }

    public String getDraftDescription() {
        return draftDescription;
    }

    public void setDraftDescription(String draftDescription) {
        this.draftDescription = draftDescription;
    }

    public String getDraftPermissionsJson() {
        return draftPermissionsJson;
    }

    public void setDraftPermissionsJson(String draftPermissionsJson) {
        this.draftPermissionsJson = draftPermissionsJson;
    }

    public String getDraftFieldPoliciesJson() {
        return draftFieldPoliciesJson;
    }

    public void setDraftFieldPoliciesJson(String draftFieldPoliciesJson) {
        this.draftFieldPoliciesJson = draftFieldPoliciesJson;
    }

    @Override
    public String toString() {
        return "SystemRole{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", builtIn = " + builtIn +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            ", draftCode = " + draftCode +
            ", draftName = " + draftName +
            ", draftDescription = " + draftDescription +
            ", draftPermissionsJson = " + draftPermissionsJson +
            ", draftFieldPoliciesJson = " + draftFieldPoliciesJson +
            "}";
    }
}
