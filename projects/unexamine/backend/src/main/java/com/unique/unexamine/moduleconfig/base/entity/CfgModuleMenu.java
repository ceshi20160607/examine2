package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块菜单草稿配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_module_menu")
public class CfgModuleMenu {

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
     * 所属模块标识
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 上级配置项标识
     */
    @TableField("parent_id")
    private Long parentId;

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
     * 展示图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 菜单跳转路径
     */
    @TableField("route_path")
    private String routePath;

    /**
     * 显示排序值
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 是否在导航中显示
     */
    @TableField("`visible`")
    private Boolean visible;

    /**
     * 生命周期状态
     */
    @TableField("`status`")
    private String status;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
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

    @Override
    public String toString() {
        return "CfgModuleMenu{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", moduleId = " + moduleId +
            ", parentId = " + parentId +
            ", code = " + code +
            ", name = " + name +
            ", icon = " + icon +
            ", routePath = " + routePath +
            ", sortOrder = " + sortOrder +
            ", visible = " + visible +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
