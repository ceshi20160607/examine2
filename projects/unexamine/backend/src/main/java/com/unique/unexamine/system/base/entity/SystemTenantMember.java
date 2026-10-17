package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户成员
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_tenant_member")
public class SystemTenantMember {

    /**
     * 租户成员主键
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
     * 对应系统成员标识
     */
    @TableField("system_member_id")
    private Long systemMemberId;

    /**
     * 所属租户部门标识
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 是否为租户管理员
     */
    @TableField("tenant_admin")
    private Boolean tenantAdmin;

    /**
     * 租户成员状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 租户成员创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 租户成员更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 租户成员乐观锁版本
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

    public Long getSystemMemberId() {
        return systemMemberId;
    }

    public void setSystemMemberId(Long systemMemberId) {
        this.systemMemberId = systemMemberId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Boolean getTenantAdmin() {
        return tenantAdmin;
    }

    public void setTenantAdmin(Boolean tenantAdmin) {
        this.tenantAdmin = tenantAdmin;
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
        return "SystemTenantMember{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", systemMemberId = " + systemMemberId +
            ", departmentId = " + departmentId +
            ", tenantAdmin = " + tenantAdmin +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
