package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户组织部门
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_department")
public class SystemDepartment {

    /**
     * 租户组织部门主键
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
     * 上级租户部门标识
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 租户内部门编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 部门展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 部门层级编码路径
     */
    @TableField("path_code")
    private String pathCode;

    /**
     * 同级部门排序值
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 租户组织部门状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 租户组织部门创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 租户组织部门更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 租户组织部门乐观锁版本
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

    public String getPathCode() {
        return pathCode;
    }

    public void setPathCode(String pathCode) {
        this.pathCode = pathCode;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
        return "SystemDepartment{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", parentId = " + parentId +
            ", code = " + code +
            ", name = " + name +
            ", pathCode = " + pathCode +
            ", sortOrder = " + sortOrder +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
